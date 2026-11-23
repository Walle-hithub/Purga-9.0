package com.lapurga;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LaPurgaPlugin extends JavaPlugin {

    private PurgaManager manager;
    private LootTable lootTable;
    private MobBuffer buffer;
    private LootGuiManager guiManager;
    private ShadowDevourerManager bossManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        lootTable = new LootTable(this);
        lootTable.load();
        buffer = new MobBuffer(this);
        manager = new PurgaManager(this, buffer);
        bossManager = new ShadowDevourerManager(this, manager);
        manager.setBossManager(bossManager);
        new SafeZoneManager(this);
        guiManager = new LootGuiManager(this, lootTable);

        PurgaCommand command = new PurgaCommand(this, manager, guiManager, bossManager);
        PluginCommand mainCmd = getCommand("dificultadespecial");
        if (mainCmd != null) {
            mainCmd.setExecutor(command);
            mainCmd.setTabCompleter(command);
        }

        PluginCommand lootCmd = getCommand("purgaloot");
        if (lootCmd != null) {
            lootCmd.setExecutor(command);
        }

        getServer().getPluginManager().registerEvents(new MobListener(this, manager, buffer), this);
        getServer().getPluginManager().registerEvents(new CreeperListener(this, manager), this);
        getServer().getPluginManager().registerEvents(new LootListener(this, manager, lootTable), this);
        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(new SafeZoneListener(), this);
        getServer().getPluginManager().registerEvents(bossManager, this);

        SpawnTask spawnTask = new SpawnTask(this, manager);
        spawnTask.setBossManager(bossManager);
        spawnTask.runTaskTimer(this, 100L, 20L);

        new ZombieTask(this, manager, buffer).runTaskTimer(this, 100L, 20L);
        new AtmosphereTask(this, manager).runTaskTimer(this, 0L, 40L);

        getLogger().info("LaPurga v2.1 activado con dificultad DEVORADOR y Jefe Titán.");
    }

    @Override
    public void onDisable() {
        if (bossManager != null) {
            bossManager.shutdown();
        }
        if (manager != null) {
            manager.shutdown();
        }
    }

    public void reloadAll() {
        reloadConfig();
        lootTable.load();
    }
}
