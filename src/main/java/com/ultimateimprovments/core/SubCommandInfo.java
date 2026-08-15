package com.ultimateimprovments.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for auto-discovery of /ui subcommands.
 * <p>
 * The class must implement {@link com.ultimateimprovments.command.SubCommand}.
 * Once the annotation is added, the command is automatically registered — no need
 * to edit PluginReloadCommand.init().
 * <p>
 * Example:
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
    /** Command name (case-insensitive). Defaults to the class name in lowercase without "Subcommand". */
    String name() default "";

    /** Aliases (additional names). */
    String[] aliases() default {};
}
