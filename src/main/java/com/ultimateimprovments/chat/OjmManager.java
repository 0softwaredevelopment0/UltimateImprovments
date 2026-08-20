package com.ultimateimprovments.chat;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * OjmManager — Override Join Messages (OJM) feature.
 *
 * <p>Replaces the default join/quit chat messages with configurable
 * MiniMessage strings (config section {@code ojm}). The {@code <player>}
 * placeholder is replaced with the player's nick before parsing.</p>
 *
 * <p>Runs at HIGHEST so vanished players are respected: VanishManager
 * already nulls the message at LOW, and a null message is left untouched.</p>
 */
public final class OjmManager implements Listener {

    private static boolean enabled = false;
    private static String joinMsg = "";
    private static String leaveMsg = "";

    private OjmManager() {}

    public static void init(Main plugin) {
        reload();
        plugin.getServer().getPluginManager().registerEvents(new OjmManager(), plugin);
    }

    /** Reads the ojm section from the config. Called on startup and on /ui reload. */
    public static void reload() {
        var cfg = Main.getInstance().getConfig();
        enabled = cfg.getBoolean("ojm.enabled", false);
        joinMsg = cfg.getString("ojm.join", "");
        leaveMsg = cfg.getString("ojm.leave", "");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        // Vanished players already have a null message (VanishManager at LOW) — don't leak them.
        if (event.joinMessage() == null) return;
        if (joinMsg == null || joinMsg.isBlank()) return;
        event.joinMessage(MessageUtil.parse(joinMsg.replace("<player>", event.getPlayer().getName())));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (!enabled) return;
        if (event.quitMessage() == null) return;
        if (leaveMsg == null || leaveMsg.isBlank()) return;
        event.quitMessage(MessageUtil.parse(leaveMsg.replace("<player>", event.getPlayer().getName())));
    }
}
