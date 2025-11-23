package fr.lyna.minion.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.*;

/**
 * Système de pathfinding intelligent pour les minions
 * Mis à jour : Nettoyage des variables inutilisées
 */
public class PathfindingSystem {

    public enum AccessLevel {
        DIRECT(1),
        STAIRS(2),
        CLIMB(3),
        UNREACHABLE(999);

        public final int score;

        AccessLevel(int score) {
            this.score = score;
        }
    }

    public static class PathResult {
        public final AccessLevel level;
        public final double distance;
        public final int heightDiff;
        public final boolean hasLineOfSight;
        public final double score;

        public PathResult(AccessLevel level, double distance, int heightDiff, boolean hasLineOfSight) {
            this.level = level;
            this.distance = distance;
            this.heightDiff = heightDiff;
            this.hasLineOfSight = hasLineOfSight;
            this.score = distance + (level.score * 3.0) + (Math.abs(heightDiff) / 10.0);
        }

        public boolean isReachable() {
            return level != AccessLevel.UNREACHABLE;
        }
    }

    public static PathResult calculatePath(Location from, Location to) {
        double distance = Math.sqrt(Math.pow(to.getX() - from.getX(), 2) + Math.pow(to.getZ() - from.getZ(), 2));
        int heightDiff = to.getBlockY() - from.getBlockY();
        boolean hasLineOfSight = checkLineOfSight(from, to);

        if (Math.abs(heightDiff) <= 2 && hasLineOfSight && distance < 10) {
            return new PathResult(AccessLevel.DIRECT, distance, heightDiff, hasLineOfSight);
        }

        if (Math.abs(heightDiff) <= 5 && distance < 10 && hasLineOfSight) {
            if (hasStairsPath(from, to)) {
                return new PathResult(AccessLevel.STAIRS, distance, heightDiff, hasLineOfSight);
            }
        }

        if (Math.abs(heightDiff) <= 150 && distance < 16) {
            return new PathResult(AccessLevel.CLIMB, distance, heightDiff, false);
        }

        return new PathResult(AccessLevel.UNREACHABLE, distance, heightDiff, false);
    }

    private static boolean checkLineOfSight(Location from, Location to) {
        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld()))
            return false;

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance < 0.1)
            return true;

        dx /= distance;
        dy /= distance;
        dz /= distance;

        double step = 0.5;
        int steps = (int) (distance / step);

        for (int i = 0; i <= steps; i++) {
            double checkX = from.getX() + dx * (i * step);
            double checkY = from.getY() + dy * (i * step) + 0.5;
            double checkZ = from.getZ() + dz * (i * step);

            Block block = from.getWorld().getBlockAt((int) Math.floor(checkX), (int) Math.floor(checkY),
                    (int) Math.floor(checkZ));

            if (block.getType().isSolid() &&
                    block.getType() != Material.FARMLAND &&
                    block.getType() != Material.DIRT &&
                    block.getType() != Material.GRASS_BLOCK &&
                    !block.getType().name().contains("GLASS")) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasStairsPath(Location from, Location to) {
        // ✅ FIX : Suppression des variables inutilisées fromBlock et toBlock
        int yDiff = Math.abs(to.getBlockY() - from.getBlockY());
        if (yDiff == 0)
            return true;

        double xStep = (to.getX() - from.getX()) / (yDiff + 1);
        double zStep = (to.getZ() - from.getZ()) / (yDiff + 1);

        for (int i = 1; i <= yDiff; i++) {
            int checkY = from.getBlockY() + (to.getBlockY() > from.getBlockY() ? i : -i);
            int checkX = (int) (from.getX() + xStep * i);
            int checkZ = (int) (from.getZ() + zStep * i);

            // On vérifie simplement la présence d'un sol via le monde
            if (from.getWorld() != null) {
                Block stepBlock = from.getWorld().getBlockAt(checkX, checkY - 1, checkZ);
                if (!stepBlock.getType().isSolid()) {
                    return false;
                }
            }
        }
        return true;
    }

    public static int detectLayer(Location location, int baseY) {
        int yDiff = location.getBlockY() - baseY;
        return yDiff / 16;
    }

    public static Block findBestAccessibleBlock(Location origin, List<Block> candidates) {
        if (candidates.isEmpty())
            return null;

        Block best = null;
        double bestScore = Double.MAX_VALUE;

        for (Block candidate : candidates) {
            PathResult path = calculatePath(origin, candidate.getLocation().add(0.5, 0, 0.5));
            if (path.isReachable() && path.score < bestScore) {
                bestScore = path.score;
                best = candidate;
            }
        }
        return best;
    }

    public static List<Block> sortByAccessibility(Location origin, List<Block> blocks) {
        Map<Block, PathResult> scores = new HashMap<>();
        for (Block block : blocks) {
            PathResult path = calculatePath(origin, block.getLocation().add(0.5, 0, 0.5));
            scores.put(block, path);
        }
        List<Block> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparingDouble(b -> scores.get(b).score));
        return sorted;
    }
}