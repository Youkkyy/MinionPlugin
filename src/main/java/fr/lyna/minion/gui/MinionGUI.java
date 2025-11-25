package fr.lyna.minion.gui;

import fr.lyna.minion.MinionPlugin;
import fr.lyna.minion.entities.FarmerMinion;
import fr.lyna.minion.managers.MinionItemManager;
import fr.lyna.minion.tasks.ChestLinkingTask;
import fr.lyna.minion.utils.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class MinionGUI implements Listener {

    private final MinionPlugin plugin;
    private final FarmerMinion minion;
    private final Player player;
    private final Inventory inventory;
    private final MinionItemManager itemManager;
    private int currentPage = 0;

    private static final int[] BORDER_SLOTS = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47,
            48, 49, 50, 51, 52, 53 };
    private static final int[] INVENTORY_SLOTS = { 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30,
            31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43 };
    private static final int[] SEED_SLOTS = { 20, 21, 22, 23, 24, 29, 30, 31, 32, 33 };
    private static final int[] UPGRADE_SLOTS = { 21, 22, 23, 30, 31, 32 };

    private static final Material[] COMMON_JUNK = {
            Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS,
            Material.POISONOUS_POTATO,
            Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT,
            Material.MELON_SLICE, Material.PUMPKIN, Material.SWEET_BERRIES,
            Material.COCOA_BEANS, Material.NETHER_WART, Material.SUGAR_CANE, Material.CACTUS,
            Material.DIRT, Material.COBBLESTONE, Material.ROTTEN_FLESH, Material.BONE
    };
    private static final int[] VOID_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    public MinionGUI(MinionPlugin plugin, FarmerMinion minion, Player player) {
        this.plugin = plugin;
        this.minion = minion;
        this.player = player;
        this.itemManager = new MinionItemManager(plugin);
        this.inventory = Bukkit.createInventory(null, 54,
                Component.text("§6§lMinion §8┃ §7Niveau " + minion.getLevel()));

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        setupInventory();
    }

    private void setupInventory() {
        inventory.clear();
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName("§r").build();
        for (int slot : BORDER_SLOTS)
            inventory.setItem(slot, border);

        switch (currentPage) {
            case 0 -> setupMainPage();
            case 1 -> setupStatsPage();
            case 2 -> setupSeedsSelectionPage();
            case 3 -> setupUpgradesPage();
            case 4 -> setupVoidConfigPage();
        }
    }

    private void setupMainPage() {
        long currentXP = minion.getExperience();
        long requiredXP = plugin.getLevelManager().getXPRequired(minion.getLevel() + 1);
        String progressBar = plugin.getLevelManager().getProgressBar(currentXP, requiredXP, 20);
        int hRange = plugin.getLevelManager().getDetectionRadius(minion.getLevel());
        int vRange = plugin.getLevelManager().getVerticalRange(minion.getLevel());

        String fuelStatus = minion.getFormattedFuelTime();
        String fuelColor = minion.hasFuel() ? "§a" : "§c";

        inventory.setItem(4,
                new ItemBuilder(Material.EXPERIENCE_BOTTLE).setName("§6§l⚡ NIVEAU " + minion.getLevel())
                        .setLore("§7Expérience: §e" + currentXP + " §7/ §e" + requiredXP, progressBar, "",
                                "§7Prestige: §6" + minion.getPrestige() + "★",
                                "§7Carburant: " + fuelColor + fuelStatus,
                                "§7Zone: §b" + (hRange * 2 + 1) + "x" + (hRange * 2 + 1) + " §7(Hauteur: §b±" + vRange
                                        + "§7)",
                                "", "§8Clique pour voir les stats détaillées")
                        .setGlowing(true).build());
        inventory.setItem(48,
                new ItemBuilder(Material.ENDER_CHEST).setName("§e§lCoffre de Stockage")
                        .setLore(minion.getLinkedChest() != null
                                ? new String[] { isChestValid(minion) ? "§a✓ Coffre lié et valide"
                                        : "§c⚠ Coffre lié mais manquant !", "§eClique pour changer" }
                                : new String[] { "§c✗ Aucun coffre lié", "§eClique pour lier un coffre" })
                        .build());

        boolean hasLeaderboard = (minion.getLeaderboardUuid() != null
                && Bukkit.getEntity(minion.getLeaderboardUuid()) != null);
        if (hasLeaderboard) {
            inventory.setItem(46, new ItemBuilder(Material.BARRIER).setName("§c§lSupprimer l'Affichage")
                    .setLore("§7Retire le panneau de stats", "§7flottant du monde.", "", "§cClique pour supprimer")
                    .build());
        } else {
            inventory.setItem(46, new ItemBuilder(Material.OAK_SIGN).setName("§e§lPlacer Affichage Stats")
                    .setLore("§7Obtiens un item pour placer", "§7les stats flottantes.", "", "§eClique pour obtenir")
                    .build());
        }

        inventory
                .setItem(52,
                        new ItemBuilder(Material.NETHER_STAR)
                                .setName("§e§lAméliorations & Modules").setLore("§7Ajoute des items spéciaux",
                                        "§7(Compacteur, Void, Potions, Carburant...)", "", "§eClique pour ouvrir")
                                .build());

        int invSize = plugin.getLevelManager().getInventorySize(minion.getLevel());
        int usedSlots = 0;
        for (int i = 0; i < INVENTORY_SLOTS.length; i++) {
            int guiSlot = INVENTORY_SLOTS[i];
            if (i < invSize) {
                ItemStack item = minion.getInventory().getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    inventory.setItem(guiSlot, item);
                    usedSlots++;
                }
            } else {
                inventory.setItem(guiSlot,
                        new ItemBuilder(Material.RED_STAINED_GLASS_PANE).setName("§c§l✗ Slot Verrouillé")
                                .setLore("§7Niveau requis pour débloquer", "§7ce slot supplémentaire.").build());
            }
        }
        int percentage = (invSize > 0) ? (usedSlots * 100) / invSize : 0;
        inventory.setItem(49, new ItemBuilder(Material.BOOK).setName("§6§lInformations").setLore(
                "§7Propriétaire: §e" + minion.getOwner().getName(),
                "§7Stockage: §a" + usedSlots + "§7/§a" + invSize + " §7slots §8(§e" + percentage + "%§8)", "",
                percentage >= 80 ? "§c⚠ Inventaire presque plein !" : "§a✓ Espace disponible", "",
                "§7État: " + (minion.hasValidTool() ? "§a✓ Actif" : "§c✗ Inactif (Manque houe)"),
                minion.getLinkedChest() == null ? "§c⚠ Aucun coffre lié !" : "§a✓ Coffre lié (Télépathie active)")
                .build());
        inventory.setItem(51,
                new ItemBuilder(Material.WHEAT_SEEDS).setName("§6§lGraines Sélectionnées")
                        .setLore("§7Graines actives: §e" + minion.getSelectedSeeds().size() + "§7/§e"
                                + minion.getInfiniteSeeds().size(), "", "§eClique pour gérer les graines")
                        .setGlowing(true).build());
        inventory.setItem(50, new ItemBuilder(Material.PLAYER_HEAD).setName("§c§lRécupérer le Minion")
                .setLore("§7Récupère ce minion.", "§e⚠ §7Les items seront droppés !", "", "§cClique pour récupérer")
                .build());
        inventory.setItem(53, new ItemBuilder(Material.ARROW).setName("§e§lStatistiques →")
                .setLore("§7Voir les stats détaillées").build());
    }

    private void setupStatsPage() {
        inventory.setItem(4, new ItemBuilder(Material.WRITABLE_BOOK).setName("§6§lSTATISTIQUES DÉTAILLÉES")
                .setLore("§7Toutes les infos sur ton minion").setGlowing(true).build());
        inventory.setItem(45,
                new ItemBuilder(Material.ARROW).setName("§e§l← Retour").setLore("§7Page principale").build());

        int level = minion.getLevel();
        int multiplier = minion.getActiveXPMultiplier();
        int baseHarvest = plugin.getLevelManager().getBaseXP("harvest-crop");
        int basePlant = plugin.getLevelManager().getBaseXP("plant-seed");
        int baseDeposit = plugin.getLevelManager().getBaseXP("deposit-chest");
        List<String> xpLore = new ArrayList<>();
        xpLore.add("§7Niveau actuel: §e" + level + "§7/100");
        xpLore.add("§7XP: §a" + minion.getExperience() + " §7/ §a" + plugin.getLevelManager().getXPRequired(level + 1));
        xpLore.add("");
        xpLore.add("§e§lGAINS D'EXPÉRIENCE :");
        if (multiplier > 1)
            xpLore.add("§d⚡ Multiplicateur Actif : x" + multiplier);
        else
            xpLore.add("§7(Aucun multiplicateur actif)");
        xpLore.add("§7• Récolte : §b" + (baseHarvest * multiplier) + " XP");
        inventory.setItem(11,
                new ItemBuilder(Material.EXPERIENCE_BOTTLE).setName("§e§lProgression").setLore(xpLore).build());
        double cooldownSeconds = plugin.getLevelManager().getCooldown(level) / 20.0;
        inventory.setItem(13,
                new ItemBuilder(Material.CLOCK).setName("§e§lVitesse de Travail")
                        .setLore("§7Délai entre actions: §a" + cooldownSeconds + "s", "",
                                "§7Actions par minute: §e" + (int) (60 / cooldownSeconds))
                        .build());
        List<Material> unlockedSeeds = plugin.getLevelManager().getUnlockedSeeds(level);
        List<String> seedsLore = new ArrayList<>();
        seedsLore.add("§7Types débloqués: §e" + unlockedSeeds.size());
        inventory.setItem(15, new ItemBuilder(Material.WHEAT_SEEDS).setName("§e§lGraines Débloquées")
                .setLore(seedsLore.toArray(new String[0])).build());
        int hRange = plugin.getLevelManager().getDetectionRadius(level);
        int vRange = plugin.getLevelManager().getVerticalRange(level);
        inventory.setItem(20,
                new ItemBuilder(Material.COMPASS).setName("§e§lZone de Travail")
                        .setLore("§7Rayon Horizontal: §e+/- " + hRange + " §7blocs",
                                "§7Rayon Vertical: §e+/- " + vRange + " §7blocs")
                        .build());
        int invSize = plugin.getLevelManager().getInventorySize(level);
        inventory.setItem(22, new ItemBuilder(Material.CHEST).setName("§e§lCapacité Stockage")
                .setLore("§7Slots disponibles: §e" + invSize).build());
        double multiplierChance = plugin.getLevelManager().getHarvestMultiplierChance(level);
        int guaranteed = 1 + (int) (multiplierChance / 100);
        inventory.setItem(24,
                new ItemBuilder(Material.GOLDEN_HOE).setName("§e§lFortune du Minion")
                        .setLore("§7Puissance Totale: §e" + multiplierChance + "%", "",
                                "§7Récolte Garantie: §a" + guaranteed + "x §7items")
                        .build());
    }

    private void setupSeedsSelectionPage() {
        inventory.setItem(4,
                new ItemBuilder(Material.COMPOSTER).setName("§6§lSÉLECTION DES GRAINES")
                        .setLore("§7Choisis les graines actives.", "", "§e§lClic gauche §7: Activer/Désactiver")
                        .setGlowing(true).build());
        inventory.setItem(45,
                new ItemBuilder(Material.ARROW).setName("§e§l← Retour").setLore("§7Page principale").build());
        List<Material> availableSeeds = new ArrayList<>(minion.getInfiniteSeeds());
        for (int i = 0; i < availableSeeds.size() && i < SEED_SLOTS.length; i++) {
            Material seed = availableSeeds.get(i);
            boolean selected = minion.isSeedSelected(seed);
            ItemStack seedItem = new ItemBuilder(seed)
                    .setName((selected ? "§a§l✓ " : "§c§l✗ ") + "§e" + getSeedDisplayName(seed))
                    .setLore("", selected ? "§a§lACTIVÉE" : "§c§lDÉSACTIVÉE", "",
                            "§eClique pour " + (selected ? "désactiver" : "activer"))
                    .setGlowing(selected).build();
            inventory.setItem(SEED_SLOTS[i], seedItem);
        }
        inventory.setItem(48, new ItemBuilder(Material.LIME_DYE).setName("§a§lTout Sélectionner").build());
        inventory.setItem(49, new ItemBuilder(Material.BARRIER).setName("§c§lTout Désélectionner").build());
        inventory
                .setItem(53,
                        new ItemBuilder(Material.PAPER)
                                .setName("§6§lRésumé").setLore("§7Graines actives: §e"
                                        + minion.getSelectedSeeds().size() + "§7/§e" + minion.getInfiniteSeeds().size())
                                .build());
    }

    private void setupUpgradesPage() {
        inventory.setItem(4, new ItemBuilder(Material.ANVIL).setName("§6§lMODULES & AMÉLIORATIONS")
                .setLore("§7Dépose tes items spéciaux ici.", "§7(Compacteur, Void, Potions...)", "",
                        "§bGlisse le carburant ici pour recharger !", "",
                        "§7Slots disponibles: §e6")
                .setGlowing(true).build());
        inventory.setItem(45,
                new ItemBuilder(Material.ARROW).setName("§e§l← Retour").setLore("§7Page principale").build());

        ItemStack filler = new ItemBuilder(Material.RED_STAINED_GLASS_PANE).setName("§cEmplacement Verrouillé").build();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (isUpgradeSlot(i)) {
                inventory.setItem(i, null);
                continue;
            }
            ItemStack current = inventory.getItem(i);
            if (current == null || current.getType() == Material.AIR) {
                inventory.setItem(i, filler);
            }
        }

        Inventory upgradeInv = minion.getUpgrades();
        ItemStack[] items = upgradeInv.getContents();
        for (int i = 0; i < UPGRADE_SLOTS.length; i++) {
            int slot = UPGRADE_SLOTS[i];
            if (i < items.length && items[i] != null && items[i].getType() != Material.AIR) {
                ItemStack displayItem = items[i].clone();
                if (itemManager.isVoidModule(displayItem)) {
                    ItemBuilder b = new ItemBuilder(displayItem);
                    List<String> lore = new ArrayList<>(
                            displayItem.getItemMeta().getLore() != null ? displayItem.getItemMeta().getLore()
                                    : new ArrayList<>());
                    lore.add("");
                    lore.add("§b➤ Clique droit pour CONFIGURER");
                    b.setLore(lore);
                    inventory.setItem(slot, b.build());
                } else {
                    inventory.setItem(slot, displayItem);
                }
            }
        }
    }

    private void setupVoidConfigPage() {
        inventory.setItem(4,
                new ItemBuilder(Material.MAGMA_CREAM).setName("§c§lCONFIGURATION DU NÉANT")
                        .setLore("§7Sélectionne les items à détruire.", "", "§eClique pour Activer/Désactiver")
                        .setGlowing(true).build());
        inventory.setItem(45,
                new ItemBuilder(Material.ARROW).setName("§e§l← Retour").setLore("§7Page Améliorations").build());
        for (int i = 0; i < COMMON_JUNK.length && i < VOID_SLOTS.length; i++) {
            Material mat = COMMON_JUNK[i];
            boolean isVoided = minion.isVoidItem(mat);
            ItemStack item = new ItemBuilder(mat)
                    .setName((isVoided ? "§c§lSUPPRIMÉ" : "§a§lCONSERVÉ") + " §7: " + getSeedDisplayName(mat))
                    .setLore("", isVoided ? "§cCet item sera détruit !" : "§aCet item sera gardé.", "",
                            "§eClique pour changer")
                    .setGlowing(isVoided).build();
            inventory.setItem(VOID_SLOTS[i], item);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory))
            return;
        Player clicker = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        // GESTION DE L'INVENTAIRE JOUEUR (Shift-Click vers le Minion)
        if (slot >= 54) {
            if (currentPage == 3 && event.isShiftClick()) {
                ItemStack item = event.getCurrentItem();

                // ✅ FIX CRITIQUE : Gestion du Shift-Click pour le Carburant
                if (item != null && itemManager.isFuelItem(item)) {
                    event.setCancelled(true);
                    consumeFuel(clicker, item); // Appel de la méthode de consommation
                    return;
                }

                if (item != null && !itemManager.isValidUpgrade(item)) {
                    event.setCancelled(true);
                    clicker.sendMessage("§c❌ Cet item n'est pas un module valide !");
                }
            } else if (currentPage != 3 && event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        if (currentPage == 4) {
            event.setCancelled(true);
            if (slot == 45) {
                currentPage = 3;
                setupInventory();
                clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                return;
            }
            for (int i = 0; i < VOID_SLOTS.length; i++) {
                if (slot == VOID_SLOTS[i] && i < COMMON_JUNK.length) {
                    minion.toggleVoidItem(COMMON_JUNK[i]);
                    plugin.getDataManager().saveMinion(minion);
                    setupInventory();
                    clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
                    return;
                }
            }
            return;
        }

        if (currentPage == 3) {
            boolean isUpgradeSlot = false;
            for (int s : UPGRADE_SLOTS) {
                if (slot == s) {
                    isUpgradeSlot = true;
                    break;
                }
            }

            if (isUpgradeSlot) {
                if (event.getClick() == ClickType.RIGHT) {
                    ItemStack item = inventory.getItem(slot);
                    if (item != null && itemManager.isVoidModule(item)) {
                        event.setCancelled(true);
                        currentPage = 4;
                        setupInventory();
                        clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                        return;
                    }
                }

                if (event.getAction().toString().contains("PLACE")
                        || event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
                    ItemStack cursor = event.getCursor();
                    if (cursor != null && cursor.getType() != Material.AIR) {

                        // ✅ GESTION DU CARBURANT (Pose directe)
                        if (itemManager.isFuelItem(cursor)) {
                            event.setCancelled(true);
                            consumeFuel(clicker, cursor); // Appel de la méthode de consommation
                            return;
                        }

                        if (!itemManager.isValidUpgrade(cursor)) {
                            event.setCancelled(true);
                            clicker.sendMessage("§c❌ Cet item n'est pas un module valide !");
                            return;
                        }
                        if (itemManager.isXPPotion(cursor)) {
                            for (int checkSlot : UPGRADE_SLOTS) {
                                ItemStack is = inventory.getItem(checkSlot);
                                if (is != null && itemManager.isXPPotion(is) && checkSlot != slot) {
                                    event.setCancelled(true);
                                    clicker.sendMessage("§c❌ Tu ne peux activer qu'une seule potion d'XP à la fois !");
                                    return;
                                }
                            }
                        }
                        if (itemManager.isHarvestPotion(cursor)) {
                            for (int checkSlot : UPGRADE_SLOTS) {
                                ItemStack is = inventory.getItem(checkSlot);
                                if (is != null && itemManager.isHarvestPotion(is) && checkSlot != slot) {
                                    event.setCancelled(true);
                                    clicker.sendMessage(
                                            "§c❌ Tu ne peux activer qu'une seule potion de récolte à la fois !");
                                    return;
                                }
                            }
                        }
                    }
                }
                return;
            }

            event.setCancelled(true);
            if (slot == 45) {
                saveUpgradesToMinion();
                plugin.getDataManager().saveMinion(minion);
                currentPage = 0;
                setupInventory();
                clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            }
            return;
        }

        if (currentPage == 2) {
            event.setCancelled(true);
            if (event.getClick() == ClickType.LEFT)
                handleSeedsPageClick(slot, clicker);
            return;
        }

        if (isBorderSlot(slot) || isStatsSlot(slot)) {
            event.setCancelled(true);
            if (currentPage == 0)
                handleMainPageClick(slot, clicker);
            else if (currentPage == 1)
                handleStatsPageClick(slot, clicker);
            return;
        }

        if (currentPage == 0 && isInventorySlot(slot)) {
            int slotIndex = getInventorySlotIndex(slot);
            if (slotIndex >= plugin.getLevelManager().getInventorySize(minion.getLevel())) {
                event.setCancelled(true);
                clicker.playSound(clicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
        } else if (currentPage != 0) {
            event.setCancelled(true);
        }
    }

    // ✅ Méthode utilitaire pour consommer le carburant (utilisée par clic et
    // shift-click)
    private void consumeFuel(Player clicker, ItemStack item) {
        long duration = itemManager.getFuelDuration(item);
        minion.addFuel(duration);
        plugin.getDataManager().saveMinion(minion);

        if (item.getAmount() > 1)
            item.setAmount(item.getAmount() - 1);
        else
            item.setAmount(0); // Pour shift-click, cela supprime l'item de l'inventaire joueur

        long hours = duration / 3600000;
        long totalHours = minion.getFuelTimeRemaining() / 3600000;

        String msg = plugin.getMessage("fuel-added")
                .replace("{hours}", String.valueOf(hours))
                .replace("{total}", String.valueOf(totalHours));
        clicker.sendMessage(msg);
        clicker.playSound(clicker.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 1f);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getInventory().equals(inventory))
            return;
        if (currentPage == 3) {
            for (int slot : event.getRawSlots()) {
                if (slot < 54) {
                    boolean isUpgradeSlot = false;
                    for (int uSlot : UPGRADE_SLOTS)
                        if (slot == uSlot)
                            isUpgradeSlot = true;
                    if (!isUpgradeSlot) {
                        event.setCancelled(true);
                        return;
                    }
                    if (!itemManager.isValidUpgrade(event.getOldCursor())) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        } else {
            boolean draggingInStorage = true;
            for (int slot : event.getRawSlots())
                if (slot < 54 && !isInventorySlot(slot))
                    draggingInStorage = false;
            if (currentPage != 0 || !draggingInStorage)
                event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory))
            return;
        if (currentPage == 3)
            saveUpgradesToMinion();
        else if (currentPage == 0)
            saveStorageToMinion();
        plugin.getDataManager().saveMinion(minion);
        HandlerList.unregisterAll(this);
    }

    private void saveUpgradesToMinion() {
        Inventory minionUpgrades = minion.getUpgrades();
        minionUpgrades.clear();
        for (int i = 0; i < UPGRADE_SLOTS.length; i++) {
            int guiSlot = UPGRADE_SLOTS[i];
            ItemStack item = inventory.getItem(guiSlot);
            if (item != null && item.getType() != Material.AIR) {
                ItemStack cleanItem = item.clone();
                if (itemManager.isVoidModule(cleanItem))
                    cleanItem = itemManager.getVoidModule();
                minionUpgrades.setItem(i, cleanItem);
            }
        }
    }

    private void saveStorageToMinion() {
        minion.getInventory().clear();
        int maxSize = plugin.getLevelManager().getInventorySize(minion.getLevel());
        for (int i = 0; i < INVENTORY_SLOTS.length && i < maxSize; i++) {
            int guiSlot = INVENTORY_SLOTS[i];
            ItemStack item = inventory.getItem(guiSlot);
            if (item != null && item.getType() != Material.AIR && !isDecorativeItem(item))
                minion.getInventory().setItem(i, item.clone());
        }
    }

    private boolean isUpgradeSlot(int slot) {
        for (int s : UPGRADE_SLOTS)
            if (s == slot)
                return true;
        return false;
    }

    private boolean isStatsSlot(int slot) {
        if (currentPage != 1)
            return false;
        return slot == 11 || slot == 13 || slot == 15 || slot == 20 || slot == 22 || slot == 24;
    }

    private boolean isBorderSlot(int slot) {
        for (int s : BORDER_SLOTS)
            if (slot == s)
                return true;
        return slot == 4 || slot == 48 || slot == 49 || slot == 50 || slot == 51 || slot == 52 || slot == 53
                || slot == 45;
    }

    private boolean isInventorySlot(int slot) {
        for (int s : INVENTORY_SLOTS)
            if (slot == s)
                return true;
        return false;
    }

    private int getInventorySlotIndex(int guiSlot) {
        for (int i = 0; i < INVENTORY_SLOTS.length; i++)
            if (INVENTORY_SLOTS[i] == guiSlot)
                return i;
        return -1;
    }

    private void handleMainPageClick(int slot, Player clicker) {
        if (slot == 4 || slot == 53) {
            currentPage = 1;
            setupInventory();
            clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        } else if (slot == 48) {
            clicker.closeInventory();
            new ChestLinkingTask(plugin, clicker, minion).start();
            clicker.playSound(clicker.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        } else if (slot == 51) {
            currentPage = 2;
            setupInventory();
            clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        } else if (slot == 52) {
            saveStorageToMinion();
            plugin.getDataManager().saveMinion(minion);
            currentPage = 3;
            setupInventory();
            clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        } else if (slot == 50) {
            clicker.closeInventory();
            plugin.getMinionManager().removeMinion(minion.getUuid());
            clicker.getInventory().addItem(minion.toItemStack());
            clicker.sendMessage(plugin.getMessage("minion-retrieved"));
            Location loc = clicker.getLocation();
            for (ItemStack i : minion.getInventory().getContents())
                if (i != null && i.getType() != Material.AIR)
                    clicker.getWorld().dropItemNaturally(loc, i);
            for (ItemStack i : minion.getUpgrades().getContents())
                if (i != null && i.getType() != Material.AIR)
                    clicker.getWorld().dropItemNaturally(loc, i);
        } else if (slot == 46) {
            if (minion.getLeaderboardUuid() != null && Bukkit.getEntity(minion.getLeaderboardUuid()) != null) {
                minion.removeLeaderboard();
                plugin.getDataManager().saveMinion(minion);
                clicker.sendMessage(plugin.colorize("&cPanneau de statistiques retiré."));
                setupInventory();
                clicker.playSound(clicker.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            } else {
                ItemStack placer = itemManager.getLeaderboardPlacer(minion.getUuid());
                clicker.getInventory().addItem(placer);
                clicker.sendMessage(plugin.colorize("&aTu as reçu le panneau de stats !"));
                clicker.closeInventory();
                clicker.playSound(clicker.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
            }
        }
    }

    private void handleStatsPageClick(int slot, Player clicker) {
        if (slot == 45) {
            currentPage = 0;
            setupInventory();
            clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        }
    }

    private void handleSeedsPageClick(int slot, Player clicker) {
        boolean isSeed = false;
        int idx = -1;
        for (int i = 0; i < SEED_SLOTS.length; i++)
            if (SEED_SLOTS[i] == slot) {
                isSeed = true;
                idx = i;
                break;
            }
        if (isSeed && idx >= 0) {
            List<Material> s = new ArrayList<>(minion.getInfiniteSeeds());
            if (idx < s.size()) {
                minion.toggleSeed(s.get(idx));
                plugin.getDataManager().saveMinion(minion);
                setupInventory();
                clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
            }
            return;
        }
        if (slot == 48) {
            minion.selectAllSeeds();
            plugin.getDataManager().saveMinion(minion);
            setupInventory();
        } else if (slot == 49) {
            minion.deselectAllSeeds();
            plugin.getDataManager().saveMinion(minion);
            setupInventory();
        } else if (slot == 45) {
            currentPage = 0;
            setupInventory();
        }
    }

    private boolean isChestValid(FarmerMinion minion) {
        Location l = minion.getLinkedChest();
        if (l == null)
            return false;
        if (!l.getChunk().isLoaded())
            return true;
        Material t = l.getBlock().getType();
        return t == Material.CHEST || t == Material.BARREL || t == Material.HOPPER || t == Material.TRAPPED_CHEST;
    }

    private String getSeedDisplayName(Material seed) {
        return switch (seed) {
            case WHEAT_SEEDS -> "Graines de Blé";
            case BEETROOT_SEEDS -> "Graines de Betteraves";
            case MELON_SEEDS -> "Graines de Melon";
            case PUMPKIN_SEEDS -> "Graines de Citrouille";
            case NETHER_WART -> "Verrues du Nether";
            case SWEET_BERRIES -> "Baies Sucrées";
            case COCOA_BEANS -> "Fèves de Cacao";
            case SUGAR_CANE -> "Canne à Sucre";
            case CACTUS -> "Cactus";
            case WHEAT -> "Blé";
            case CARROT -> "Carottes";
            case POTATO -> "Pommes de Terre";
            case BEETROOT -> "Betteraves";
            case MELON_SLICE -> "Tranche de Melon";
            case PUMPKIN -> "Citrouille";
            case POISONOUS_POTATO -> "P. de Terre Empoisonnée";
            case ROTTEN_FLESH -> "Chair Putréfiée";
            case BONE -> "Os";
            case ARROW -> "Flèche";
            case DIRT -> "Terre";
            case COBBLESTONE -> "Pierre";
            default -> formatMaterialName(seed);
        };
    }

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();
        for (String word : words)
            formatted.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        return formatted.toString().trim();
    }

    public void open() {
        player.openInventory(inventory);
    }

    private boolean isDecorativeItem(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        net.kyori.adventure.text.Component c = item.getItemMeta().displayName();
        if (c == null)
            return false;
        String n = PlainTextComponentSerializer.plainText().serialize(c);
        return n.contains("Progression") || n.contains("Vitesse") || n.contains("Zone") || n.contains("Capacité")
                || n.contains("Fortune") || n.contains("Graines") || n.contains("NIVEAU") || n.contains("STATISTIQUES")
                || n.contains("SÉLECTION") || n.contains("Informations") || n.contains("MODULES")
                || n.contains("CONFIGURATION") || n.contains("Affichage Stats");
    }
}