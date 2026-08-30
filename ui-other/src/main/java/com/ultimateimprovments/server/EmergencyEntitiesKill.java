package com.ultimateimprovments.server;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
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
 * If MSPT is above the threshold — the plugin removes the entity type
 * with the highest load (the most common non-player type).
 */
public class EmergencyEntitiesKill extends BukkitRunnable {

    private boolean enabled = true;
    private double msptThreshold = 50.0;
    private int entityLimit = 1000;
    private List<String> killWorlds = new ArrayList<>();
    private boolean logEnabled = true;
    private boolean broadcastToAll = false;
    private long cooldownMs = 30_000L;

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
        broadcastToAll = cfg.getBoolean("emergency_entity_kill.broadcast_to_all", false);
        cooldownMs = cfg.getLong("emergency_entity_kill.notification_cooldown_ms", 30_000L);
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

        removeMostCommonEntities(worlds, mspt);
    }

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

    private void removeMostCommonEntities(List<World> worlds, double mspt) {
        Map<String, Integer> counts = new HashMap<>();

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
                    "<yellow>Removed " + removed + " " + topType
                            + " (MSPT=" + String.format("%.1f", mspt)
                            + ", total entities: " + counts.values().stream().mapToInt(Integer::intValue).sum() + ")</yellow>"
            );
        }

        String message = MessageUtil.PREFIX + "<white>Удалено </white><yellow>" + removed + " </yellow><white>" + topType + "</white>"
                + " <gray>(MSPT </gray><red>" + String.format("%.1f", mspt) + "</red><gray>)</gray>";

        if (broadcastToAll) {
            ServerOverloadNotify.broadcastAll(message, cooldownMs);
        } else {
            ServerOverloadNotify.setCooldownMs(cooldownMs);
            ServerOverloadNotify.broadcast(message);
        }
    }
}
