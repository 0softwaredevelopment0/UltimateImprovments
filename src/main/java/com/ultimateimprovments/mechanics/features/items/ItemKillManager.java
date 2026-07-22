package com.ultimateimprovments.mechanics.features.items;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitRunnable;

public class ItemKillManager extends BukkitRunnable {

    private static ItemKillManager instance;
    private static boolean enabled = true;
    private static int itemLimit = 6400;
    private static boolean warnOperators = true;

    public static void init(Main plugin) {
        instance = new ItemKillManager();
        reloadConfig();
        int interval = Main.getInstance().getConfig().getInt("features.itemskill.interval_ticks", 20);
        instance.runTaskTimer(plugin, 40L, interval);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.itemskill");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
        itemLimit = cfg.getInt("item_limit", 6400);
        warnOperators = cfg.getBoolean("warn_operators", true);
    }

    @Override
    public void run() {
        if (!enabled) return;

        int itemCount = Bukkit.getWorlds().stream()
                .flatMap(w -> w.getEntitiesByClass(org.bukkit.entity.Item.class).stream())
                .mapToInt(e -> 1)
                .sum();

        if (itemCount >= itemLimit) {
            ConsoleLogger.warn("[ItemKill] Item count: " + itemCount + " — killing all items!");

            if (warnOperators) {
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("ui.admin") || p.isOp())
                        .forEach(p -> p.sendMessage(MessageUtil.parse("<dark_red>СЕРВЕР</dark_red> <dark_gray>»</dark_gray> <white>Кол-во предметов: <red>" + itemCount + "</red><white> слишком большое, они будут удалены, для предотвращения лагов!</white></white>")));
            }

            Bukkit.getWorlds().forEach(w -> w.getEntitiesByClass(org.bukkit.entity.Item.class).forEach(e -> e.remove()));
        }
    }
}
