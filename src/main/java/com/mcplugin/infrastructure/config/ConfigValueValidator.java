package com.mcplugin.infrastructure.config;

import com.mcplugin.infrastructure.core.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Проверяет значения config.yml по правилам из {@link ConfigRules}.
 * <p>
 * Извлечено из {@link ConfigIntegrityValidator} для уменьшения размера класса.
 */
final class ConfigValueValidator {

    private ConfigValueValidator() {}

    private enum Severity { OK, WARN, ERROR }

    private static class Result {
        static final Result OK = new Result(Severity.OK, "");
        final Severity severity;
        final String message;
        Result(Severity severity, String message) { this.severity = severity; this.message = message; }
        static Result error(String msg) { return new Result(Severity.ERROR, msg); }
        static Result warn(String msg) { return new Result(Severity.WARN, msg); }
    }

    /** Проверяет все значения конфига по правилам. */
    static void validateValues(Main plugin, FileConfiguration config) {
        int errors = 0;
        int warnings = 0;

        for (ConfigRules.Rule rule : ConfigRules.ALL) {
            if (!config.isSet(rule.key)) continue;
            Result result = checkValue(config, rule);
            switch (result.severity) {
                case ERROR -> { errors++; plugin.getLogger().warning("[ConfigValidator] \u26A0 [ERROR] " + rule.key + ": " + result.message); }
                case WARN -> { warnings++; plugin.getLogger().warning("[ConfigValidator] \u26A0 [WARN] " + rule.key + ": " + result.message); }
            }
        }

        if (errors > 0 || warnings > 0) {
            plugin.getLogger().warning("");
            plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            plugin.getLogger().warning("!  CONFIG VALUE VALIDATION COMPLETE                     !");
            plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            if (errors > 0) plugin.getLogger().warning("!  " + errors + " error(s) found - fix these values!              !");
            if (warnings > 0) plugin.getLogger().warning("!  " + warnings + " warning(s) found - review recommended.         !");
            plugin.getLogger().warning("!  Plugin will use defaults for invalid values.          !");
            plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n");
        } else {
            plugin.getLogger().info("[ConfigValidator] \u2713 All config values passed validation.");
        }
    }

    private static Result checkValue(FileConfiguration config, ConfigRules.Rule rule) {
        Object value = config.get(rule.key);
        if (value == null) return Result.OK;

        return switch (rule.type) {
            case BOOLEAN -> checkBoolean(value);
            case INT -> checkInt(value, rule);
            case DOUBLE -> checkDouble(value, rule);
            case STRING -> checkString(value, rule);
            case STRING_LIST -> checkStringList(rule.key, value);
            case INT_LIST -> checkIntList(value);
            case DOUBLE_LIST -> checkDoubleList(value);
        };
    }

    private static Result checkBoolean(Object value) {
        if (value instanceof Boolean) return Result.OK;
        return Result.error("Ожидался boolean (true/false), получен " + typeName(value) + ": " + value);
    }

    private static Result checkInt(Object value, ConfigRules.Rule rule) {
        if (!(value instanceof Number num))
            return Result.error("Ожидалось целое число, получен " + typeName(value) + ": " + value);

        long longVal = num.longValue();
        if (value instanceof Double || value instanceof Float) {
            double dVal = num.doubleValue();
            if (dVal != Math.floor(dVal))
                return Result.warn("Значение с плавающей точкой, будет округлено до " + (long) dVal);
        }
        if (longVal < Integer.MIN_VALUE || longVal > Integer.MAX_VALUE)
            return Result.error("Значение вне диапазона int: " + longVal);

        int intVal = (int) longVal;
        StringBuilder boundsMsg = new StringBuilder();
        if (intVal < rule.min) boundsMsg.append("минимальное ").append(formatNum(rule.min));
        if (intVal > rule.max) {
            if (!boundsMsg.isEmpty()) boundsMsg.append(", ");
            boundsMsg.append("максимальное ").append(formatNum(rule.max));
        }
        if (!boundsMsg.isEmpty()) {
            String desc = rule.description.isEmpty() ? "" : " (" + rule.description + ")";
            return Result.error("Значение " + intVal + " вне диапазона [" + formatNum(rule.min) + ".." + formatNum(rule.max) + "]" + desc);
        }
        return Result.OK;
    }

