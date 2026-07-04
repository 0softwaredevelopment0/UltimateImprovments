package com.mcplugin.infrastructure.util;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

import java.util.regex.Pattern;

/**
 * Log4J filter — перехватывает логи команд с паролями и скрывает их.
 * <p>
 * Сервер пишет в консоль "{@code rizer001 issued server command: /mp auth login mypassword}".
 * Этот фильтр находит такие строки и маскирует пароль.
 */
public class AuthCommandLogFilter extends AbstractFilter {

    /**
     * Паттерн для нахождения команд аутентификации с паролем.
     * Маскирует:
     * <ul>
     *   <li>{@code /mp auth login <password>}</li>
     *   <li>{@code /mp auth register <password>}</li>
     *   <li>{@code /mp auth chgpass <nick> <new_password>}</li>
     * </ul>
     */
    private static final Pattern AUTH_PASSWORD_PATTERN = Pattern.compile(
            "/mp auth (?:login|register|chgpass)(?:\\s+\\S+)?\\s+\\S+",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Ищет команду с паролем в сообщении и возвращает маскированную копию.
     * Пример: {@code "rizer001 issued server command: /mp auth login mypassword"}
     * → {@code "rizer001 issued server command: /mp auth login ***"}
     */
    static String maskPassword(String message) {
        return AUTH_PASSWORD_PATTERN.matcher(message).replaceAll(m -> {
            // Берём часть до последнего пробела (команда без пароля) и добавляем ***
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
     * Регистрирует фильтр в root Logger'е Log4J.
     * Должен вызываться при старте плагина (один раз).
     */
    public static void register() {
        LoggerContext ctx = LoggerContext.getContext(false);
        if (ctx == null || ctx.getConfiguration() == null) return;

        ctx.getConfiguration().getRootLogger().addFilter(new AuthCommandLogFilter());
        ctx.updateLoggers();

        ConsoleLogger.info("[Auth] AuthCommandLogFilter registered — passwords hidden from console");
    }
}
