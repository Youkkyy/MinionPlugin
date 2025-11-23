package fr.lyna.minion.commands;

import fr.lyna.minion.MinionPlugin;
import fr.lyna.minion.entities.FarmerMinion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class MinionDebugCommand implements CommandExecutor {

    private final MinionPlugin plugin;

    public MinionDebugCommand(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player))
            return true;
        if (!player.hasPermission("minion.admin"))
            return true;

        // 1. Trouver le minion regardé
        FarmerMinion minion = getTargetMinion(player);

        if (minion == null) {
            // Sinon chercher le plus proche
            double closestDist = Double.MAX_VALUE;
            for (FarmerMinion m : plugin.getMinionManager().getAllMinions()) {
                if (m.getLocation().getWorld().equals(player.getWorld())) {
                    double d = m.getLocation().distance(player.getLocation());
                    if (d < closestDist) {
                        closestDist = d;
                        minion = m;
                    }
                }
            }
        }

        if (minion == null) {
            player.sendMessage(Component.text("Aucun minion trouvé.", NamedTextColor.RED));
            return true;
        }

        // 2. ANALYSE COMPLÈTE
        player.sendMessage(Component.text("=== 🕵️ DIAGNOSTIC MINION ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("UUID: ", NamedTextColor.YELLOW)
                .append(Component.text(minion.getUuid().toString().substring(0, 8), NamedTextColor.WHITE)));

        player.sendMessage(Component.text("État Actuel: ", NamedTextColor.YELLOW)
                .append(Component.text(String.valueOf(minion.getState()), NamedTextColor.AQUA)));

        // Check Physique
        boolean villagerExists = (minion.getVillager() != null && !minion.getVillager().isDead());
        player.sendMessage(Component.text("Entité Physique: ", NamedTextColor.YELLOW)
                .append(villagerExists ? Component.text("OUI", NamedTextColor.GREEN)
                        : Component.text("NON (Mort/Disparu)", NamedTextColor.RED)));

        boolean chunkLoaded = minion.getLocation().getChunk().isLoaded();
        player.sendMessage(Component.text("Chunk Chargé: ", NamedTextColor.YELLOW)
                .append(chunkLoaded ? Component.text("OUI", NamedTextColor.GREEN)
                        : Component.text("NON", NamedTextColor.RED)));

        // Check Stats Config
        int level = minion.getLevel();
        int radiusH = plugin.getLevelManager().getDetectionRadius(level);
        int radiusV = plugin.getLevelManager().getVerticalRange(level);
        int cooldown = plugin.getLevelManager().getCooldown(level);

        player.sendMessage(Component.text("Niveau: ", NamedTextColor.YELLOW)
                .append(Component.text(level, NamedTextColor.WHITE)));

        player.sendMessage(Component.text("Rayon Détection: ", NamedTextColor.YELLOW)
                .append(Component.text("H=" + radiusH + " / V=" + radiusV, NamedTextColor.WHITE)));

        if (radiusH == 0) {
            player.sendMessage(
                    Component.text("⚠️ ALERTE: Rayon Horizontal = 0 ! Vérifie levels.yml", NamedTextColor.RED));
        }

        // Check Cooldown
        boolean canAct = minion.canPerformAction();
        player.sendMessage(Component.text("Peut agir (Cooldown): ", NamedTextColor.YELLOW)
                .append(canAct ? Component.text("OUI", NamedTextColor.GREEN)
                        : Component.text("NON (En pause)", NamedTextColor.RED)));

        player.sendMessage(Component.text("  (Délai config: " + cooldown + " ticks)", NamedTextColor.GRAY));

        // Check Outil
        boolean hasTool = minion.hasValidTool();
        player.sendMessage(Component.text("Outil Valide: ", NamedTextColor.YELLOW)
                .append(hasTool ? Component.text("OUI", NamedTextColor.GREEN)
                        : Component.text("NON (Pas de houe)", NamedTextColor.RED)));

        // Check Cible
        Location target = minion.getTargetLocation();
        if (target != null) {
            player.sendMessage(Component.text("Cible en cours: ", NamedTextColor.YELLOW)
                    .append(Component.text(target.getBlockX() + ", " + target.getBlockY() + ", " + target.getBlockZ(),
                            NamedTextColor.WHITE)));

            player.sendMessage(Component.text("Type bloc cible: ", NamedTextColor.YELLOW)
                    .append(Component.text(String.valueOf(target.getBlock().getType()), NamedTextColor.WHITE)));

            double dist = minion.getLocation().distance(target);
            player.sendMessage(Component.text("Distance cible: ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%.2f", dist), NamedTextColor.WHITE)));
        } else {
            player.sendMessage(Component.text("Cible: ", NamedTextColor.YELLOW)
                    .append(Component.text("Aucune (Cherche...)", NamedTextColor.GRAY)));
        }

        // Check Inventaire
        player.sendMessage(Component.text("Inventaire Plein: ", NamedTextColor.YELLOW)
                .append(minion.isInventoryFull() ? Component.text("OUI", NamedTextColor.RED)
                        : Component.text("NON", NamedTextColor.GREEN)));

        player.sendMessage(Component.text("============================", NamedTextColor.GOLD));

        // Force reset si demandé
        if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
            minion.setState(FarmerMinion.MinionState.IDLE);
            minion.setTargetLocation(null);
            if (minion.getVillager() != null) {
                minion.getVillager().teleport(minion.getSpawnLocation());
            }
            player.sendMessage(Component.text("♻️ État du minion réinitialisé !", NamedTextColor.GREEN));
        }

        return true;
    }

    private FarmerMinion getTargetMinion(Player player) {
        Entity target = player.getTargetEntity(10);
        if (target instanceof Villager villager) {
            NamespacedKey key = new NamespacedKey(plugin, "minion_uuid");
            if (villager.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                try {
                    return plugin.getMinionManager().getMinion(UUID.fromString(
                            villager.getPersistentDataContainer().get(key, PersistentDataType.STRING)));
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }
}