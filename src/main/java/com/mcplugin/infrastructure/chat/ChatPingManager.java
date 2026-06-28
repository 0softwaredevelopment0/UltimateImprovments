package com.mcplugin.infrastructure.chat;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.util.MessageUtil;
import com.mcplugin.infrastructure.util.SoundUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 🔔 ChatPingManager — система пингов в чате.
 * <p>
 * Позволяет пинговать игроков через специальные метки в сообщении:
 * <ul>
 *   <li>{@code @everyone} — все игроки (кроме отправителя)</li>
 *   <li>{@code @<ник>} — конкретный игрок (по нику)</li>
 *   <li>{@code @non-op} — все игроки без OP</li>
 *   <li>{@code @is-admin} — все с правом mcplugin.admin или mcplugin.*</li>
 *   <li>{@code @is-non-admin} — все без права mcplugin.admin/mcplugin.*</li>
 * </ul>
 * <p>
 * Пинги обрабатываются в сообщении после разрешения плейсхолдеров:
 * метка заменяется на ник(и) игрока(ов) с подчёркиванием и цветом,
 * каждому пропингуемому игроку проигрывается звук.
 */
public class ChatPingManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static boolean enabled = true;
    private static String pingStyle;
    private static String pingSoundName;
    private static float pingSoundVolume;
    private static float pingSoundPitch;

    // Паттерн для поиска @меток в сообщении
    // Ищет @everyone, @non-op, @is-admin, @is-non-admin, @<ник>
    private static final Pattern PING_PATTERN = Pattern.compile(
            "@(everyone|non-op|is-admin|is-non-admin|[\\w]+)"
    );

    private ChatPingManager() {}

    /**
     * Загружает настройки из конфига.
     */
    public static void reloadConfig() {
        FileConfiguration cfg = Main.getInstance().getConfig();
        enabled = cfg.getBoolean("chat_ping.enabled", true);
        pingStyle = cfg.getString("chat_ping.ping_style",
                "<underlined><color:#FFAA00>@%s</color></underlined>");
        pingSoundName = cfg.getString("chat_ping.sound_name", "ENTITY_EXPERIENCE_ORB_PICKUP");
        pingSoundVolume = (float) cfg.getDouble("chat_ping.sound_volume", 0.5);
        pingSoundPitch = (float) cfg.getDouble("chat_ping.sound_pitch", 1.5);
    }

    /**
     * Обрабатывает пинги в сообщении.
     * <p>
     * Вызывается из {@link ChatManager#onPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent)}
     * ПОСЛЕ разрешения плейсхолдеров, но ДО отправки сообщения.
     *
     * @param message  текущее текстовое сообщение (с уже разрешёнными плейсхолдерами)
     * @param sender   игрок, отправивший сообщение
     * @return результат обработки (модифицированное сообщение + список пропингованных)
     */
    public static PingResult processPings(String message, Player sender) {
        if (!enabled || message == null || message.isEmpty()) {
            return new PingResult(message, List.of());
        }

        List<Player> pingedPlayers = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        Matcher m = PING_PATTERN.matcher(message);
        int lastEnd = 0;

        while (m.find()) {
            sb.append(message, lastEnd, m.start());

            String keyword = m.group(1);
            List<Player> targets = resolveTargets(keyword, sender);

            if (targets.isEmpty()) {
                // Ник не найден или метка не дала результатов — оставляем как есть
                sb.append(m.group());
            } else {
                // Строим стилизованный текст для каждого цели
                List<String> styledNames = new ArrayList<>();
                for (Player target : targets) {
                    // Заменяем %s на ник игрока в pingStyle
                    String styled = pingStyle.replace("%s", target.getName());
                    styledNames.add(styled);
                    if (!target.equals(sender)) {
                        pingedPlayers.add(target);
                    }
                }
                sb.append(String.join("<gray>, </gray>", styledNames));
            }

            lastEnd = m.end();
        }

        sb.append(message.substring(lastEnd));

        return new PingResult(sb.toString(), pingedPlayers);
    }

    /**
     * Проигрывает звук пинга и отправляет уведомление указанным игрокам.
     */
    public static void notifyPingedPlayers(List<Player> players, Player sender) {
        if (players == null || players.isEmpty()) return;
        Sound sound = SoundUtil.getSound(pingSoundName, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        String notificationMsg = MessagesManager.getString("chat_ping.notification", "");
        Component notification = notificationMsg.isEmpty() ? null
                : MessageUtil.parse(notificationMsg.replace("{sender}", sender.getName()));
        for (Player p : players) {
            SoundUtil.playSound(p, sound, pingSoundVolume, pingSoundPitch);
            if (notification != null) {
                p.sendMessage(notification);
            }
        }
    }

    /**
     * Разрешает метку пинга в список игроков.
     */
    private static List<Player> resolveTargets(String keyword, Player sender) {
        // Специальные метки
        switch (keyword.toLowerCase()) {
            case "everyone" -> {
                List<Player> all = new ArrayList<>(Bukkit.getOnlinePlayers());
                all.remove(sender);
                return all;
            }
            case "non-op" -> {
                List<Player> result = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(sender) && !p.isOp()) {
                        result.add(p);
                    }
                }
                return result;
            }
            case "is-admin" -> {
                List<Player> result = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(sender) && (p.hasPermission("mcplugin.admin") || p.hasPermission("mcplugin.*"))) {
                        result.add(p);
                    }
                }
                return result;
            }
            case "is-non-admin" -> {
                List<Player> result = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(sender) && !p.hasPermission("mcplugin.admin") && !p.hasPermission("mcplugin.*")) {
                        result.add(p);
                    }
                }
                return result;
            }
            default -> {
                // Конкретный ник
                Player target = Bukkit.getPlayerExact(keyword);
                if (target != null && target.isOnline()) {
                    return List.of(target);
                }
                return List.of();
            }
        }
    }

    /**
     * Результат обработки пингов.
     */
    public record PingResult(
            String formattedMessage,
            List<Player> pingedPlayers
    ) {}
}
