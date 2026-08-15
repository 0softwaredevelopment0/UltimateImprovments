package com.ultimateimprovments.mechanics.security.auth;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the authentication timeout.
 * Kicks the player if they did not log in / register within N seconds.
 * <p>
 * The timer starts when the player joins (handleJoin) and is cancelled
 * on successful authentication or when the player leaves.
 */
public class AuthTimeoutManager {

    private static AuthTimeoutManager instance;
    private final Map<UUID, BukkitRunnable> loginTimeoutTasks = new ConcurrentHashMap<>();

    public AuthTimeoutManager() {
        instance = this;
    }

    public static AuthTimeoutManager getInstance() {
        return instance;
    }

    /**
     * Starts the kick timer for a player.
     *
     * @param player the player who must authenticate
     */
    public void startLoginTimeout(Player player) {
        UUID uuid = player.getUniqueId();
        cancelLoginTimeout(uuid);

        int timeoutSec = AuthConfig.getLoginTimeoutSeconds();
        long timeoutTicks = timeoutSec * 20L;

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                if (AuthPlayerState.getInstance().isAuthenticated(uuid)) return;

                String kickMsg = MessagesManager.getString("auth.admin.kick_timeout",
                        "<red>⏱ Login timeout!</red>\n<gray>You did not log in within %seconds% seconds.</gray>")
                        .replace("%seconds%", String.valueOf(timeoutSec));
                player.kickPlayer(MessageUtil.legacy(kickMsg));
            }
        };

        task.runTaskLater(Main.getInstance(), timeoutTicks);
        loginTimeoutTasks.put(uuid, task);
    }

    /**
     * Cancels the kick timer for a player.
     *
     * @param uuid the player's UUID
     */
    public void cancelLoginTimeout(UUID uuid) {
        BukkitRunnable task = loginTimeoutTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Clears all of the player's data.
     */
    public void removePlayer(UUID uuid) {
        cancelLoginTimeout(uuid);
    }
}
