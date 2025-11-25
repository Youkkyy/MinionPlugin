package fr.lyna.minion.tasks;

import fr.lyna.minion.MinionPlugin;
import fr.lyna.minion.entities.FarmerMinion;
import fr.lyna.minion.managers.MinionItemManager;
import net.milkbowl.vault.economy.Economy;
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

    private double movementSpeed;
    private boolean useParticles;
    private boolean useSounds;

    private Sound cachedSoundHarvest;
    private Sound cachedSoundTill;
    private Sound cachedSoundDeposit;
    private Sound cachedSoundSell;

    private Particle cachedParticleHarvest;
    private Particle cachedParticlePlant;
    private Particle cachedParticleDeposit;
    private Particle cachedParticleSell;

    private final MinionItemManager itemManager;
    private boolean hasWarnedEconomy = false;

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
        this.cachedSoundSell = getSoundSafe(
                plugin.getConfig().getString("visuals.audio.sell", "ENTITY_EXPERIENCE_ORB_PICKUP"));

        this.cachedParticleHarvest = getParticleSafe(
                plugin.getConfig().getString("visuals.effects.harvest", "HAPPY_VILLAGER"));
        this.cachedParticlePlant = getParticleSafe(
                plugin.getConfig().getString("visuals.effects.plant", "VILLAGER_HAPPY"));
        this.cachedParticleDeposit = getParticleSafe(
                plugin.getConfig().getString("visuals.effects.deposit", "VILLAGER_HAPPY"));
        this.cachedParticleSell = getParticleSafe(
                plugin.getConfig().getString("visuals.effects.sell", "WAX_ON"));
    }

    @Override
    public void run() {
        if (currentTick % 20 == 0 || cachedMinionList.isEmpty()) {
            cachedMinionList = new ArrayList<>(plugin.getMinionManager().getAllMinions());
            if (currentTick % 100 == 0)
                reloadConfigValues();
        }
        currentTick++;

        // 50ms par tick de jeu standard (si le TPS est 20)
        // On consomme le fuel en temps réel
        long timeElapsedMillis = 50;

        long startTime = System.nanoTime();
        long maxTimeNs = 25_000_000;

        for (FarmerMinion minion : cachedMinionList) {
            // Consommation Fuel
            if (minion.hasFuel()) {
                minion.consumeFuel(timeElapsedMillis);
                // Mise à jour leaderboard toutes les 30s pour le timer fuel
                if (currentTick % 600 == 0)
                    minion.updateLeaderboardDisplay();
            } else {
                // Si pas de fuel, on force l'IDLE et on skip l'IA
                if (minion.getState() != FarmerMinion.MinionState.IDLE) {
                    minion.setState(FarmerMinion.MinionState.IDLE);
                    // Effet visuel "Panne" toutes les 5s
                    if (currentTick % 100 == 0) {
                        if (minion.getLocation().getWorld() != null)
                            minion.getLocation().getWorld().spawnParticle(Particle.SMOKE,
                                    minion.getLocation().add(0, 2, 0), 5, 0, 0, 0, 0.05);
                    }
                }
                continue;
            }

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

    private void processMinionAI(FarmerMinion minion) {
        boolean hasCompacted = tryCompacting(minion);
        tryAutoSell(minion, hasCompacted);

        if (isInventoryFull(minion)) {
            if (minion.getLinkedChest() != null) {
                performTelepathicDeposit(minion);
                return;
            }
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

    private void tryAutoSell(FarmerMinion minion, boolean hasCompactor) {
        int sellPercentage = minion.getAutoSellPercentage();
        if (sellPercentage <= 0)
            return;

        Economy econ = plugin.getEconomy();

        if (econ == null) {
            if (plugin.setupEconomy()) {
                econ = plugin.getEconomy();
                if (hasWarnedEconomy) {
                    plugin.getLogger().info("✅ Économie détectée tardivement ! La revente fonctionne maintenant.");
                    hasWarnedEconomy = false;
                }
            } else {
                if (!hasWarnedEconomy) {
                    plugin.getLogger().severe("❌ Les minions essaient de vendre mais l'économie est introuvable.");
                    hasWarnedEconomy = true;
                }
                return;
            }
        }

        double totalEarned = 0;
        Inventory inv = minion.getInventory();
        Map<Material, Double> prices = getSellPrices();
        Map<Material, Material> compactRecipes = getCompactRecipes();

        boolean soldSomething = false;

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR)
                continue;
            if (isSeedOrSapling(item.getType()))
                continue;
            if (hasCompactor && compactRecipes.containsKey(item.getType()))
                continue;
            if (!prices.containsKey(item.getType()))
                continue;

            double unitPrice = prices.get(item.getType());
            double finalPrice = unitPrice * item.getAmount() * (sellPercentage / 100.0);

            totalEarned += finalPrice;
            inv.setItem(i, null);
            soldSomething = true;
        }

        if (soldSomething && totalEarned > 0) {
            econ.depositPlayer(minion.getOwner(), totalEarned);
            minion.addMoneyEarned(totalEarned);
            playEffect(minion.getLocation().add(0, 1.5, 0), cachedParticleSell, cachedSoundSell);
        }
    }

    private Map<Material, Double> getSellPrices() {
        Map<Material, Double> prices = new HashMap<>();
        org.bukkit.configuration.ConfigurationSection sec = plugin.getConfig()
                .getConfigurationSection("auto-sell.prices");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    prices.put(Material.valueOf(key), sec.getDouble(key));
                } catch (Exception ignored) {
                }
            }
        }
        return prices;
    }

    private boolean isSeedOrSapling(Material mat) {
        return mat.name().endsWith("_SEEDS")
                || mat.name().contains("SAPLING")
                || mat == Material.COCOA_BEANS
                || mat == Material.NETHER_WART;
    }

    private boolean tryCompacting(FarmerMinion minion) {
        boolean hasCompactor = false;
        for (ItemStack item : minion.getUpgrades().getContents()) {
            if (itemManager.isCompactor(item)) {
                hasCompactor = true;
                break;
            }
        }
        if (!hasCompactor)
            return false;

        Inventory inv = minion.getInventory();
        Map<Material, Material> recipes = getCompactRecipes();
        boolean performedCompact = false;

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

                    inv.setItem(i, null);
                    ItemStack resultBlock = new ItemStack(blockType, blocksToCreate);
                    HashMap<Integer, ItemStack> leftovers = inv.addItem(resultBlock);

                    if (leftovers.isEmpty()) {
                        if (remainder > 0) {
                            ItemStack remainderStack = new ItemStack(item.getType(), remainder);
                            inv.addItem(remainderStack);
                        }
                        performedCompact = true;
                    } else {
                        item.setAmount(amount);
                        inv.setItem(i, item);
                    }
                }
            }
        }
        return hasCompactor;
    }

    private void depositNonCompactables(FarmerMinion minion, Set<Material> compactableMaterials) {
        depositToChest(minion, item -> !compactableMaterials.contains(item.getType()));
    }

    private void performTelepathicDeposit(FarmerMinion minion) {
        depositToChest(minion, item -> true);
    }

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
                HashMap<Integer, ItemStack> leftover = chestInv.addItem(item);
                if (leftover.isEmpty()) {
                    minionInv.setItem(i, null);
                    transferred = true;
                } else {
                    item.setAmount(leftover.get(0).getAmount());
                    minionInv.setItem(i, item);
                    if (leftover.get(0).getAmount() < leftover.get(0).getMaxStackSize()
                            && leftover.get(0).getAmount() != item.getMaxStackSize()) {
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

        Collection<ItemStack> drops = block.getDrops(minion.getTools().get(0));
        double multiplierChance = plugin.getLevelManager().getHarvestMultiplierChance(minion.getLevel());
        int levelGuaranteed = 1 + (int) (multiplierChance / 100);
        double remaining = multiplierChance % 100;
        int potionMultiplier = minion.getActiveHarvestMultiplier();
        boolean hasVoid = minion.hasVoidModule();

        List<ItemStack> finalDrops = new ArrayList<>();

        for (ItemStack d : drops) {
            if (hasVoid && minion.isVoidItem(d.getType())) {
                playVoidEffect(minion);
                int rawAmount = d.getAmount() * levelGuaranteed * potionMultiplier;
                minion.addHarvestStat(d.getType(), rawAmount);
                continue;
            }
            int amount = d.getAmount() * levelGuaranteed;
            if (Math.random() * 100 < remaining)
                amount += d.getAmount();
            amount *= potionMultiplier;
            minion.addHarvestStat(d.getType(), amount);
            ItemStack n = d.clone();
            n.setAmount(amount);
            finalDrops.add(n);
        }

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

        if (!canFitAll)
            return;

        block.setType(Material.AIR);
        for (ItemStack drop : finalDrops) {
            minion.getInventory().addItem(drop);
        }

        minion.addExperience("harvest-crop");
        playEffect(block.getLocation(), cachedParticleHarvest, cachedSoundHarvest);
        resetToIdle(minion);
        minion.markActionPerformed();
    }

    private void playVoidEffect(FarmerMinion minion) {
        if (minion.getLocation().getWorld() != null && Math.random() < 0.2) {
            minion.getLocation().getWorld().spawnParticle(Particle.SMOKE, minion.getLocation().add(0, 1, 0), 2, 0.2,
                    0.2, 0.2, 0);
        }
    }

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