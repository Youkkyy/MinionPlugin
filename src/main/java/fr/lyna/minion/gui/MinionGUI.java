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
        }
    }

    private void setupMainPage() {
        long currentXP = minion.getExperience();
        long requiredXP = plugin.getLevelManager().getXPRequired(minion.getLevel() + 1);
        String progressBar = plugin.getLevelManager().getProgressBar(currentXP, requiredXP, 20);
        int hRange = plugin.getLevelManager().getDetectionRadius(minion.getLevel());
        int vRange = plugin.getLevelManager().getVerticalRange(minion.getLevel());

        inventory.setItem(4,
                new ItemBuilder(Material.EXPERIENCE_BOTTLE).setName("§6§l⚡ NIVEAU " + minion.getLevel())
                        .setLore("§7Expérience: §e" + currentXP + " §7/ §e" + requiredXP, progressBar, "",
                                "§7Prestige: §6" + minion.getPrestige() + "★",
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
        inventory.setItem(52, new ItemBuilder(Material.NETHER_STAR).setName("§e§lAméliorations & Modules")
                .setLore("§7Ajoute des items spéciaux", "§7(Compacteur, Auto-Sell...)", "", "§eClique pour ouvrir")
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
        inventory.setItem(11, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .setName("§e§lProgression").setLore("§7Niveau actuel: §e" + level + "§7/100", "§7XP: §a"
                        + minion.getExperience() + " §7/ §a" + plugin.getLevelManager().getXPRequired(level + 1))
                .build());
        double cooldownSeconds = plugin.getLevelManager().getCooldown(level) / 20.0;
        inventory.setItem(13,
                new ItemBuilder(Material.CLOCK).setName("§e§lVitesse de Travail")
                        .setLore("§7Délai entre actions: §a" + cooldownSeconds + "s", "",
                                "§7Actions par minute: §e" + (int) (60 / cooldownSeconds))
                        .build());

        List<Material> unlockedSeeds = plugin.getLevelManager().getUnlockedSeeds(level);
        List<String> seedsLore = new ArrayList<>();
        seedsLore.add("§7Types débloqués: §e" + unlockedSeeds.size());
        seedsLore.add("");
        seedsLore.add("§7Liste:");
        for (Material seed : unlockedSeeds)
            seedsLore.add("§8• §e" + getSeedDisplayName(seed));
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
        double extraChance = multiplierChance % 100;
        inventory.setItem(24, new ItemBuilder(Material.GOLDEN_HOE).setName("§e§lFortune du Minion").setLore(
                "§7Puissance Totale: §e" + multiplierChance + "%", "",
                "§7Récolte Garantie: §a" + guaranteed + "x §7items",
                extraChance > 0 ? "§7Chance Bonus: §d" + extraChance + "% §7d'avoir §d" + (guaranteed + 1) + "x" : "")
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

    // METHODE fillEmptySlots() SUPPRIMÉE (Code mort)

    private boolean isUpgradeSlot(int slot) {
        for (int s : UPGRADE_SLOTS) {
            if (s == slot)
                return true;
        }
        return false;
    }

    private void setupUpgradesPage() {
        // 1. On place les éléments fixes (Titre + Bouton Retour)
        inventory.setItem(4, new ItemBuilder(Material.ANVIL).setName("§6§lMODULES & AMÉLIORATIONS")
                .setLore("§7Dépose tes items spéciaux ici.", "§7(Compacteur, etc...)", "", "§7Slots disponibles: §e6")
                .setGlowing(true).build());
        inventory.setItem(45,
                new ItemBuilder(Material.ARROW).setName("§e§l← Retour").setLore("§7Page principale").build());

        // 2. Remplissage du fond (Sécurité + Visuel Rouge)
        ItemStack filler = new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .setName("§cEmplacement Verrouillé")
                .build();

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

        // 3. Chargement des items réels du minion
        Inventory upgradeInv = minion.getUpgrades();
        ItemStack[] items = upgradeInv.getContents();

        for (int i = 0; i < UPGRADE_SLOTS.length; i++) {
            int slot = UPGRADE_SLOTS[i];
            if (i < items.length && items[i] != null && items[i].getType() != Material.AIR) {
                inventory.setItem(slot, items[i].clone());
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory))
            return;
        Player clicker = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        // Clic dans l'inventaire du joueur (Bas)
        if (slot >= 54) {
            if (currentPage == 3 && event.isShiftClick()) {
                ItemStack item = event.getCurrentItem();
                if (item != null && !itemManager.isValidUpgrade(item)) {
                    event.setCancelled(true);
                    clicker.sendMessage("§c❌ Cet item n'est pas un module valide !");
                }
            } else if (currentPage != 3 && event.isShiftClick()) {
                event.setCancelled(true); // Bloque shift-click vers les autres pages
            }
            return;
        }

        // --- PAGE 3 : UPGRADES ---
        if (currentPage == 3) {
            boolean isUpgradeSlot = false;
            for (int i : UPGRADE_SLOTS)
                if (slot == i)
                    isUpgradeSlot = true;

            if (isUpgradeSlot) {
                // Validation de l'item posé
                if (event.getAction().toString().contains("PLACE")
                        || event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {

                    ItemStack cursor = event.getCursor();
                    if (cursor != null && cursor.getType() != Material.AIR) {

                        // 1. Vérif validité générale
                        if (!itemManager.isValidUpgrade(cursor)) {
                            event.setCancelled(true);
                            clicker.sendMessage("§c❌ Cet item n'est pas un module valide !");
                            return;
                        }

                        // 2. ✅ VÉRIFICATION ANTI-CUMUL POTIONS
                        if (itemManager.isXPPotion(cursor)) {
                            // VARIABLE upgradeInv SUPPRIMÉE (Code mort)
                            boolean hasPotionAlready = false;

                            // On scanne le GUI actuel pour être précis
                            for (int checkSlot : UPGRADE_SLOTS) {
                                ItemStack itemInSlot = inventory.getItem(checkSlot);
                                if (itemInSlot != null && itemManager.isXPPotion(itemInSlot) && checkSlot != slot) {
                                    hasPotionAlready = true;
                                    break;
                                }
                            }

                            if (hasPotionAlready) {
                                event.setCancelled(true);
                                clicker.sendMessage("§c❌ Tu ne peux activer qu'une seule potion d'XP à la fois !");
                                clicker.playSound(clicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                                return;
                            }
                        }
                    }
                }
                return; // Autorise l'action si tout est OK
            }

            // Si on clique ailleurs dans la page 3
            event.setCancelled(true);
            if (slot == 45) { // Retour
                saveUpgradesToMinion();
                plugin.getDataManager().saveMinion(minion);
                currentPage = 0;
                setupInventory();
                clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            }
            return;
        }

        // --- AUTRES PAGES ---
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

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getInventory().equals(inventory))
            return;
        if (currentPage == 3) {
            for (int slot : event.getRawSlots()) {
                if (slot < 54) { // Si drag dans le GUI
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
            for (int slot : event.getRawSlots()) {
                if (slot < 54 && !isInventorySlot(slot))
                    draggingInStorage = false;
            }
            if (currentPage != 0 || !draggingInStorage)
                event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory))
            return;

        if (currentPage == 3) {
            saveUpgradesToMinion();
        } else if (currentPage == 0) {
            saveStorageToMinion();
        }

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
                minionUpgrades.setItem(i, item.clone());
            }
        }
    }

    private void saveStorageToMinion() {
        minion.getInventory().clear();
        int maxSize = plugin.getLevelManager().getInventorySize(minion.getLevel());
        for (int i = 0; i < INVENTORY_SLOTS.length && i < maxSize; i++) {
            int guiSlot = INVENTORY_SLOTS[i];
            ItemStack item = inventory.getItem(guiSlot);
            if (item != null && item.getType() != Material.AIR && !isDecorativeItem(item)) {
                minion.getInventory().setItem(i, item.clone());
            }
        }
    }

    // --- UTILITAIRES ---
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
            case WHEAT_SEEDS -> "Blé";
            case CARROT -> "Carottes";
            case POTATO -> "Pommes de terre";
            case BEETROOT_SEEDS -> "Betteraves";
            case NETHER_WART -> "Verrues du Nether";
            case MELON_SEEDS -> "Melons";
            case PUMPKIN_SEEDS -> "Citrouilles";
            case SWEET_BERRIES -> "Baies sucrées";
            case COCOA_BEANS -> "Cacao";
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
                || n.contains("SÉLECTION") || n.contains("Informations") || n.contains("MODULES");
    }
}