    private static Result checkDouble(Object value, ConfigRules.Rule rule) {
        if (!(value instanceof Number num))
            return Result.error("Ожидалось число с плавающей точкой, получен " + typeName(value) + ": " + value);

        double dVal = num.doubleValue();
        if (Double.isInfinite(dVal)) return Result.error("Значение равно бесконечности (Infinity)");
        if (Double.isNaN(dVal)) return Result.error("Значение не является числом (NaN)");

        StringBuilder boundsMsg = new StringBuilder();
        if (dVal < rule.min) boundsMsg.append("минимальное ").append(formatNum(rule.min));
        if (dVal > rule.max) {
            if (!boundsMsg.isEmpty()) boundsMsg.append(", ");
            boundsMsg.append("максимальное ").append(formatNum(rule.max));
        }
        if (!boundsMsg.isEmpty()) {
            String desc = rule.description.isEmpty() ? "" : " (" + rule.description + ")";
            return Result.error("Значение " + dVal + " вне диапазона [" + formatNum(rule.min) + ".." + formatNum(rule.max) + "]" + desc);
        }
        return Result.OK;
    }

    private static Result checkString(Object value, ConfigRules.Rule rule) {
        if (!(value instanceof String str))
            return Result.error("Ожидалась строка, получен " + typeName(value) + ": " + value);
        if (rule.notEmpty && str.isEmpty()) return Result.error("Строка пуста");
        if (rule.notBlank && str.trim().isEmpty()) return Result.error("Строка состоит только из пробелов");

        StringBuilder badChars = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                if (!badChars.isEmpty()) badChars.append(", ");
                badChars.append("U+").append(String.format("%04X", (int) c));
            }
        }
        if (!badChars.isEmpty()) return Result.warn("Строка содержит управляющие символы: " + badChars);
        if (rule.maxLength > 0 && str.length() > rule.maxLength)
            return Result.error("Длина (" + str.length() + ") превышает максимум (" + rule.maxLength + ")");
        if (rule.regex != null && !rule.regex.isEmpty()) {
            try { if (!str.matches(rule.regex)) return Result.error("Строка не соответствует формату: " + rule.regex); }
            catch (PatternSyntaxException ignored) {}
        }
        return Result.OK;
    }

    @SuppressWarnings("unchecked")
    private static Result checkStringList(String key, Object value) {
        if (!(value instanceof List)) return Result.warn("Ожидался список строк, получен " + typeName(value));
        List<?> list = (List<?>) value;
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof String))
                return Result.warn("Элемент #" + i + " — ожидалась строка, получен " + typeName(item));
            String str = (String) item;
            StringBuilder badChars = new StringBuilder();
            for (int j = 0; j < str.length(); j++) {
                char c = str.charAt(j);
                if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                    if (!badChars.isEmpty()) badChars.append(", ");
                    badChars.append("U+").append(String.format("%04X", (int) c));
                }
            }
            if (!badChars.isEmpty()) return Result.warn("Элемент #" + i + " содержит управляющие символы: " + badChars);
            if (key.endsWith("regex_patterns")) {
                try { Pattern.compile(str); }
                catch (PatternSyntaxException e) { return Result.error("Элемент #" + i + " — невалидный regex: " + e.getMessage()); }
            }
        }
        return Result.OK;
    }

    @SuppressWarnings("unchecked")
    private static Result checkIntList(Object value) {
        if (!(value instanceof List)) return Result.warn("Ожидался список целых чисел, получен " + typeName(value));
        for (Object item : (List<?>) value) {
            if (!(item instanceof Number)) return Result.warn("Элемент — ожидалось целое число, получен " + typeName(item));
        }
        return Result.OK;
    }

    @SuppressWarnings("unchecked")
    private static Result checkDoubleList(Object value) {
        if (!(value instanceof List)) return Result.warn("Ожидался список чисел, получен " + typeName(value));
        for (Object item : (List<?>) value) {
            if (!(item instanceof Number)) return Result.warn("Элемент — ожидалось число, получен " + typeName(item));
        }
        return Result.OK;
    }

    private static String typeName(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof Integer || value instanceof Long) return "int";
        if (value instanceof Double || value instanceof Float) return "double";
        if (value instanceof String) return "string";
        if (value instanceof List) return "list";
        if (value instanceof ConfigurationSection) return "section";
        return value.getClass().getSimpleName();
    }

    private static String formatNum(double d) {
        return d == (long) d ? String.valueOf((long) d) : String.valueOf(d);
    }
}
