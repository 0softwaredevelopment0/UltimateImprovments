package com.mcplugin.server;

import com.mcplugin.Main;
import com.mcplugin.guns.projectile.ProjectileManager;
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

public class EmergencyEntitiesKill extends BukkitRunnable {

    // ===== НАСТРОЙКИ (загружаются из config.yml) =====
    private boolean enabled = true;
    private int entityLimit = 1000;
    private double instantKillMspt = 600.0;
    private double overloadMsptThreshold = 60.0;
    private int maxOverloadAccumulation = 200;
    private int overloadIncrementPerTick = 20;
    private boolean removePlasmaOnOverload = true;
    private double plasmaRemovalMspt = 50.0;
    private List<String> killWorlds = new ArrayList<>();
    private boolean logEnabled = true;

    // ===== СОСТОЯНИЕ =====
    private static EmergencyEntitiesKill instance;
    private int overloadAccumulated = 0;

    public EmergencyEntitiesKill() {
        instance = this;
        reloadConfig();
    }

    public static void reload() {
        if (instance != null) {
            instance.reloadConfig();
            Main.getInstance().getLogger().info("[EMERGENCY_KILL] Config reloaded (enabled=" + instance.enabled + ")");
        }
    }

    public void reloadConfig() {
        var cfg = Main.getInstance().getConfig();
        enabled = cfg.getBoolean("emergency_entity_kill.enabled", true);
        entityLimit = cfg.getInt("emergency_entity_kill.entity_limit", 1000);
        instantKillMspt = cfg.getDouble("emergency_entity_kill.instant_kill_mspt", 600.0);
        overloadMsptThreshold = cfg.getDouble("emergency_entity_kill.overload_mspt_threshold", 60.0);
        maxOverloadAccumulation = cfg.getInt("emergency_entity_kill.max_overload_accumulation", 200);
        overloadIncrementPerTick = cfg.getInt("emergency_entity_kill.overload_increment_per_tick", 20);
        removePlasmaOnOverload = cfg.getBoolean("emergency_entity_kill.remove_plasma_on_overload", true);
        plasmaRemovalMspt = cfg.getDouble("emergency_entity_kill.plasma_removal_mspt", 50.0);
        killWorlds = cfg.getStringList("emergency_entity_kill.kill_worlds");
        logEnabled = cfg.getBoolean("emergency_entity_kill.log", true);
    }

    @Override
    public void run() {
        if (!enabled) return;

        double mspt = Bukkit.getServer().getAverageTickTime();

        int totalEntities = 0;
        List<World> worlds = getRelevantWorlds();
        for (World world : worlds) {
            totalEntities += world.getEntities().size();
        }

        boolean overloadByEntities = totalEntities >= entityLimit;

        // =========================
        // УДАЛЕНИЕ ПЛАЗМЫ (только при перегрузке сущностями)
        // =========================
        if (removePlasmaOnOverload && mspt >= plasmaRemovalMspt && overloadByEntities) {
            int removed = ProjectileManager.removePlasmaProjectiles();
            if (removed > 0) {
                if (logEnabled) {
                    Main.getInstance().getLogger().warning(
                            "[Server/Caution] MSPT=" + mspt
                                    + " -> PLASMA REMOVED: " + removed
                    );
                }
                ServerOverloadNotify.broadcast(
                        "§7[§fServer §8/ §6Caution§7] §fMSPT §c" + String.format("%.1f", mspt)
                                + " §7→ §cУдалено §e" + removed + " §fплазменных снарядов"
                );
            }
        }

        // =========================
        // МГНОВЕННАЯ ЭКСТРЕННАЯ ОЧИСТКА (без таймера)
        // =========================
        if (mspt >= instantKillMspt && overloadByEntities) {
            if (logEnabled) {
                Main.getInstance().getLogger().severe(
                        "[Server/Critical] MSPT=" + mspt + " ENTITIES=" + totalEntities
                );
            }
            ServerOverloadNotify.broadcast(
                    "§7[§fServer §8/ §4Critical§7] §fMSPT §c" + String.format("%.1f", mspt)
                            + " §fСущности §c" + totalEntities
                            + " §7→ §cМгновенное удаление!"
            );
            overloadAccumulated = 0;
            removeMostCommonEntities(worlds);
            return;
        }

        // =========================
        // НОРМАЛЬНАЯ СИТУАЦИЯ — сброс счётчика
        // =========================
        if (mspt < overloadMsptThreshold || !overloadByEntities) {
            overloadAccumulated = 0;
            return;
        }

        // =========================
        // НАКОПЛЕНИЕ ПЕРЕГРУЗКИ (10-сек таймер)
        // Каждый вызов (раз в 1 сек) добавляет overloadIncrementPerTick.
        // Когда накопление >= maxOverloadAccumulation — удаление.
        // По умолчанию: 200/20 = 10 сек до удаления.
        // =========================
        overloadAccumulated += overloadIncrementPerTick;

        if (overloadAccumulated >= maxOverloadAccumulation) {
            overloadAccumulated = 0;
            removeMostCommonEntities(worlds);
        }
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
     * Двухпроходное удаление самых частых не-игрок-сущностей:
     * 1-й проход: подсчёт + поиск самого частого типа (один цикл)
     * 2-й проход: удаление всех сущностей этого типа
     */
    private void removeMostCommonEntities(List<World> worlds) {
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
            Main.getInstance().getLogger().warning(
                    "[Server/Warning] Removed " + removed + " " + topType
                            + " (total entities: " + counts.values().stream().mapToInt(Integer::intValue).sum() + ")"
            );
        }

        ServerOverloadNotify.broadcast(
                "§7[§fServer §8/ §eWarning§7] §fУдалено §e" + removed + " §f" + topType
        );
    }
}
