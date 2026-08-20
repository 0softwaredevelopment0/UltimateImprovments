package com.ultimateimprovments.config;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 🧯 ConfigCrashSalvage — «синтаксический краш → игнор секции».
 * <p>
 * Когда config.yml не парсится (ошибка YAML-синтаксиса в одной секции), вместо
 * удаления ВСЕГО файла (как было раньше) спасаем конфиг:
 * <ol>
 *   <li>Находим корневую секцию, в которой произошла ошибка (по номеру строки из
 *       исключения SnakeYAML / Bukkit, фолбэк — перебор секций по одной);</li>
 *   <li>Удаляем ТОЛЬКО эту секцию, сохраняя остальные настройки пользователя;</li>
 *   <li>Сломанная секция копируется в {@code plugins/UltimateImprovments/config-broken/}
 *       — ничего не теряется, пользователь может починить её вручную;</li>
 *   <li>Если после удаления файл всё ещё не парсится — повторяем (несколько сломанных секций);</li>
 *   <li>Затем {@link ConfigRepairManager} (вызывается следом при старте) допишет
 *       в конец файла дефолтные ключи удалённых секций — плагин продолжит работать
 *       с настройками по умолчанию для этих секций.</li>
 * </ol>
 * Если сломанную часть не удаётся локализовать (например, мусор вне секций) —
 * метод вернёт {@code false}, и вызывающий код применит старый запасной вариант
 * (пересоздание файла из JAR).
 */
public final class ConfigCrashSalvage {

    /** Имя папки с бэкапами удалённых секций (внутри dataFolder). */
    public static final String BACKUP_DIR_NAME = "config-broken";

    private static final int MAX_ROUNDS = 100;
    private static final Pattern LINE_IN_MESSAGE = Pattern.compile("(?i)line\\s+(\\d+)");

    private ConfigCrashSalvage() {}

    /** Результат спасательной операции. */
    public static final class Result {
        /** true — файл теперь парсится (секции могли быть удалены). */
        public final boolean success;
        /** Имена удалённых (сломанных) корневых секций. */
        public final List<String> removedSections;
        /** Человекочитаемое описание результата. */
        public final String message;

        Result(boolean success, List<String> removedSections, String message) {
            this.success = success;
            this.removedSections = removedSections;
            this.message = message;
        }
    }

    // ==========================================================================
    // 🏗 ENTRY POINTS
    // ==========================================================================

    /**
     * Пытается починить {@code plugins/<plugin>/config.yml} после неудачной загрузки.
     *
     * @return true, если файл теперь парсится (даже если пришлось удалить секции)
     */
    public static boolean salvage(Main plugin) {
        File dataFolder = plugin.getDataFolder();
        File configFile = new File(dataFolder, "config.yml");
        File backupDir = new File(dataFolder, BACKUP_DIR_NAME);
        Result result = salvageFile(configFile, backupDir, msg -> ConsoleLogger.warn("[ConfigSalvage] " + msg));

        for (String section : result.removedSections) {
            ConsoleLogger.warn("[ConfigSalvage] ⚠ Dropped broken section '" + section + "'");
        }
        if (result.success) {
            ConsoleLogger.info("[ConfigSalvage] ✔ " + result.message);
        } else {
            ConsoleLogger.warn("[ConfigSalvage] ✗ " + result.message);
        }
        return result.success;
    }

