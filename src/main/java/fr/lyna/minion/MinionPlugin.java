package fr.lyna.minion;

import fr.lyna.minion.commands.MinionCommand;
import fr.lyna.minion.commands.MinionDebugCommand;
import fr.lyna.minion.commands.MinionTestCommand;
import fr.lyna.minion.listeners.MinionDamageListener;
import fr.lyna.minion.listeners.MinionPlaceListener;
import fr.lyna.minion.managers.DataManager;
import fr.lyna.minion.managers.LevelManager;
import fr.lyna.minion.managers.MinionManager;
import fr.lyna.minion.tasks.FarmingTask;
import org.bukkit.plugin.java.JavaPlugin;

public class MinionPlugin extends JavaPlugin {

    private static MinionPlugin instance;
    private MinionManager minionManager;
    private DataManager dataManager;
    private LevelManager levelManager;
    private FarmingTask farmingTask;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        setupFiles();

        this.levelManager = new LevelManager(this);
        // 1. On initialise le manager
        this.minionManager = new MinionManager(this);

        // 2. 🔥 CRUCIAL : On tue tous les villagers minions qui trainent dans le monde
        // Cela élimine les doublons statiques avant même de charger les vrais.
        this.minionManager.killAllMinionEntities();

        this.dataManager = new DataManager(this);
        // 3. On charge les données et on fait respawn les minions proprement
        dataManager.loadAllMinions();

        int taskDelay = getConfig().getInt("settings.minion-tick-delay", 5);
        this.farmingTask = new FarmingTask(this);
        this.farmingTask.runTaskTimer(this, 20L, taskDelay); // Délai initial de 1s pour laisser le monde charger

        registerCommands();
        registerListeners();

        getLogger().info("✅ MinionPlugin activé (Clean Slate Protocol actif)");
    }

    @Override
    public void onDisable() {
        if (farmingTask != null) farmingTask.cancel();

        // 4. On sauvegarde tout
        if (dataManager != null) dataManager.saveAllMinions();

        // 5. On retire les entités du monde pour laisser une map propre
        if (minionManager != null) minionManager.despawnAllMinions();

        getLogger().info("❌ MinionPlugin désactivé");
    }

    private void setupFiles() {
        java.io.File schematicFolder = new java.io.File(getDataFolder(), "schematics");
        if (!schematicFolder.exists()) schematicFolder.mkdirs();
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

    public String getMessage(String path) {
        return colorize(getConfig().getString("messages.prefix", "&8[&6Minion&8] ") + getConfig().getString("messages." + path, path));
    }

    public String colorize(String message) {
        return message.replace("&", "§");
    }
}