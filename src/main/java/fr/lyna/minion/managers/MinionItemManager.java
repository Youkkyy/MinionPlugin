package fr.lyna.minion.managers;

import fr.lyna.minion.MinionPlugin;
import fr.lyna.minion.utils.ItemBuilder;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class MinionItemManager {

    private final MinionPlugin plugin;

    // Clés NBT pour identifier les items spéciaux
    public static final String KEY_UPGRADE_TYPE = "minion_upgrade_type";
    public static final String KEY_XP_MULTIPLIER = "minion_xp_multiplier";
    public static final String KEY_HARVEST_MULTIPLIER = "minion_harvest_multiplier";
    public static final String KEY_LEADERBOARD_PLACER = "minion_leaderboard_placer";

    public MinionItemManager(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    // --- COMPACTEUR ---
    public ItemStack getCompactor() {
        ItemStack item = new ItemBuilder(Material.PISTON)
                .setName("§6§lCompacteur Automatique")
                .setLore("§7Transforme automatiquement les", "§7ressources en blocs.", "", "§eModule d'amélioration")
                .setGlowing(true).build();
        return tagItem(item, "COMPACTOR");
    }

    public boolean isCompactor(ItemStack item) {
        return checkUpgradeType(item, "COMPACTOR");
    }

    // --- MODULE DE NÉANT (VOID) ---
    public ItemStack getVoidModule() {
        ItemStack item = new ItemBuilder(Material.MAGMA_CREAM)
                .setName("§c§lModule de Néant")
                .setLore(
                        "§7Détruit automatiquement les items",
                        "§7indésirables (Graines, déchets...).",
                        "",
                        "§bCliquable dans le menu pour",
                        "§bconfigurer le filtre.",
                        "",
                        "§eModule d'amélioration")
                .setGlowing(true).build();
        return tagItem(item, "VOID_MODULE");
    }

    public boolean isVoidModule(ItemStack item) {
        return checkUpgradeType(item, "VOID_MODULE");
    }

    // --- ✅ ITEM DE PLACEMENT LEADERBOARD ---
    /**
     * Crée l'item qui permet au joueur de placer le panneau de stats.
     * L'UUID du minion est stocké dans l'item pour lier le panneau au bon minion.
     */
    public ItemStack getLeaderboardPlacer(UUID minionUUID) {
        ItemStack item = new ItemBuilder(Material.OAK_SIGN)
                .setName("§e§lPanneau de Statistiques")
                .setLore(
                        "§7Affiche les stats de ton minion",
                        "§7en temps réel dans le monde.",
                        "",
                        "§e⚡ Clique Droit sur un bloc",
                        "§epour placer le panneau !",
                        "",
                        "§8(Uniquement dans le même monde)")
                .setGlowing(true)
                .build();

        // On stocke l'UUID du minion cible dans les données de l'item
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, KEY_LEADERBOARD_PLACER),
                PersistentDataType.STRING, minionUUID.toString());
        item.setItemMeta(meta);

        return item;
    }

    public boolean isLeaderboardPlacer(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, KEY_LEADERBOARD_PLACER),
                PersistentDataType.STRING);
    }

    public UUID getMinionUUIDFromPlacer(ItemStack item) {
        if (!isLeaderboardPlacer(item))
            return null;
        try {
            String str = item.getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey(plugin, KEY_LEADERBOARD_PLACER), PersistentDataType.STRING);
            return UUID.fromString(str);
        } catch (Exception e) {
            return null;
        }
    }

    // --- POTIONS D'XP ---
    public ItemStack getXPPotion(int tier) {
        if (tier < 1)
            tier = 1;
        if (tier > 6)
            tier = 6;

        String name = switch (tier) {
            case 1 -> "§fElixir du Débutant §7(x1)";
            case 2 -> "§aPotion de Savoir §7(x2)";
            case 3 -> "§bEssence d'Expérience §7(x3)";
            case 4 -> "§dInfusion Mystique §7(x4)";
            case 5 -> "§6Nectar des Dieux §7(x5)";
            case 6 -> "§c§lSang de Titan §7(x6)";
            default -> "§fPotion d'XP";
        };

        Color color = switch (tier) {
            case 1 -> Color.WHITE;
            case 2 -> Color.LIME;
            case 3 -> Color.AQUA;
            case 4 -> Color.PURPLE;
            case 5 -> Color.ORANGE;
            case 6 -> Color.RED;
            default -> Color.WHITE;
        };

        ItemStack item = new ItemBuilder(Material.POTION)
                .setName(name)
                .setLore(
                        "§7Augmente le gain d'XP du minion.",
                        "§7Bonus: §e" + tier + "x plus rapide",
                        "",
                        "§c⚠ Une seule potion à la fois !",
                        "§eModule d'amélioration")
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

    public boolean isXPPotion(ItemStack item) {
        return checkUpgradeType(item, "XP_BOOST");
    }

    public int getXPMultiplier(ItemStack item) {
        if (!isXPPotion(item))
            return 0;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(new NamespacedKey(plugin, KEY_XP_MULTIPLIER), PersistentDataType.INTEGER, 1);
    }

    // --- POTIONS DE RÉCOLTE ---
    public ItemStack getHarvestPotion(int tier) {
        if (tier < 1)
            tier = 1;
        if (tier > 6)
            tier = 6;
        int multiplier = tier * 2;

        String name = switch (tier) {
            case 1 -> "§eFertilisant Basique §7(x2)";
            case 2 -> "§aEngrais Concentré §7(x4)";
            case 3 -> "§bSérum d'Abondance §7(x6)";
            case 4 -> "§dExtrait de Croissance §7(x8)";
            case 5 -> "§6Elixir de Moisson §7(x10)";
            case 6 -> "§c§lEssence de Gaia §7(x12)";
            default -> "§fPotion de Récolte";
        };

        Color color = switch (tier) {
            case 1 -> Color.YELLOW;
            case 2 -> Color.TEAL;
            case 3 -> Color.BLUE;
            case 4 -> Color.FUCHSIA;
            case 5 -> Color.MAROON;
            case 6 -> Color.BLACK;
            default -> Color.YELLOW;
        };

        ItemStack item = new ItemBuilder(Material.POTION)
                .setName(name)
                .setLore(
                        "§7Multiplie les récoltes du minion.",
                        "§7Bonus: §eRécolte x" + multiplier,
                        "",
                        "§c⚠ Une seule potion à la fois !",
                        "§eModule d'amélioration")
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

    public boolean isHarvestPotion(ItemStack item) {
        return checkUpgradeType(item, "HARVEST_BOOST");
    }

    public int getHarvestMultiplier(ItemStack item) {
        if (!isHarvestPotion(item))
            return 0;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(new NamespacedKey(plugin, KEY_HARVEST_MULTIPLIER), PersistentDataType.INTEGER, 1);
    }

    // --- UTILITAIRES ---
    public boolean isValidUpgrade(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        NamespacedKey key = new NamespacedKey(plugin, KEY_UPGRADE_TYPE);
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING);
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
        NamespacedKey key = new NamespacedKey(plugin, KEY_UPGRADE_TYPE);
        String type = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return expectedType.equals(type);
    }
}