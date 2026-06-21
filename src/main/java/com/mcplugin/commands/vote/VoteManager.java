package com.mcplugin.commands.vote;

import com.mcplugin.Main;
import com.mcplugin.database.DatabaseManager;
import com.mcplugin.util.MessageUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Система голосования.
 * <p>
 * Команды:
 * <ul>
 *   <li>{@code /mp vote create <name> <title> <desc> -answer_1:t,d -answer_2:t,d ... -time:<N><s|m|h|d>}</li>
 *   <li>{@code /mp vote <name> [<index>|<title>]} — проголосовать или посмотреть результаты</li>
 *   <li>{@code /mp vote delete <name>} — удалить с подтверждением</li>
 *   <li>{@code /mp vote change <name> <params>} — изменить (пересоздать с теми же params)</li>
 * </ul>
 */
public class VoteManager {

    // =========================
    // IN-MEMORY CACHE
    // =========================
    private static final Map<String, Vote> votes = new ConcurrentHashMap<>();
    /** Игроки, подтвердившие удаление голосования (vote_name -> sender_uuid -> true) */
    private static final Map<String, Map<UUID, Boolean>> pendingDeletes = new ConcurrentHashMap<>();
    /** Активные таймеры закрытия голосований */
    private static final Map<String, BukkitTask> closeTimers = new ConcurrentHashMap<>();

    // =========================
    // INIT — загрузить из БД при старте
    // =========================
    public static void init() {
        votes.clear();
        pendingDeletes.clear();
        closeTimers.clear();
        loadFromDatabase();
        Main.getInstance().getLogger().info("[VOTE] Loaded " + votes.size() + " votes from database.");
    }

