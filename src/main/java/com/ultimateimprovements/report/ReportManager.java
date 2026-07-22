package com.ultimateimprovements.report;

import com.ultimateimprovements.config.MessagesManager;
import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.database.DatabaseManager;
import com.ultimateimprovements.util.MessageUtil;
import com.ultimateimprovements.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Система репортов на игроков.
 * <p>
 * Позволяет подавать жалобы, админам — управлять списком для модерации,
 * модерировать с вынесением вердикта.
 */
public class ReportManager implements Listener {

    private static ReportManager instance;

    // Сессии модерации: модератор → текущая сессия
    private static final Map<UUID, ModerationSession> modSessions = new ConcurrentHashMap<>();

    // Подтверждение удаления из mod_reports: админ → report_id
    private static final Map<UUID, Integer> removeConfirmations = new ConcurrentHashMap<>();

    public static class ModerationSession {
        public int reportId;
        public String modName;
        public String conclusion = "";
        public Step step = Step.CONCLUSION;

        public enum Step { CONCLUSION, VERDICT }
    }

    public static class ReportData {
        public int id;
        public String reporterUuid;
        public String reportedUuid;
        public String reason;
        public String status; // pending, confirmed, rejected, closed
        public long createdAt;
        public long expiresAt;
        public String reporterName = "";
        public String reportedName = "";
        public String verdictOption = "";
        public String verdict = "";
        public String moderatorName = "";
    }

