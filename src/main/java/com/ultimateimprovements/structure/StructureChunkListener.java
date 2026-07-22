package com.ultimateimprovements.structure;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.energy.consumption.light.LightManager;
import com.ultimateimprovements.energy.machines.workbench.EnergyWorkbenchManager;
import com.ultimateimprovements.energy.storage.battery.BatteryManager;
import com.ultimateimprovements.energy.transfer.cable.CableNetwork;
import com.ultimateimprovements.mechanics.environment.lightning.LightningManager;
import com.ultimateimprovements.mechanics.environment.magnet.MagnetManager;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Слушатель загрузки чанков и миров — сканирует чанк на Marker'ы структур
 * и восстанавливает in-memory кэш StructureMarker.
 * <p>
 * Регистрируется при старте плагина, слушает все загруженные чанки.
 * <p>
 * Проблема: при старте сервера загружены только спавн-чанки.
 * Решение: множественные отложенные rebuild (5с, 30с, 120с) +
 * WorldLoadEvent + debounced rebuild из ChunkLoadEvent.
 */
public class StructureChunkListener implements Listener {

    // ════════════════════════════════════════
    // WORLD LOAD — при загрузке нового мира сканируем его чанки
    // ════════════════════════════════════════
    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        World world = e.getWorld();
        for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
            StructureMarker.scanChunk(chunk);
        }
        // Перестраиваем менеджеры с учётом нового мира
        rebuildAllManagers();
    }

    // ════════════════════════════════════════
    // CHUNK LOAD — при загрузке чанка сканируем Marker'ы
    // Если найдены новые — планируем debounced rebuild
    // ════════════════════════════════════════
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        boolean foundNew = StructureMarker.scanChunk(e.getChunk());
        if (foundNew) {
            scheduleDebouncedChunkRebuild();
        }
    }

    // ════════════════════════════════════════
    // SCAN ALL — сканировать все загруженные чанки на всех мирах
    // ════════════════════════════════════════
    public static void scanAll() {
        for (org.bukkit.World world : Main.getInstance().getServer().getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                StructureMarker.scanChunk(chunk);
            }
        }
    }

    // ════════════════════════════════════════
    // DEBOUNCED CHUNK REBUILD
    // Если чанки загружаются пачками — не дёргаем rebuild каждый раз,
    // а ждём 2 секунды без новых чанков с Marker'ами.
    // ════════════════════════════════════════
    private static int chunkRebuildTaskId = -1;

    private static synchronized void scheduleDebouncedChunkRebuild() {
        if (chunkRebuildTaskId != -1) {
            Bukkit.getScheduler().cancelTask(chunkRebuildTaskId);
        }
        chunkRebuildTaskId = Bukkit.getScheduler().runTaskLater(
            Main.getInstance(),
            () -> {
                synchronized (StructureChunkListener.class) {
                    chunkRebuildTaskId = -1;
                }
                rebuildAllManagers();
            },
            40L // 2 секунды debounce
        ).getTaskId();
    }

    // ════════════════════════════════════════
    // SCHEDULE DELAYED REBUILDS
    // При старте сервера загружены только спавн-чанки. scanAll() находит почти ничего.
    // Множественные отложенные перестройки покрывают разные стадии загрузки:
    //   5 сек — большинство чанков рядом со спавном
    //  30 сек — чанки дальше от спавна, мультимиры
    // 120 сек — чанки на краю загрузки, поздние миры (Multiverse etc.)
    // ════════════════════════════════════════
    public static void scheduleDelayedRebuild(Plugin plugin) {
        // 5 секунд
        Bukkit.getScheduler().runTaskLater(plugin, () -> rebuildAllManagers(), 100L);

        // 30 секунд
        Bukkit.getScheduler().runTaskLater(plugin, () -> rebuildAllManagers(), 600L);

        // 120 секунд
        Bukkit.getScheduler().runTaskLater(plugin, () -> rebuildAllManagers(), 2400L);
    }

    // ════════════════════════════════════════
    // REBUILD ALL MANAGERS
    // Перестраивает все менеджеры из кэша StructureMarker.
    // Сначала сканирует все загруженные чанки, затем перезапускает rebuild каждого менеджера.
    // ════════════════════════════════════════
    private static void rebuildAllManagers() {
        // 1. Сканируем все загруженные чанки (кэш пополняется новыми Marker'ами)
        scanAll();

        // 2. Перестраиваем каждый менеджер из маркеров
        CableNetwork.rebuildFromMarkers();
        BatteryManager.rebuildFromMarkers();
        LightManager.rebuildFromMarkers();
        LightningManager.rebuildFromMarkers();
        EnergyWorkbenchManager.scanFromMarkers();
        MagnetManager.rebuildFromMarkers();
    }
}
