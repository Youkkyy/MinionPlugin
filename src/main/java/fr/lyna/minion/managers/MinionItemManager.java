package fr.lyna.minion.managers;

import fr.lyna.minion.MinionPlugin;
import fr.lyna.minion.utils.ItemBuilder;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MinionItemManager {

    private final MinionPlugin plugin;

    // Clés NBT
    public static final String KEY_UPGRADE_TYPE = "minion_upgrade_type";
    public static final String KEY_XP_MULTIPLIER = "minion_xp_multiplier";
    public static final String KEY_HARVEST_MULTIPLIER = "minion_harvest_multiplier";
    public static final String KEY_LEADERBOARD_PLACER = "minion_leaderboard_placer";
    public static final String KEY_AUTOSELL_TIER = "minion_autosell_tier";
    public static final String KEY_FUEL_DURATION = "minion_fuel_duration"; // Nouvelle clé

    public MinionItemManager(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    // --- COMPACTEUR ---
    public ItemStack getCompactor() {
        return buildConfigItem("items.compactor", "COMPACTOR");
    }

    public boolean isCompactor(ItemStack item) {
        return checkUpgradeType(item, "COMPACTOR");
    }

    // --- MODULE DE NÉANT ---
    public ItemStack getVoidModule() {
        return buildConfigItem("items.void-module", "VOID_MODULE");
    }

    public boolean isVoidModule(ItemStack item) {
        return checkUpgradeType(item, "VOID_MODULE");
    }

    // --- MODULE AUTO-SELL ---
    public ItemStack getAutoSellModule(int tier) {
        if (tier < 1)
            tier = 1;
        if (tier > 3)
            tier = 3;

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("items.auto-sell");
        if (sec == null)
            return new ItemStack(Material.IRON_NUGGET);

        String matName = sec.getString("material." + tier, "IRON_NUGGET");
        Material mat = Material.getMaterial(matName);
        if (mat == null)
            mat = Material.IRON_NUGGET;

        int percent = plugin.getConfig().getInt("auto-sell.tiers." + tier, 25);
        String name = sec.getString("name", "Module Vente").replace("{tier}", String.valueOf(tier));
        List<String> lore = replacePlaceholders(sec.getStringList("lore"), "{percent}", String.valueOf(percent));

        ItemStack item = new ItemBuilder(mat)
                .setName(name)
                .setLore(lore)
                .setGlowing(tier > 1)
                .build();

        ItemStack tagged = tagItem(item, "AUTOSELL_MODULE");
        org.bukkit.inventory.meta.ItemMeta meta = tagged.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, KEY_AUTOSELL_TIER), PersistentDataType.INTEGER,
                tier);
        tagged.setItemMeta(meta);
        return tagged;
    }

    public boolean isAutoSellModule(ItemStack item) {
        return checkUpgradeType(item, "AUTOSELL_MODULE");
    }

    public int getAutoSellTier(ItemStack item) {
        if (!isAutoSellModule(item))
            return 0;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(new NamespacedKey(plugin, KEY_AUTOSELL_TIER), PersistentDataType.INTEGER, 0);
    }

    // --- CARBURANT (FUEL) ---
    public ItemStack getFuelItem(int tier) {
        if (tier < 1)
            tier = 1;
        if (tier > 6)
            tier = 6;

        int hours = plugin.getConfig().getInt("fuel.durations." + tier, 2);
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("items.fuel");

        Material mat = Material.CHARCOAL;
        String name = "&eCarburant";
        List<String> lore = new ArrayList<>();

        if (sec != null) {
            mat = Material.valueOf(sec.getString("material", "CHARCOAL"));
            name = sec.getString("name", "&eCarburant").replace("{hours}", String.valueOf(hours));
            lore = replacePlaceholders(sec.getStringList("lore"), "{hours}", String.valueOf(hours));
        }

        ItemStack item = new ItemBuilder(mat)
                .setName(name)
                .setLore(lore)
                .setGlowing(true)
                .build();

        ItemStack tagged = tagItem(item, "FUEL_ITEM");
        // On stocke la durée en millisecondes
        long durationMillis = hours * 3600L * 1000L;

        org.bukkit.inventory.meta.ItemMeta meta = tagged.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, KEY_FUEL_DURATION), PersistentDataType.LONG,
                durationMillis);
        tagged.setItemMeta(meta);

        return tagged;
    }

    public boolean isFuelItem(ItemStack item) {
        return checkUpgradeType(item, "FUEL_ITEM");
    }

    public long getFuelDuration(ItemStack item) {
        if (!isFuelItem(item))
            return 0;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(new NamespacedKey(plugin, KEY_FUEL_DURATION), PersistentDataType.LONG, 0L);
    }

    // --- POTIONS XP ---
    public ItemStack getXPPotion(int tier) {
        if (tier < 1)
            tier = 1;
        if (tier > 6)
            tier = 6;

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("items.xp-potion");
        String name = sec != null ? sec.getString("name", "Potion XP").replace("{multiplier}", String.valueOf(tier))
                : "Potion XP";
        List<String> lore = sec != null
                ? replacePlaceholders(sec.getStringList("lore"), "{multiplier}", String.valueOf(tier))
                : new ArrayList<>();

        Color color = getColorForTier(tier);
        ItemStack item = new ItemBuilder(Material.POTION)
                .setName(name)
                .setLore(lore)
                .addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES)
                .build();

        if (item.getItemMeta() instanceof PotionMeta pm) {
            pm.setColor(color);
            item.setItemMeta(pm);
        }

        ItemStack tagged = tagItem(item, "XP_BOOST");
        org.bukkit.inventory.meta.ItemMeta meta = tagged.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, KEY_XP_MULTIPLIER), PersistentDataType.INTEGER,
                tier);
        tagged.setItemMeta(meta);
        return tagged;
    }

    // --- POTIONS HARVEST ---
    public ItemStack getHarvestPotion(int tier) {
        if (tier < 1)
            tier = 1;
        if (tier > 6)
            tier = 6;
        int multiplier = tier * 2;

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("items.harvest-potion");
        String name = sec != null
                ? sec.getString("name", "Potion Fortune").replace("{multiplier}", String.valueOf(multiplier))
                : "Potion Fortune";
        List<String> lore = sec != null
                ? replacePlaceholders(sec.getStringList("lore"), "{multiplier}", String.valueOf(multiplier))
                : new ArrayList<>();

        Color color = getColorForTier(tier); // Réutilise les mêmes couleurs
        ItemStack item = new ItemBuilder(Material.POTION)
                .setName(name)
                .setLore(lore)
                .addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES)
                .build();

        if (item.getItemMeta() instanceof PotionMeta pm) {
            pm.setColor(color);
            item.setItemMeta(pm);
        }

        ItemStack tagged = tagItem(item, "HARVEST_BOOST");
        org.bukkit.inventory.meta.ItemMeta meta = tagged.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, KEY_HARVEST_MULTIPLIER),
                PersistentDataType.INTEGER, multiplier);
        tagged.setItemMeta(meta);
        return tagged;
    }

    public boolean isXPPotion(ItemStack item) {
        return checkUpgradeType(item, "XP_BOOST");
    }

    public int getXPMultiplier(ItemStack item) {
        if (!isXPPotion(item))
            return 0;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(new NamespacedKey(plugin, KEY_XP_MULTIPLIER), PersistentDataType.INTEGER, 1);
    }

    public boolean isHarvestPotion(ItemStack item) {
        return checkUpgradeType(item, "HARVEST_BOOST");
    }

    public int getHarvestMultiplier(ItemStack item) {
        if (!isHarvestPotion(item))
            return 0;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(new NamespacedKey(plugin, KEY_HARVEST_MULTIPLIER), PersistentDataType.INTEGER, 1);
    }

    // --- LEADERBOARD PLACER ---
    public ItemStack getLeaderboardPlacer(UUID minionUUID) {
        ItemStack item = new ItemBuilder(Material.OAK_SIGN)
                .setName("§e§lPanneau de Statistiques")
                .setLore("§7Place ce panneau pour voir", "§7les stats de ton minion !", "", "§eClic droit pour placer")
                .setGlowing(true)
                .build();

        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, KEY_LEADERBOARD_PLACER),
                PersistentDataType.STRING, minionUUID.toString());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isLeaderboardPlacer(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, KEY_LEADERBOARD_PLACER), PersistentDataType.STRING);
    }

    public UUID getMinionUUIDFromPlacer(ItemStack item) {
        if (!isLeaderboardPlacer(item))
            return null;
        try {
            return UUID.fromString(item.getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey(plugin, KEY_LEADERBOARD_PLACER), PersistentDataType.STRING));
        } catch (Exception e) {
            return null;
        }
    }

    // --- UTILITAIRES INTERNES ---
    public boolean isValidUpgrade(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, KEY_UPGRADE_TYPE),
                PersistentDataType.STRING);
    }

    private ItemStack buildConfigItem(String path, String typeTag) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(path);
        Material mat = Material.STONE;
        String name = "Item Inconnu";
        List<String> lore = new ArrayList<>();

        if (sec != null) {
            mat = Material.valueOf(sec.getString("material", "STONE"));
            name = sec.getString("name", "Item");
            lore = sec.getStringList("lore");
        }

        ItemStack item = new ItemBuilder(mat).setName(name).setLore(lore).setGlowing(true).build();
        return tagItem(item, typeTag);
    }

    private ItemStack tagItem(ItemStack item, String type) {
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, KEY_UPGRADE_TYPE), PersistentDataType.STRING,
                type);
        item.setItemMeta(meta);
        return item;
    }

    private boolean checkUpgradeType(ItemStack item, String expectedType) {
        if (item == null || !item.hasItemMeta())
            return false;
        String type = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, KEY_UPGRADE_TYPE),
                PersistentDataType.STRING);
        return expectedType.equals(type);
    }

    private List<String> replacePlaceholders(List<String> list, String placeholder, String value) {
        List<String> result = new ArrayList<>();
        for (String s : list)
            result.add(s.replace(placeholder, value));
        return result;
    }

    private Color getColorForTier(int tier) {
        return switch (tier) {
            case 1 -> Color.WHITE;
            case 2 -> Color.LIME;
            case 3 -> Color.AQUA;
            case 4 -> Color.PURPLE;
            case 5 -> Color.ORANGE;
            case 6 -> Color.RED;
            default -> Color.WHITE;
        };
    }
}