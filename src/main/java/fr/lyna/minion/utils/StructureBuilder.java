package fr.lyna.minion.utils;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;

public class StructureBuilder {

    public static void pasteFarmStructure(JavaPlugin plugin, Location baseLocation) {
        String schemName = plugin.getConfig().getString("farming.schematic-name", "mcfarm");
        File schematic = new File(plugin.getDataFolder(), "schematics/" + schemName + ".schem");

        if (!schematic.exists()) {
            plugin.getLogger().severe("❌ Schematic '" + schemName + ".schem' introuvable !");
            return;
        }

        World bukkitWorld = baseLocation.getWorld();
        if (bukkitWorld == null)
            return;

        try {
            ClipboardFormat format = ClipboardFormats.findByFile(schematic);
            if (format == null)
                return;

            Clipboard clipboard;
            try (FileInputStream fis = new FileInputStream(schematic);
                    ClipboardReader reader = format.getReader(fis)) {
                clipboard = reader.read();
            }

            Chunk chunk = baseLocation.getChunk();
            int chunkMinX = chunk.getX() * 16;
            int chunkMinZ = chunk.getZ() * 16;

            int schematicWidth = clipboard.getDimensions().getBlockX();
            int schematicLength = clipboard.getDimensions().getBlockZ();

            int offsetX = (16 - schematicWidth) / 2;
            int offsetZ = (16 - schematicLength) / 2;

            int pasteX = chunkMinX + offsetX;
            int pasteY = baseLocation.getBlockY();
            int pasteZ = chunkMinZ + offsetZ;

            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);

            // On définit l'origine avant de créer le holder
            clipboard.setOrigin(clipboard.getMinimumPoint());

            // ✅ FIX : Ajout de ClipboardHolder dans le try-with-resources pour le fermer
            // automatiquement
            try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(weWorld)
                    .maxBlocks(-1)
                    .build();
                    ClipboardHolder holder = new ClipboardHolder(clipboard)) {

                Operation operation = holder
                        .createPaste(editSession)
                        .to(BlockVector3.at(pasteX, pasteY, pasteZ))
                        .ignoreAirBlocks(false)
                        .build();

                Operations.complete(operation);
                // editSession.commit() est appelé automatiquement à la fermeture du try,
                // mais on peut le laisser explicite si souhaité.
            }

        } catch (Exception e) {
            plugin.getLogger().severe("❌ Erreur structure : " + e.getMessage());
            e.printStackTrace();
        }
    }
}