package com.mcplugin.command;

import com.mcplugin.core.Main;
import com.mcplugin.config.MessagesManager;
import com.mcplugin.util.MessageUtil;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * AskCordsManager — отправляет запрос игроку на показ координат.
 * <p>
 * Команда: {@code /mp askcords <nick>}
 * <p>
 * Механика:
 * <ol>
 *   <li>Игрок A вводит {@code /mp askcords ИгрокБ}</li>
 *   <li>Игроку Б приходит clickable-сообщение с кнопками ✔ Принять / ❌ Отклонить</li>
 *   <li>Если принял — игрок A получает мир и координаты игрока Б</li>
 *   <li>Если отклонил — игрок A получает сообщение об отказе</li>
 *   <li>Кулдаун на команду: 10 секунд</li>
 * </ol>
 */
public class AskCordsManager {

    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final Map<UUID, UUID> pendingRequests = new HashMap<>(); // target -> sender

    private static final long COOLDOWN_MS = 10_000L; // 10 секунд

    private AskCordsManager() {}

    /**
     * Выполняет команду /mp askcords <nick> от имени игрока.
     */
    public static boolean execute(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("askcords.usage",
                            "<red>❌ Usage: </red><white>/mp askcords <nick></white>")));
            return true;
        }

        UUID senderUuid = sender.getUniqueId();

        // =========================
        // COOLDOWN CHECK
        // =========================
        long now = System.currentTimeMillis();
        Long lastUse = cooldowns.get(senderUuid);
        if (lastUse != null && (now - lastUse) < COOLDOWN_MS) {
            long remaining = ((COOLDOWN_MS - (now - lastUse)) / 1000) + 1;
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("askcords.cooldown",
                            "<red>❌ Please wait </red><yellow>{seconds}</yellow><red> seconds before using this again!</red>")
                            .replace("{seconds}", String.valueOf(remaining))));
            return true;
        }

        // =========================
        // FIND TARGET
        // =========================
        String targetName = args[1];
        @SuppressWarnings("deprecation")
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("askcords.player_not_found",
                            "<red>❌ Player </red><yellow>{player}</yellow><red> not found!</red>")
                            .replace("{player}", targetName)));
            return true;
        }

        // Нельзя отправить запрос самому себе
        if (sender.equals(target)) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("askcords.cannot_self",
                            "<red>❌ You cannot send a coordinates request to yourself!</red>")));
            return true;
        }

        // =========================
        // SEND REQUEST
        // =========================
        cooldowns.put(senderUuid, now);
        pendingRequests.put(target.getUniqueId(), senderUuid);

        // Сообщение отправителю
        sender.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askcords.request_sent",
                        "<green>✔</green> <white>Coordinates request sent to</white> <yellow>{player}</yellow><white>.</white>")
                        .replace("{player}", target.getName())));
        sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.5f);

        // Сообщение цели с кнопками
        sendRequestToTarget(sender, target);

        return true;
    }

    /**
     * Отправляет получателю clickable-сообщение с кнопками принятия/отказа.
     */
    private static void sendRequestToTarget(Player sender, Player target) {
        target.sendMessage("");
        target.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askcords.request_header",
                        "<gold>═══════════════════════════════════</gold>")));
        target.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askcords.request_title",
                        "<gold>  ✦ </gold><white>Coordinates Request</white>")));
        target.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askcords.request_header",
                        "<gold>═══════════════════════════════════</gold>")));
        target.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askcords.request_body",
                        "<gray>Player </gray><yellow>{player}</yellow><gray> is requesting your coordinates.</gray>")
                        .replace("{player}", sender.getName())));
        target.sendMessage("");

        // ✔ Accept button
        TextComponent acceptBtn = new TextComponent(
                MessagesManager.getString("askcords.accept_button", "     §a[§2✔ Accept§a]"));
        acceptBtn.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/mp askcords_accept " + sender.getName()
        ));
        acceptBtn.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(
                        MessagesManager.getString("askcords.accept_hover", "§aClick to accept and share your coordinates")
                ).create()
        ));

        // ❌ Decline button
        TextComponent declineBtn = new TextComponent(
                MessagesManager.getString("askcords.decline_button", " §c[§4❌ Decline§c]"));
        declineBtn.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/mp askcords_decline " + sender.getName()
        ));
        declineBtn.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(
                        MessagesManager.getString("askcords.decline_hover", "§cClick to decline the request")
                ).create()
        ));

        target.spigot().sendMessage(acceptBtn, declineBtn);
        target.sendMessage("");

        target.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askcords.request_footer",
                        "<gold>═══════════════════════════════════</gold>")));
        target.sendMessage("");

        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
    }

    /**
     * Обрабатывает подтверждение запроса от цели.
     * Вызывается через /mp askcords_accept <nick>
     */
    public static boolean accept(Player target, String senderName) {
        UUID targetUuid = target.getUniqueId();
        UUID senderUuid = pendingRequests.remove(targetUuid);

        if (senderUuid == null) {
            target.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("askcords.no_pending_request",
                            "<red>❌ You have no pending coordinates request!</red>")));
            return true;
        }

        Player sender = Bukkit.getPlayer(senderUuid);
        if (sender == null || !sender.isOnline()) {
            target.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("askcords.sender_offline",
                            "<red>❌ The player who requested the coordinates is no longer online!</red>")));
            return true;
        }

        // Получаем мир и координаты цели
        Location targetLoc = target.getLocation();
        String worldName = targetLoc.getWorld().getName();
        int x = targetLoc.getBlockX();
        int y = targetLoc.getBlockY();
        int z = targetLoc.getBlockZ();

        // Сообщение отправителю с координатами
        sender.sendMessage("");
        sender.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askcords.result_header",
                        "<gold>═══════════════════════════════════</gold>")));
        sender.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askcords.result_title",
                        "<gold>  ✦ </gold><green>Coordinates received!</green>")));
        sender.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askcords.result_header",
                        "<gold>═══════════════════════════════════</gold>")));
        sender.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askcords.result_player",
                        "<gray>Player: </gray><yellow>{player}</yellow>")
                        .replace("{player}", target.getName())));
        sender.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askcords.result_world",
                        "<gray>World: </gray><white>{world}</white>")
                        .replace("{world}", worldName)));
        sender.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askcords.result_coords",
                        "<gray>Coordinates: </gray><white>{x} / {y} / {z}</white>")
                        .replace("{x}", String.valueOf(x))
                        .replace("{y}", String.valueOf(y))
                        .replace("{z}", String.valueOf(z))));
        sender.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askcords.result_header",
                        "<gold>═══════════════════════════════════</gold>")));
        sender.sendMessage("");

        sender.playSound(sender.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

        // Сообщение цели
        target.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askcords.accepted_notify",
                        "<green>✔</green> <white>You shared your coordinates with</white> <yellow>{player}</yellow><white>.</white>")
                        .replace("{player}", sender.getName())));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.2f);

        return true;
    }

    /**
     * Обрабатывает отказ запроса от цели.
     * Вызывается через /mp askcords_decline <nick>
     */
    public static boolean decline(Player target, String senderName) {
        UUID targetUuid = target.getUniqueId();
        UUID senderUuid = pendingRequests.remove(targetUuid);

        if (senderUuid == null) {
            target.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("askcords.no_pending_request",
                            "<red>❌ You have no pending coordinates request!</red>")));
            return true;
        }

        Player sender = Bukkit.getPlayer(senderUuid);
        if (sender != null && sender.isOnline()) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("askcords.declined_notify_sender",
                            "<red>❌ Player </red><yellow>{player}</yellow><red> declined your coordinates request.</red>")
                            .replace("{player}", target.getName())));
            sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);
        }

        target.sendMessage(MessageUtil.parse(
                MessagesManager.getString("askcords.declined_notify_target",
                        "<yellow>✦</yellow> <white>You declined the coordinates request from</white> <yellow>{player}</yellow><white>.</white>")
                        .replace("{player}", sender != null ? sender.getName() : senderName)));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.8f);

        return true;
    }

    /**
     * Очищает данные игрока (при выходе).
     */
    public static void cleanup(UUID uuid) {
        cooldowns.remove(uuid);
        pendingRequests.remove(uuid);
        // Также удаляем из waiting-запросов, где этот игрок отправитель
        pendingRequests.values().removeIf(v -> v.equals(uuid));
    }
}
