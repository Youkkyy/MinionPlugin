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

public class MinionItemManager {

    private final MinionPlugin plugin;
    // Clés NBT
    public static final String KEY_UPGRADE_TYPE = "minion_upgrade_type";
    public static final String KEY_XP_MULTIPLIER = "minion_xp_multiplier";

    public MinionItemManager(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    // --- COMPACTEUR (Existant) ---
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

    // --- NOUVEAU : POTIONS D'XP ---

    /**
     * Crée une potion d'XP selon le niveau (1 à 6)
     */
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
                // ✅ CORRECTION : HIDE_ADDITIONAL_TOOLTIP est le seul flag valide en 1.21 pour
                // les potions
                // Même s'il est "deprecated" (avertissement), il est obligatoire car
                // HIDE_POTION_EFFECTS n'existe plus.
                .addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES)
                .build();

        // Ajout de la couleur de potion
        if (item.getItemMeta() instanceof PotionMeta pm) {
            pm.setColor(color);
            item.setItemMeta(pm);
        }

        // Ajout des tags NBT (Type + Valeur du multiplicateur)
        ItemStack tagged = tagItem(item, "XP_BOOST");

        org.bukkit.inventory.meta.ItemMeta meta = tagged.getItemMeta();
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, KEY_XP_MULTIPLIER),
                PersistentDataType.INTEGER,
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
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(
                new NamespacedKey(plugin, KEY_XP_MULTIPLIER),
                PersistentDataType.INTEGER,
                1);
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