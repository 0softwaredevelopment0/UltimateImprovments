package com.ultimateimprovments.server;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * EmergencyEntitiesKill — unified entity cleanup mechanic on overload.
 * <p>
 * If MSPT is above the threshold (default 50ms) — the plugin removes the entity type
 * with the highest load (the most common non-player type). On the next check
 * (once per second) the load is recalculated: if it is still above the threshold —
 * the next highest-load type is removed, and so on until it stabilizes below the threshold.
 */
public class EmergencyEntitiesKill extends BukkitRunnable {

    // ===== SETTINGS (loaded from config.yml) =====
    private boolean enabled = true;
    private double msptThreshold = 50.0;
    private int entityLimit = 1000;
    private List<String> killWorlds = new ArrayList<>();
    private boolean logEnabled = true;

    // ===== STATE =====
    private static EmergencyEntitiesKill instance;

    public EmergencyEntitiesKill() {
        instance = this;
        reloadConfig();
    }

    public static void reload() {
        if (instance != null) {
            instance.reloadConfig();
            ConsoleLogger.info("[EMERGENCY_KILL] Config reloaded (enabled=" + instance.enabled + ")");
        }
    }

    public void reloadConfig() {
        var cfg = Main.getInstance().getConfig();
        enabled = cfg.getBoolean("emergency_entity_kill.enabled", true);
        msptThreshold = cfg.getDouble("emergency_entity_kill.mspt_threshold", 50.0);
        entityLimit = cfg.getInt("emergency_entity_kill.entity_limit", 1000);
        killWorlds = cfg.getStringList("emergency_entity_kill.kill_worlds");
        logEnabled = cfg.getBoolean("emergency_entity_kill.log", true);
    }

    @Override
    public void run() {
        if (!enabled) return;

        double mspt = Bukkit.getServer().getAverageTickTime();
        if (mspt < msptThreshold) return;

        List<World> worlds = getRelevantWorlds();

        int totalEntities = 0;
        for (World world : worlds) {
            totalEntities += world.getEntities().size();
        }
        if (totalEntities < entityLimit) return;

        // Unified mechanic: remove the type with the highest load.
        // The next call (after 1s) recalculates MSPT — if it is still above
        // the threshold, the next highest-load type is removed. This repeats until it drops.
        removeMostCommonEntities(worlds, mspt);
    }

    /**
     * Returns the list of worlds where entity removal is allowed.
     * If killWorlds is empty — returns all worlds.
     */
    private List<World> getRelevantWorlds() {
        List<World> allWorlds = Bukkit.getWorlds();
        if (killWorlds.isEmpty()) {
            return allWorlds;
        }
        Set<String> worldSet = killWorlds.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        return allWorlds.stream()
                .filter(w -> worldSet.contains(w.getName().toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Two-pass removal of the most common non-player entity type:
     * pass 1: count + find the most common type (single loop)
     * pass 2: remove all entities of that type
     */
    private void removeMostCommonEntities(List<World> worlds, double mspt) {
        Map<String, Integer> counts = new HashMap<>();

        // Pass 1: count + find the maximum in one pass
        String topType = null;
        int maxCount = 0;

        for (World world : worlds) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) continue;
                if (!entity.isValid()) continue;

                String type = entity.getType().name();
                int newCount = counts.getOrDefault(type, 0) + 1;
                counts.put(type, newCount);

                if (newCount > maxCount) {
                    maxCount = newCount;
                    topType = type;
                }
            }
        }

        if (topType == null || maxCount == 0) return;

        // Pass 2: removal
        int removed = 0;
        for (World world : worlds) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) continue;
                if (!entity.isValid()) continue;
                if (entity.getType().name().equals(topType)) {
                    entity.remove();
                    removed++;
                }
            }
        }

        if (logEnabled) {
            ConsoleLogger.log(
                    MessageUtil.PREFIX + " <yellow>Removed " + removed + " " + topType
                            + " (MSPT=" + String.format("%.1f", mspt)
                            + ", total entities: " + counts.values().stream().mapToInt(Integer::intValue).sum() + ")</yellow>"
            );
        }

        ServerOverloadNotify.broadcast(
                MessageUtil.PREFIX + " <white>Удалено </white><yellow>" + removed + " </yellow><white>" + topType + "</white>"
                        + " <gray>(MSPT </gray><red>" + String.format("%.1f", mspt) + "</red><gray>)</gray>"
        );
    }
}
