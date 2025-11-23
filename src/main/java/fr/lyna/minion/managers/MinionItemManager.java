package fr.lyna.minion.managers;

import fr.lyna.minion.MinionPlugin;
import fr.lyna.minion.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class MinionItemManager {

    private final MinionPlugin plugin;
    // Clé NBT pour identifier l'item de manière unique
    public static final String KEY_UPGRADE_TYPE = "minion_upgrade_type";

    public MinionItemManager(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Génère l'item Compacteur officiel
     */
    public ItemStack getCompactor() {
        ItemStack item = new ItemBuilder(Material.PISTON)
                .setName("§6§lCompacteur Automatique")
                .setLore(
                        "§7Transforme automatiquement les",
                        "§7ressources en blocs.",
                        "",
                        "§7Exemple:",
                        "§79 Blés ➜ §e1 Botte de Foin",
                        "§79 Lingots ➜ §e1 Bloc",
                        "",
                        "§ePlace cet item dans le slot",
                        "§ed'amélioration du minion.")
                .setGlowing(true)
                .build();

        ItemMeta meta = item.getItemMeta();
        // Signature unique
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, KEY_UPGRADE_TYPE),
                PersistentDataType.STRING,
                "COMPACTOR");
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Vérifie si un item est un compacteur (pour la logique métier)
     */
    public boolean isCompactor(ItemStack item) {
        return checkUpgradeType(item, "COMPACTOR");
    }

    /**
     * ✅ NOUVEAU : Vérifie si c'est un item d'amélioration valide (Générique)
     * Empêche de mettre de la terre ou du blé dans les slots.
     */
    public boolean isValidUpgrade(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        NamespacedKey key = new NamespacedKey(plugin, KEY_UPGRADE_TYPE);
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    private boolean checkUpgradeType(ItemStack item, String expectedType) {
        if (item == null || !item.hasItemMeta())
            return false;
        NamespacedKey key = new NamespacedKey(plugin, KEY_UPGRADE_TYPE);
        if (!item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING))
            return false;
        String type = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return expectedType.equals(type);
    }
}