package com.ultimateimprovments.server;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

public class ServerOverloadWarning extends BukkitRunnable {

    private boolean warnedHigh = false;
    private boolean warnedCritical = false;

    private boolean enabled = true;
    private double highMspt = 40.0;
    private double criticalMspt = 50.0;

    private static ServerOverloadWarning instance;

    public ServerOverloadWarning() {
        instance = this;
        reloadConfig();
    }

    public static void reload() {
        if (instance != null) {
            instance.reloadConfig();
            ConsoleLogger.info("[OVERLOAD_WARNING] Config reloaded (enabled=" + instance.enabled + ")");
        }
    }

    public void reloadConfig() {
        FileConfiguration cfg = Main.getInstance().getConfig();
        enabled = cfg.getBoolean("server_overload_warning.enabled", true);
        highMspt = cfg.getDouble("server_overload_warning.high_mspt", 40.0);
        criticalMspt = cfg.getDouble("server_overload_warning.critical_mspt", 50.0);
        ServerOverloadNotify.setCooldownMs(cfg.getLong("server_overload_warning.notify_cooldown_seconds", 30) * 1000L);
    }

    @Override
    public void run() {
        if (!enabled) {
            warnedHigh = false;
            warnedCritical = false;
            return;
        }

        double mspt = Bukkit.getServer().getAverageTickTime();

        if (mspt >= criticalMspt) {
            warnedHigh = false;
            if (!warnedCritical) {
                warnedCritical = true;
                ConsoleLogger.log(
                        "<red>MSPT=" + String.format("%.1f", mspt)
                                + " — critical threshold!</red>"
                );
                ServerOverloadNotify.broadcast(
                        MessageUtil.PREFIX + "<white>Server MSPT reached a critical threshold! Check tick info.<dark_gray>(<yellow>"
                                + String.format("%.1f", mspt) + "<dark_gray>)"
                );
            }
            return;
        }

        if (mspt >= highMspt) {
            warnedCritical = false;
            if (!warnedHigh) {
                warnedHigh = true;
                ConsoleLogger.log(
                        "<yellow>MSPT=" + String.format("%.1f", mspt)
                                + " — safe operation exceeded!</yellow>"
                );
                ServerOverloadNotify.broadcast(
                        MessageUtil.PREFIX + "<white>Server MSPT is exceeding safe operation parameters! <dark_gray>(<yellow>"
                                + String.format("%.1f", mspt) + "<dark_gray>)"
                );
            }
            return;
        }

        warnedHigh = false;
        warnedCritical = false;
    }
}