    // =========================
    // LOAD FROM DB
    // =========================
    private static void loadFromDatabase() {
        try (Connection con = DatabaseManager.getConnection()) {
            // Загружаем голосования
            try (PreparedStatement st = con.prepareStatement(
                    "SELECT name, title, question, creator_uuid, created_at, expires_at, ended FROM votes")) {
                ResultSet rs = st.executeQuery();
                while (rs.next()) {
                    Vote vote = new Vote();
                    vote.name = rs.getString("name");
                    vote.title = rs.getString("title");
                    vote.question = rs.getString("question");
                    vote.creator = UUID.fromString(rs.getString("creator_uuid"));
                    vote.createdAt = rs.getLong("created_at");
                    vote.expiresAt = rs.getLong("expires_at");
                    vote.ended = rs.getInt("ended") == 1;
                    vote.answers = new ArrayList<>();
                    vote.votes = new ConcurrentHashMap<>();
                    votes.put(vote.name, vote);
                }
            }

            // Загружаем ответы
            try (PreparedStatement st = con.prepareStatement(
                    "SELECT vote_name, answer_index, title, description FROM vote_answers ORDER BY vote_name, answer_index")) {
                ResultSet rs = st.executeQuery();
                while (rs.next()) {
                    String voteName = rs.getString("vote_name");
                    Vote vote = votes.get(voteName);
                    if (vote == null) continue;
                    int idx = rs.getInt("answer_index");
                    Answer a = new Answer();
                    a.title = rs.getString("title");
                    a.description = rs.getString("description");
                    while (vote.answers.size() <= idx) vote.answers.add(null);
                    vote.answers.set(idx, a);
                }
            }

            // Загружаем голоса игроков
            try (PreparedStatement st = con.prepareStatement(
                    "SELECT vote_name, player_uuid, answer_index FROM vote_records")) {
                ResultSet rs = st.executeQuery();
                while (rs.next()) {
                    String voteName = rs.getString("vote_name");
                    Vote vote = votes.get(voteName);
                    if (vote == null) continue;
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    int answerIndex = rs.getInt("answer_index");
                    vote.votes.put(playerUuid, answerIndex);
                }
            }

            // Восстанавливаем таймеры для неистёкших голосований
            long now = System.currentTimeMillis();
            for (Vote vote : votes.values()) {
                if (vote.expiresAt > now && !vote.ended) {
                    scheduleClose(vote.name, vote.expiresAt - now);
                } else if (vote.expiresAt <= now && !vote.ended) {
                    closeVote(vote.name);
                }
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[VOTE] Failed to load votes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================
    // PARSE VOTE CREATE ARGS
    // =========================
    public static boolean parseCreate(Player creator, String[] args, int startIndex) {
        if (args.length < startIndex + 3) {
            creator.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage:</red> <white>/mp vote create <name> <title> <description> -answer_<N>:<title,desc> ... -time:<N><s|m|h|d></white>"));
            return true;
        }

        String name = args[startIndex];
        String title = args[startIndex + 1];
        String question = args[startIndex + 2];

        // валидация имени
        if (!name.matches("[a-zA-Z0-9_-]{1,32}")) {
            creator.sendMessage(MessageUtil.parse(
                    "<red>❌ Название голосования должно содержать только буквы, цифры, дефис и подчёркивание (1-32 символа).</red>"));
            return true;
        }

        if (votes.containsKey(name.toLowerCase())) {
            creator.sendMessage(MessageUtil.parse(
                    "<red>❌ Голосование с таким названием уже существует!</red>"));
            return true;
        }

        List<Answer> answers = new ArrayList<>();
        long duration = 3600000; // 1 hour default

        for (int i = startIndex + 3; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-answer_")) {
                // Format: -answer_N:title,description
                String withoutPrefix = arg.substring("-answer_".length());
                int colonIdx = withoutPrefix.indexOf(':');
                if (colonIdx < 0) {
                    creator.sendMessage(MessageUtil.parse(
                            "<red>❌ Неверный формат ответа: </red><yellow>" + arg + "</yellow><red>. Ожидается: -answer_N:title,description</red>"));
                    return true;
                }
                // число N
                String numStr = withoutPrefix.substring(0, colonIdx);
                int answerNum;
                try {
                    answerNum = Integer.parseInt(numStr);
                } catch (NumberFormatException e) {
                    creator.sendMessage(MessageUtil.parse(
                            "<red>❌ Неверный номер ответа: </red><yellow>" + numStr + "</yellow>"));
                    return true;
                }
                String rest = withoutPrefix.substring(colonIdx + 1);
                int commaIdx = rest.indexOf(',');
                String answerTitle;
                String answerDesc;
                if (commaIdx >= 0) {
                    answerTitle = rest.substring(0, commaIdx).trim();
                    answerDesc = rest.substring(commaIdx + 1).trim();
                } else {
                    answerTitle = rest.trim();
                    answerDesc = "";
                }
                if (answerTitle.isEmpty()) {
                    creator.sendMessage(MessageUtil.parse(
                            "<red>❌ Заголовок ответа не может быть пустым!</red>"));
                    return true;
                }
                Answer a = new Answer();
                a.title = answerTitle;
                a.description = answerDesc;
                // Insert at correct position
                while (answers.size() <= answerNum) answers.add(null);
                answers.set(answerNum, a);
            } else if (arg.startsWith("-time:")) {
                String timeStr = arg.substring("-time:".length()).trim();
                duration = parseDuration(timeStr);
                if (duration < 0) {
                    creator.sendMessage(MessageUtil.parse(
                            "<red>❌ Неверный формат времени: </red><yellow>" + timeStr +
                            "</yellow><red>. Используйте например: 30s, 5m, 2h, 1d</red>"));
                    return true;
                }
            }
        }

        // Remove nulls (gaps in answers)
        answers.removeAll(Collections.singleton(null));
        if (answers.size() < 2) {
            creator.sendMessage(MessageUtil.parse(
                    "<red>❌ Нужно минимум 2 варианта ответа!</red>"));
            return true;
        }

        createVote(creator, name, title, question, answers, duration);
        return true;
    }

    // =========================
    // CREATE VOTE
    // =========================
    public static void createVote(Player creator, String name, String title, String question,
                                   List<Answer> answers, long durationMillis) {
        Vote vote = new Vote();
        vote.name = name;
        vote.title = title;
        vote.question = question;
        vote.answers = new ArrayList<>(answers);
        vote.creator = creator.getUniqueId();
        vote.createdAt = System.currentTimeMillis();
        vote.expiresAt = vote.createdAt + durationMillis;
        vote.votes = new ConcurrentHashMap<>();

        votes.put(name.toLowerCase(), vote);
        saveToDatabase(vote);
        scheduleClose(name.toLowerCase(), durationMillis);
        broadcastVote(vote);

        creator.sendMessage(MessageUtil.parse(
                "<green>✅</green> <white>Голосование </white><yellow>" + name + "</yellow><white> создано! Длительность: </white><yellow>" + formatDuration(durationMillis) + "</yellow>"));
    }

    // =========================
    // VOTE
    // =========================
    public static boolean vote(Player player, String voteName, String answerStr) {
        Vote vote = votes.get(voteName.toLowerCase());
        if (vote == null) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Голосование </red><yellow>" + voteName + "</yellow><red> не найдено!</red>"));
            return true;
        }
        if (vote.ended) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Голосование </red><yellow>" + vote.name + "</yellow><red> уже завершено!</red>"));
            return true;
        }
        if (System.currentTimeMillis() >= vote.expiresAt) {
            closeVote(vote.name);
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Голосование </red><yellow>" + vote.name + "</yellow><red> истекло!</red>"));
            return true;
        }

        int answerIndex = -1;

        // Try parsing as number
        try {
            int idx = Integer.parseInt(answerStr);
            if (idx >= 0 && idx < vote.answers.size()) {
                answerIndex = idx;
            }
        } catch (NumberFormatException ignored) {}

        // Try matching by title (case-insensitive)
        if (answerIndex < 0) {
            for (int i = 0; i < vote.answers.size(); i++) {
                if (vote.answers.get(i).title.equalsIgnoreCase(answerStr)) {
                    answerIndex = i;
                    break;
                }
            }
        }

        if (answerIndex < 0) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Неверный вариант ответа! Доступные варианты:</red>"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < vote.answers.size(); i++) {
                if (i > 0) sb.append("§7, ");
                sb.append("§e").append(i).append(": §f").append(vote.answers.get(i).title);
            }
            player.sendMessage(sb.toString());
            return true;
        }

        UUID uuid = player.getUniqueId();
        vote.votes.put(uuid, answerIndex);
        saveVoteRecord(vote.name, uuid, answerIndex);

        Answer chosen = vote.answers.get(answerIndex);
        player.sendMessage(MessageUtil.parse(
                "<green>✅</green> <white>Ваш голос учтён: </white><yellow>" + chosen.title + "</yellow>"));

        // Текущая статистика голосования (сколько всего проголосовало)
        int total = vote.votes.size();
        player.sendMessage(MessageUtil.parse(
                "<gray>Проголосовало: </gray><yellow>" + total + "</yellow><gray> из </gray><yellow>" + vote.getDisplayTotal() + "</yellow><gray> участников (online).</gray>"));

        return true;
    }

    // =========================
    // VIEW VOTE RESULTS
    // =========================
    public static boolean view(Player player, String voteName) {
        Vote vote = votes.get(voteName.toLowerCase());
        if (vote == null) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Голосование </red><yellow>" + voteName + "</yellow><red> не найдено!</red>"));
            return true;
        }

        player.sendMessage("");
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6  §e✦ " + vote.title);
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§7┃ §f" + vote.question);
        player.sendMessage("");

        // Подсчёт голосов
        Map<Integer, Integer> counts = new HashMap<>();
        for (int idx : vote.votes.values()) {
            counts.merge(idx, 1, Integer::sum);
        }
        int totalVotes = vote.votes.size();

        for (int i = 0; i < vote.answers.size(); i++) {
            Answer a = vote.answers.get(i);
            int count = counts.getOrDefault(i, 0);
            int pct = totalVotes > 0 ? (count * 100 / totalVotes) : 0;
            String bar = createProgressBar(pct, 20);

            player.sendMessage("§7┃ §e" + i + ". §f" + a.title);
            if (!a.description.isEmpty()) {
                player.sendMessage("§7┃   §7" + a.description);
            }
            player.sendMessage("§7┃   " + bar + " §e" + count + " §7(" + pct + "%)");
            player.sendMessage("");
        }

        String status;
        if (vote.ended) {
            status = "§cЗавершено";
        } else if (System.currentTimeMillis() >= vote.expiresAt) {
            status = "§cИстекает...";
        } else {
            long remaining = vote.expiresAt - System.currentTimeMillis();
            status = "§aОсталось: §e" + formatDuration(remaining);
        }
        player.sendMessage("§7┃ §7Всего голосов: §e" + totalVotes);
        player.sendMessage("§7┃ Статус: " + status);
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("");

        return true;
    }

    // =========================
    // LIST ALL VOTES
    // =========================
    public static boolean list(Player player) {
        if (votes.isEmpty()) {
            player.sendMessage(MessageUtil.parse(
                    "<yellow>✦</yellow> <white>Нет активных голосований.</white>"));
            return true;
        }

        player.sendMessage("");
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6  ✦ §fГолосования §7(" + votes.size() + ")");
        player.sendMessage("§6═══════════════════════════════════");

        for (Vote v : votes.values()) {
            String status;
            if (v.ended) {
                status = "§c✗ Завершено";
            } else if (System.currentTimeMillis() >= v.expiresAt) {
                status = "§cИстекает...";
            } else {
                long remaining = v.expiresAt - System.currentTimeMillis();
                status = "§a" + formatDuration(remaining);
            }
            player.sendMessage("§7┃ §e" + v.name + " §7— §f" + v.title);
            player.sendMessage("§7┃   §7Вариантов: §e" + v.answers.size() + " §7| Голосов: §e" + v.votes.size() + " §7| " + status);
            player.sendMessage("§7┃   §7§o/mp vote " + v.name + " §7— проголосовать");
            player.sendMessage("");
        }
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("");

        return true;
    }

    // =========================
    // DELETE VOTE (with confirmation)
    // =========================
    public static boolean delete(Player player, String voteName) {
        Vote vote = votes.get(voteName.toLowerCase());
        if (vote == null) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Голосование </red><yellow>" + voteName + "</yellow><red> не найдено!</red>"));
            return true;
        }
        if (!player.getUniqueId().equals(vote.creator) && !player.hasPermission("mcplugin.command.vote.delete.other")) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Только создатель голосования может его удалить!</red>"));
            return true;
        }

        String nameLower = vote.name.toLowerCase();
        Map<UUID, Boolean> pending = pendingDeletes.computeIfAbsent(nameLower, k -> new ConcurrentHashMap<>());

        if (pending.getOrDefault(player.getUniqueId(), false)) {
            // Подтверждено — удаляем
            pending.remove(player.getUniqueId());
            doDeleteVote(vote);
            player.sendMessage(MessageUtil.parse(
                    "<green>✅</green> <white>Голосование </white><yellow>" + vote.name + "</yellow><white> удалено.</white>"));
            return true;
        }

        // Запрашиваем подтверждение
        pending.put(player.getUniqueId(), true);
        player.sendMessage("");
        player.sendMessage("§8┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
        player.sendMessage("§8┃   §4⚠ §cПодтвердите удаление голосования");
        player.sendMessage("§8┃");
        player.sendMessage("§8┃   §7Название: §e" + vote.name);
        player.sendMessage("§8┃   §7Тема: §f" + vote.title);
        player.sendMessage("§8┃");
        player.sendMessage("§8┃   §7Это действие необратимо!");

        TextComponent confirmButton = new TextComponent("§8┃     §c[§4✗ Подтвердить удаление§c]");
        confirmButton.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/mp vote delete " + vote.name
        ));
        confirmButton.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§cНажмите чтобы подтвердить удаление").create()
        ));
        player.spigot().sendMessage(confirmButton);

        player.sendMessage("§8┃   §7или введите §f/mp vote delete " + vote.name + "§7 снова");
        player.sendMessage("§8┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
        player.sendMessage("");

        // Автосброс через 30 сек
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (pending.remove(player.getUniqueId()) != null) {
                player.sendMessage(MessageUtil.parse(
                        "<yellow>✦</yellow> <white>Запрос на удаление голосования </white><yellow>" + vote.name + "</yellow><white> сброшен (время вышло).</white>"));
            }
        }, 600L); // 30 sec

        return true;
    }

    // =========================
    // DO DELETE
    // =========================
    private static void doDeleteVote(Vote vote) {
        String nameLower = vote.name.toLowerCase();
        // Cancel close timer
        BukkitTask timer = closeTimers.remove(nameLower);
        if (timer != null) timer.cancel();

        votes.remove(nameLower);
        pendingDeletes.remove(nameLower);

        // Remove from DB
        try (Connection con = DatabaseManager.getConnection()) {
            try (PreparedStatement st = con.prepareStatement("DELETE FROM vote_records WHERE vote_name = ?")) {
                st.setString(1, vote.name);
                st.executeUpdate();
            }
            try (PreparedStatement st = con.prepareStatement("DELETE FROM vote_answers WHERE vote_name = ?")) {
                st.setString(1, vote.name);
                st.executeUpdate();
            }
            try (PreparedStatement st = con.prepareStatement("DELETE FROM votes WHERE name = ?")) {
                st.setString(1, vote.name);
                st.executeUpdate();
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[VOTE] Failed to delete vote from DB: " + e.getMessage());
        }

        Bukkit.broadcast(MessageUtil.parse(
                "<dark_red>✦</dark_red> <red>Голосование </red><yellow>" + vote.name + "</yellow><red> было удалено.</red>"));
    }

    // =========================
    // CHANGE VOTE (recreate with new params)
    // =========================
    public static boolean change(Player player, String voteName, String[] args, int startIndex) {
        Vote oldVote = votes.get(voteName.toLowerCase());
        if (oldVote == null) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Голосование </red><yellow>" + voteName + "</yellow><red> не найдено!</red>"));
            return true;
        }
        if (!player.getUniqueId().equals(oldVote.creator) && !player.hasPermission("mcplugin.command.vote.change.other")) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Только создатель голосования может его изменить!</red>"));
            return true;
        }

        // Parse new params from args
        if (args.length < startIndex + 1) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ Укажите хотя бы один параметр для изменения!</red>"));
            return true;
        }

        // We'll collect changes and apply them
        String newTitle = null;
        String newQuestion = null;
        List<Answer> newAnswers = null;
        Long newDuration = null;

        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-title") || arg.equals("-t")) {
                if (i + 1 < args.length) {
                    newTitle = args[++i];
                }
            } else if (arg.equals("-desc") || arg.equals("-d") || arg.equals("-question")) {
                if (i + 1 < args.length) {
                    newQuestion = args[++i];
                }
            } else if (arg.startsWith("-answer_")) {
                // Same format as create
                String withoutPrefix = arg.substring("-answer_".length());
                int colonIdx = withoutPrefix.indexOf(':');
                if (colonIdx < 0) continue;
                String numStr = withoutPrefix.substring(0, colonIdx);
                int answerNum;
                try {
                    answerNum = Integer.parseInt(numStr);
                } catch (NumberFormatException e) {
                    continue;
                }
                String rest = withoutPrefix.substring(colonIdx + 1);
                int commaIdx = rest.indexOf(',');
                String answerTitle;
                String answerDesc;
                if (commaIdx >= 0) {
                    answerTitle = rest.substring(0, commaIdx).trim();
                    answerDesc = rest.substring(commaIdx + 1).trim();
                } else {
                    answerTitle = rest.trim();
                    answerDesc = "";
                }
                if (answerTitle.isEmpty()) continue;

                if (newAnswers == null) newAnswers = new ArrayList<>(oldVote.answers);
                Answer a = new Answer();
                a.title = answerTitle;
                a.description = answerDesc;
                while (newAnswers.size() <= answerNum) newAnswers.add(null);
                newAnswers.set(answerNum, a);
            } else if (arg.startsWith("-time:")) {
                newDuration = parseDuration(arg.substring("-time:".length()).trim());
            }
        }

        // Применяем изменения
        boolean changed = false;

        if (newTitle != null && !newTitle.isEmpty()) {
            oldVote.title = newTitle;
            changed = true;
        }
        if (newQuestion != null && !newQuestion.isEmpty()) {
            oldVote.question = newQuestion;
            changed = true;
        }
        if (newAnswers != null) {
            newAnswers.removeAll(Collections.singleton(null));
            if (newAnswers.size() >= 2) {
                oldVote.answers = new ArrayList<>(newAnswers);
                changed = true;
            } else {
                player.sendMessage(MessageUtil.parse(
                        "<red>❌ После изменения должно быть минимум 2 варианта ответа!</red>"));
            }
        }
        if (newDuration != null && newDuration > 0) {
            oldVote.expiresAt = System.currentTimeMillis() + newDuration;
            // Reschedule timer
            BukkitTask oldTimer = closeTimers.remove(oldVote.name.toLowerCase());
            if (oldTimer != null) oldTimer.cancel();
            scheduleClose(oldVote.name.toLowerCase(), newDuration);
            changed = true;
        }

        if (changed) {
            // saveToDatabase уже сохраняет answers внутри
            saveToDatabase(oldVote);
            player.sendMessage(MessageUtil.parse(
                    "<green>✅</green> <white>Голосование </white><yellow>" + oldVote.name + "</yellow><white> обновлено.</white>"));
            broadcastVote(oldVote);
        } else {
            player.sendMessage(MessageUtil.parse(
                    "<yellow>✦</yellow> <white>Ничего не изменилось.</white>"));
        }

        return true;
    }

    // =========================
    // BROADCAST VOTE
    // =========================
    public static void broadcastVote(Vote vote) {
        String durationStr = formatDuration(vote.expiresAt - System.currentTimeMillis());

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("");
            player.sendMessage("§6═══════════════════════════════════════");
            player.sendMessage("§6  §e✦ §f" + vote.title);
            player.sendMessage("§6═══════════════════════════════════════");
            player.sendMessage("§7┃ §f" + vote.question);
            player.sendMessage("");
            player.sendMessage("§7┃ §7Варианты ответов:");

            for (int i = 0; i < vote.answers.size(); i++) {
                Answer a = vote.answers.get(i);

                TextComponent answerBtn = new TextComponent("§8┃ §e" + i + ". §a[§2" + a.title + "§a]");
                answerBtn.setClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/mp vote " + vote.name + " " + i
                ));
                answerBtn.setHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder("§aНажмите чтобы проголосовать за §f" + a.title + "\n")
                                .append(!a.description.isEmpty() ? "§7" + a.description : "")
                                .create()
                ));
                player.spigot().sendMessage(answerBtn);

                if (!a.description.isEmpty()) {
                    player.sendMessage("§8┃   §7" + a.description);
                }
            }

            player.sendMessage("");
            player.sendMessage("§8┃ §7Чтобы проголосовать, нажмите на кнопку выше");
            player.sendMessage("§8┃ §7или введите §f/mp vote " + vote.name + " <номер>");
            player.sendMessage("§8┃");
            player.sendMessage("§8┃ §7Осталось: §e" + durationStr);
            player.sendMessage("§6═══════════════════════════════════════");
            player.sendMessage("");
        }
    }

    // =========================
    // CLOSE VOTE
    // =========================
    public static void closeVote(String voteName) {
        Vote vote = votes.get(voteName.toLowerCase());
        if (vote == null || vote.ended) return;

        vote.ended = true;
        closeTimers.remove(voteName.toLowerCase());

        // Подсчёт результатов
        Map<Integer, Integer> counts = new HashMap<>();
        for (int idx : vote.votes.values()) {
            counts.merge(idx, 1, Integer::sum);
        }
        int total = vote.votes.size();

        // Определяем победителя
        int maxCount = 0;
        int winnerIdx = -1;
        boolean tie = false;
        for (int i = 0; i < vote.answers.size(); i++) {
            int c = counts.getOrDefault(i, 0);
            if (c > maxCount) {
                maxCount = c;
                winnerIdx = i;
                tie = false;
            } else if (c == maxCount && maxCount > 0) {
                tie = true;
            }
        }

        // Broadcast results
        Bukkit.broadcast(MessageUtil.parse(""));
        Bukkit.broadcast(MessageUtil.parse(
                "§6═══════════════════════════════════════\n" +
                "§6  ✦ §fГолосование завершено §7[§e" + vote.name + "§7]\n" +
                "§6═══════════════════════════════════════"));

        if (total == 0) {
            Bukkit.broadcast(MessageUtil.parse("§7┃ §7Никто не проголосовал."));
        } else if (tie) {
            Bukkit.broadcast(MessageUtil.parse("§7┃ §eНичья! §7Несколько вариантов набрали одинаковое количество голосов."));
            for (int i = 0; i < vote.answers.size(); i++) {
                int c = counts.getOrDefault(i, 0);
                if (c == maxCount) {
                    Bukkit.broadcast(MessageUtil.parse("§7┃ §e" + vote.answers.get(i).title + " §7— §e" + c + " §7голосов"));
                }
            }
        } else if (winnerIdx >= 0) {
            Answer winner = vote.answers.get(winnerIdx);
            int pct = (maxCount * 100 / total);
            Bukkit.broadcast(MessageUtil.parse(
                    "§7┃ §aПобедитель: §e" + winner.title + " §7(§e" + maxCount + "§7/" + total + " — §e" + pct + "%§7)"));
        }

        Bukkit.broadcast(MessageUtil.parse(
                "§7┃ §7Всего проголосовало: §e" + total));
        Bukkit.broadcast(MessageUtil.parse(
                "§6═══════════════════════════════════════"));
        Bukkit.broadcast(MessageUtil.parse(""));

        updateVoteEndedInDb(vote.name, true);
    }

    // =========================
    // SCHEDULE CLOSE
    // =========================
    private static void scheduleClose(String voteName, long delayMillis) {
        long delayTicks = Math.max(1, delayMillis / 50);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                closeVote(voteName);
            }
        }.runTaskLater(Main.getInstance(), delayTicks);
        closeTimers.put(voteName, task);
    }

    // =========================
    // DATABASE HELPERS
    // =========================
    private static void saveToDatabase(Vote vote) {
        try (Connection con = DatabaseManager.getConnection()) {
            try (PreparedStatement st = con.prepareStatement(
                    "INSERT OR REPLACE INTO votes (name, title, question, creator_uuid, created_at, expires_at, ended) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                st.setString(1, vote.name);
                st.setString(2, vote.title);
                st.setString(3, vote.question);
                st.setString(4, vote.creator.toString());
                st.setLong(5, vote.createdAt);
                st.setLong(6, vote.expiresAt);
                st.setInt(7, vote.ended ? 1 : 0);
                st.executeUpdate();
            }
            saveAnswersToDatabase(vote);
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[VOTE] DB save error: " + e.getMessage());
        }
    }

    private static void saveAnswersToDatabase(Vote vote) {
        try (Connection con = DatabaseManager.getConnection()) {
            // Clear old answers
            try (PreparedStatement st = con.prepareStatement("DELETE FROM vote_answers WHERE vote_name = ?")) {
                st.setString(1, vote.name);
                st.executeUpdate();
            }
            // Insert new
            try (PreparedStatement st = con.prepareStatement(
                    "INSERT INTO vote_answers (vote_name, answer_index, title, description) VALUES (?, ?, ?, ?)")) {
                for (int i = 0; i < vote.answers.size(); i++) {
                    Answer a = vote.answers.get(i);
                    st.setString(1, vote.name);
                    st.setInt(2, i);
                    st.setString(3, a.title);
                    st.setString(4, a.description);
                    st.addBatch();
                }
                st.executeBatch();
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[VOTE] DB save answers error: " + e.getMessage());
        }
    }

    private static void saveVoteRecord(String voteName, UUID playerUuid, int answerIndex) {
        try (Connection con = DatabaseManager.getConnection()) {
            // Upsert — replace existing vote
            try (PreparedStatement st = con.prepareStatement(
                    "INSERT OR REPLACE INTO vote_records (vote_name, player_uuid, answer_index) VALUES (?, ?, ?)")) {
                st.setString(1, voteName);
                st.setString(2, playerUuid.toString());
                st.setInt(3, answerIndex);
                st.executeUpdate();
            }
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[VOTE] DB save record error: " + e.getMessage());
        }
    }

    private static void updateVoteEndedInDb(String voteName, boolean ended) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement("UPDATE votes SET ended = ? WHERE name = ?")) {
            st.setInt(1, ended ? 1 : 0);
            st.setString(2, voteName);
            st.executeUpdate();
        } catch (Exception e) {
            Main.getInstance().getLogger().severe("[VOTE] DB update ended error: " + e.getMessage());
        }
    }

    // =========================
    // UTILITY
    // =========================
    private static long parseDuration(String timeStr) {
        if (timeStr.isEmpty()) return -1;
        char unit = timeStr.charAt(timeStr.length() - 1);
        String numStr = timeStr.substring(0, timeStr.length() - 1);
        long num;
        try {
            num = Long.parseLong(numStr);
        } catch (NumberFormatException e) {
            return -1;
        }
        if (num <= 0) return -1;
        return switch (Character.toLowerCase(unit)) {
            case 's' -> num * 1000;
            case 'm' -> num * 60000;
            case 'h' -> num * 3600000;
            case 'd' -> num * 86400000;
            default -> -1;
        };
    }

    private static String formatDuration(long millis) {
        if (millis < 0) millis = 0;
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d " + (hours % 24) + "h " + (minutes % 60) + "m";
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        if (minutes > 0) return minutes + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    private static String createProgressBar(int pct, int width) {
        int filled = pct * width / 100;
        StringBuilder sb = new StringBuilder("§8[");
        for (int i = 0; i < width; i++) {
            if (i < filled) {
                if (pct >= 66) sb.append("§a█");
                else if (pct >= 33) sb.append("§e█");
                else sb.append("§c█");
            } else {
                sb.append("§7▒");
            }
        }
        sb.append("§8]");
        return sb.toString();
    }

    // =========================
    // DATA CLASSES
    // =========================
    public static class Vote {
        public String name;
        public String title;
        public String question;
        public List<Answer> answers;
        public UUID creator;
        public long createdAt;
        public long expiresAt;
        public boolean ended;
        public Map<UUID, Integer> votes; // player -> answer index

        /** Сколько человек онлайн (можно голосовать) */
        public int getDisplayTotal() {
            return (int) Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.hasPermission("mcplugin.command.vote.bypass"))
                    .count();
        }
    }

    public static class Answer {
        public String title;
        public String description;
    }

    // =========================
    // SHUTDOWN — cancel all active timers
    // =========================
    public static void shutdown() {
        for (BukkitTask task : closeTimers.values()) {
            try {
                task.cancel();
            } catch (Exception ignored) {}
        }
        closeTimers.clear();
        Main.getInstance().getLogger().info("[VOTE] All timers cancelled.");
    }

    // =========================
    // GET VOTES (for tab completion)
    // =========================
    public static Set<String> getVoteNames() {
        return votes.keySet();
    }

    public static Vote getVote(String name) {
        return votes.get(name.toLowerCase());
    }
}
