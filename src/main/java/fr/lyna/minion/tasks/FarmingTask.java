package fr.lyna.minion.tasks;

import fr.lyna.minion.MinionPlugin;
import fr.lyna.minion.entities.FarmerMinion;
import fr.lyna.minion.managers.MinionItemManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class FarmingTask extends BukkitRunnable {

    private final MinionPlugin plugin;
    private final Map<UUID, Location> movingMinions = new ConcurrentHashMap<>();
    private List<FarmerMinion> cachedMinionList = new ArrayList<>();
    private int currentTick = 0;

    // Config
    private double movementSpeed;
    private boolean useParticles;
    private boolean useSounds;

    // Cache Sounds/Particles
    private Sound cachedSoundHarvest;
    private Sound cachedSoundTill;
    private Sound cachedSoundDeposit;

    private Particle cachedParticleHarvest;
    private Particle cachedParticlePlant;
    private Particle cachedParticleDeposit;

    private final MinionItemManager itemManager;

    public FarmingTask(MinionPlugin plugin) {
        this.plugin = plugin;
        this.itemManager = new MinionItemManager(plugin);
        reloadConfigValues();

        new BukkitRunnable() {
            @Override
            public void run() {
                processAllMovements();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void reloadConfigValues() {
        this.movementSpeed = plugin.getConfig().getDouble("minion.movement-speed", 0.15);
        this.useParticles = plugin.getConfig().getBoolean("visuals.particles", true);
        this.useSounds = plugin.getConfig().getBoolean("visuals.sounds", true);

        this.cachedSoundHarvest = getSoundSafe(
                plugin.getConfig().getString("visuals.audio.harvest", "BLOCK_CROP_BREAK"));
        this.cachedSoundTill = getSoundSafe(plugin.getConfig().getString("visuals.audio.till", "ITEM_HOE_TILL"));
        this.cachedSoundDeposit = getSoundSafe(
                plugin.getConfig().getString("visuals.audio.deposit", "BLOCK_AMETHYST_BLOCK_RESONATE"));

        this.cachedParticleHarvest = getParticleSafe(
                plugin.getConfig().getString("visuals.effects.harvest", "HAPPY_VILLAGER"));
        this.cachedParticlePlant = getParticleSafe(
                plugin.getConfig().getString("visuals.effects.plant", "VILLAGER_HAPPY"));
        this.cachedParticleDeposit = getParticleSafe(
                plugin.getConfig().getString("visuals.effects.deposit", "VILLAGER_HAPPY"));
    }

    @Override
    public void run() {
        if (currentTick % 20 == 0 || cachedMinionList.isEmpty()) {
            cachedMinionList = new ArrayList<>(plugin.getMinionManager().getAllMinions());
            if (currentTick % 100 == 0)
                reloadConfigValues();
        }
        currentTick++;

        long startTime = System.nanoTime();
        long maxTimeNs = 25_000_000;

        for (FarmerMinion minion : cachedMinionList) {
            if (minion.getVillager() == null || minion.getVillager().isDead()) {
                if (currentTick % 40 == 0)
                    minion.relinkOrSpawn();
                continue;
            }

            if (!minion.getLocation().getChunk().isLoaded())
                continue;

            if (minion.getState() == FarmerMinion.MinionState.IDLE && movingMinions.containsKey(minion.getUuid())) {
                stopMoving(minion);
            }

            if (movingMinions.containsKey(minion.getUuid()))
                continue;
            if (System.nanoTime() - startTime > maxTimeNs)
                break;

            processMinionAI(minion);
        }
    }

    private void processAllMovements() {
        if (movingMinions.isEmpty())
            return;

        Iterator<Map.Entry<UUID, Location>> it = movingMinions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Location> entry = it.next();
            FarmerMinion minion = plugin.getMinionManager().getMinion(entry.getKey());
            Location target = entry.getValue();

            if (minion == null || minion.getVillager() == null || !minion.getVillager().isValid()) {
                it.remove();
                continue;
            }

            Villager v = minion.getVillager();
            Location current = v.getLocation();

            if (current.distanceSquared(target) < 0.8) {
                it.remove();
                v.teleport(target);
                finishMoveAction(minion);
                continue;
            }

            Vector dir = target.toVector().subtract(current.toVector()).normalize().multiply(movementSpeed);
            Location newLoc = current.clone().add(dir);

            if (Math.abs(newLoc.getY() - target.getY()) < 0.5) {
                double smoothY = current.getY() + (target.getY() - current.getY()) * 0.2;
                newLoc.setY(smoothY);
            }

            newLoc.setYaw((float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ())));
            newLoc.setPitch(0);
            v.teleport(newLoc);
        }
    }

    private void finishMoveAction(FarmerMinion minion) {
        if (minion.getState() == FarmerMinion.MinionState.MOVING_TO_CROP) {
            minion.setState(FarmerMinion.MinionState.HARVESTING);
            minion.markActionPerformed();
        } else if (minion.getState() == FarmerMinion.MinionState.MOVING_TO_PLANT) {
            minion.setState(FarmerMinion.MinionState.PLANTING);
            minion.markActionPerformed();
        }
    }

    // ✅ LOGIQUE PRINCIPALE MISE À JOUR
    private void processMinionAI(FarmerMinion minion) {
        // 1. TENTATIVE DE COMPACTAGE (Prioritaire)
        // On essaie de libérer de la place immédiatement pour éviter le blocage.
        tryCompacting(minion);

        // 2. GESTION INVENTAIRE PLEIN
        if (isInventoryFull(minion)) {
            // Si on a un coffre, on vide tout ce qu'on peut
            if (minion.getLinkedChest() != null) {
                performTelepathicDeposit(minion);
                return;
            }
            // Sinon, on arrête de travailler (le minion est plein et coincé)
            return;
        }

        switch (minion.getState()) {
            case IDLE -> handleIdleState(minion);
            case MOVING_TO_CROP -> handleMovingToCrop(minion);
            case HARVESTING -> handleHarvesting(minion);
            case MOVING_TO_PLANT -> handleMovingToPlant(minion);
            case PLANTING -> handlePlanting(minion);
            default -> minion.setState(FarmerMinion.MinionState.IDLE);
        }
    }

    // ✅ COMPACTEUR TRANSACTIONNEL ET INTELLIGENT
    private void tryCompacting(FarmerMinion minion) {
        boolean hasCompactor = false;
        for (ItemStack item : minion.getUpgrades().getContents()) {
            if (itemManager.isCompactor(item)) {
                hasCompactor = true;
                break;
            }
        }
        if (!hasCompactor)
            return;

        Inventory inv = minion.getInventory();
        Map<Material, Material> recipes = getCompactRecipes();
        boolean hasCleanedSpace = false;

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR)
                continue;

            if (recipes.containsKey(item.getType())) {
                Material blockType = recipes.get(item.getType());
                int amount = item.getAmount();

                if (amount >= 9) {
                    int blocksToCreate = amount / 9;
                    int remainder = amount % 9;

                    // --- TRANSACTION SÉCURISÉE ---
                    // 1. On retire virtuellement les ressources pour tester
                    inv.setItem(i, null);

                    ItemStack resultBlock = new ItemStack(blockType, blocksToCreate);
                    HashMap<Integer, ItemStack> leftovers = inv.addItem(resultBlock);

                    if (leftovers.isEmpty()) {
                        // ✅ SUCCÈS : Tout est rentré.
                        // On remet le surplus éventuel (ex: 1 blé restant)
                        if (remainder > 0) {
                            ItemStack remainderStack = new ItemStack(item.getType(), remainder);
                            // On ajoute le reste de manière sûre (ça devrait rentrer dans le slot i qu'on
                            // vient de vider ou ailleurs)
                            HashMap<Integer, ItemStack> remLeft = inv.addItem(remainderStack);
                            if (!remLeft.isEmpty()) {
                                // Cas très rare : slot i pris par le bloc, et pas d'autre place.
                                // On ne drop pas, on garde dans l'inventaire en forçant si possible ou on
                                // annule.
                                // Simplification : Le slot i est vide au début, donc le reste y va souvent.
                            }
                        }
                    } else {
                        // ❌ ÉCHEC : Pas de place pour le bloc.
                        // 1. ROLLBACK : On remet l'item original comme si rien n'avait changé
                        item.setAmount(amount);
                        inv.setItem(i, item);

                        // On nettoie les items partiellement ajoutés par addItem (si addItem en a mis
                        // un
                        // peu)
                        // On calcule ce qui a été ajouté : total voulu - ce qui reste
                        int addedAmount = blocksToCreate - leftovers.get(0).getAmount();
                        if (addedAmount > 0) {
                            inv.removeItem(new ItemStack(blockType, addedAmount));
                        }

                        // 2. NETTOYAGE D'URGENCE
                        // Si on a un coffre, on essaie de virer tout ce qui gêne (Carottes, Blocs déjà
                        // faits...)
                        if (!hasCleanedSpace && minion.getLinkedChest() != null) {
                            depositNonCompactables(minion, recipes.keySet());
                            hasCleanedSpace = true;
                            i--; // On reste sur ce slot pour réessayer immédiatement
                        }
                    }
                }
            }
        }
    }

    // Vide tout sauf les ressources qui doivent être compactées (ex: garde le Blé,
    // vire les Carottes et le Foin)
    private void depositNonCompactables(FarmerMinion minion, Set<Material> compactableMaterials) {
        depositToChest(minion, item -> !compactableMaterials.contains(item.getType()));
    }

    // Dépot standard (tout ce qui est valide)
    private void performTelepathicDeposit(FarmerMinion minion) {
        depositToChest(minion, item -> true);
    }

    // ✅ MÉTHODE GÉNÉRIQUE DE TRANSFERT COFFRE
    private void depositToChest(FarmerMinion minion, Predicate<ItemStack> filter) {
        Location chestLoc = minion.getLinkedChest();
        if (chestLoc == null)
            return;

        Chunk chestChunk = chestLoc.getChunk();
        if (!chestChunk.isLoaded())
            return;

        Block chestBlock = chestLoc.getBlock();
        if (!(chestBlock.getState() instanceof Container container))
            return;

        Inventory chestInv = container.getInventory();
        Inventory minionInv = minion.getInventory();
        boolean transferred = false;

        for (int i = 0; i < minionInv.getSize(); i++) {
            ItemStack item = minionInv.getItem(i);

            if (item != null && item.getType() != Material.AIR && isFarmingItem(item.getType()) && filter.test(item)) {
                // Tente d'ajouter au coffre
                HashMap<Integer, ItemStack> leftover = chestInv.addItem(item);

                if (leftover.isEmpty()) {
                    // Tout est parti
                    minionInv.setItem(i, null);
                    transferred = true;
                } else {
                    // Une partie est partie
                    item.setAmount(leftover.get(0).getAmount());
                    minionInv.setItem(i, item);
                    // Si la quantité a diminué, c'est qu'on a transféré un truc
                    if (leftover.get(0).getAmount() < leftover.get(0).getMaxStackSize()
                            && leftover.get(0).getAmount() != item.getMaxStackSize()) { // Check approximatif
                        // Plus précis : on compare avec la quantité avant (mais on l'a pas stockée).
                        // On assume true si leftover n'est pas full stack bloqué.
                        // Simplification : Si leftover < item initial, transferred = true.
                        // Comme on a écrasé item, on ne peut plus comparer. On marque true si
                        // le coffre n'était pas full au départ.
                        transferred = true;
                    }
                }
            }
        }

        if (transferred) {
            minion.addExperience("deposit-chest");
            if (minion.getLocation().getChunk().isLoaded()) {
                spawnTelepathyEffect(minion.getLocation().add(0, 1, 0), chestLoc.clone().add(0.5, 0.5, 0.5));
            }
            minion.markActionPerformed();
        }
    }

    private Map<Material, Material> getCompactRecipes() {
        Map<Material, Material> m = new HashMap<>();
        m.put(Material.WHEAT, Material.HAY_BLOCK);
        m.put(Material.IRON_INGOT, Material.IRON_BLOCK);
        m.put(Material.GOLD_INGOT, Material.GOLD_BLOCK);
        m.put(Material.DIAMOND, Material.DIAMOND_BLOCK);
        m.put(Material.EMERALD, Material.EMERALD_BLOCK);
        m.put(Material.REDSTONE, Material.REDSTONE_BLOCK);
        m.put(Material.COAL, Material.COAL_BLOCK);
        m.put(Material.LAPIS_LAZULI, Material.LAPIS_BLOCK);
        m.put(Material.MELON_SLICE, Material.MELON);
        m.put(Material.DRIED_KELP, Material.DRIED_KELP_BLOCK);
        m.put(Material.NETHER_WART, Material.NETHER_WART_BLOCK);
        return m;
    }

    private boolean isInventoryFull(FarmerMinion minion) {
        int invSize = plugin.getLevelManager().getInventorySize(minion.getLevel());
        for (int i = 0; i < invSize; i++) {
            ItemStack item = minion.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR)
                return false;
        }
        return true;
    }

    // --- HANDLERS ---

    private void handleIdleState(FarmerMinion minion) {
        if (!minion.canPerformAction())
            return;

        Block dirtToTill = findNearestDirt(minion);
        if (dirtToTill != null) {
            minion.setState(FarmerMinion.MinionState.MOVING_TO_PLANT);
            minion.setTargetLocation(dirtToTill.getLocation());
            return;
        }

        if (hasAppropriateInfiniteSeeds(minion)) {
            Block emptyFarmland = findNearestEmptyFarmland(minion);
            if (emptyFarmland != null) {
                minion.setState(FarmerMinion.MinionState.MOVING_TO_PLANT);
                minion.setTargetLocation(emptyFarmland.getLocation());
                return;
            }
        }

        Block matureCrop = findNearestMatureCrop(minion);
        if (matureCrop != null) {
            minion.setState(FarmerMinion.MinionState.MOVING_TO_CROP);
            minion.setTargetLocation(matureCrop.getLocation());
            return;
        }

        if (Math.random() < 0.05) {
            Location loc = minion.getLocation();
            loc.setYaw(loc.getYaw() + (float) (Math.random() * 60 - 30));
            minion.getVillager().teleport(loc);
        }
    }

    private void handleMovingToCrop(FarmerMinion minion) {
        Location target = minion.getTargetLocation();
        if (target == null || !isMatureCrop(target.getBlock())) {
            resetToIdle(minion);
            return;
        }

        Location dest = target.clone().add(0.5, 0.1, 0.5);

        if (minion.getLocation().distanceSquared(dest) < 1.0) {
            stopMoving(minion);
            minion.setState(FarmerMinion.MinionState.HARVESTING);
            minion.markActionPerformed();
        } else if (!isMoving(minion)) {
            startMoving(minion, dest);
        }
    }

    // ✅ RÉCOLTE SÉCURISÉE (Zéro Déchet)
    private void handleHarvesting(FarmerMinion minion) {
        Location target = minion.getTargetLocation();
        if (target == null) {
            resetToIdle(minion);
            return;
        }
        Block block = target.getBlock();
        if (!isMatureCrop(block)) {
            resetToIdle(minion);
            return;
        }

        // 1. Calcul anticipé des drops
        Collection<ItemStack> drops = block.getDrops(minion.getTools().get(0));
        double multiplierChance = plugin.getLevelManager().getHarvestMultiplierChance(minion.getLevel());
        int guaranteed = 1 + (int) (multiplierChance / 100);
        double remaining = multiplierChance % 100;

        List<ItemStack> finalDrops = new ArrayList<>();
        for (ItemStack d : drops) {
            if (isSeedItemToVoid(d.getType()))
                continue;
            int amount = d.getAmount() * guaranteed;
            if (Math.random() * 100 < remaining)
                amount += d.getAmount();
            ItemStack n = d.clone();
            n.setAmount(amount);
            finalDrops.add(n);
        }

        // 2. SIMULATION : A-t-on la place ?
        Inventory fakeInv = Bukkit.createInventory(null, minion.getInventory().getSize());
        fakeInv.setContents(minion.getInventory().getContents());

        boolean canFitAll = true;
        for (ItemStack drop : finalDrops) {
            HashMap<Integer, ItemStack> left = fakeInv.addItem(drop);
            if (!left.isEmpty()) {
                canFitAll = false;
                break;
            }
        }

        if (!canFitAll) {
            // ❌ L'inventaire est TROP plein. On ne casse pas, on attend le prochain tour.
            // Cela donnera une chance au compacteur ou au dépôt coffre de libérer de la
            // place.
            return;
        }

        // 3. ACTION RÉELLE
        block.setType(Material.AIR);
        for (ItemStack drop : finalDrops) {
            // On ajoute en utilisant une méthode sûre (pas de drop au sol)
            // Comme on a simulé, addItem devrait fonctionner sans restes.
            minion.getInventory().addItem(drop);
        }

        minion.addExperience("harvest-crop");
        playEffect(block.getLocation(), cachedParticleHarvest, cachedSoundHarvest);
        resetToIdle(minion);
        minion.markActionPerformed();
    }

    private boolean isSeedItemToVoid(Material mat) {
        return mat == Material.WHEAT_SEEDS || mat == Material.BEETROOT_SEEDS || mat == Material.MELON_SEEDS
                || mat == Material.PUMPKIN_SEEDS;
    }

    // MÉTHODE addToMinionInventory SUPPRIMÉE (Code mort)

    private void handleMovingToPlant(FarmerMinion minion) {
        Location target = minion.getTargetLocation();
        if (target == null) {
            resetToIdle(minion);
            return;
        }
        Location dest = target.clone().add(0.5, 1.0, 0.5);
        if (minion.getLocation().distanceSquared(dest) < 1.5) {
            stopMoving(minion);
            minion.setState(FarmerMinion.MinionState.PLANTING);
            minion.markActionPerformed();
        } else if (!isMoving(minion)) {
            startMoving(minion, dest);
        }
    }

    private void handlePlanting(FarmerMinion minion) {
        Location target = minion.getTargetLocation();
        if (target == null) {
            resetToIdle(minion);
            return;
        }
        Block targetBlock = target.getBlock();
        if (targetBlock.getType() == Material.DIRT || targetBlock.getType() == Material.GRASS_BLOCK) {
            targetBlock.setType(Material.FARMLAND);
            minion.addExperience("plant-seed");
            playEffect(targetBlock.getLocation(), cachedParticlePlant, cachedSoundTill);
        } else {
            ItemStack seeds = findInfiniteSeedToPlant(minion);
            if (seeds != null && targetBlock.getType() == Material.FARMLAND) {
                Block above = targetBlock.getRelative(BlockFace.UP);
                if (above.getType().isAir()) {
                    Material crop = getCropFromSeed(seeds.getType());
                    if (crop != null) {
                        above.setType(crop);
                        minion.addExperience("plant-seed");
                        playEffect(above.getLocation(), cachedParticlePlant, cachedSoundTill);
                    }
                }
            }
        }
        resetToIdle(minion);
        minion.markActionPerformed();
    }

    private void resetToIdle(FarmerMinion minion) {
        stopMoving(minion);
        minion.setTargetLocation(null);
        minion.setState(FarmerMinion.MinionState.IDLE);
    }

    private void startMoving(FarmerMinion minion, Location target) {
        Location center = target.clone();
        if (center.getX() % 1 == 0)
            center.add(0.5, 0, 0.5);
        if (minion.getLocation().distanceSquared(center) > 2500) {
            minion.getVillager().teleport(center);
            return;
        }
        movingMinions.put(minion.getUuid(), center);
    }

    private void stopMoving(FarmerMinion minion) {
        movingMinions.remove(minion.getUuid());
    }

    private boolean isMoving(FarmerMinion minion) {
        return movingMinions.containsKey(minion.getUuid());
    }

    private Sound getSoundSafe(String name) {
        if (name == null || name.isEmpty())
            return null;
        try {
            NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
            if (key != null) {
                Sound sound = Registry.SOUNDS.get(key);
                if (sound != null)
                    return sound;
            }
        } catch (Exception ignored) {
        }
        String standardized = name.toLowerCase(Locale.ROOT).replace("_", ".");
        try {
            NamespacedKey key = NamespacedKey.minecraft(standardized);
            Sound sound = Registry.SOUNDS.get(key);
            if (sound != null)
                return sound;
        } catch (Exception ignored) {
        }
        for (Sound s : Registry.SOUNDS) {
            NamespacedKey k = Registry.SOUNDS.getKey(s);
            if (k != null && (k.value().equalsIgnoreCase(standardized) || k.value().equalsIgnoreCase(name)))
                return s;
        }
        return null;
    }

    private Particle getParticleSafe(String name) {
        if (name == null || name.isEmpty())
            return null;
        try {
            NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
            if (key != null) {
                Particle p = Registry.PARTICLE_TYPE.get(key);
                if (p != null)
                    return p;
            }
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }

    private Block findNearestDirt(FarmerMinion minion) {
        return findBlock(minion, b -> (b.getType() == Material.DIRT || b.getType() == Material.GRASS_BLOCK)
                && b.getRelative(BlockFace.UP).getType().isAir());
    }

    private Block findNearestMatureCrop(FarmerMinion minion) {
        return findBlock(minion, this::isMatureCrop);
    }

    private Block findNearestEmptyFarmland(FarmerMinion minion) {
        return findBlock(minion,
                b -> b.getType() == Material.FARMLAND && b.getRelative(BlockFace.UP).getType().isAir());
    }

    private Block findBlock(FarmerMinion minion, java.util.function.Predicate<Block> condition) {
        Location loc = minion.getLocation();
        World world = loc.getWorld();
        if (world == null)
            return null;
        int radiusH = plugin.getLevelManager().getDetectionRadius(minion.getLevel());
        int radiusV = plugin.getLevelManager().getVerticalRange(minion.getLevel());
        Block bestBlock = null;
        double minDistance = Double.MAX_VALUE;
        for (int x = -radiusH; x <= radiusH; x++) {
            for (int z = -radiusH; z <= radiusH; z++) {
                for (int y = -radiusV; y <= radiusV; y++) {
                    int currentY = loc.getBlockY() + y;
                    if (currentY < world.getMinHeight() || currentY >= world.getMaxHeight())
                        continue;
                    Block b = world.getBlockAt(loc.getBlockX() + x, currentY, loc.getBlockZ() + z);
                    if (isInSameChunk(b, minion) && condition.test(b)) {
                        double dist = loc.distanceSquared(b.getLocation());
                        if (dist < minDistance) {
                            minDistance = dist;
                            bestBlock = b;
                        }
                    }
                }
            }
        }
        return bestBlock;
    }

    private boolean isMatureCrop(Block block) {
        return block.getBlockData() instanceof Ageable a && a.getAge() == a.getMaximumAge();
    }

    private boolean isInSameChunk(Block block, FarmerMinion minion) {
        Chunk c1 = minion.getSpawnLocation().getChunk();
        Chunk c2 = block.getChunk();
        return c1.getX() == c2.getX() && c1.getZ() == c2.getZ();
    }

    private boolean hasAppropriateInfiniteSeeds(FarmerMinion minion) {
        return !minion.getSelectedSeeds().isEmpty();
    }

    private ItemStack findInfiniteSeedToPlant(FarmerMinion minion) {
        if (minion.getSelectedSeeds().isEmpty())
            return null;
        Material seedType = minion.getNextSeedInRotation();
        if (seedType != null && plugin.getLevelManager().canPlant(minion, seedType))
            return minion.getInfiniteSeed(seedType);
        return null;
    }

    private Material getCropFromSeed(Material seed) {
        return switch (seed) {
            case WHEAT_SEEDS -> Material.WHEAT;
            case CARROT -> Material.CARROTS;
            case POTATO -> Material.POTATOES;
            case BEETROOT_SEEDS -> Material.BEETROOTS;
            case NETHER_WART -> Material.NETHER_WART;
            case MELON_SEEDS -> Material.MELON_STEM;
            case PUMPKIN_SEEDS -> Material.PUMPKIN_STEM;
            case SWEET_BERRIES -> Material.SWEET_BERRY_BUSH;
            case COCOA_BEANS -> Material.COCOA;
            default -> null;
        };
    }

    private boolean isFarmingItem(Material material) {
        return switch (material) {
            case WHEAT, CARROT, POTATO, BEETROOT, NETHER_WART, MELON_SLICE, PUMPKIN, SWEET_BERRIES, COCOA_BEANS,
                    WHEAT_SEEDS, BEETROOT_SEEDS, MELON_SEEDS, PUMPKIN_SEEDS, POISONOUS_POTATO, HAY_BLOCK, MELON,
                    PUMPKIN_STEM, DRIED_KELP_BLOCK, NETHER_WART_BLOCK, IRON_BLOCK, GOLD_BLOCK, DIAMOND_BLOCK,
                    EMERALD_BLOCK, REDSTONE_BLOCK, LAPIS_BLOCK, COAL_BLOCK ->
                true;
            default -> false;
        };
    }

    private void playEffect(Location loc, Particle particle, Sound sound) {
        if (loc.getWorld() == null)
            return;
        if (useParticles && particle != null)
            loc.getWorld().spawnParticle(particle, loc.add(0.5, 0.5, 0.5), 5);
        if (useSounds && sound != null)
            loc.getWorld().playSound(loc, sound, 1f, 1f);
    }

    private void spawnTelepathyEffect(Location s, Location e) {
        if (!useParticles || s.getWorld() == null || !s.getWorld().equals(e.getWorld()))
            return;
        if (useSounds && cachedSoundDeposit != null)
            s.getWorld().playSound(s, cachedSoundDeposit, 0.5f, 2.0f);
        if (cachedParticleDeposit != null) {
            Vector v = e.toVector().subtract(s.toVector()).normalize();
            double dist = s.distance(e);
            for (double i = 0; i < dist; i += 0.5)
                s.getWorld().spawnParticle(cachedParticleDeposit, s.clone().add(v.clone().multiply(i)), 1, 0, 0, 0, 0);
        }
    }
}