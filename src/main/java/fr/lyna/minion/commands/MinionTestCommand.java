package fr.lyna.minion.commands;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Commande de diagnostic pour tester FAWE et les schematics
 * Mise à jour pour Paper 1.21 (Adventure API + Static Fixes)
 *
 * Usage : /miniontest
 */
public class MinionTestCommand implements CommandExecutor {

    private final Plugin plugin;

    public MinionTestCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Cette commande est réservée aux joueurs !", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("=== DIAGNOSTIC MINION PLUGIN ===", NamedTextColor.GOLD));

        // Test 1 : FAWE chargé ?
        try {
            // On vérifie juste si l'instance existe pour s'assurer que le plugin est chargé
            WorldEdit.getInstance();

            // ✅ FIX: Accès statique correct à getVersion()
            String version = WorldEdit.getVersion();

            player.sendMessage(Component.text("✅ WorldEdit/FAWE détecté : " + version, NamedTextColor.GREEN));
        } catch (Exception e) {
            player.sendMessage(Component.text("❌ WorldEdit/FAWE non chargé !", NamedTextColor.RED));
            player.sendMessage(Component.text("   Installe FAWE sur ton serveur !", NamedTextColor.RED));
            return true;
        }

        // Test 2 : Fichier schematic présent ?
        File schematic = new File(plugin.getDataFolder(), "schematics/mcfarm.schem");
        if (schematic.exists()) {
            player.sendMessage(
                    Component.text("✅ Schematic trouvé : " + schematic.getAbsolutePath(), NamedTextColor.GREEN));
            player.sendMessage(
                    Component.text("   Taille : " + (schematic.length() / 1024) + " KB", NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("❌ Schematic introuvable !", NamedTextColor.RED));
            player.sendMessage(
                    Component.text("   Chemin attendu : " + schematic.getAbsolutePath(), NamedTextColor.RED));
            player.sendMessage(
                    Component.text("   Place ton fichier mcfarm.schem dans ce dossier !", NamedTextColor.YELLOW));
        }

        // Test 3 : Monde compatible ?
        try {
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(player.getWorld());
            player.sendMessage(
                    Component.text("✅ Monde compatible WorldEdit : " + weWorld.getName(), NamedTextColor.GREEN));
        } catch (Exception e) {
            player.sendMessage(Component.text("❌ Monde non compatible WorldEdit !", NamedTextColor.RED));
        }

        // Test 4 : Permissions WorldEdit
        if (player.hasPermission("worldedit.clipboard.paste")) {
            player.sendMessage(Component.text("✅ Permissions WorldEdit OK", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("⚠ Pas de permission WorldEdit (pas critique)", NamedTextColor.YELLOW));
        }

        // Test 5 : Chunk chargé
        if (player.getLocation().getChunk().isLoaded()) {
            player.sendMessage(Component.text("✅ Chunk actuel chargé", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("⚠ Chunk non chargé", NamedTextColor.YELLOW));
        }

        player.sendMessage(Component.text("================================", NamedTextColor.GOLD));
        player.sendMessage(
                Component.text("Si tous les tests sont OK, le plugin devrait fonctionner !", NamedTextColor.AQUA));

        return true;
    }
}