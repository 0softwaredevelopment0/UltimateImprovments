package com.ultimateimprovments.listener;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.AlertBroadcast;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LoadedAlertListener — after a server start, every player who receives
 * alerts ({@code ui.alerts} / OP, per {@link AlertBroadcast}) is told
 * <pre>prefix + "UltimateImprovments loaded successfully!"</pre>
 * exactly ONCE per server session: the first time they are present after
 * startup (online at enable time, or on their first join afterwards).
 * Later joins in the same session stay silent. The in-memory flag set
 * resets naturally on the next server restart (fresh classloader).
 * <p>
 * Feature toggle: {@code loaded_alert.enabled} (default true, read live —
 * /ui reload applies it without a restart).
 */
public final class LoadedAlertListener implements Listener {

    /** Players already notified in this server session (one message each). */
    private static final Set<UUID> NOTIFIED = ConcurrentHashMap.newKeySet();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        notifyIfNeeded(event.getPlayer());
    }

    /** Notifies alert-holders that are already online when ui-core (re)enables. */
    public static void sweepOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            notifyIfNeeded(player);
        }
    }

    private static void notifyIfNeeded(Player player) {
        if (!isEnabled()) return;
        if (!AlertBroadcast.hasAlertPermission(player)) return;
        if (!NOTIFIED.add(player.getUniqueId())) return; // already shown this session
        player.sendMessage(MessageUtil.parse(
                MessageUtil.PREFIX + "<white>UltimateImprovments loaded successfully!"));
    }

    /** Feature toggle: {@code loaded_alert.enabled} (default true). */
    private static boolean isEnabled() {
        Main plugin = Main.getInstance();
        return plugin == null
                || plugin.getConfig().getBoolean("loaded_alert.enabled", true);
    }
}

