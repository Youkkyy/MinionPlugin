package fr.lyna.minion.commands;

import fr.lyna.minion.MinionPlugin;
import fr.lyna.minion.entities.FarmerMinion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class MinionStatsCommand implements CommandExecutor {

    private final MinionPlugin plugin;

    public MinionStatsCommand(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Cette commande est réservée aux joueurs !", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showOwnedMinions(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("prestige")) {
            handlePrestige(player);
            return true;
        }

        return false;
    }

    private void showOwnedMinions(Player player) {
        List<FarmerMinion> ownedMinions = plugin.getMinionManager().getAllMinions()
                .stream()
                .filter(m -> m.getOwnerUUID().equals(player.getUniqueId()))
                .toList();

        if (ownedMinions.isEmpty()) {
            player.sendMessage(Component.text("Tu n'as aucun minion actif !", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("     ")
                .append(Component.text("TES MINIONS", NamedTextColor.YELLOW, TextDecoration.BOLD)));
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));

        int totalLevel = 0;
        long totalXP = 0;

        for (FarmerMinion minion : ownedMinions) {
            totalLevel += minion.getLevel();
            totalXP += minion.getExperience();

            String stars = "★".repeat(minion.getPrestige());

            player.sendMessage(Component.empty());
            player.sendMessage(
                    Component.text("Minion #" + minion.getUuid().toString().substring(0, 8), NamedTextColor.YELLOW));

            player.sendMessage(Component.text("  Niveau: ", NamedTextColor.GRAY)
                    .append(Component.text(minion.getLevel(), NamedTextColor.GREEN))
                    .append(Component.text(" " + stars, NamedTextColor.GOLD)));

            player.sendMessage(Component.text("  XP: ", NamedTextColor.GRAY)
                    .append(Component.text(minion.getExperience(), NamedTextColor.AQUA))
                    .append(Component.text("/", NamedTextColor.GRAY))
                    .append(Component.text(plugin.getLevelManager().getXPRequired(minion.getLevel() + 1),
                            NamedTextColor.AQUA)));

            List<Material> unlockedSeeds = plugin.getLevelManager().getUnlockedSeeds(minion.getLevel());
            player.sendMessage(Component.text("  Graines: ", NamedTextColor.GRAY)
                    .append(Component.text(unlockedSeeds.size() + " types", NamedTextColor.YELLOW)));

            Component state = minion.hasValidTool() ? Component.text("✓ Actif", NamedTextColor.GREEN)
                    : Component.text("✗ Inactif", NamedTextColor.RED);

            player.sendMessage(Component.text("  État: ", NamedTextColor.GRAY).append(state));
        }

        player.sendMessage(Component.empty());

        // Résumé global
        player.sendMessage(Component.text("Total: ", NamedTextColor.GRAY)
                .append(Component.text(ownedMinions.size() + " minion(s)", NamedTextColor.YELLOW)));

        player.sendMessage(Component.text("Niveau moyen: ", NamedTextColor.GRAY)
                .append(Component.text((totalLevel / ownedMinions.size()), NamedTextColor.GREEN)));

        // ✅ FIX : Affichage du total XP (variable maintenant utilisée)
        player.sendMessage(Component.text("XP Total cumulé: ", NamedTextColor.GRAY)
                .append(Component.text(totalXP, NamedTextColor.AQUA)));

        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
    }

    private void handlePrestige(Player player) {
        FarmerMinion closestMinion = null;
        double closestDistance = Double.MAX_VALUE;

        for (FarmerMinion minion : plugin.getMinionManager().getAllMinions()) {
            if (!minion.getOwnerUUID().equals(player.getUniqueId()))
                continue;

            // Vérification que le minion est dans le même monde avant de calculer la
            // distance
            if (!player.getWorld().equals(minion.getLocation().getWorld()))
                continue;

            double distance = player.getLocation().distance(minion.getLocation());
            if (distance < closestDistance && distance < 10) {
                closestDistance = distance;
                closestMinion = minion;
            }
        }

        if (closestMinion == null) {
            player.sendMessage(Component.text("Aucun minion trouvé à proximité (10 blocs)", NamedTextColor.RED));
            return;
        }

        // Niveau 100 requis (selon ta config actuelle)
        if (closestMinion.getLevel() < 100) {
            player.sendMessage(Component.text("❌ Ton minion doit être niveau 100 pour prestige !", NamedTextColor.RED));
            player.sendMessage(Component.text("Niveau actuel: ", NamedTextColor.GRAY)
                    .append(Component.text(closestMinion.getLevel(), NamedTextColor.YELLOW)));
            return;
        }

        if (closestMinion.getPrestige() >= 5) {
            player.sendMessage(Component.text("✨ Ton minion a atteint le prestige maximum !", NamedTextColor.GOLD));
            return;
        }

        closestMinion.setPrestige(closestMinion.getPrestige() + 1);
        closestMinion.setLevel(1);
        closestMinion.setExperience(0);
        closestMinion.updateNameTag();

        plugin.getDataManager().saveMinion(closestMinion);

        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("        ")
                .append(Component.text("⭐ PRESTIGE ⭐", NamedTextColor.YELLOW, TextDecoration.BOLD)));
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Ton minion a atteint le prestige " + closestMinion.getPrestige() + " !",
                NamedTextColor.GREEN));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Bonus débloqués:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("• Vitesse de farming augmentée", NamedTextColor.GRAY));
        player.sendMessage(Component.text("• XP gains multipliés", NamedTextColor.GRAY));
        player.sendMessage(Component.text("• Capacités améliorées", NamedTextColor.GRAY));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));

        player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.getWorld().spawnParticle(
                Particle.END_ROD,
                closestMinion.getLocation().add(0, 1, 0),
                100, 0.5, 1, 0.5, 0.1);
    }
}