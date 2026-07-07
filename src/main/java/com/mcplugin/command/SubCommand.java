package com.mcplugin.command;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * Единый интерфейс для всех субкоманд /mp.
 *
 * <p>Каждая субкоманда — отдельный класс с execute() и tabComplete().
 * Регистрируется в {@link SubCommandRegistry} и автоматически подхватывается
 * диспетчером {@link PluginReloadCommand}.</p>
 */
public interface SubCommand {

    /**
     * Выполняет субкоманду.
     *
     * @param sender отправитель команды (игрок или консоль)
     * @param args   полный массив аргументов (args[0] — имя субкоманды)
     * @return true если команда обработана
     */
    boolean execute(CommandSender sender, String[] args);

    /**
     * Возвращает подсказки tab-complete для этой субкоманды.
     *
     * @param sender отправитель
     * @param args   полный массив аргументов
     * @return список подсказок или пустой список
     */
    default List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    /**
     * Возвращает имя субкоманды (регистронезависимое).
     * По умолчанию — имя класса в lowercase.
     */
    default String getName() {
        return getClass().getSimpleName()
                .replace("Subcommand", "")
                .toLowerCase();
    }

    /**
     * Возвращает список алиасов (дополнительных имён) для этой субкоманды.
     */
    default List<String> getAliases() {
        return Collections.emptyList();
    }
}
