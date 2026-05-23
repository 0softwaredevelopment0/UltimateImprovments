package com.mcplugin.server;

import com.mcplugin.Main;
import com.mcplugin.guns.projectile.ProjectileManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class EmergencyEntitiesKill extends BukkitRunnable {

    private int overloadTicks = 0;

    private static final int MAX_OVERLOAD_TIME = 20 * 10;
    private static final double INSTANT_KILL_MSPT = 600.0;
    private static final int ENTITY_LIMIT = 1000;

    private static final double BULLET_KILL_MSPT = 50.0;

    @Override
    public void run() {

        double mspt = Bukkit.getServer().getAverageTickTime();

        int totalEntities = 0;

        for (World world : Bukkit.getWorlds()) {
            totalEntities += world.getEntities().size();
        }

        boolean overloadByEntities = totalEntities >= ENTITY_LIMIT;

        // =========================
        // 🔫 PLASMA OVERLOAD FIXED
        // =========================
        if (mspt >= BULLET_KILL_MSPT) {

            if (ProjectileManager.hasPlasmaProjectiles()) {

                int removed = ProjectileManager.removePlasmaProjectiles();

                String msg =
                        "[OVERLOAD] MSPT=" + mspt +
                                " → PLASMA REMOVED: " + removed;

                Main.getInstance().getLogger().warning(msg);

                ServerOverloadNotify.broadcast(
                        "§7[§4OVERLOAD§7] §fMSPT §c" + mspt +
                                " §7→ §cRemoved §e" + removed +
                                " §fplasma projectiles"
                );
            }
        }

        // =========================
        // INSTANT EMERGENCY
        // =========================
        if (mspt >= INSTANT_KILL_MSPT && overloadByEntities) {

            Main.getInstance().getLogger().severe(
                    "[OVERLOAD] MSPT=" + mspt + " ENTITIES=" + totalEntities
            );

            ServerOverloadNotify.broadcast(
                    "§7[§4OVERLOAD§7] §fMSPT §c" + mspt +
                            " §fEntities §c" + totalEntities +
                            "§f will be deleted."
            );

            overloadTicks = 0;
            removeMostCommonEntities();
            return;
        }

        if (mspt < 60 || !overloadByEntities) {
            overloadTicks = 0;
            return;
        }

        overloadTicks += 20;

        if (overloadTicks < MAX_OVERLOAD_TIME) {
            return;
        }

        overloadTicks = 0;
        removeMostCommonEntities();
    }

    private void removeMostCommonEntities() {
        Map<String, Integer> counts = new HashMap<>();

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {

                if (entity instanceof Player) continue;

                String type = entity.getType().name();
                counts.put(type, counts.getOrDefault(type, 0) + 1);
            }
        }

        if (counts.isEmpty()) return;

        String topType = null;
        int max = 0;

        for (var entry : counts.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                topType = entry.getKey();
            }
        }

        if (topType == null) return;

        int removed = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {

                if (entity instanceof Player) continue;

                if (entity.getType().name().equals(topType)) {
                    entity.remove();
                    removed++;
                }
            }
        }

        Main.getInstance().getLogger().severe(
                "[OVERLOAD] Removed " + removed + " " + topType
        );

        ServerOverloadNotify.broadcast(
                "§7[§4OVERLOAD§7] §fRemoved §e" + removed + " §f" + topType
        );
    }
}