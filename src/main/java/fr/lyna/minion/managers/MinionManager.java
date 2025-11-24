package fr.lyna.minion.managers;

import fr.lyna.minion.MinionPlugin;
import fr.lyna.minion.entities.FarmerMinion;
import fr.lyna.minion.gui.MinionGUI;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class MinionManager implements Listener {

    private final MinionPlugin plugin;
    private final Map<UUID, FarmerMinion> minions;
    private final NamespacedKey minionUuidKey;

    public MinionManager(MinionPlugin plugin) {
        this.plugin = plugin;
        this.minions = new HashMap<>();
        this.minionUuidKey = new NamespacedKey(plugin, "minion_uuid");
    }

    public void killAllMinionEntities() {
        plugin.getLogger().info("🧹 Nettoyage préventif des entités minions...");
        int count = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (villager.getPersistentDataContainer().has(minionUuidKey, PersistentDataType.STRING)) {
                    villager.remove();
                    count++;
                }
            }
        }
        plugin.getLogger().info("✅ Nettoyage terminé : " + count + " entités supprimées (prêtes au respawn).");
    }

    /**
     * Appelé quand le plugin s'éteint.
     * On retire les entités pour éviter qu'elles soient sauvegardées par Minecraft.
     */
    public void despawnAllMinions() {
        for (FarmerMinion minion : minions.values()) {
            // ✅ CORRECTION : On utilise despawn() au lieu de remove()
            // Cela supprime le villageois mais GARDE le TextDisplay (Leaderboard)
            minion.despawn();
        }
        minions.clear();
    }

    public void addMinion(FarmerMinion minion) {
        if (minions.containsKey(minion.getUuid()))
            return;
        minions.put(minion.getUuid(), minion);
        minion.spawn();
    }

    public FarmerMinion createMinion(Player owner, Location location) {
        if (isChunkOccupied(location.getChunk())) {
            owner.sendMessage(plugin.colorize("&c❌ Un minion est déjà présent dans ce chunk !"));
            return null;
        }

        if (!owner.hasPermission("minion.bypass.limit")) {
            int limit = plugin.getConfig().getInt("settings.max-minions-per-player", 5);
            if (limit > 0 && getPlayerMinionCount(owner.getUniqueId()) >= limit) {
                owner.sendMessage(plugin.getMessage("limit-reached").replace("{limit}", String.valueOf(limit)));
                return null;
            }
        }

        UUID minionUUID = UUID.randomUUID();
        FarmerMinion minion = new FarmerMinion(minionUUID, owner.getUniqueId(), location, plugin);
        minion.spawn();

        if (minion.getVillager() == null || minion.getVillager().isDead()) {
            plugin.getLogger().severe("❌ Echec du spawn minion " + minionUUID);
            return null;
        }

        minions.put(minionUUID, minion);
        plugin.getDataManager().saveMinion(minion);
        return minion;
    }

    public void removeMinion(UUID minionUUID) {
        FarmerMinion minion = minions.get(minionUUID);
        if (minion != null) {
            minion.remove(); // Ici on veut tout supprimer
            minions.remove(minionUUID);
            plugin.getDataManager().deleteMinion(minionUUID);
        }
    }

    public boolean isChunkOccupied(Chunk chunk) {
        for (FarmerMinion minion : minions.values()) {
            Chunk mChunk = minion.getSpawnLocation().getChunk();
            if (mChunk.getWorld().equals(chunk.getWorld()) && mChunk.getX() == chunk.getX()
                    && mChunk.getZ() == chunk.getZ()) {
                return true;
            }
        }
        return false;
    }

    public FarmerMinion getMinion(UUID uuid) {
        return minions.get(uuid);
    }

    public Collection<FarmerMinion> getAllMinions() {
        return minions.values();
    }

    public int getPlayerMinionCount(UUID playerUUID) {
        return (int) minions.values().stream().filter(m -> m.getOwnerUUID().equals(playerUUID)).count();
    }

    // --- LISTENERS ---

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager))
            return;
        if (!villager.getPersistentDataContainer().has(minionUuidKey, PersistentDataType.STRING))
            return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        String uuidStr = villager.getPersistentDataContainer().get(minionUuidKey, PersistentDataType.STRING);
        UUID uuid = UUID.fromString(uuidStr);
        FarmerMinion minion = getMinion(uuid);

        if (minion == null) {
            villager.remove();
            player.sendMessage(plugin.colorize("&cCe minion est un fantôme, il a été supprimé."));
            return;
        }

        if (!minion.getOwnerUUID().equals(player.getUniqueId()) && !player.hasPermission("minion.admin")) {
            player.sendMessage(plugin.colorize("&cCe minion ne t'appartient pas !"));
            return;
        }

        if (player.isSneaking()) {
            retrieveMinion(player, minion);
        } else {
            new MinionGUI(plugin, minion, player).open();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked() instanceof Villager v
                && v.getPersistentDataContainer().has(minionUuidKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    private void retrieveMinion(Player player, FarmerMinion minion) {
        if (plugin.getConfig().getBoolean("settings.drop-items-on-break", true)) {
            Location loc = minion.getLocation();
            for (ItemStack item : minion.getInventory()) {
                if (item != null && item.getType() != Material.AIR)
                    loc.getWorld().dropItemNaturally(loc, item);
            }
        }
        player.getInventory().addItem(minion.toItemStack());
        player.sendMessage(plugin.getMessage("minion-retrieved"));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        removeMinion(minion.getUuid());
    }
}