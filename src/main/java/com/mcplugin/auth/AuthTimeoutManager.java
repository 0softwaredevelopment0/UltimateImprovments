package com.mcplugin.auth;

import com.mcplugin.Main;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Управление таймаутом на авторизацию.
 * Если игрок не залогинился / не зарегистрировался за N секунд — кикает.
 * <p>
 * Таймер запускается при входе игрока (handleJoin) и отменяется
 * при успешной авторизации или выходе с сервера.
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
     * Запускает таймер кика для игрока.
     *
     * @param player игрок, который должен авторизоваться
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

                player.kickPlayer(
                        "§6✦ MC-Plugin\n" +
                        "§7━━━━━━━━━━━━━━━━━━━━━\n\n" +
                        "§c⏱ Время на авторизацию истекло!\n" +
                        "§7Вы не вошли в аккаунт за §c" + timeoutSec + "§7 сек.\n\n" +
                        "§7━━━━━━━━━━━━━━━━━━━━━"
                );
            }
        };

        task.runTaskLater(Main.getInstance(), timeoutTicks);
        loginTimeoutTasks.put(uuid, task);
    }

    /**
     * Отменяет таймер кика для игрока.
     *
     * @param uuid UUID игрока
     */
    public void cancelLoginTimeout(UUID uuid) {
        BukkitRunnable task = loginTimeoutTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Очищает все данные игрока.
     */
    public void removePlayer(UUID uuid) {
        cancelLoginTimeout(uuid);
    }
}
