package com.ultimateimprovments.server;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
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
 * EmergencyEntitiesKill — единая механика очистки сущностей при перегрузке.
 * <p>
 * Если MSPT выше порога (по умолчанию 50ms) — плагин удаляет тип сущностей
 * с самой высокой нагрузкой (самый частый не-игрок тип). На следующей проверке
 * (раз в секунду) нагрузка пересчитывается: если она всё ещё выше порога —
 * удаляется следующий по нагрузке тип, и так до стабилизации ниже порога.
 */
public class EmergencyEntitiesKill extends BukkitRunnable {

    // ===== НАСТРОЙКИ (загружаются из config.yml) =====
    private boolean enabled = true;
    private double msptThreshold = 50.0;
    private int entityLimit = 1000;
    private List<String> killWorlds = new ArrayList<>();
    private boolean logEnabled = true;

    // ===== СОСТОЯНИЕ =====
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

        // Единая механика: удаляем тип с самой высокой нагрузкой.
        // Следующий вызов (через 1 сек) пересчитает MSPT — если он всё ещё выше
        // порога, удалится следующий по нагрузке тип. Так повторяется до спада.
        removeMostCommonEntities(worlds, mspt);
    }

    /**
     * Возвращает список миров, в которых разрешено удаление сущностей.
     * Если killWorlds пуст — возвращает все миры.
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
     * Двухпроходное удаление самого частого не-игрок-типа сущностей:
     * 1-й проход: подсчёт + поиск самого частого типа (один цикл)
     * 2-й проход: удаление всех сущностей этого типа
     */
    private void removeMostCommonEntities(List<World> worlds, double mspt) {
        Map<String, Integer> counts = new HashMap<>();

        // Проход 1: подсчёт + поиск максимума за один проход
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

        // Проход 2: удаление
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
            ConsoleLogger.warn(
                    "Server » Removed " + removed + " " + topType
                            + " (MSPT=" + String.format("%.1f", mspt)
                            + ", total entities: " + counts.values().stream().mapToInt(Integer::intValue).sum() + ")"
            );
        }

        ServerOverloadNotify.broadcast(
                "<white>sᴇʀᴠᴇʀ <dark_gray>» <reset><white>Удалено </white><yellow>" + removed + " </yellow><white>" + topType + "</white>"
                        + " <gray>(MSPT </gray><red>" + String.format("%.1f", mspt) + "</red><gray>)</gray>"
        );
    }
}