    public static void init() {
        instance = new ReportManager();
        Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());
        // Запускаем таймер очистки просроченных репортов (каждые 5 минут)
        Bukkit.getScheduler().runTaskTimerAsynchronously(Main.getInstance(), () -> {
            expireOldReports();
        }, 200L, 6000L); // 10 сек задержка, 5 мин интервал
    }

    public static ReportManager getInstance() { return instance; }

    // ════════════════════════════════════════
    // TRACK PLAYER VISITS
    // ════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            try (Connection con = DatabaseManager.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "INSERT INTO player_visits (uuid, name, first_join, last_join) VALUES (?, ?, strftime('%s','now'), strftime('%s','now')) " +
                         "ON CONFLICT(uuid) DO UPDATE SET name = ?, last_join = strftime('%s','now')")) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getName());
                ps.setString(3, player.getName());
                ps.executeUpdate();
            } catch (Exception e) {
                ConsoleLogger.warn("[Reports] Failed to track player visit: " + e.getMessage());
            }
        });
    }

    /**
     * Проверяет, заходил ли игрок на сервер хоть раз.
     */
    public static boolean hasEverJoined(String playerName) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT uuid FROM player_visits WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Получает UUID игрока по нику из базы визитов.
     */
    public static String getUuidByName(String playerName) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT uuid FROM player_visits WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("uuid");
        } catch (Exception ignored) {}
        return null;
    }

    // ════════════════════════════════════════
    // CREATE REPORT
    // ════════════════════════════════════════

    /**
     * Создаёт репорт на игрока.
     * @return null если успешно, иначе сообщение ошибки
     */
    public static String createReport(Player reporter, String reportedName, String reason) {
        // Проверяем, есть ли у репортёра уже активный репорт
        String reporterUuid = reporter.getUniqueId().toString();
        if (hasPendingReport(reporterUuid)) {
            return MessagesManager.getString("report.errors.already_pending",
                    "<red>❌ У вас уже есть активный репорт! Дождитесь его модерации.</red>");
        }

        // Получаем UUID репортируемого
        String reportedUuid = getUuidByName(reportedName);
        if (reportedUuid == null) {
            return MessagesManager.getString("report.errors.never_joined",
                    "<red>❌ Игрок <yellow>%player%</yellow> <red>ни разу не заходил на сервер!</red>")
                    .replace("%player%", reportedName);
        }

        // Нельзя зарепортить себя
        if (reporterUuid.equals(reportedUuid)) {
            return MessagesManager.getString("report.errors.self_report",
                    "<red>❌ Вы не можете подать репорт на самого себя!</red>");
        }

        // Получаем время истечения из конфига
        long expireSeconds = parseExpireTime(
                Main.getInstance().getConfig().getString("report.expire_time", "3d"));
        long now = System.currentTimeMillis() / 1000;
        long expiresAt = now + expireSeconds;

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO reports (reporter_uuid, reported_uuid, reason, status, created_at, expires_at) " +
                     "VALUES (?, ?, ?, 'pending', ?, ?)")) {
            ps.setString(1, reporterUuid);
            ps.setString(2, reportedUuid);
            ps.setString(3, reason);
            ps.setLong(4, now);
            ps.setLong(5, expiresAt);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[Reports] Failed to create report: " + e.getMessage());
            return MessagesManager.getString("report.errors.db_error",
                    "<red>❌ Ошибка базы данных при создании репорта!</red>");
        }

        // Уведомляем репортируемого (если онлайн)
        Player reportedPlayer = Bukkit.getPlayerExact(reportedName);
        if (reportedPlayer != null && reportedPlayer.isOnline()) {
            String notifiedMsg = MessagesManager.getString("report.reported_notification",
                    "<yellow>⚠</yellow> <white>На вас была подана жалоба!</white>");
            reportedPlayer.sendMessage(MessageUtil.parse(notifiedMsg));
        }

        return null; // успех
    }

    /**
     * Проверяет, есть ли у игрока репорт в статусе pending.
     */
    public static boolean hasPendingReport(String uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id FROM reports WHERE reporter_uuid = ? AND status = 'pending'")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Получает статус активного репорта игрока.
     */
    public static ReportData getActiveReport(String uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT r.*, " +
                     "COALESCE((SELECT name FROM player_visits WHERE uuid = r.reporter_uuid), '?') as reporter_name, " +
                     "COALESCE((SELECT name FROM player_visits WHERE uuid = r.reported_uuid), '?') as reported_name " +
                     "FROM reports r WHERE r.reporter_uuid = ? AND r.status = 'pending'")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ReportData data = new ReportData();
                data.id = rs.getInt("id");
                data.reporterUuid = rs.getString("reporter_uuid");
                data.reportedUuid = rs.getString("reported_uuid");
                data.reason = rs.getString("reason");
                data.status = rs.getString("status");
                data.createdAt = rs.getLong("created_at");
                data.expiresAt = rs.getLong("expires_at");
                data.reporterName = rs.getString("reporter_name");
                data.reportedName = rs.getString("reported_name");
                return data;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Получает репорт по ID.
     */
    public static ReportData getReportById(int id) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT r.*, " +
                     "COALESCE((SELECT name FROM player_visits WHERE uuid = r.reporter_uuid), '?') as reporter_name, " +
                     "COALESCE((SELECT name FROM player_visits WHERE uuid = r.reported_uuid), '?') as reported_name, " +
                     "COALESCE((SELECT name FROM player_visits WHERE uuid = r.moderator_uuid), '?') as moderator_name " +
                     "FROM reports r WHERE r.id = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ReportData data = new ReportData();
                data.id = rs.getInt("id");
                data.reporterUuid = rs.getString("reporter_uuid");
                data.reportedUuid = rs.getString("reported_uuid");
                data.reason = rs.getString("reason");
                data.status = rs.getString("status");
                data.createdAt = rs.getLong("created_at");
                data.expiresAt = rs.getLong("expires_at");
                data.verdictOption = rs.getString("verdict_option");
                data.verdict = rs.getString("verdict");
                data.reporterName = rs.getString("reporter_name");
                data.reportedName = rs.getString("reported_name");
                data.moderatorName = rs.getString("moderator_name");
                return data;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Получает все репорты.
     */
    public static List<ReportData> getAllReports() {
        List<ReportData> list = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT r.*, " +
                     "COALESCE((SELECT name FROM player_visits WHERE uuid = r.reporter_uuid), '?') as reporter_name, " +
                     "COALESCE((SELECT name FROM player_visits WHERE uuid = r.reported_uuid), '?') as reported_name, " +
                     "COALESCE((SELECT name FROM player_visits WHERE uuid = r.moderator_uuid), '?') as moderator_name " +
                     "FROM reports r ORDER BY r.created_at DESC LIMIT 100")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ReportData data = new ReportData();
                data.id = rs.getInt("id");
                data.reporterUuid = rs.getString("reporter_uuid");
                data.reportedUuid = rs.getString("reported_uuid");
                data.reason = rs.getString("reason");
                data.status = rs.getString("status");
                data.createdAt = rs.getLong("created_at");
                data.expiresAt = rs.getLong("expires_at");
                data.verdictOption = rs.getString("verdict_option");
                data.verdict = rs.getString("verdict");
                data.reporterName = rs.getString("reporter_name");
                data.reportedName = rs.getString("reported_name");
                data.moderatorName = rs.getString("moderator_name");
                list.add(data);
            }
        } catch (Exception ignored) {}
        return list;
    }

    // ════════════════════════════════════════
    // MODERATION — ADD/REMOVE FROM MOD QUEUE
    // ════════════════════════════════════════

    /**
     * Добавляет репорт в список модерации с именем.
     */
    public static boolean addToModQueue(int reportId, String name) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO mod_reports (report_id, name) VALUES (?, ?)")) {
            ps.setInt(1, reportId);
            ps.setString(2, name);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Удаляет репорт из списка модерации.
     */
    public static boolean removeFromModQueue(int reportId) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM mod_reports WHERE report_id = ?")) {
            ps.setInt(1, reportId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Проверяет, существует ли mod_report с таким ID репорта.
     */
    public static boolean isInModQueue(int reportId) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id FROM mod_reports WHERE report_id = ?")) {
            ps.setInt(1, reportId);
            return ps.executeQuery().next();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Проверяет, существует ли mod_report с таким именем.
     */
    public static boolean isModNameExists(String name) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id FROM mod_reports WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, name);
            return ps.executeQuery().next();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Получает имя mod_reports по ID репорта.
     */
    public static String getModNameByReportId(int reportId) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT name FROM mod_reports WHERE report_id = ?")) {
            ps.setInt(1, reportId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("name") : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Получает список всех репортов в модерации (с данными репорта).
     */
    public static List<String> getModQueueNames() {
        List<String> names = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT mr.name, r.id, r.status, " +
                     "COALESCE((SELECT name FROM player_visits WHERE uuid = r.reported_uuid), '?') as reported_name " +
                     "FROM mod_reports mr JOIN reports r ON mr.report_id = r.id " +
                     "ORDER BY mr.name")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String name = rs.getString("name");
                int id = rs.getInt("id");
                String status = rs.getString("status");
                String reportedName = rs.getString("reported_name");
                names.add(name + " — #" + id + " [" + status + "] " + reportedName);
            }
        } catch (Exception ignored) {}
        return names;
    }

    /**
     * Получает только имена из очереди модерации (для таб-комплита /mp modreport).
     */
    public static List<String> getModQueueNameList() {
        List<String> names = new ArrayList<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT name FROM mod_reports ORDER BY name")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        } catch (Exception ignored) {}
        return names;
    }

    /**
     * Получает ID репорта по имени mod_reports.
     */
    public static int getReportIdByModName(String name) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT report_id FROM mod_reports WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("report_id");
        } catch (Exception ignored) {}
        return -1;
    }

    /**
     * Удаляет mod_reports по имени.
     */
    public static boolean removeFromModQueueByName(String name) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM mod_reports WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, name);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ════════════════════════════════════════
    // MODERATION SESSION (chat input mode)
    // ════════════════════════════════════════

    /**
     * Проверяет, находится ли игрок в активной сессии модерации.
     * Используется в {@link com.ultimateimprovements.chat.ChatManager#onPlayerChat}
     * чтобы не отправлять сообщения модератора в чат.
     */
    public static boolean isInModeration(Player player) {
        return modSessions.containsKey(player.getUniqueId());
    }

    /**
     * Начинает сессию модерации для игрока.
     */
    public static void startModeration(Player moderator, int reportId, String modName) {
        ModerationSession session = new ModerationSession();
        session.reportId = reportId;
        session.modName = modName;
        session.step = ModerationSession.Step.CONCLUSION;
        session.conclusion = "";
        modSessions.put(moderator.getUniqueId(), session);

        String msg = MessagesManager.getString("report.moderation.enter_conclusion",
                "<green>✔</green> <white>Напишите заключение по репорту </white><yellow>%name%</yellow>");
        moderator.sendMessage(MessageUtil.parse(msg.replace("%name%", modName)));
        moderator.sendMessage(MessageUtil.parse(
                MessagesManager.getString("report.moderation.type_or_cancel",
                        "<gray>Напишите текст или </gray><red>cancel</red><gray> для отмены.</gray>")));
    }

    /**
     * Обработка сообщений от игроков в режиме модерации.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ModerationSession session = modSessions.get(uuid);
        if (session == null) return;

        event.setCancelled(true);
        String text = event.getMessage().trim();

        if (text.equalsIgnoreCase("cancel")) {
            modSessions.remove(uuid);
            removeConfirmations.remove(uuid);
            player.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("report.moderation.cancelled",
                            "<yellow>✦</yellow> <gray>Модерация отменена.</gray>")));
            return;
        }

        if (session.step == ModerationSession.Step.CONCLUSION) {
            // Сохраняем заключение, переходим к вердикту
            session.conclusion = text;
            session.step = ModerationSession.Step.VERDICT;

            player.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("report.moderation.verdict_prompt",
                            "<green>✔</green> <white>Заключение сохранено. Выберите вердикт:</white>")));
            player.sendMessage(MessageUtil.parse("<white>1.</white> <green>Подтверждён</green>"));
            player.sendMessage(MessageUtil.parse("<white>2.</white> <red>Отклонён</red>"));
            player.sendMessage(MessageUtil.parse("<white>3.</white> <gray>Закрыт</gray>"));
            player.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("report.moderation.type_or_cancel",
                            "<gray>Напишите номер или </gray><red>cancel</red><gray> для отмены.</gray>")));
        } else if (session.step == ModerationSession.Step.VERDICT) {
            // Обрабатываем вердикт
            String verdictOption;
            String verdictLabel;
            switch (text) {
                case "1" -> {
                    verdictOption = "confirmed";
                    verdictLabel = MessagesManager.getString("report.verdict.confirmed", "Подтверждён");
                }
                case "2" -> {
                    verdictOption = "rejected";
                    verdictLabel = MessagesManager.getString("report.verdict.rejected", "Отклонён");
                }
                case "3" -> {
                    verdictOption = "closed";
                    verdictLabel = MessagesManager.getString("report.verdict.closed", "Закрыт");
                }
                default -> {
                    player.sendMessage(MessageUtil.parse(
                            "<red>❌ Неверный номер! Используйте 1, 2 или 3.</red>"));
                    return;
                }
            }

            // Сохраняем вердикт в БД
            final String finalVerdictOption = verdictOption;
            final String finalVerdictLabel = verdictLabel;
            Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
                try (Connection con = DatabaseManager.getConnection();
                     PreparedStatement ps = con.prepareStatement(
                             "UPDATE reports SET status = ?, verdict = ?, verdict_option = ?, moderator_uuid = ?, moderated_at = strftime('%s','now') " +
                             "WHERE id = ?")) {
                    ps.setString(1, finalVerdictOption);
                    ps.setString(2, session.conclusion);
                    ps.setString(3, finalVerdictLabel);
                    ps.setString(4, uuid.toString());
                    ps.setInt(5, session.reportId);
                    ps.executeUpdate();
                } catch (Exception e) {
                    ConsoleLogger.warn("[Reports] Failed to save verdict: " + e.getMessage());
                }
            });

            // Уведомляем репортёра (если онлайн)
            try (Connection con = DatabaseManager.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT reporter_uuid FROM reports WHERE id = ?")) {
                ps.setInt(1, session.reportId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String reporterUuid = rs.getString("reporter_uuid");
                    Player reporterPlayer = Bukkit.getPlayer(UUID.fromString(reporterUuid));
                    if (reporterPlayer != null && reporterPlayer.isOnline()) {
                        String notifiedMsg = MessagesManager.getString("report.reporter_notified",
                                "<green>✔</green> <white>Ваш репорт рассмотрен! Вердикт: </white><yellow>%verdict%</yellow>")
                                .replace("%verdict%", finalVerdictLabel);
                        reporterPlayer.sendMessage(MessageUtil.parse(notifiedMsg));
                    }
                }
            } catch (Exception ignored) {}

            modSessions.remove(uuid);

            player.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("report.moderation.verdict_saved",
                            "<green>✔</green> <white>Вердикт </white><yellow>%verdict%</yellow> <white>сохранён по репорту </white><yellow>%name%</yellow>")
                            .replace("%verdict%", finalVerdictLabel)
                            .replace("%name%", session.modName)));
        }
    }

    // ════════════════════════════════════════
    // REMOVE CONFIRMATION
    // ════════════════════════════════════════

    public static void requestRemoveConfirmation(Player admin, int reportId, String modName) {
        removeConfirmations.put(admin.getUniqueId(), reportId);
        admin.sendMessage(MessageUtil.parse(
                MessagesManager.getString("report.admin.remove_confirm",
                        "<yellow>⚠</yellow> <white>Удалить </white><yellow>%name%</yellow><white>? Напишите </white><yellow>/mp reports remove confirm</yellow><white> для подтверждения.</white>")
                        .replace("%name%", modName)));
        // Задержка истечения подтверждения (30 сек)
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            removeConfirmations.remove(admin.getUniqueId());
        }, 600L);
    }

    public static boolean hasRemoveConfirmation(Player admin, int reportId) {
        Integer stored = removeConfirmations.get(admin.getUniqueId());
        return stored != null && stored == reportId;
    }

    public static int getPendingConfirmationId(Player admin) {
        return removeConfirmations.getOrDefault(admin.getUniqueId(), -1);
    }

    public static void clearRemoveConfirmation(Player admin) {
        removeConfirmations.remove(admin.getUniqueId());
    }

    // ════════════════════════════════════════
    // EXPIRE OLD REPORTS
    // ════════════════════════════════════════

    private static void expireOldReports() {
        long now = System.currentTimeMillis() / 1000;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE reports SET status = 'expired' WHERE status = 'pending' AND expires_at < ?")) {
            ps.setLong(1, now);
            int expired = ps.executeUpdate();
            if (expired > 0) {
                ConsoleLogger.info("[Reports] Expired " + expired + " pending report(s).");
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Reports] Failed to expire reports: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════
    // UTILITY
    // ════════════════════════════════════════

    /**
     * Парсит время истечения (3d, 12h, 30m, 60s) в секунды.
     */
    public static long parseExpireTime(String input) {
        if (input == null || input.isEmpty()) return 259200L; // 3d по умолчанию
        input = input.trim().toLowerCase();
        try {
            char unit = input.charAt(input.length() - 1);
            long value = Long.parseLong(input.substring(0, input.length() - 1));
            return switch (unit) {
                case 'd' -> value * 86400L;
                case 'h' -> value * 3600L;
                case 'm' -> value * 60L;
                case 's' -> value;
                default -> 259200L;
            };
        } catch (Exception e) {
            return 259200L;
        }
    }

    public static String formatTimeLeft(long expiresAt) {
        long now = System.currentTimeMillis() / 1000;
        long left = expiresAt - now;
        if (left <= 0) return "просрочен";
        if (left < 60) return left + "с";
        if (left < 3600) return (left / 60) + "м";
        if (left < 86400) return (left / 3600) + "ч";
        return (left / 86400) + "д";
    }
}
