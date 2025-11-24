package fr.lyna.minion.listeners;

import fr.lyna.minion.MinionPlugin;
import fr.lyna.minion.entities.FarmerMinion;
import fr.lyna.minion.managers.MinionItemManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class LeaderboardListener implements Listener {

    private final MinionPlugin plugin;
    private final MinionItemManager itemManager;

    public LeaderboardListener(MinionPlugin plugin) {
        this.plugin = plugin;
        this.itemManager = new MinionItemManager(plugin);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        if (event.getClickedBlock() == null)
            return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || !itemManager.isLeaderboardPlacer(item))
            return;

        event.setCancelled(true);

        UUID minionUUID = itemManager.getMinionUUIDFromPlacer(item);
        if (minionUUID == null) {
            player.sendMessage(plugin.colorize("&cErreur : Item corrompu."));
            return;
        }

        FarmerMinion minion = plugin.getMinionManager().getMinion(minionUUID);
        if (minion == null) {
            player.sendMessage(plugin.colorize("&cCe minion n'existe plus !"));
            item.setAmount(0); // Retirer l'item inutile
            return;
        }

        // Vérification du Monde
        if (!player.getWorld().getUID().equals(minion.getLocation().getWorld().getUID())) {
            player.sendMessage(plugin.colorize("&cTu dois placer ce panneau dans le même monde que le minion !"));
            return;
        }

        Location placeLoc = event.getClickedBlock().getLocation().add(0.5, 1.2, 0.5);

        // Création du TextDisplay (Entity 1.19.4+)
        TextDisplay display = (TextDisplay) player.getWorld().spawnEntity(placeLoc, EntityType.TEXT_DISPLAY);
        display.setBillboard(Display.Billboard.CENTER); // Suit le regard du joueur
        display.setBackgroundColor(Color.fromARGB(100, 0, 0, 0)); // Fond semi-transparent noir
        display.setShadowed(true);
        display.setSeeThrough(false);

        // Sauvegarde dans le minion
        minion.setLeaderboardUuid(display.getUniqueId());
        plugin.getDataManager().saveMinion(minion);

        // Retrait de l'item
        item.setAmount(0);

        player.playSound(placeLoc, Sound.ENTITY_ARMOR_STAND_PLACE, 1f, 1f);
        player.sendMessage(plugin.colorize("&aPanneau de statistiques placé !"));
    }
}