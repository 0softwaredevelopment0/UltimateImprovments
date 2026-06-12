package com.mcplugin.server;

import com.mcplugin.Main;
import org.bukkit.Bukkit;
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

                Main.getInstance()
                        .getLogger()
                        .severe("[SERVER | CRITICAL] MSPT=" + mspt);

                ServerOverloadNotify.broadcast(
                        "§7[§fSERVER §8| §cCRITICAL§7] §fMSPT §c" + String.format("%.2f", mspt) +
                                " §7→ §cCritical server load!"
                );
            }

            return;
        }

        if (mspt >= 40.0) {

            warnedCritical = false;

            if (!warnedHigh) {

                warnedHigh = true;

                Main.getInstance()
                        .getLogger()
                        .warning("[SERVER | WARNING] MSPT=" + mspt);

                ServerOverloadNotify.broadcast(
                        "§7[§fSERVER §8| §eWARNING§7] §fMSPT §c" + String.format("%.2f", mspt) +
                                " §7→ §eHigh server load!"
                );
            }

            return;
        }

        warnedHigh = false;
        warnedCritical = false;
    }
}
