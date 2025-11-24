package fr.lyna.minion.managers;

import fr.lyna.minion.MinionPlugin;
import fr.lyna.minion.entities.FarmerMinion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DataManager {

    private final MinionPlugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    public DataManager(MinionPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "minions.yml");
        loadConfig();
    }

    private void loadConfig() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveConfig() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadAllMinions() {
        ConfigurationSection section = dataConfig.getConfigurationSection("minions");
        if (section == null)
            return;
        for (String key : section.getKeys(false)) {
            try {
                FarmerMinion minion = loadMinion(key);
                if (minion != null)
                    plugin.getMinionManager().addMinion(minion);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private FarmerMinion loadMinion(String uuidString) {
        String path = "minions." + uuidString;
        if (!dataConfig.contains(path + ".owner") || !dataConfig.contains(path + ".location"))
            return null;

        UUID uuid = UUID.fromString(uuidString);
        UUID ownerUUID = UUID.fromString(dataConfig.getString(path + ".owner"));
        Location location = deserializeLocation(dataConfig.getString(path + ".location"));
        if (location == null)
            return null;

        FarmerMinion minion = new FarmerMinion(uuid, ownerUUID, location, plugin);
        minion.setLevel(dataConfig.getInt(path + ".level", 1));
        minion.setExperience(dataConfig.getLong(path + ".experience", 0));
        minion.setPrestige(dataConfig.getInt(path + ".prestige", 0));

        // ✅ CHARGEMENT DATE CRÉATION
        long savedCreation = dataConfig.getLong(path + ".creation-time", 0);
        if (savedCreation > 0) {
            minion.setCreationTime(savedCreation);
        }

        String lbUuid = dataConfig.getString(path + ".leaderboard-uuid");
        if (lbUuid != null) {
            try {
                minion.setLeaderboardUuid(UUID.fromString(lbUuid));
            } catch (Exception ignored) {
            }
        }

        if (dataConfig.contains(path + ".harvest-stats")) {
            ConfigurationSection statsSection = dataConfig.getConfigurationSection(path + ".harvest-stats");
            Map<Material, Long> stats = new HashMap<>();
            if (statsSection != null) {
                for (String matName : statsSection.getKeys(false)) {
                    try {
                        Material mat = Material.valueOf(matName);
                        long count = statsSection.getLong(matName);
                        stats.put(mat, count);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            minion.setHarvestStats(stats);
        }

        List<?> inventoryList = dataConfig.getList(path + ".inventory");
        if (inventoryList != null) {
            for (Object obj : inventoryList)
                if (obj instanceof ItemStack stack)
                    minion.addToInventory(stack);
        }

        List<?> upgradesList = dataConfig.getList(path + ".upgrades");
        if (upgradesList != null) {
            Inventory upgradeInv = minion.getUpgrades();
            upgradeInv.clear();
            int index = 0;
            for (Object obj : upgradesList) {
                if (obj instanceof ItemStack stack) {
                    upgradeInv.setItem(index, stack);
                    index++;
                }
            }
        }

        String chestLoc = dataConfig.getString(path + ".linked-chest");
        if (chestLoc != null)
            minion.setLinkedChest(deserializeLocation(chestLoc));

        List<String> selectedSeedsList = dataConfig.getStringList(path + ".selected-seeds");
        if (!selectedSeedsList.isEmpty()) {
            Set<Material> seeds = new HashSet<>();
            for (String seedName : selectedSeedsList) {
                try {
                    seeds.add(Material.valueOf(seedName));
                } catch (Exception ignored) {
                }
            }
            minion.setSelectedSeeds(seeds);
        }

        List<String> voidList = dataConfig.getStringList(path + ".void-filter");
        if (voidList != null && !voidList.isEmpty()) {
            Set<Material> voids = new HashSet<>();
            for (String s : voidList) {
                try {
                    voids.add(Material.valueOf(s));
                } catch (Exception ignored) {
                }
            }
            minion.setVoidFilter(voids);
        }

        return minion;
    }

    public void saveMinion(FarmerMinion minion) {
        String path = "minions." + minion.getUuid().toString();
        dataConfig.set(path + ".owner", minion.getOwnerUUID().toString());
        dataConfig.set(path + ".location", serializeLocation(minion.getSpawnLocation()));
        dataConfig.set(path + ".level", minion.getLevel());
        dataConfig.set(path + ".experience", minion.getExperience());
        dataConfig.set(path + ".prestige", minion.getPrestige());

        // ✅ SAUVEGARDE DE LA DATE
        dataConfig.set(path + ".creation-time", minion.getCreationTime());

        if (minion.getLeaderboardUuid() != null) {
            dataConfig.set(path + ".leaderboard-uuid", minion.getLeaderboardUuid().toString());
        } else {
            dataConfig.set(path + ".leaderboard-uuid", null);
        }

        dataConfig.set(path + ".harvest-stats", null);
        if (!minion.getHarvestStats().isEmpty()) {
            for (Map.Entry<Material, Long> entry : minion.getHarvestStats().entrySet()) {
                dataConfig.set(path + ".harvest-stats." + entry.getKey().name(), entry.getValue());
            }
        }

        Inventory inv = minion.getInventory();
        List<ItemStack> invList = new ArrayList<>();
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR)
                invList.add(item.clone());
        }
        dataConfig.set(path + ".inventory", invList.isEmpty() ? null : invList);

        Inventory ups = minion.getUpgrades();
        List<ItemStack> upList = new ArrayList<>();
        boolean isEmpty = true;

        for (ItemStack item : ups.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                upList.add(item.clone());
                isEmpty = false;
            } else {
                upList.add(new ItemStack(Material.AIR));
            }
        }
        dataConfig.set(path + ".upgrades", isEmpty ? null : upList);

        if (minion.getLinkedChest() != null)
            dataConfig.set(path + ".linked-chest", serializeLocation(minion.getLinkedChest()));
        else
            dataConfig.set(path + ".linked-chest", null);

        List<String> selectedSeeds = new ArrayList<>();
        for (Material seed : minion.getSelectedSeeds())
            selectedSeeds.add(seed.name());
        dataConfig.set(path + ".selected-seeds", selectedSeeds.isEmpty() ? null : selectedSeeds);

        List<String> voidList = new ArrayList<>();
        for (Material m : minion.getVoidFilter()) {
            voidList.add(m.name());
        }
        dataConfig.set(path + ".void-filter", voidList.isEmpty() ? null : voidList);

        saveConfig();
    }

    public void deleteMinion(UUID uuid) {
        dataConfig.set("minions." + uuid.toString(), null);
        saveConfig();
    }

    public void saveAllMinions() {
        for (FarmerMinion minion : plugin.getMinionManager().getAllMinions())
            saveMinion(minion);
    }

    private String serializeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null)
            return null;
        return String.format(java.util.Locale.US, "%s,%.1f,%.1f,%.1f", loc.getWorld().getName(), loc.getX(), loc.getY(),
                loc.getZ());
    }

    private Location deserializeLocation(String str) {
        if (str == null || str.isEmpty())
            return null;
        try {
            String[] parts = str.split(",");
            if (parts.length != 4)
                return null;
            org.bukkit.World world = Bukkit.getWorld(parts[0]);
            if (world == null)
                return null;
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            return new Location(world, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }
}