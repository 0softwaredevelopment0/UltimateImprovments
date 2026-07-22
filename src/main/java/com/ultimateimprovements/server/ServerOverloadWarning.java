package com.ultimateimprovements.server;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.util.MessageUtil;
import com.ultimateimprovements.util.ConsoleLogger;
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
                Main.getInstance()
                        .getLogger()
                        .severe("[Server/Critical] MSPT=" + mspt);
                ServerOverloadNotify.broadcast(
                        "<gray>[<white>Server</white><dark_gray>/</dark_gray><dark_red>Critical</dark_red>] <white>MSPT </white><red>" + String.format("%.2f", mspt) +
                                " </red><gray>→ </gray><red>Критическая нагрузка на сервер!</red>"
                );
            }
            return;
        }

        if (mspt >= highMspt) {
            warnedCritical = false;
            if (!warnedHigh) {
                warnedHigh = true;
                Main.getInstance()
                        .getLogger()
                        .warning("[Server/Warning] MSPT=" + mspt);
                ServerOverloadNotify.broadcast(
                        "<gray>[<white>Server</white><dark_gray>/</dark_gray><yellow>Warning</yellow>] <white>MSPT </white><red>" + String.format("%.2f", mspt) +
                                " </red><gray>→ </gray><yellow>Высокая нагрузка на сервер!</yellow>"
                );
            }
            return;
        }

        warnedHigh = false;
        warnedCritical = false;
    }
}
