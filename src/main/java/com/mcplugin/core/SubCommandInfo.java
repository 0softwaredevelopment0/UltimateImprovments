package com.mcplugin.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Аннотация для авто-обнаружения субкоманд /mp.
 * <p>
 * Класс должен implements {@link com.mcplugin.command.SubCommand}.
 * После добавления аннотации команда автоматически регистрируется — не нужно
 * править PluginReloadCommand.init().
 * <p>
 * Пример:
 * <pre>{@code
 * @SubCommandInfo(name = "warp", aliases = {"warps"})
 * public class WarpSubCommand implements SubCommand {
 *     ...
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubCommandInfo {
    /** Имя команды (регистронезависимое). По умолчанию — имя класса в lowercase без "Subcommand". */
    String name() default "";

    /** Алиасы (дополнительные имена). */
    String[] aliases() default {};
}
