package com.mcplugin.server;

import com.mcplugin.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;

public class ServerOverloadWarning extends BukkitRunnable {

    private boolean warnedHigh = false;
    private boolean warnedCritical = false;

    @Override
    public void run() {

        double mspt = Bukkit.getServer().getAverageTickTime();

        if (mspt >= 50.0) {

            warnedHigh = false;

            if (!warnedCritical) {

                warnedCritical = true;

                String msg =
                        ChatColor.DARK_RED +
                                "☠ CRITICAL SERVER LOAD! " +
                                ChatColor.GRAY +
                                "(MSPT: " +
                                ChatColor.WHITE +
                                String.format("%.2f", mspt) +
                                ChatColor.GRAY +
                                ")";

                Main.getInstance()
                        .getLogger()
                        .severe("[PERFORMANCE] CRITICAL MSPT: " + mspt);

                ServerOverloadNotify.broadcast(msg);
            }

            return;
        }

        if (mspt >= 40.0) {

            warnedCritical = false;

            if (!warnedHigh) {

                warnedHigh = true;

                String msg =
                        ChatColor.RED +
                                "⚠ HIGH SERVER LOAD! " +
                                ChatColor.GRAY +
                                "(MSPT: " +
                                ChatColor.WHITE +
                                String.format("%.2f", mspt) +
                                ChatColor.GRAY +
                                ")";

                Main.getInstance()
                        .getLogger()
                        .warning("[PERFORMANCE] HIGH MSPT: " + mspt);

                ServerOverloadNotify.broadcast(msg);
            }

            return;
        }

        warnedHigh = false;
        warnedCritical = false;
    }
}