    /**
     * Копирует сломанный config.yml в {@code config-broken/} перед пересозданием
     * из JAR — чтобы данные не терялись даже в запасном сценарии, когда
     * сломанную часть не удалось локализовать.
     */
    public static void backupWholeFile(Main plugin) {
        File dataFolder = plugin.getDataFolder();
        File configFile = new File(dataFolder, "config.yml");
        File backupDir = new File(dataFolder, BACKUP_DIR_NAME);
        if (!configFile.exists()) return;
        try {
            backupDir.mkdirs();
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            File target = new File(backupDir, "config-broken-" + timestamp + ".yml");
            Files.copy(configFile.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            ConsoleLogger.warn("[ConfigSalvage] Backed up broken config.yml to " + target.getName());
        } catch (IOException e) {
            ConsoleLogger.warn("[ConfigSalvage] Could not back up broken config.yml: " + e.getMessage());
        }
    }

    /**
     * Чистая логика спасания файла — без Bukkit/Main, тестируемая в JUnit.
     * <p>
     * Не вызывается, если файл уже парсится (вернёт success без удалений).
     *
     * @param configFile файл конфига
     * @param backupDir  папка, куда складываются удалённые секции (создаётся при необходимости)
     * @param log        потребитель лог-строк (может быть no-op)
     */
    public static Result salvageFile(File configFile, File backupDir, Consumer<String> log) {
        if (!configFile.exists()) {
            return new Result(false, new ArrayList<>(), "config.yml does not exist");
        }

        List<String> lines;
        try {
            lines = new ArrayList<>(Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new Result(false, new ArrayList<>(), "cannot read config.yml: " + e.getMessage());
        }

        // Уже парсится — спасать нечего
        Throwable[] lastError = new Throwable[1];
        if (parses(lines, lastError)) {
            return new Result(true, new ArrayList<>(), "config.yml is valid, nothing to salvage");
        }

        List<String> removed = new ArrayList<>();
        int rounds = 0;

        while (true) {
            if (++rounds > MAX_ROUNDS) {
                return new Result(false, removed, "could not stabilize config.yml after " + MAX_ROUNDS + " attempts");
            }

            if (parses(lines, lastError)) break;

            List<Section> sections = findRootSections(lines);
            if (sections.isEmpty()) {
                return new Result(false, removed, "no root sections found — cannot isolate the broken part");
            }

            int errorLine = findErrorLine(lastError[0]);

            // Кандидаты на удаление: сначала секция, содержащая строку ошибки,
            // затем ближайшие к ней, затем все остальные по порядку.
            List<Section> candidates = orderCandidates(sections, errorLine);
            Section target = null;
            for (Section candidate : candidates) {
                List<String> test = without(lines, candidate);
                if (parses(test, lastError)) {
                    target = candidate;
                    break;
                }
            }
            if (target == null && !candidates.isEmpty()) {
                // Один кандидат не чинит файл (например, сломано несколько секций) —
                // удаляем лучшего кандидата и продолжаем цикл.
                target = candidates.get(0);
            }
            if (target == null) {
                return new Result(false, removed, "broken content is not inside any root section — cannot isolate it");
            }

            backupSection(backupDir, target, lines, lastError[0]);
            log.accept("Removing broken section '" + target.key + "' (lines "
                    + (target.start + 1) + "-" + (target.end + 1) + ")");
            lines = without(lines, target);
            removed.add(target.key);
        }

        if (!removed.isEmpty()) {
            try {
                Files.write(configFile.toPath(), lines, StandardCharsets.UTF_8);
            } catch (IOException e) {
                return new Result(false, removed, "cannot write salvaged config.yml: " + e.getMessage());
            }
        }

        String summary = removed.isEmpty()
                ? "config.yml is valid, nothing to salvage"
                : "removed " + removed.size() + " broken section(s): " + String.join(", ", removed)
                        + " — defaults will be re-added by auto-repair";
        log.accept(summary);
        return new Result(true, removed, summary);
    }

    // ==========================================================================
    // 🔍 ЛОКАЛИЗАЦИЯ СЛОМАННОЙ СЕКЦИИ
    // ==========================================================================

    /**
     * Пытается определить (1-based) номер строки с ошибкой из цепочки исключений:
     * SnakeYAML {@code MarkedYAMLException.getProblemMark().getLine()}, геттер
     * {@code getLineNumber()} (некоторые обёртки Paper), либо regex по тексту сообщения.
     *
     * @return номер строки (1-based) или -1, если определить не удалось
     */
    static int findErrorLine(Throwable error) {
        if (error == null) return -1;

        // 1) reflection-геттеры по всей цепочке causes
        for (Throwable t = error; t != null; t = t.getCause()) {
            Integer line = reflectionLine(t);
            if (line != null && line > 0) return line;
        }
        // 2) текст сообщения: "...line 12, column 3..."
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getMessage() == null) continue;
            Matcher m = LINE_IN_MESSAGE.matcher(t.getMessage());
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {
                    // попробовать следующее сообщение
                }
            }
        }
        return -1;
    }

    private static Integer reflectionLine(Throwable t) {
        // 1) getLineNumber() — например, у обёрток Paper/ансилков
        try {
            Object val = t.getClass().getMethod("getLineNumber").invoke(t);
            if (val instanceof Number n) return n.intValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // нет такого метода
        }
        // 2) SnakeYAML MarkedYAMLException → getProblemMark().getLine() (0-based)
        try {
            Class<?> cls = Class.forName("org.yaml.snakeyaml.error.MarkedYAMLException");
            if (cls.isInstance(t)) {
                Object mark = cls.getMethod("getProblemMark").invoke(t);
                if (mark != null) {
                    Object line = mark.getClass().getMethod("getLine").invoke(mark);
                    if (line instanceof Number n) return n.intValue() + 1;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // SnakeYAML не на classpath — не страшно, есть фолбэк по сообщению
        }
        return null;
    }

    /**
     * Сортирует секции-кандидаты: содержащие строку ошибки первыми, затем по
     * удалённости от неё, затем по порядку в файле. Если номер строки неизвестен
     * (errorLine &lt; 0) — просто порядок файла.
     */
    private static List<Section> orderCandidates(List<Section> sections, int errorLine) {
        List<Section> ordered = new ArrayList<>(sections);
        if (errorLine < 0) return ordered;

        ordered.sort((a, b) -> {
            boolean aContains = a.contains(errorLine - 1);
            boolean bContains = b.contains(errorLine - 1);
            if (aContains != bContains) return aContains ? -1 : 1;
            int da = Math.abs(a.start - (errorLine - 1));
            int db = Math.abs(b.start - (errorLine - 1));
            int byDistance = Integer.compare(da, db);
            if (byDistance != 0) return byDistance;
            return Integer.compare(a.start, b.start);
        });
        return ordered;
    }

    // ==========================================================================
    // 🗂 РАБОТА С ФАЙЛОМ / СЕКЦИЯМИ
    // ==========================================================================

    /**
     * Пробует распарсить строки как YAML конфиг. Ошибка кладётся в {@code lastError[0]}.
     * <p>
     * ВАЖНО: используется {@code loadFromString} (бросает {@code InvalidConfigurationException}),
     * а не {@code loadConfiguration} — последний в Paper-26 молча ГЛОТАЕТ ошибку парсинга
     * (логирует и возвращает пустой конфиг), из-за чего битый файл выглядел бы «валидным».
     */
    private static boolean parses(List<String> lines, Throwable[] lastError) {
        try {
            new YamlConfiguration().loadFromString(String.join("\n", lines));
            lastError[0] = null;
            return true;
        } catch (Throwable t) {
            lastError[0] = t;
            return false;
        }
    }

    /** Список строк без секции {@code section}. */
    private static List<String> without(List<String> lines, Section section) {
        List<String> result = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            if (i < section.start || i > section.end) result.add(lines.get(i));
        }
        return result;
    }

    /** Копирует удалённую секцию в backupDir с поясняющим заголовком. */
    private static void backupSection(File backupDir, Section section, List<String> lines, Throwable error) {
        try {
            backupDir.mkdirs();
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            String name = sanitizeFileName(section.key) + "-" + timestamp + ".yml";
            StringBuilder sb = new StringBuilder();
            sb.append("# === Broken section removed from config.yml (YAML syntax error) ===\n");
            if (error != null && error.getMessage() != null) {
                sb.append("# Error: ").append(error.getMessage().replace('\n', ' ')).append('\n');
            }
            sb.append("# Fix this section and merge it back into config.yml (remove the duplicate key).\n");
            sb.append("# --------------------------------------------------------------------------\n");
            for (int i = section.start; i <= section.end; i++) {
                sb.append(lines.get(i)).append('\n');
            }
            Files.write(new File(backupDir, name).toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // Бэкап не критичен — не прерываем спасание
        }
    }

    private static String sanitizeFileName(String key) {
        return key.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /** Находит все корневые секции файла (0-based диапазоны строк). */
    static List<Section> findRootSections(List<String> lines) {
        List<Integer> keyLines = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (isRootKey(lines.get(i))) keyLines.add(i);
        }

        List<Section> sections = new ArrayList<>(keyLines.size());
        for (int k = 0; k < keyLines.size(); k++) {
            int start = keyLines.get(k);
            int end = (k + 1 < keyLines.size()) ? keyLines.get(k + 1) - 1 : lines.size() - 1;
            sections.add(new Section(extractKey(lines.get(start)), start, end));
        }
        return sections;
    }

    /** Корневой ключ — строка без отступа вида {@code key:} (не комментарий, не элемент списка). */
    private static boolean isRootKey(String line) {
        if (line == null || line.isEmpty()) return false;
        char first = line.charAt(0);
        if (first == ' ' || first == '\t') return false;
        String trimmed = line.trim();
        if (trimmed.startsWith("#")) return false;
        if (trimmed.startsWith("- ")) return false;
        return trimmed.matches("^[a-zA-Z_][a-zA-Z0-9_-]*:.*");
    }

    private static String extractKey(String line) {
        String trimmed = line.trim();
        int colon = trimmed.indexOf(':');
        return colon > 0 ? trimmed.substring(0, colon) : trimmed;
    }

    /** Корневая секция: ключ + диапазон строк (включительно). */
    static final class Section {
        final String key;
        final int start;
        final int end;

        Section(String key, int start, int end) {
            this.key = key;
            this.start = start;
            this.end = end;
        }

        /** Содержит ли секция строку с индексом {@code lineIndex} (0-based). */
        boolean contains(int lineIndex) {
            return start <= lineIndex && lineIndex <= end;
        }
    }
}
