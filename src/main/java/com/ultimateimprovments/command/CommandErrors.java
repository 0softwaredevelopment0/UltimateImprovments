package com.ultimateimprovments.command;

import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.command.CommandSender;

/**
 * Единый обработчик отсутствия прав у команд.
 * <p>
 * Все классы команд обращаются сюда, когда игроку не хватает пермишена
 * (именно пермишена, а не другого условия) для выполнения команды.
 * Метод {@link #noPermission(CommandSender)} пишет одно общее сообщение
 * «нет прав» в чат отправителя.
 */
public final class CommandErrors {

    private static final String NO_PERMISSION =
            "<red>✖ <white>У тебя нет прав на выполнение этой команды.</white>";

    private CommandErrors() {}

    /**
     * Сообщает отправителю, что у него нет прав на команду.
     * Консоль пропускается (у неё всегда есть права).
     */
    public static void noPermission(CommandSender sender) {
        if (sender == null || !(sender instanceof org.bukkit.entity.Player)) return;
        sender.sendMessage(MessageUtil.parse(NO_PERMISSION));
    }
}
