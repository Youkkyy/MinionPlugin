package fr.lyna.minion.listeners;

import fr.lyna.minion.MinionPlugin;
import fr.lyna.minion.entities.FarmerMinion;
import fr.lyna.minion.managers.MinionManager;
import fr.lyna.minion.utils.StructureBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Set;

public class MinionPlaceListener implements Listener {

    private final MinionPlugin plugin;
    private final MinionManager minionManager;

    public MinionPlaceListener(MinionPlugin plugin) {
        this.plugin = plugin;
        this.minionManager = plugin.getMinionManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlaceMinion(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        Block clicked = event.getClickedBlock();
        if (clicked == null)
            return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isMinionItem(item))
            return;

        event.setCancelled(true);

        if (!player.hasPermission("minion.place")) {
            player.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        Location baseLoc = clicked.getLocation().add(0, 1, 0);
        Chunk targetChunk = baseLoc.getChunk();

        // 1. Vérification Chunk
        if (minionManager.isChunkOccupied(targetChunk)) {
            player.sendMessage(plugin.colorize("&c❌ Un minion est déjà présent dans ce chunk !"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // 2. Vérification Limite
        if (!player.hasPermission("minion.bypass.limit")) {
            int limit = plugin.getConfig().getInt("settings.max-minions-per-player", 5);
            if (limit > 0 && minionManager.getPlayerMinionCount(player.getUniqueId()) >= limit) {
                player.sendMessage(plugin.getMessage("limit-reached").replace("{limit}", String.valueOf(limit)));
                return;
            }
        }

        // 3. Collage de la structure (FAWE)
        player.sendMessage(Component.text("🌾 Construction de la ferme...", NamedTextColor.YELLOW));
        try {
            StructureBuilder.pasteFarmStructure(plugin, clicked.getLocation());
        } catch (Exception e) {
            player.sendMessage(Component.text("❌ Erreur structure : " + e.getMessage(), NamedTextColor.RED));
            e.printStackTrace();
            return;
        }

        // 4. Recherche Farmland
        Location farmland = findFreeFarmland(baseLoc);
        if (farmland == null) {
            player.sendMessage(Component.text("❌ Aucune terre cultivable libre trouvée !", NamedTextColor.RED));
            return;
        }

        // 5. CRÉATION DU MINION
        FarmerMinion minion = minionManager.createMinion(player, farmland);
        if (minion == null) {
            player.sendMessage(Component.text("❌ Erreur lors de la création.", NamedTextColor.RED));
            return;
        }

        // 6. RESTAURATION DES DONNÉES (Si présentes sur l'item)
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        NamespacedKey lvlKey = new NamespacedKey(plugin, "saved_level");
        NamespacedKey xpKey = new NamespacedKey(plugin, "saved_xp");
        NamespacedKey presKey = new NamespacedKey(plugin, "saved_prestige");
        NamespacedKey seedsKey = new NamespacedKey(plugin, "saved_seeds");
        NamespacedKey moneyKey = new NamespacedKey(plugin, "saved_money_earned"); // 💰 Clé argent

        boolean restored = false;

        if (data.has(lvlKey, PersistentDataType.INTEGER)) {
            int level = data.get(lvlKey, PersistentDataType.INTEGER);
            long xp = data.getOrDefault(xpKey, PersistentDataType.LONG, 0L);
            int prestige = data.getOrDefault(presKey, PersistentDataType.INTEGER, 0);
            double money = data.getOrDefault(moneyKey, PersistentDataType.DOUBLE, 0.0); // 💰 Récupération argent

            // Application des stats
            minion.setLevel(level);
            minion.setExperience(xp);
            minion.setPrestige(prestige);
            minion.setTotalMoneyEarned(money); // 💰 Application

            // Restauration des graines sélectionnées
            if (data.has(seedsKey, PersistentDataType.STRING)) {
                String seedsStr = data.get(seedsKey, PersistentDataType.STRING);
                Set<Material> savedSeeds = new HashSet<>();
                for (String s : seedsStr.split(",")) {
                    try {
                        savedSeeds.add(Material.valueOf(s));
                    } catch (Exception ignored) {
                    }
                }
                minion.setSelectedSeeds(savedSeeds);
            }

            // Mise à jour visuelle
            minion.updateNameTag();
            restored = true;
        }

        // Sauvegarde immédiate en DB
        plugin.getDataManager().saveMinion(minion);

        // 7. Feedback
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
        player.spawnParticle(Particle.HAPPY_VILLAGER, farmland, 30, 0.5, 0.8, 0.5);

        if (restored) {
            player.sendMessage(Component.text("✨ Minion restauré avec succès (Niveau " + minion.getLevel() + ") !",
                    NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("✨ Nouveau Minion fermier placé !", NamedTextColor.GREEN));
        }

        // Retrait de l'item
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    private boolean isMinionItem(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD)
            return false;
        if (!item.hasItemMeta())
            return false;

        Component displayName = item.getItemMeta().displayName();
        if (displayName == null)
            return false;

        String plainName = PlainTextComponentSerializer.plainText().serialize(displayName);
        return plainName.contains("Minion Fermier");
    }

    private Location findFreeFarmland(Location origin) {
        World world = origin.getWorld();
        Chunk chunk = origin.getChunk();
        int chunkX = chunk.getX() * 16;
        int chunkZ = chunk.getZ() * 16;

        // Recherche du bas vers le haut pour trouver le premier farmland libre
        for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Block block = world.getBlockAt(chunkX + x, y, chunkZ + z);
                    if (block.getType() == Material.FARMLAND && block.getRelative(0, 1, 0).isEmpty()) {
                        return block.getLocation().add(0.5, 1, 0.5);
                    }
                }
            }
        }
        return null;
    }
}