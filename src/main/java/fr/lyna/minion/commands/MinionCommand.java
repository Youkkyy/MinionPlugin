package fr.lyna.minion.commands;

import fr.lyna.minion.MinionPlugin;
import fr.lyna.minion.entities.FarmerMinion;
import fr.lyna.minion.managers.MinionItemManager;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.util.RayTraceResult;

import java.util.*;

public class MinionCommand implements CommandExecutor, TabCompleter {

    private final MinionPlugin plugin;

    public MinionCommand(MinionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> handleGive(sender, args);
            case "giveitem" -> handleGiveItem(sender, args);
            case "level", "lvl" -> handleLevel(sender, args);
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender);
            case "stats" -> handleStats(sender);
            case "prestige" -> handlePrestige(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.colorize("&cCette commande est réservée aux joueurs !"));
            return;
        }

        if (!sender.hasPermission("minion.give")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
                if (amount < 1 || amount > 64) {
                    player.sendMessage(plugin.colorize("&cLe nombre doit être entre 1 et 64 !"));
                    return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.colorize("&cNombre invalide !"));
                return;
            }
        }

        for (int i = 0; i < amount; i++) {
            UUID minionUUID = UUID.randomUUID();
            FarmerMinion minion = new FarmerMinion(minionUUID, player.getUniqueId(), player.getLocation(), plugin);
            player.getInventory().addItem(minion.toItemStack());
        }

        player.sendMessage(plugin.getMessage("minion-received")
                .replace("{amount}", String.valueOf(amount)));
    }

    private void handleGiveItem(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minion.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.colorize("&cUsage: /minion giveitem <joueur> <item>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.colorize("&cJoueur introuvable."));
            return;
        }

        String itemType = args[2].toLowerCase();
        MinionItemManager itemManager = new MinionItemManager(plugin);
        ItemStack item = null;

        if (itemType.equals("compactor")) {
            item = itemManager.getCompactor();
        } else if (itemType.equals("void")) {
            item = itemManager.getVoidModule();
        } else if (itemType.startsWith("autosell")) {
            try {
                int tier = Integer.parseInt(itemType.substring(8));
                item = itemManager.getAutoSellModule(tier);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.colorize("&cUtilise autosell1, autosell2, autosell3"));
                return;
            }
        } else if (itemType.startsWith("xp")) {
            try {
                int tier = Integer.parseInt(itemType.substring(2));
                item = itemManager.getXPPotion(tier);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.colorize("&cUtilise xp1, xp2... xp6"));
                return;
            }
        } else if (itemType.startsWith("harvest")) {
            try {
                int tier = Integer.parseInt(itemType.substring(7));
                item = itemManager.getHarvestPotion(tier);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.colorize("&cUtilise harvest1, harvest2... harvest6"));
                return;
            }
        } else if (itemType.startsWith("fuel")) {
            try {
                int tier = Integer.parseInt(itemType.substring(4));
                item = itemManager.getFuelItem(tier);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.colorize("&cUtilise fuel1, fuel2... fuel6"));
                return;
            }
        }

        if (item != null) {
            target.getInventory().addItem(item);
            sender.sendMessage(plugin.colorize("&aItem spécial donné à " + target.getName()));
        } else {
            sender.sendMessage(
                    plugin.colorize("&cItem inconnu. Dispo: compactor, void, autosell1-3, xp1-6, harvest1-6, fuel1-6"));
        }
    }

    private void handleLevel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.colorize("&cSeul un joueur peut exécuter cette commande."));
            return;
        }

        if (!player.hasPermission("minion.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        if (args.length < 3) {
            player.sendMessage(plugin.colorize("&cUsage: /minion level <add|set> <nombre>"));
            return;
        }

        FarmerMinion minion = getTargetMinion(player);
        if (minion == null) {
            player.sendMessage(plugin.colorize("&c❌ Tu dois regarder un minion pour modifier son niveau !"));
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.colorize("&cNombre invalide !"));
            return;
        }

        int oldLevel = minion.getLevel();
        int newLevel = oldLevel;

        if (args[1].equalsIgnoreCase("add")) {
            newLevel += amount;
        } else if (args[1].equalsIgnoreCase("set")) {
            newLevel = amount;
        } else {
            player.sendMessage(plugin.colorize("&cAction inconnue. Utilise 'add' ou 'set'."));
            return;
        }

        if (newLevel < 1)
            newLevel = 1;
        if (newLevel > 100)
            newLevel = 100;

        minion.setLevel(newLevel);
        minion.setExperience(0);
        minion.updateInfiniteSeeds();
        minion.updateNameTag();

        plugin.getDataManager().saveMinion(minion);

        player.sendMessage(plugin.colorize("&a✅ Minion mis à jour !"));
        player.sendMessage(plugin.colorize("&7Niveau : &e" + oldLevel + " &7➜ &a" + newLevel));

        player.playSound(minion.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        minion.getLocation().getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, minion.getLocation().add(0, 1, 0), 20,
                0.5, 0.5, 0.5, 0.1);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("minion.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        plugin.getDataManager().saveAllMinions();
        plugin.reloadConfig();
        plugin.getLevelManager().reload();

        sender.sendMessage(plugin.colorize("&a✅ Configuration rechargée avec succès !"));
        sender.sendMessage(plugin.colorize("&7• config.yml rechargé"));
        sender.sendMessage(plugin.colorize("&7• levels.yml rechargé"));
    }

    private void handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.colorize("&cCette commande est réservée aux joueurs !"));
            return;
        }

        int count = plugin.getMinionManager().getPlayerMinionCount(player.getUniqueId());
        int limit = plugin.getConfig().getInt("settings.max-minions-per-player", 5);
        String limitStr = (limit < 0) ? "∞" : String.valueOf(limit);

        player.sendMessage(plugin.colorize("&8&m                                    "));
        player.sendMessage(plugin.colorize("&6&lMES MINIONS"));
        player.sendMessage(plugin.colorize("&7Minions actifs: &e" + count + "&7/&e" + limitStr));
        player.sendMessage(plugin.colorize("&8&m                                    "));
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage(plugin.colorize("&8&m                                    "));
        sender.sendMessage(plugin.colorize("&6&lMINION PLUGIN INFO"));
        sender.sendMessage(plugin.colorize("&7Version: &e" + plugin.getPluginMeta().getVersion()));

        String author = "Inconnu";
        if (!plugin.getPluginMeta().getAuthors().isEmpty()) {
            author = plugin.getPluginMeta().getAuthors().get(0);
        }
        sender.sendMessage(plugin.colorize("&7Auteur: &e" + author));

        sender.sendMessage(plugin.colorize("&7Minions actifs: &e" + plugin.getMinionManager().getAllMinions().size()));
        sender.sendMessage(plugin.colorize("&8&m                                    "));
    }

    private void handleStats(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.colorize("&cCette commande est réservée aux joueurs !"));
            return;
        }

        List<FarmerMinion> ownedMinions = plugin.getMinionManager().getAllMinions()
                .stream()
                .filter(m -> m.getOwnerUUID().equals(player.getUniqueId()))
                .toList();

        if (ownedMinions.isEmpty()) {
            player.sendMessage(plugin.colorize("&cTu n'as aucun minion actif !"));
            return;
        }

        player.sendMessage(plugin.colorize("&8&m═════════════════════════════════════"));
        player.sendMessage(plugin.colorize("&6&l     STATISTIQUES DE TES MINIONS"));
        player.sendMessage(plugin.colorize("&8&m═════════════════════════════════════"));

        long totalXP = 0;

        for (FarmerMinion minion : ownedMinions) {
            totalXP += minion.getExperience();

            String prestigeStars = "&6" + "★".repeat(minion.getPrestige());

            player.sendMessage(plugin.colorize(""));
            player.sendMessage(plugin.colorize("&eMinion #" + minion.getUuid().toString().substring(0, 8)));
            player.sendMessage(plugin.colorize("  &7Niveau: &a" + minion.getLevel() + " " + prestigeStars));
            player.sendMessage(plugin.colorize("  &7XP: &b" + minion.getExperience() + "&7/&b"
                    + plugin.getLevelManager().getXPRequired(minion.getLevel() + 1)));

            double cooldown = plugin.getLevelManager().getCooldown(minion.getLevel()) / 20.0;
            int hRange = plugin.getLevelManager().getDetectionRadius(minion.getLevel());
            int vRange = plugin.getLevelManager().getVerticalRange(minion.getLevel());

            player.sendMessage(plugin.colorize("  &7Vitesse: &a" + cooldown + "s"));
            player.sendMessage(plugin.colorize(
                    "  &7Zone: &e" + (hRange * 2 + 1) + "x" + (hRange * 2 + 1) + " &7(H: &e±" + vRange + "&7)"));

            String state = minion.hasValidTool() ? "&a✓ Actif" : "&c✗ Inactif";
            player.sendMessage(plugin.colorize("  &7État: " + state));
        }

        player.sendMessage(plugin.colorize(""));
        player.sendMessage(plugin.colorize("&7Total: &e" + ownedMinions.size() + " minion(s)"));
        player.sendMessage(plugin.colorize("&7XP total cumulé: &b" + totalXP));
        player.sendMessage(plugin.colorize("&8&m═════════════════════════════════════"));
    }

    private void handlePrestige(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.colorize("&cCette commande est réservée aux joueurs !"));
            return;
        }

        if (!sender.hasPermission("minion.prestige")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        FarmerMinion closestMinion = getTargetMinion(player);

        if (closestMinion == null) {
            double closestDistance = Double.MAX_VALUE;
            for (FarmerMinion minion : plugin.getMinionManager().getAllMinions()) {
                if (!minion.getOwnerUUID().equals(player.getUniqueId()))
                    continue;
                double distance = player.getLocation().distance(minion.getLocation());
                if (distance < closestDistance && distance < 5) {
                    closestDistance = distance;
                    closestMinion = minion;
                }
            }
        }

        if (closestMinion == null) {
            player.sendMessage(plugin.colorize("&cAucun minion trouvé à proximité. Rapproche-toi ou regarde-le."));
            return;
        }

        if (closestMinion.getLevel() < 100) {
            player.sendMessage(plugin.colorize("&c❌ Ton minion doit être niveau 100 pour passer le prestige !"));
            player.sendMessage(plugin.colorize("&7Niveau actuel: &e" + closestMinion.getLevel()));
            return;
        }

        if (closestMinion.getPrestige() >= 5) {
            player.sendMessage(plugin.colorize("&6✨ Ton minion a déjà atteint le prestige maximum !"));
            return;
        }

        closestMinion.setPrestige(closestMinion.getPrestige() + 1);
        closestMinion.setLevel(1);
        closestMinion.setExperience(0);
        closestMinion.updateNameTag();

        plugin.getDataManager().saveMinion(closestMinion);

        player.sendMessage(plugin.colorize("&8&m═════════════════════════════════════"));
        player.sendMessage(plugin.colorize("&6&l        ⭐ PRESTIGE ⭐"));
        player.sendMessage(plugin.colorize("&8&m═════════════════════════════════════"));
        player.sendMessage(plugin.colorize(""));
        player.sendMessage(plugin.colorize("&aTon minion a atteint le prestige " + closestMinion.getPrestige() + " !"));
        player.sendMessage(plugin.colorize("&eLe niveau a été réinitialisé à 1."));
        player.sendMessage(plugin.colorize("&7Bonne chance pour la suite !"));
        player.sendMessage(plugin.colorize(""));
        player.sendMessage(plugin.colorize("&8&m═════════════════════════════════════"));

        player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.getWorld().spawnParticle(Particle.END_ROD, closestMinion.getLocation().add(0, 1, 0), 100, 0.5, 1, 0.5,
                0.1);
    }

    private FarmerMinion getTargetMinion(Player player) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                10,
                0.5,
                entity -> entity instanceof Villager);

        if (result != null && result.getHitEntity() instanceof Villager villager) {
            NamespacedKey key = new NamespacedKey(plugin, "minion_uuid");
            if (villager.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                String uuidStr = villager.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                try {
                    return plugin.getMinionManager().getMinion(UUID.fromString(uuidStr));
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.colorize("&8&m                                    "));
        sender.sendMessage(plugin.colorize("&6&lCOMMANDES MINION"));
        sender.sendMessage(plugin.colorize("&e/minion give [nombre] &7- Obtenir des minions"));
        sender.sendMessage(plugin.colorize("&e/minion list &7- Voir tes minions actifs"));
        sender.sendMessage(plugin.colorize("&e/minion stats &7- Statistiques détaillées"));
        sender.sendMessage(plugin.colorize("&e/minion prestige &7- Prestige (niveau 100)"));
        sender.sendMessage(plugin.colorize("&e/minion info &7- Infos du plugin"));

        if (sender.hasPermission("minion.admin")) {
            sender.sendMessage(plugin.colorize("&c&lADMIN:"));
            sender.sendMessage(plugin.colorize("&c/minion giveitem <p> <item> &7- Donner item spécial"));
            sender.sendMessage(plugin.colorize("&c/minion level <add|set> <nb> &7- Modifier niveau"));
            sender.sendMessage(plugin.colorize("&c/minion reload &7- Recharger config"));
        }

        sender.sendMessage(plugin.colorize("&8&m                                    "));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("give");
            completions.add("list");
            completions.add("stats");
            completions.add("prestige");
            completions.add("info");

            if (sender.hasPermission("minion.admin")) {
                completions.add("reload");
                completions.add("level");
                completions.add("giveitem");
            }

            completions.removeIf(s -> !s.toLowerCase().startsWith(args[0].toLowerCase()));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give")) {
                completions.addAll(Arrays.asList("1", "5", "10", "16", "64"));
            } else if (args[0].equalsIgnoreCase("level") && sender.hasPermission("minion.admin")) {
                completions.add("add");
                completions.add("set");
            } else if (args[0].equalsIgnoreCase("giveitem") && sender.hasPermission("minion.admin")) {
                return null;
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("level") && sender.hasPermission("minion.admin")) {
                completions.addAll(Arrays.asList("1", "10", "50", "100"));
            } else if (args[0].equalsIgnoreCase("giveitem") && sender.hasPermission("minion.admin")) {
                completions.add("compactor");
                completions.add("void");
                completions.add("autosell1");
                completions.add("autosell2");
                completions.add("autosell3");
                for (int i = 1; i <= 6; i++)
                    completions.add("xp" + i);
                for (int i = 1; i <= 6; i++)
                    completions.add("harvest" + i);
                for (int i = 1; i <= 6; i++)
                    completions.add("fuel" + i);
            }
        }

        return completions;
    }
}