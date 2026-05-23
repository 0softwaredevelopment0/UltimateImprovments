package com.mcplugin;

import com.mcplugin.cable.CableNetwork;
import com.mcplugin.commands.PluginReloadCommand;
import com.mcplugin.cp.CodePanelClick;
import com.mcplugin.cp.CodePanelCommand;
import com.mcplugin.crafting.*;
import com.mcplugin.database.*;
import com.mcplugin.energy.*;
import com.mcplugin.energy.crafting.*;
import com.mcplugin.energy.visual.CableVisualTask;
import com.mcplugin.guns.plasmacannon.GunListener;
import com.mcplugin.guns.plasmacannon.PlasmaProjectileTask;
import com.mcplugin.guns.shoker.ShokerListener;
import com.mcplugin.listeners.*;
import com.mcplugin.server.*;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.zip.ZipFile;

public class Main extends JavaPlugin {

    private static Main instance;

    public static Main getInstance() {
        return instance;
    }

    // =========================
    // TASKS
    // =========================
    private BukkitTask generatorTask;
    private BukkitTask cableLossTask;
    private BukkitTask batteryTask;
    private BukkitTask balancerTask;
    private BukkitTask cableVisualTask;
    private BukkitTask overloadTask;
    private BukkitTask redstoneGuardTask;

    private BukkitTask gunTask;

    private boolean tasksStarted = false;

    @Override
    public void onEnable() {

        instance = this;

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        saveDefaultConfig();
        reloadConfig();

        // =========================
        // 🔑 PDC KEYS INIT (ВАЖНО FIX)
        // =========================
        Keys.init(this);

        // =========================
        // SQLITE INIT
        // =========================
        try {
            DatabaseManager.connect();
            DatabaseInit.init();
            getLogger().info("[SQLITE] Database initialized successfully.");
        } catch (Exception e) {
            getLogger().severe("[SQLITE] Init failed: " + e.getMessage());
            e.printStackTrace();
        }

        installDatapack();

        // =========================
        // CORE INIT
        // =========================
        try {
            CableNetwork.init();
            EnergyWorkbenchManager.init();

            MultimeterCraftListener.init();
            PlasmaCannonCraftListener.init();
            ShokerCraftListener.init();

            getLogger().info("[CORE] Systems initialized.");

        } catch (Exception e) {
            getLogger().severe("[CORE] Init failed: " + e.getMessage());
            e.printStackTrace();
        }

        var pm = getServer().getPluginManager();

        // =========================
        // LISTENERS
        // =========================
        pm.registerEvents(new BlockPlaceListener(), this);
        pm.registerEvents(new BlockBreakListener(), this);

        pm.registerEvents(new MultimeterListener(), this);

        pm.registerEvents(new MultimeterCraftListener(), this);
        pm.registerEvents(new PlasmaCannonCraftListener(), this);
        pm.registerEvents(new ShokerCraftListener(), this);

        pm.registerEvents(new EnergyCraftingListener(), this);

        pm.registerEvents(new PluginHideListener(), this);
        pm.registerEvents(new ServerBrandListener(), this);

        pm.registerEvents(new ShokerListener(), this);
        pm.registerEvents(new GunListener(), this);

        RedstoneGuard.init(this);
        pm.registerEvents(new RedstoneGuardListener(), this);

        startTasks();

        register("cp", new CodePanelCommand());
        register("cp_click", new CodePanelClick());
        register("mcplugin", new PluginReloadCommand());

        getLogger().info("[PLUGIN] Plugin enabled!");
    }

    // =========================
    // DATAPACK
    // =========================
    private void installDatapack() {
        try {
            File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
            File datapacksFolder = new File(worldFolder, "datapacks");

            if (!datapacksFolder.exists()) {
                datapacksFolder.mkdirs();
            }

            File targetFolder = new File(datapacksFolder, "MC-Datapack");

            if (targetFolder.exists()) return;

            targetFolder.mkdirs();
            copyFromJar("datapacks/MC-Datapack/", targetFolder);

        } catch (Exception e) {
            getLogger().severe("[DATAPACK] Failed: " + e.getMessage());
        }
    }

    private void copyFromJar(String resourcePath, File targetDir) throws Exception {

        var jar = getFile();

        try (ZipFile zip = new ZipFile(jar)) {

            var entries = zip.entries();

            while (entries.hasMoreElements()) {

                var entry = entries.nextElement();

                if (!entry.getName().startsWith(resourcePath)) continue;

                String relative = entry.getName().substring(resourcePath.length());

                if (relative.isEmpty()) continue;

                File outFile = new File(targetDir, relative);

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                    continue;
                }

                outFile.getParentFile().mkdirs();

                try (var in = zip.getInputStream(entry);
                     var out = new java.io.FileOutputStream(outFile)) {
                    in.transferTo(out);
                }
            }
        }
    }

    // =========================
    // TASKS
    // =========================
    public void startTasks() {

        if (tasksStarted) return;
        tasksStarted = true;

        generatorTask = new GeneratorTask().runTaskTimer(this, 0L, 100L);
        cableLossTask = new CableLossTask().runTaskTimer(this, 0L, 100L);
        batteryTask = new BatteryDrainTask().runTaskTimer(this, 0L, 20L);
        balancerTask = new EnergyBalancerTask().runTaskTimer(this, 0L, 20L);
        cableVisualTask = new CableVisualTask().runTaskTimer(this, 0L, 2L);
        overloadTask = new EmergencyEntitiesKill().runTaskTimer(this, 20L, 20L);
        redstoneGuardTask = new RedstoneGuardTask().runTaskTimer(this, 1L, 1L);

        gunTask = new PlasmaProjectileTask().runTaskTimer(this, 1L, 1L);

        getLogger().info("[TASKS] Started.");
    }

    public void stopTasks() {

        if (generatorTask != null) generatorTask.cancel();
        if (cableLossTask != null) cableLossTask.cancel();
        if (batteryTask != null) batteryTask.cancel();
        if (balancerTask != null) balancerTask.cancel();
        if (cableVisualTask != null) cableVisualTask.cancel();
        if (overloadTask != null) overloadTask.cancel();
        if (redstoneGuardTask != null) redstoneGuardTask.cancel();
        if (gunTask != null) gunTask.cancel();

        tasksStarted = false;
    }

    private void register(String name, CommandExecutor executor) {

        if (getCommand(name) == null) {
            getLogger().warning("Command /" + name + " not found!");
            return;
        }

        getCommand(name).setExecutor(executor);
    }

    @Override
    public void onDisable() {

        stopTasks();

        try {
            CableNetwork.save();
        } catch (Exception e) {
            getLogger().severe("Cable save failed");
        }

        try {
            DatabaseManager.close();
        } catch (Exception e) {
            getLogger().severe("DB close failed");
        }

        getLogger().info("[PLUGIN] Disabled");
    }
}