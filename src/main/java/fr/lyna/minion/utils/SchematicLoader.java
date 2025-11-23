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
import org.bukkit.*;
import org.bukkit.block.Block;

import java.io.File;
import java.io.FileInputStream;

public final class SchematicLoader {

    public static final class PasteResult {
        public final int minX, minZ, maxX, maxZ, y;

        public PasteResult(int minX, int minZ, int maxX, int maxZ, int y) {
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
            this.y = y;
        }
    }

    public static PasteResult pasteCenteredInChunk(World world, Chunk chunk, int baseY, String name) throws Exception {
        File file = new File(Bukkit.getPluginsFolder(), "MinionPlugin/schematics/" + name + ".schem");
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null)
            throw new IllegalStateException("Format schematic inconnu: " + file.getName());

        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            clipboard = reader.read();
        }

        int width = clipboard.getDimensions().getX();
        int length = clipboard.getDimensions().getZ();
        if (width > 16 || length > 16) {
            throw new IllegalArgumentException(
                    "La schematic dépasse la taille d’un chunk (16x16). W=" + width + " L=" + length);
        }

        int chunkOriginX = chunk.getX() * 16;
        int chunkOriginZ = chunk.getZ() * 16;
        int pasteX = chunkOriginX + (16 - width) / 2;
        int pasteZ = chunkOriginZ + (16 - length) / 2;

        try (EditSession edit = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world));
                ClipboardHolder holder = new ClipboardHolder(clipboard)) {

            // Création du paste builder
            Operation operation = holder.createPaste(edit)
                    .to(BlockVector3.at(pasteX, baseY, pasteZ))
                    .build();

            Operations.complete(operation);
        }

        return new PasteResult(pasteX, pasteZ, pasteX + width - 1, pasteZ + length - 1, baseY);
    }

    public static Location findFirstFarmland(World world, PasteResult r) {
        for (int x = r.minX; x <= r.maxX; x++) {
            for (int z = r.minZ; z <= r.maxZ; z++) {
                Block b = world.getBlockAt(x, r.y, z);
                if (b.getType() == Material.FARMLAND && b.getRelative(0, 1, 0).isEmpty()) {
                    return new Location(world, x + 0.5, r.y + 1, z + 0.5);
                }
            }
        }
        return new Location(world, (r.minX + r.maxX) / 2.0 + 0.5, r.y + 1, (r.minZ + r.maxZ) / 2.0 + 0.5);
    }
}