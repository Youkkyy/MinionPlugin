package fr.lyna.minion;

import fr.lyna.minion.commands.MinionCommand;
import fr.lyna.minion.commands.MinionDebugCommand;
import fr.lyna.minion.commands.MinionTestCommand;
import fr.lyna.minion.listeners.LeaderboardListener;
import fr.lyna.minion.listeners.MinionDamageListener;
import fr.lyna.minion.listeners.MinionPlaceListener;
import fr.lyna.minion.managers.DataManager;
import fr.lyna.minion.managers.LevelManager;
import fr.lyna.minion.managers.MinionManager;
import fr.lyna.minion.tasks.FarmingTask;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class MinionPlugin extends JavaPlugin {

    private static MinionPlugin instance;
    private MinionManager minionManager;
    private DataManager dataManager;
    private LevelManager levelManager;
    private FarmingTask farmingTask;
    private Economy economy;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        setupFiles();

        // ✅ MODIFICATION : On tente de charger l'économie, mais on ne coupe pas le
        // plugin si ça échoue.
        // C'est FarmingTask qui réessaiera plus tard.
        if (!setupEconomy()) {
            getLogger()
                    .warning("⚠️ Économie introuvable au démarrage. Le plugin va réessayer de se connecter plus tard.");
        }

        this.levelManager = new LevelManager(this);
        this.minionManager = new MinionManager(this);

        // Clean Slate Protocol
        this.minionManager.killAllMinionEntities();

        this.dataManager = new DataManager(this);
        dataManager.loadAllMinions();

        int taskDelay = getConfig().getInt("settings.minion-tick-delay", 5);
        this.farmingTask = new FarmingTask(this);
        this.farmingTask.runTaskTimer(this, 20L, taskDelay);

        registerCommands();
        registerListeners();

        getLogger().info("✅ MinionPlugin activé (Clean Slate Protocol actif)");
    }

    @Override
    public void onDisable() {
        if (farmingTask != null)
            farmingTask.cancel();

        if (dataManager != null)
            dataManager.saveAllMinions();

        if (minionManager != null)
            minionManager.despawnAllMinions();

        getLogger().info("❌ MinionPlugin désactivé");
    }

    // ✅ Rendue publique pour être appelée depuis FarmingTask
    public boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        if (economy != null) {
            getLogger().info("✅ Économie connectée avec succès : " + economy.getName());
            return true;
        }
        return false;
    }

    private void setupFiles() {
        java.io.File schematicFolder = new java.io.File(getDataFolder(), "schematics");
        if (!schematicFolder.exists())
            schematicFolder.mkdirs();
        try {
            saveResource("schematics/mcfarm.schem", false);
        } catch (Exception ignored) {
        }
        try {
            saveResource("levels.yml", false);
        } catch (Exception ignored) {
        }
    }

    private void registerCommands() {
        getCommand("minion").setExecutor(new MinionCommand(this));
        getCommand("miniontest").setExecutor(new MinionTestCommand(this));
        getCommand("miniondebug").setExecutor(new MinionDebugCommand(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(minionManager, this);
        getServer().getPluginManager().registerEvents(new MinionPlaceListener(this), this);
        getServer().getPluginManager().registerEvents(new MinionDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new LeaderboardListener(this), this);
    }

    public static MinionPlugin getInstance() {
        return instance;
    }

    public MinionManager getMinionManager() {
        return minionManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public LevelManager getLevelManager() {
        return levelManager;
    }

    public Economy getEconomy() {
        return economy;
    }

    public String getMessage(String path) {
        return colorize(getConfig().getString("messages.prefix", "&8[&6Minion&8] ")
                + getConfig().getString("messages." + path, path));
    }

    public String colorize(String message) {
        return message.replace("&", "§");
    }
}