package com.ultimateimprovments.util;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

import java.util.regex.Pattern;

/**
 * Log4J filter — intercepts logs of commands with passwords and hides them.
 * <p>
 * The server writes "{@code rizer001 issued server command: /ui auth login mypassword}" to the console.
 * This filter finds such lines and masks the password.
 */
public class AuthCommandLogFilter extends AbstractFilter {

    /**
     * Pattern for finding authentication commands with a password.
     * Masks:
     * <ul>
     *   <li>{@code /ui auth login <password>}</li>
     *   <li>{@code /ui auth register <password>}</li>
     *   <li>{@code /ui auth chgpass <nick> <new_password>}</li>
     * </ul>
     */
    private static final Pattern AUTH_PASSWORD_PATTERN = Pattern.compile(
            "/ui auth (?:login|register|chgpass)(?:\\s+\\S+)?\\s+\\S+",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Finds a password command in the message and returns a masked copy.
     * Example: {@code "rizer001 issued server command: /ui auth login mypassword"}
     * → {@code "rizer001 issued server command: /ui auth login ***"}
     */
    static String maskPassword(String message) {
        return AUTH_PASSWORD_PATTERN.matcher(message).replaceAll(m -> {
            // Take the part before the last space (the command without the password) and add ***
            String full = m.group();
            int lastSpace = full.lastIndexOf(' ');
            if (lastSpace > 0) {
                return full.substring(0, lastSpace) + " ***";
            }
            return full + " ***";
        });
    }

    @Override
    public Result filter(LogEvent event) {
        if (event == null) return Result.NEUTRAL;

        Message msgObj = event.getMessage();
        if (msgObj == null) return Result.NEUTRAL;

        String formatted = msgObj.getFormattedMessage();
        if (formatted == null) return Result.NEUTRAL;

        if (AUTH_PASSWORD_PATTERN.matcher(formatted).find()) {
            return Result.DENY;
        }

        return Result.NEUTRAL;
    }

    /**
     * Registers the filter in Log4J's root Logger.
     * Must be called on plugin startup (once).
     */
    public static void register() {
        LoggerContext ctx = LoggerContext.getContext(false);
        if (ctx == null || ctx.getConfiguration() == null) return;

        ctx.getConfiguration().getRootLogger().addFilter(new AuthCommandLogFilter());
        ctx.updateLoggers();

        ConsoleLogger.info("[Auth] AuthCommandLogFilter registered — passwords hidden from console");
    }
}
