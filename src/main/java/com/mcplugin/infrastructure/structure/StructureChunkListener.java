package com.mcplugin.infrastructure.structure;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.energy.consumption.light.LightManager;
import com.mcplugin.energy.machines.workbench.EnergyWorkbenchManager;
import com.mcplugin.energy.storage.battery.BatteryManager;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.mechanics.environment.lightning.LightningManager;
import com.mcplugin.mechanics.environment.magnet.MagnetManager;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Слушатель загрузки чанков — сканирует чанк на Marker'ы структур
 * и восстанавливает in-memory кэш StructureMarker.
 * <p>
 * Регистрируется при старте плагина, слушает все загруженные чанки.
 */
public class StructureChunkListener implements Listener {

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        // Сканируем чанк на Marker'ы и добавляем в кэш
        StructureMarker.scanChunk(e.getChunk());
    }

    /**
     * Сканирует ВСЕ загруженные чанки на всех мирах (вызывается при init плагина).
     */
    public static void scanAll() {
        Main.getInstance().getLogger().info("[StructureMarker] Scanning all loaded chunks for structure markers...");
        int count = 0;
        for (org.bukkit.World world : Main.getInstance().getServer().getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                StructureMarker.scanChunk(chunk);
                count++;
            }
        }
        Main.getInstance().getLogger().info("[StructureMarker] Scanned " + count + " chunks");
    }

    /**
     * Планирует отложенную перестройку структур из Marker'ов.
     * <p>
     * При старте сервера загружены только спавн-чанки. scanAll() находит почти ничего.
     * Через 5 секунд, когда больше чанков загружено, повторно сканируем и перестраиваем
     * все менеджеры, работающие через Marker'ы.
     * <p>
     * ВАЖНО: Только ОДИН вызов. Повторный вызов rebuildFromMarkers() сбросит runtime-состояние
     * (энергию в CableNode, режимы батарей и т.д.), которое было накоплено после первой перестройки.
     *
     * @param plugin плагин (для BukkitScheduler)
     */
    public static void scheduleDelayedRebuild(Plugin plugin) {
        // Через 5 секунд — большинство чанков рядом со спавном уже загружены
        Bukkit.getScheduler().runTaskLater(plugin, StructureChunkListener::rebuildAllManagers, 100L);
    }

    /**
     * Перестраивает все менеджеры из кэша StructureMarker.
     * Сначала сканирует все загруженные чанки, затем перезапускает rebuild каждого менеджера.
     */
    private static void rebuildAllManagers() {
        // 1. Сканируем все загруженные чанки (кэш пополняется новыми Marker'ами)
        scanAll();

        int markerCount = StructureMarker.getAllEntries().size();
        Main.getInstance().getLogger().info("[StructureMarker] Rebuilding all managers from cache (" + markerCount + " markers)...");

        // 2. Перестраиваем каждый менеджер из маркеров
        CableNetwork.rebuildFromMarkers();
        BatteryManager.rebuildFromMarkers();
        LightManager.rebuildFromMarkers();
        LightningManager.rebuildFromMarkers();
        EnergyWorkbenchManager.scanFromMarkers();
        MagnetManager.rebuildFromMarkers();

        Main.getInstance().getLogger().info("[StructureMarker] Rebuild complete.");
    }
}
