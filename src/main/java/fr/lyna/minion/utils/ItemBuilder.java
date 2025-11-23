package fr.lyna.minion.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe utilitaire pour construire facilement des ItemStacks
 * Mise à jour pour Paper 1.21+ (Adventure API)
 */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    /**
     * Constructeur avec un Material
     */
    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    /**
     * Constructeur avec un ItemStack existant
     */
    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = item.getItemMeta();
    }

    /**
     * Définit le nom de l'item (avec support des codes couleur & et §)
     * Utilise l'API Adventure pour éviter la dépréciation.
     */
    public ItemBuilder setName(String name) {
        if (meta != null) {
            // Conversion String -> Component
            meta.displayName(toComponent(name));
        }
        return this;
    }

    /**
     * Définit la lore de l'item (avec support des codes couleur)
     */
    public ItemBuilder setLore(String... lore) {
        if (meta != null) {
            List<Component> componentLore = new ArrayList<>();
            for (String line : lore) {
                componentLore.add(toComponent(line));
            }
            meta.lore(componentLore);
        }
        return this;
    }

    /**
     * Définit la lore de l'item avec une liste
     */
    public ItemBuilder setLore(List<String> lore) {
        if (meta != null) {
            List<Component> componentLore = new ArrayList<>();
            for (String line : lore) {
                componentLore.add(toComponent(line));
            }
            meta.lore(componentLore);
        }
        return this;
    }

    /**
     * Ajoute un enchantement
     */
    public ItemBuilder addEnchantment(Enchantment enchantment, int level) {
        if (meta != null) {
            meta.addEnchant(enchantment, level, true);
        }
        return this;
    }

    /**
     * Ajoute un effet brillant (glow) sans enchantement visible
     */
    public ItemBuilder setGlowing(boolean glowing) {
        if (meta != null && glowing) {
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    /**
     * Définit la quantité de l'item
     */
    public ItemBuilder setAmount(int amount) {
        item.setAmount(amount);
        return this;
    }

    /**
     * Cache certains attributs de l'item
     */
    public ItemBuilder addItemFlags(ItemFlag... flags) {
        if (meta != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    /**
     * Rend l'item incassable
     */
    public ItemBuilder setUnbreakable(boolean unbreakable) {
        if (meta != null) {
            meta.setUnbreakable(unbreakable);
        }
        return this;
    }

    /**
     * Construit et retourne l'ItemStack final
     */
    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Méthode utilitaire pour convertir les Strings (avec & ou §) en Component
     * Adventure
     */
    private Component toComponent(String text) {
        // Convertit les '&' en couleurs, puis désérialise en Component
        return LegacyComponentSerializer.legacySection().deserialize(text.replace("&", "§"));
    }
}