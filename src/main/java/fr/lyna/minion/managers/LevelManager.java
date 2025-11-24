package fr.lyna.minion.managers;

import fr.lyna.minion.MinionPlugin;
import fr.lyna.minion.entities.FarmerMinion;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

public class LevelManager {

    private final MinionPlugin plugin;
    private FileConfiguration levelsConfig;

    // Cache
    private final Map<Integer, Long> xpRequirements = new HashMap<>();
    private final Map<Integer, Integer> cooldownsByLevel = new HashMap<>();
    private final Map<Integer, Double> harvestChances = new HashMap<>();
    private final Map<Integer, Integer> inventorySizes = new HashMap<>();
    private final Map<Integer, Integer> detectionRadii = new HashMap<>();
    private final Map<Integer, Integer> verticalRanges = new HashMap<>();
    private final Map<Integer, List<Material>> seedUnlocks = new HashMap<>();

    public LevelManager(MinionPlugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }

    private void loadConfiguration() {
        File levelsFile = new File(plugin.getDataFolder(), "levels.yml");
        if (!levelsFile.exists()) {
            plugin.saveResource("levels.yml", false);
        }
        levelsConfig = YamlConfiguration.loadConfiguration(levelsFile);
        precalculateValues();
    }

    private void precalculateValues() {
        double base = levelsConfig.getDouble("xp-formula.base", 500);
        double exponent = levelsConfig.getDouble("xp-formula.exponent", 2.3);
        double linear = levelsConfig.getDouble("xp-formula.linear", 250);

        for (int i = 1; i <= 100; i++) {
            long xp = (long) (base * Math.pow(i, exponent) + (i * linear));
            xpRequirements.put(i, xp);
        }

        loadBonusMap(cooldownsByLevel, "cooldowns", 60);
        loadBonusMapDouble(harvestChances, "level-bonuses.harvest-multiplier-chance", 0.0);
        loadBonusMap(inventorySizes, "level-bonuses.inventory-size", 9);
        loadBonusMap(detectionRadii, "level-bonuses.detection-radius", 5);
        loadBonusMap(verticalRanges, "level-bonuses.vertical-range", 3);

        ConfigurationSection unlockSection = levelsConfig.getConfigurationSection("seed-unlocks");
        if (unlockSection != null) {
            for (String key : unlockSection.getKeys(false)) {
                int level = Integer.parseInt(key);
                List<String> materialNames = unlockSection.getStringList(key);
                List<Material> materials = new ArrayList<>();
                for (String name : materialNames) {
                    try {
                        materials.add(Material.valueOf(name));
                    } catch (Exception ignored) {
                    }
                }
                seedUnlocks.put(level, materials);
            }
        }
    }

    private void loadBonusMap(Map<Integer, Integer> map, String path, int defaultValue) {
        ConfigurationSection section = levelsConfig.getConfigurationSection(path);
        int lastValue = defaultValue;
        for (int i = 1; i <= 100; i++) {
            if (section != null && section.contains(String.valueOf(i))) {
                lastValue = section.getInt(String.valueOf(i));
            }
            map.put(i, lastValue);
        }
    }

    private void loadBonusMapDouble(Map<Integer, Double> map, String path, double defaultValue) {
        ConfigurationSection section = levelsConfig.getConfigurationSection(path);
        double lastValue = defaultValue;
        for (int i = 1; i <= 100; i++) {
            if (section != null && section.contains(String.valueOf(i))) {
                lastValue = section.getDouble(String.valueOf(i));
            }
            map.put(i, lastValue);
        }
    }

    public long getXPRequired(int level) {
        return xpRequirements.getOrDefault(level, Long.MAX_VALUE);
    }

    // ✅ NOUVELLE MÉTHODE : Permet au GUI de récupérer la base XP
    public int getBaseXP(String action) {
        return levelsConfig.getInt("xp-gains." + action, 0);
    }

    public int getCooldown(int level) {
        return cooldownsByLevel.getOrDefault(level, 60);
    }

    public int getDetectionRadius(int level) {
        int r = detectionRadii.getOrDefault(level, 4);
        return r < 1 ? 4 : r;
    }

