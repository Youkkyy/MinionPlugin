package fr.lyna.minion.tasks;

import fr.lyna.minion.MinionPlugin;
import fr.lyna.minion.entities.FarmerMinion;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class ChestLinkingTask extends BukkitRunnable implements Listener {

    private final MinionPlugin plugin;
    private final Player player;
    private final FarmerMinion minion;
    private int timeLeft;

    public ChestLinkingTask(MinionPlugin plugin, Player player, FarmerMinion minion) {
        this.plugin = plugin;
        this.player = player;
        this.minion = minion;
        this.timeLeft = plugin.getConfig().getInt("linking.link-timeout", 30);
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.runTaskTimer(plugin, 0L, 20L);
        player.sendMessage(plugin.getMessage("link-start"));
    }

    @Override
    public void run() {
        timeLeft--;
        if (timeLeft <= 0) {
            player.sendMessage(plugin.getMessage("link-timeout"));
            cancel();
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getPlayer().equals(player))
            return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        Block block = event.getClickedBlock();
        if (block == null)
            return;

        Material type = block.getType();
        if (type == Material.CHEST || type == Material.BARREL || type == Material.HOPPER
                || type == Material.TRAPPED_CHEST) {
            event.setCancelled(true);

            int maxDist = plugin.getConfig().getInt("linking.max-link-distance", 50);
            if (minion.getLocation().distance(block.getLocation()) > maxDist) {
                player.sendMessage(plugin.getMessage("link-too-far"));
                return;
            }

            minion.setLinkedChest(block.getLocation());
            plugin.getDataManager().saveMinion(minion);

            block.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.5, 0.5),
                    30);
            player.sendMessage(plugin.getMessage("link-success"));

            cancel();
        }
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        super.cancel();
        HandlerList.unregisterAll(this);
    }
}