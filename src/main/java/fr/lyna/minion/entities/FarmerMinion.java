package fr.lyna.minion.entities;

import fr.lyna.minion.MinionPlugin;
import fr.lyna.minion.utils.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.stream.Collectors;

public class FarmerMinion {

    private final UUID uuid;
    private final UUID ownerUUID;
    private final Location spawnLocation;
    private final MinionPlugin plugin;

    private Villager villager;
    private final List<ItemStack> tools;

    // Inventaires
    private final Inventory inventory;
    private final Inventory upgrades;

    private Location targetLocation;
    private Location linkedChest;
    private MinionState state;
    private long lastActionTime;

    private int level;
    private long experience;
    private int prestige;

    private final Set<Material> infiniteSeeds;
    private final Set<Material> selectedSeeds;
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

        // ✅ FIX 1.21 : Titres en Component
        this.inventory = Bukkit.createInventory(null, 54, Component.text("Minion Storage"));
        this.upgrades = Bukkit.createInventory(null, 9, Component.text("Minion Upgrades"));

        this.state = MinionState.IDLE;
        this.lastActionTime = 0;
        this.level = 1;
        this.experience = 0;
        this.prestige = 0;

        this.infiniteSeeds = new HashSet<>();
        this.selectedSeeds = new HashSet<>();

        ItemStack defaultTool = plugin.getLevelManager().getDefaultTool();
        this.tools.add(defaultTool);

        updateInfiniteSeeds();
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

        if (!selectedSeeds.isEmpty()) {
            String seedsData = selectedSeeds.stream().map(Material::name).collect(Collectors.joining(","));
            data.set(new NamespacedKey(plugin, "saved_seeds"), PersistentDataType.STRING, seedsData);
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
            // ✅ FIX 1.21 : Utilisation de customName(Component)
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
                    // ✅ FIX 1.21
                    v.customName(LegacyComponentSerializer.legacySection().deserialize(getDisplayName()));
                    v.setCustomNameVisible(true);
                    updateInfiniteSeeds();
                    return;
                }
            }
        }
        spawn();
    }

    public void remove() {
        if (villager != null && !villager.isDead())
            villager.remove();
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
            // ✅ FIX 1.21
            villager.customName(LegacyComponentSerializer.legacySection().deserialize(getDisplayName()));
        }
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