    public int getVerticalRange(int level) {
        int r = verticalRanges.getOrDefault(level, 3);
        return r < 1 ? 3 : r;
    }

    public int getInventorySize(int level) {
        return inventorySizes.getOrDefault(level, 9);
    }

    public double getHarvestMultiplierChance(int level) {
        return harvestChances.getOrDefault(level, 0.0);
    }

    public List<Material> getUnlockedSeeds(int level) {
        List<Material> unlocked = new ArrayList<>();
        for (Map.Entry<Integer, List<Material>> entry : seedUnlocks.entrySet()) {
            if (level >= entry.getKey())
                unlocked.addAll(entry.getValue());
        }
        return unlocked;
    }

    public boolean canPlant(FarmerMinion minion, Material seedType) {
        return getUnlockedSeeds(minion.getLevel()).contains(seedType);
    }

    public void addXP(FarmerMinion minion, String action) {
        int baseXP = getBaseXP(action);
        if (baseXP <= 0)
            return;

        // Récupération du multiplicateur actif (x2, x4... x12 ou x1 par défaut)
        int multiplier = minion.getActiveXPMultiplier();

        // Calcul final
        long finalXP = (long) baseXP * multiplier;

        long currentXP = minion.getExperience();
        minion.setExperience(currentXP + finalXP);
        checkLevelUp(minion);
    }

    private void checkLevelUp(FarmerMinion minion) {
        int currentLevel = minion.getLevel();
        if (currentLevel >= 100)
            return;
        long xpRequired = getXPRequired(currentLevel + 1);
        if (minion.getExperience() >= xpRequired) {
            minion.setLevel(currentLevel + 1);
            minion.setExperience(minion.getExperience() - xpRequired);
            if (minion.getOwner() != null && minion.getOwner().isOnline()) {
                String msg = levelsConfig.getString("messages.level-up", "&aNiveau sup !")
                        .replace("{level}", String.valueOf(minion.getLevel()));
                minion.getOwner().getPlayer().sendMessage(plugin.colorize(msg));
                minion.getLocation().getWorld().playSound(minion.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP,
                        1f, 1f);
            }
            plugin.getDataManager().saveMinion(minion);
            checkLevelUp(minion);
        }
    }

    public ItemStack getDefaultTool() {
        String mat = levelsConfig.getString("default-tool.material", "WOODEN_HOE");
        ItemStack tool = new ItemStack(Material.valueOf(mat));
        ConfigurationSection enchants = levelsConfig.getConfigurationSection("default-tool.enchantments");
        if (enchants != null) {
            for (String key : enchants.getKeys(false)) {
                Enchantment enchantment = getEnchantmentSafe(key);
                if (enchantment != null) {
                    tool.addUnsafeEnchantment(enchantment, enchants.getInt(key));
                } else {
                    plugin.getLogger().warning("Enchantement inconnu dans levels.yml : " + key);
                }
            }
        }
        return tool;
    }

    private Enchantment getEnchantmentSafe(String name) {
        if (name == null || name.isEmpty())
            return null;

        Registry<Enchantment> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);

        try {
            NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
            if (key != null) {
                Enchantment ench = registry.get(key);
                if (ench != null)
                    return ench;
            }
        } catch (Exception ignored) {
        }

        try {
            String standardKey = name.toLowerCase(Locale.ROOT);
            NamespacedKey key = NamespacedKey.minecraft(standardKey);
            Enchantment ench = registry.get(key);
            if (ench != null)
                return ench;
        } catch (Exception ignored) {
        }

        for (Enchantment e : registry) {
            if (e.getKey().value().equalsIgnoreCase(name))
                return e;
        }

        return null;
    }

    public String getProgressBar(long current, long required, int bars) {
        float percent = (float) current / required;
        int progress = (int) (bars * percent);
        StringBuilder sb = new StringBuilder("&a");
        for (int i = 0; i < bars; i++) {
            if (i == progress)
                sb.append("&7");
            sb.append("|");
        }
        return sb.toString();
    }

    public void reload() {
        xpRequirements.clear();
        cooldownsByLevel.clear();
        harvestChances.clear();
        inventorySizes.clear();
        detectionRadii.clear();
        verticalRanges.clear();
        seedUnlocks.clear();
        loadConfiguration();
    }
}