package fr.lyna.minion.entities;

import fr.lyna.minion.MinionPlugin;
import fr.lyna.minion.managers.MinionItemManager;
import fr.lyna.minion.utils.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class FarmerMinion {

    private final UUID uuid;
    private final UUID ownerUUID;
    private final Location spawnLocation;
    private final MinionPlugin plugin;

    private Villager villager;
    private final List<ItemStack> tools;

    private final Inventory inventory;
    private final Inventory upgrades;

    private Location targetLocation;
    private Location linkedChest;
    private MinionState state;
    private long lastActionTime;

    private int level;
    private long experience;
    private int prestige;

    // ✅ STATS AVANCÉES
    private final Map<Material, Long> harvestStats;
    private long creationTime; // ❌ Plus 'final' pour permettre le chargement
    private UUID leaderboardUuid;

    private final List<Map.Entry<Long, Integer>> harvestHistory;

    private final Set<Material> infiniteSeeds;
    private final Set<Material> selectedSeeds;
    private final Set<Material> voidFilter;

    private int seedRotationIndex = 0;
    private boolean chestChunkWasUnloaded = false;

    public enum MinionState {
        IDLE, MOVING_TO_CROP, HARVESTING, MOVING_TO_PLANT, PLANTING, MOVING_TO_CHEST, DEPOSITING
    }

    public FarmerMinion(UUID uuid, UUID ownerUUID, Location spawnLocation, MinionPlugin plugin) {
        this.uuid = uuid;
        this.ownerUUID = ownerUUID;
        this.spawnLocation = spawnLocation.clone();
        this.plugin = plugin;
        this.tools = new ArrayList<>();

        this.inventory = Bukkit.createInventory(null, 54, Component.text("Minion Storage"));
        this.upgrades = Bukkit.createInventory(null, 9, Component.text("Minion Upgrades"));

        this.state = MinionState.IDLE;
        this.lastActionTime = 0;
        this.level = 1;
        this.experience = 0;
        this.prestige = 0;

        // Init Stats
        this.harvestStats = new HashMap<>();
        this.harvestHistory = new ArrayList<>();
        this.creationTime = System.currentTimeMillis(); // Par défaut "maintenant", sera écrasé par le DataManager
        this.leaderboardUuid = null;

        this.infiniteSeeds = new HashSet<>();
        this.selectedSeeds = new HashSet<>();
        this.voidFilter = new HashSet<>();

        voidFilter.add(Material.POISONOUS_POTATO);
        voidFilter.add(Material.WHEAT_SEEDS);
        voidFilter.add(Material.BEETROOT_SEEDS);

        ItemStack defaultTool = plugin.getLevelManager().getDefaultTool();
        this.tools.add(defaultTool);

        updateInfiniteSeeds();
    }

    // --- GESTION STATS & LEADERBOARD ---

    public void addHarvestStat(Material material, int amount) {
        if (amount <= 0)
            return;
        harvestStats.put(material, harvestStats.getOrDefault(material, 0L) + amount);
        synchronized (harvestHistory) {
            harvestHistory.add(new AbstractMap.SimpleEntry<>(System.currentTimeMillis(), amount));
        }
        updateLeaderboardDisplay();
    }

    public Map<Material, Long> getHarvestStats() {
        return harvestStats;
    }

    public void setHarvestStats(Map<Material, Long> stats) {
        this.harvestStats.clear();
        this.harvestStats.putAll(stats);
    }

    public long getTotalHarvested() {
        return harvestStats.values().stream().mapToLong(Long::longValue).sum();
    }

    public long getCreationTime() {
        return creationTime;
    }

    // ✅ Setter ajouté pour restaurer la vraie date
    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public void setLeaderboardUuid(UUID uuid) {
        if (this.leaderboardUuid != null && !this.leaderboardUuid.equals(uuid)) {
            removeLeaderboard();
        }
        this.leaderboardUuid = uuid;
        updateLeaderboardDisplay();
    }

    public UUID getLeaderboardUuid() {
        return leaderboardUuid;
    }

    public void removeLeaderboard() {
        if (leaderboardUuid != null) {
            Entity entity = Bukkit.getEntity(leaderboardUuid);
            if (entity != null) {
                entity.remove();
                if (entity.getLocation().getWorld() != null) {
                    entity.getLocation().getWorld().playSound(entity.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                }
            }
            leaderboardUuid = null;
        }
    }

    private int getRealTimeHourlyRate() {
        long now = System.currentTimeMillis();
        int countLastMinute = 0;

        synchronized (harvestHistory) {
            harvestHistory.removeIf(entry -> (now - entry.getKey()) > 60000);
            for (Map.Entry<Long, Integer> entry : harvestHistory) {
                countLastMinute += entry.getValue();
            }
        }
        return countLastMinute * 60;
    }

    public void updateLeaderboardDisplay() {
        if (leaderboardUuid == null)
            return;

        // Astuce : On tente de charger le chunk si on doit mettre à jour l'affichage
        // Mais seulement si le minion est actif (chunk chargé)
        if (!spawnLocation.getChunk().isLoaded())
            return;

        Entity entity = Bukkit.getEntity(leaderboardUuid);
        if (entity instanceof TextDisplay display) {
            DecimalFormat df = new DecimalFormat("#,###");
            SimpleDateFormat dateFmt = new SimpleDateFormat("dd/MM/yyyy HH:mm");

            List<String> activeModules = getActiveModuleNames();
            String modulesStr = activeModules.isEmpty() ? "§7Aucun" : String.join("§7, ", activeModules);

            StringBuilder cropDetails = new StringBuilder();
            if (harvestStats.isEmpty()) {
                cropDetails.append("§7(Aucune récolte)");
            } else {
                harvestStats.entrySet().stream()
                        .sorted(Map.Entry.<Material, Long>comparingByValue().reversed())
                        .limit(5)
                        .forEach(e -> {
                            String name = formatMaterialName(e.getKey());
                            cropDetails.append("§f- ").append(name).append(": §a").append(df.format(e.getValue()))
                                    .append("\n");
                        });
            }

            String text = String.join("\n",
                    "§6§l⭐ STATISTIQUES MINION FERMIER ⭐",
                    "",
                    "§7Propriétaire: §e" + Bukkit.getOfflinePlayer(ownerUUID).getName(),
                    "§7Posé le: §b" + dateFmt.format(new Date(creationTime)),
                    "",
                    "§e§lPERFORMANCE",
                    "§7Vitesse Temps Réel: §a" + df.format(getRealTimeHourlyRate()) + " items/h",
                    "§7Total Récolté: §6" + df.format(getTotalHarvested()),
                    "",
                    "§e§lDÉTAIL RÉCOLTES",
                    cropDetails.toString(),
                    "",
                    "§e§lMODULES ACTIFS",
                    modulesStr);

            display.text(LegacyComponentSerializer.legacySection().deserialize(text));
        }
    }

    private List<String> getActiveModuleNames() {
        List<String> names = new ArrayList<>();
        MinionItemManager im = new MinionItemManager(plugin);

        if (hasVoidModule())
            names.add("§cVoid");
        boolean hasCompactor = false;
        for (ItemStack i : upgrades.getContents())
            if (i != null && im.isCompactor(i))
                hasCompactor = true;
        if (hasCompactor)
            names.add("§bCompacteur");

        int xpMult = getActiveXPMultiplier();
        if (xpMult > 1)
            names.add("§dXP x" + xpMult);

        int harvestMult = getActiveHarvestMultiplier();
        if (harvestMult > 1)
            names.add("§6Fortune x" + harvestMult);

        return names;
    }

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // --- FIN GESTION STATS ---

    public boolean hasVoidModule() {
        MinionItemManager itemManager = new MinionItemManager(plugin);
        for (ItemStack item : upgrades.getContents()) {
            if (item != null && itemManager.isVoidModule(item)) {
                return true;
            }
        }
        return false;
    }

    public boolean isVoidItem(Material material) {
        return voidFilter.contains(material);
    }

    public void toggleVoidItem(Material material) {
        if (voidFilter.contains(material)) {
            voidFilter.remove(material);
        } else {
            voidFilter.add(material);
        }
    }

    public Set<Material> getVoidFilter() {
        return new HashSet<>(voidFilter);
    }

    public void setVoidFilter(Set<Material> filter) {
        this.voidFilter.clear();
        this.voidFilter.addAll(filter);
    }

    public int getActiveXPMultiplier() {
        int multiplier = 1;
        MinionItemManager itemManager = new MinionItemManager(plugin);
        for (ItemStack item : upgrades.getContents()) {
            if (item != null && item.getType() != Material.AIR && itemManager.isXPPotion(item)) {
                int val = itemManager.getXPMultiplier(item);
                if (val > multiplier)
                    multiplier = val;
            }
        }
        return multiplier;
    }

    public int getActiveHarvestMultiplier() {
        int multiplier = 1;
        MinionItemManager itemManager = new MinionItemManager(plugin);
        for (ItemStack item : upgrades.getContents()) {
            if (item != null && item.getType() != Material.AIR && itemManager.isHarvestPotion(item)) {
                int val = itemManager.getHarvestMultiplier(item);
                if (val > multiplier)
                    multiplier = val;
            }
        }
        return multiplier;
    }

    public ItemStack toItemStack() {
        ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD)
                .setName("§6§lMinion Fermier " + (prestige > 0 ? "§e⭐" + prestige : ""))
                .setLore(
                        "§7Niveau: §e" + level,
                        "§7Prestige: §6" + prestige,
                        "§7XP: §a" + experience,
                        "",
                        "§7Place ce minion pour qu'il",
                        "§7continue son travail !",
                        "",
                        "§8(Toute la progression est conservée)");

        ItemStack item = builder.build();
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();

        data.set(new NamespacedKey(plugin, "saved_level"), PersistentDataType.INTEGER, level);
        data.set(new NamespacedKey(plugin, "saved_xp"), PersistentDataType.LONG, experience);
        data.set(new NamespacedKey(plugin, "saved_prestige"), PersistentDataType.INTEGER, prestige);

        data.set(new NamespacedKey(plugin, "saved_creation_time"), PersistentDataType.LONG, creationTime);

        if (!harvestStats.isEmpty()) {
            String statsStr = harvestStats.entrySet().stream()
                    .map(e -> e.getKey().name() + ":" + e.getValue())
                    .collect(Collectors.joining(","));
            data.set(new NamespacedKey(plugin, "saved_stats"), PersistentDataType.STRING, statsStr);
        }

        if (!selectedSeeds.isEmpty()) {
            String seedsData = selectedSeeds.stream().map(Material::name).collect(Collectors.joining(","));
            data.set(new NamespacedKey(plugin, "saved_seeds"), PersistentDataType.STRING, seedsData);
        }

        if (!voidFilter.isEmpty()) {
            String voidData = voidFilter.stream().map(Material::name).collect(Collectors.joining(","));
            data.set(new NamespacedKey(plugin, "saved_void"), PersistentDataType.STRING, voidData);
        }

        item.setItemMeta(meta);
        return item;
    }

    public void updateInfiniteSeeds() {
        infiniteSeeds.clear();
        List<Material> unlockedSeeds = plugin.getLevelManager().getUnlockedSeeds(level);
        infiniteSeeds.addAll(unlockedSeeds);
        if (selectedSeeds.isEmpty() && !infiniteSeeds.isEmpty())
            selectedSeeds.addAll(infiniteSeeds);
        selectedSeeds.retainAll(infiniteSeeds);
    }

    public boolean hasInfiniteSeed(Material seedType) {
        return infiniteSeeds.contains(seedType);
    }

    public boolean isSeedSelected(Material seedType) {
        return selectedSeeds.contains(seedType) && infiniteSeeds.contains(seedType);
    }

    public void toggleSeed(Material seedType) {
        if (!infiniteSeeds.contains(seedType))
            return;
        if (selectedSeeds.contains(seedType))
            selectedSeeds.remove(seedType);
        else
            selectedSeeds.add(seedType);
    }

    public void selectAllSeeds() {
        selectedSeeds.clear();
        selectedSeeds.addAll(infiniteSeeds);
    }

    public void deselectAllSeeds() {
        selectedSeeds.clear();
    }

    public ItemStack getInfiniteSeed(Material seedType) {
        if (!hasInfiniteSeed(seedType))
            return null;
        return new ItemStack(seedType, 1);
    }

    public Material getNextSeedInRotation() {
        if (selectedSeeds.isEmpty())
            return null;
        List<Material> seedList = new ArrayList<>(selectedSeeds);
        seedRotationIndex = seedRotationIndex % seedList.size();
        Material nextSeed = seedList.get(seedRotationIndex);
        seedRotationIndex++;
        return nextSeed;
    }

    public void resetSeedRotation() {
        seedRotationIndex = 0;
    }

    public boolean isChestChunkTemporarilyLoaded() {
        return chestChunkWasUnloaded;
    }

    public void setChestChunkTemporarilyLoaded(boolean value) {
        this.chestChunkWasUnloaded = value;
    }

    public void spawn() {
        if (spawnLocation.getWorld() == null)
            return;
        Chunk chunk = spawnLocation.getChunk();
        if (!chunk.isLoaded())
            chunk.load();

        if (villager != null && !villager.isDead())
            villager.remove();

        villager = spawnLocation.getWorld().spawn(spawnLocation, Villager.class, v -> {
            v.setAI(false);
            v.setCollidable(false);
            v.setSilent(true);
            v.setInvulnerable(true);
            v.customName(LegacyComponentSerializer.legacySection().deserialize(getDisplayName()));
            v.setCustomNameVisible(true);
            v.setProfession(Villager.Profession.FARMER);
            v.setVillagerLevel(5);
            v.getPersistentDataContainer().set(new NamespacedKey(plugin, "minion_uuid"), PersistentDataType.STRING,
                    uuid.toString());
            if (!tools.isEmpty() && tools.get(0) != null)
                v.getEquipment().setItemInMainHand(tools.get(0));
        });
    }

    public void relinkOrSpawn() {
        if (spawnLocation.getWorld() == null)
            return;
        NamespacedKey key = new NamespacedKey(plugin, "minion_uuid");
        Collection<Villager> nearby = spawnLocation.getWorld()
                .getNearbyEntities(spawnLocation, 5, 5, 5, e -> e instanceof Villager).stream().map(e -> (Villager) e)
                .toList();
        for (Villager v : nearby) {
            if (v.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                if (uuid.toString().equals(v.getPersistentDataContainer().get(key, PersistentDataType.STRING))) {
                    this.villager = v;
                    v.customName(LegacyComponentSerializer.legacySection().deserialize(getDisplayName()));
                    v.setCustomNameVisible(true);
                    updateInfiniteSeeds();
                    return;
                }
            }
        }
        spawn();
    }

    /**
     * ✅ Méthode appelée lors de la suppression définitive par le joueur
     * (Récupération)
     * Supprime le minion ET le leaderboard.
     */
    public void remove() {
        if (villager != null && !villager.isDead())
            villager.remove();
        removeLeaderboard();
    }

    /**
     * ✅ NOUVEAU: Méthode appelée lors de l'arrêt du serveur (Reload/Restart)
     * Supprime UNIQUEMENT le minion physique (Villager) pour éviter les glitchs,
     * MAIS GARDE le Leaderboard (TextDisplay) qui est persistant.
     */
    public void despawn() {
        if (villager != null && !villager.isDead())
            villager.remove();
        // On ne touche PAS au leaderboard ici !
    }

    public String getDisplayName() {
        String format = plugin.getConfig().getString("minion.name-format", "&6&l{stars}{prestige} &e{level} &7Fermier");
        String symbol = plugin.getConfig().getString("minion.prestige-symbol", "★");
        StringBuilder stars = new StringBuilder();
        if (prestige > 0) {
            stars.append(" ");
            for (int i = 0; i < prestige; i++)
                stars.append(symbol);
        }
        return format.replace("{stars}", stars.toString())
                .replace("{prestige}", prestige > 0 ? "" : "")
                .replace("{level}", String.valueOf(level))
                .replace("&", "§");
    }

    public void updateNameTag() {
        if (villager != null && !villager.isDead()) {
            villager.customName(LegacyComponentSerializer.legacySection().deserialize(getDisplayName()));
        }
        updateLeaderboardDisplay();
    }

    public boolean hasValidTool() {
        return !tools.isEmpty() && tools.get(0).getType().name().endsWith("_HOE");
    }

    public boolean isInventoryFull() {
        int maxSize = plugin.getLevelManager().getInventorySize(level);
        int usedSlots = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR)
                usedSlots++;
        }
        return usedSlots >= maxSize;
    }

    public boolean canPerformAction() {
        long currentTime = System.currentTimeMillis();
        int cooldown = plugin.getLevelManager().getCooldown(level);
        return (currentTime - lastActionTime) >= (cooldown * 50L);
    }

    public void markActionPerformed() {
        this.lastActionTime = System.currentTimeMillis();
    }

    public void addToInventory(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return;
        int maxSize = plugin.getLevelManager().getInventorySize(level);
        int usedSlots = 0;
        for (ItemStack slot : inventory.getContents()) {
            if (slot != null && slot.getType() != Material.AIR)
                usedSlots++;
        }
        if (usedSlots < maxSize)
            inventory.addItem(item);
    }

    public void addTool(ItemStack tool) {
        tools.clear();
        tools.add(tool.clone());
        if (villager != null && !villager.isDead())
            villager.getEquipment().setItemInMainHand(tool);
    }

    public void addExperience(String action) {
        plugin.getLevelManager().addXP(this, action);
        updateNameTag();
    }

    public org.bukkit.OfflinePlayer getOwner() {
        return Bukkit.getOfflinePlayer(ownerUUID);
    }

    public UUID getUuid() {
        return uuid;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public Location getLocation() {
        return villager != null ? villager.getLocation() : spawnLocation;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public Villager getVillager() {
        return villager;
    }

    public List<ItemStack> getTools() {
        return tools;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Inventory getUpgrades() {
        return upgrades;
    }

    public Location getTargetLocation() {
        return targetLocation;
    }

    public void setTargetLocation(Location target) {
        this.targetLocation = target;
    }

    public Location getLinkedChest() {
        return linkedChest;
    }

    public void setLinkedChest(Location chest) {
        this.linkedChest = chest;
    }

    public MinionState getState() {
        return state;
    }

    public void setState(MinionState state) {
        this.state = state;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
        updateNameTag();
        updateInfiniteSeeds();
    }

    public long getExperience() {
        return experience;
    }

    public void setExperience(long xp) {
        this.experience = xp;
    }

    public int getPrestige() {
        return prestige;
    }

    public void setPrestige(int prestige) {
        this.prestige = prestige;
        updateNameTag();
    }

    public Set<Material> getInfiniteSeeds() {
        return new HashSet<>(infiniteSeeds);
    }

    public Set<Material> getSelectedSeeds() {
        return new HashSet<>(selectedSeeds);
    }

    public void setSelectedSeeds(Set<Material> seeds) {
        this.selectedSeeds.clear();
        this.selectedSeeds.addAll(seeds);
    }
}