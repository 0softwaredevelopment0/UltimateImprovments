package com.mcplugin.auth;

import com.mcplugin.util.MessageUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ограничение частоты запросов к БД авторизации.
 * Защита от спама по кнопке подтверждения пароля.
 */
public class AuthRateLimiter {

    private static AuthRateLimiter instance;
    private final Map<UUID, Long> requestCooldowns = new ConcurrentHashMap<>();

    public AuthRateLimiter() {
        instance = this;
    }

    public static AuthRateLimiter getInstance() {
        return instance;
    }

    /**
     * Проверяет, не превысил ли игрок лимит запросов.
     * Если превысил — отправляет сообщение и возвращает false.
     *
     * @param player игрок
     * @return true если запрос разрешён, false если нужно подождать
     */
    public boolean checkCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastRequest = requestCooldowns.get(uuid);
        long cooldownMs = AuthConfig.getRequestCooldownMs();

        if (lastRequest != null && (now - lastRequest) < cooldownMs) {
            long remaining = ((cooldownMs - (now - lastRequest)) / 1000) + 1;
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Подождите </red><yellow>" + remaining + "</yellow> <red>сек. перед следующим запросом!</red>"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
            return false;
        }

        requestCooldowns.put(uuid, now);
        return true;
    }

    /**
     * Очищает кулдаун для игрока (при успешном входе/выходе).
     */
    public void removePlayer(UUID uuid) {
        requestCooldowns.remove(uuid);
    }
}
