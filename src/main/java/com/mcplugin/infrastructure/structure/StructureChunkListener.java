package com.mcplugin.infrastructure.structure;

import com.mcplugin.infrastructure.core.Main;
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
}
