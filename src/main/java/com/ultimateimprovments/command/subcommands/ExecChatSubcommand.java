package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /ui execchat — отправить сообщение чата от имени игрока.
 * <p>
 * Полная имитация ввода: текст проходит весь обычный чат-пайплайн
 * (AsyncPlayerChatEvent → фильтры, муты, кастомное форматирование чата),
 * а если текст начинается с {@code /} — выполняется как команда
 * (та же логика, что и {@code Player#chat}, где сервер сам разводит
 * чат и команды).
 *
 * <pre>{@code
 * /ui execchat Rizer "hello everyone"
 * /ui execchat Rizer "/home"        // выполнит /home от имени Rizer
 * }</pre>
 * <p>
 * Семантика:
 * <ul>
 *   <li>Команды выполняются с правами ЦЕЛИ (как если бы игрок напечатал её сам),
 *       а не с правами отправителя — эскалации прав нет.</li>
 *   <li>Чат-сообщения проходят обычные проверки: муты и фильтры применяются,
 *       поэтому сообщение замученного игрока будет заблокировано.</li>
 *   <li>Право на команду: {@code ui.command.execchat} (default: false).</li>
 * </ul>
 */
public final class ExecChatSubcommand {

    private ExecChatSubcommand() {}

    /**
     * Соединяет все аргументы начиная с startIndex в одну строку
     * и срезает обрамляющие кавычки: {@code "/home test"} → {@code /home test}.
     */
    private static String joinMessage(String[] args, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(args[i]);
        }
        String message = sb.toString().trim();
        if (message.length() >= 2
                && ((message.startsWith("\"") && message.endsWith("\""))
                    || (message.startsWith("'") && message.endsWith("'")))) {
            message = message.substring(1, message.length() - 1).trim();
        }
        return message;
    }

    @SuppressWarnings("deprecation") // Player#chat — единственный верный способ полной имитации ввода в 1.21+
    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ui.command.execchat")) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to use this command!</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui execchat <player> \"<message>\"</white>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Player </red><yellow>" + args[1] + "</yellow><red> not found or offline!</red>"));
            return true;
        }

        String message = joinMessage(args, 2);
        if (message.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Message cannot be empty!</red>"));
            return true;
        }

        // Полная имитация ввода: "/..." → команда, иначе → чат (все фильтры/муты применяются)
        target.chat(message);

        String kind = message.startsWith("/") ? "command" : "chat message";
        ConsoleLogger.info("[ExecChat] " + sender.getName() + " made " + target.getName()
                + " send " + kind + ": " + message);
        sender.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Executed as </white><yellow>" + target.getName()
                        + "</yellow><white> (" + kind + "): </white><gray>" + message + "</gray>"));
        return true;
    }

    public static List<String> tabComplete(String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                result.add(p.getName());
            }
        } else if (args.length == 3) {
            result.add("\"message\"");
            result.add("\"/command\"");
        }
        return result;
    }
}
