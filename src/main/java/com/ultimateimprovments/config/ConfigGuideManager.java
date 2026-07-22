package com.ultimateimprovments.config;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.FileLogger;
import com.ultimateimprovments.util.ConsoleLogger;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * ConfigGuideManager — управляет ЭМБЕДЖЕННЫМ в config.yml руководством пользователя.
 * <p>
 * С v26.2 plugin-guide.txt больше не хранится как отдельный файл. Его содержимое лежит
 * В НАЧАЛЕ config.yml в виде YAML-комментариев между маркерами:
 * <pre>
 * # === MC-PLUGIN GUIDE BEGIN (auto-managed, don't edit between markers) ===
 * [несколько сотен строк содежимого плагин-гайда в виде комментов с префиксом "# "]
 * # === MC-PLUGIN GUIDE END ===
 * </pre>
 * <p>
 * Хеш целостности эмбедженного гайда хранится в config.yml под ключом
 * {@code _meta.guide_hash} (самая нижняя секция файла). Раз в запуск плагин:
 * <ol>
 *   <li>Извлекает из config.yml текст между маркерами;</li>
 *   <li>Сравнивает его SHA-256 с {@code _meta.guide_hash};</li>
 *   <li>Если есть новая версия в JAR-ресурсе {@code plugin-guide.txt} — заменяет диапазон
 *       между маркерами и пересчитывает хеш.</li>
 *   <li>Если в dataFolder остался старый файл {@code plugin-guide.txt} (от предыдущей версии
 *       плагина) — мигрирует его содержимое в config.yml, после чего удаляет файл и хеш-файл.</li>
 * </ol>
 * <p>
 * Размер эмбедженного гайда ~180 KB — обновляется крайне редко (при апдейте плагина), trade-off
 * между размером config.yml и удобством «один файл — всё» принят.
 */
public class ConfigGuideManager {

    public static final String GUIDE_BEGIN_MARKER = "# === MC-PLUGIN GUIDE BEGIN (auto-managed by ConfigGuideManager, do not edit between markers) ===";
    public static final String GUIDE_END_MARKER = "# === MC-PLUGIN GUIDE END ===";
    /** Баннер «не редактировать» над блоком _meta (в самом низу config.yml). */
    public static final String META_BANNER = "# === INTEGRITY META — НЕ РЕДАКТИРОВАТЬ (auto-managed by ConfigGuideManager) ===";
    public static final String META_KEY = "_meta";
    public static final String META_HASH_KEY = "guide_hash";
    /** Имя JAR-ресурса, откуда берётся актуальный гайд для эмбеда. */
    public static final String GUIDE_RESOURCE = "plugin-guide.txt";
    /** Имя устаревшего файла в dataFolder (миграция с прошлых версий). */
    public static final String LEGACY_GUIDE_FILE = "plugin-guide.txt";
    public static final String LEGACY_HASH_FILE = "plugin-guide.hash";

    private ConfigGuideManager() {}

    /**
     * Инициализирует гайд: мигрирует старые plugin-guide.txt/plugin-guide.hash в config.yml
     * (если остались в dataFolder), затем встраивает/обновляет эмбедженный диапазон.
     */
    public static void init(Main plugin) {
        // 1. Миграция устаревших файлов из dataFolder (если остались)
        migrateLegacyFiles(plugin);

        // 2. Если в config.yml нет маркеров гайда → встроить из JAR-ресурса
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            ConsoleLogger.warn("[Guide] config.yml отсутствует — гайд не будет встроен до первого saveDefaultConfig.");
            return;
        }

        ensureEmbeddedGuide(plugin, configFile);
    }

    /**
     * Встраивает/обновляет эмбедженный гайд в config.yml между маркерами.
     * <p>
     * Логика:
     * <ul>
     *   <li>Если маркеров нет → вставить в начало файла (сразу после warnings/comments header).</li>
     *   <li>Если маркеры есть → скопировать текущий диапазон (raw text), посчитать SHA-256,
     *       сравнить с {@code _meta.guide_hash}; если не совпадает — заменить содержимым из
     *       JAR-ресурса {@code plugin-guide.txt} и обновить хеш.</li>
     * </ul>
     */
    public static void ensureEmbeddedGuide(Main plugin, File configFile) {
        try {
            List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            String guideText = loadGuideTextFromJar(plugin);

            if (guideText == null) {
                ConsoleLogger.warn("[Guide] resource '" + GUIDE_RESOURCE
                        + "' not bundled — embedded guide cannot be updated.");
                return;
            }
            String guideHash = sha256(guideText);

            int beginIdx = indexOfLine(lines, GUIDE_BEGIN_MARKER);
            int endIdx = beginIdx >= 0 ? indexOfLine(lines, GUIDE_END_MARKER) : -1;

            String currentEmbeddedHash;
            if (beginIdx >= 0 && endIdx > beginIdx) {
                // Хеш должен считаться от КАНОНИЧНОЙ формы (raw guide text без префиксов "# "),
                // иначе любой комментарий-префикс вызовет ложный "needs update" на каждом
                // старте. Преобразование делает выделенный package-private helper
                // {@link #reconstructRawGuideText} — единая логика для boot-time check и
                // тестов; String.join("\n", ...) тут критичен: append('\n') per-line
                // добавлял бы лишний перевод строки в конце и хеш бы постоянно отличался.
                currentEmbeddedHash = sha256(reconstructRawGuideText(lines, beginIdx, endIdx));
            } else {
                currentEmbeddedHash = null;
            }

            if (currentEmbeddedHash == null) {
                // Нет эмбеда → встроить в начало файла
                List<String> newLines = new ArrayList<>(lines.size() + 200);
                newLines.add(GUIDE_BEGIN_MARKER);
                for (String gl : guideText.split("\\R", -1)) {
                    if (gl.isEmpty()) {
                        newLines.add("#");
                    } else {
                        newLines.add("# " + gl);
                    }
                }
                newLines.add(GUIDE_END_MARKER);
                newLines.add(""); // пустая строка-разделитель
                newLines.addAll(lines);
                Files.write(configFile.toPath(), newLines, StandardCharsets.UTF_8);
                ConsoleLogger.info("[Guide] Embedded plugin-guide into config.yml ("
                        + newLines.size() + " lines).");
            } else if (!guideHash.equals(currentEmbeddedHash)) {
                // Есть расхождение — заменить содержимое между маркерами, сохранив всё после END_MARKER
                List<String> newLines = new ArrayList<>(lines.size() + 200);
                // 1) префикс до BEGIN_MARKER
                for (int i = 0; i < beginIdx; i++) newLines.add(lines.get(i));
                // 2) BEGIN + новое содержимое гайда + END
                newLines.add(GUIDE_BEGIN_MARKER);
                for (String gl : guideText.split("\\R", -1)) {
                    if (gl.isEmpty()) {
                        newLines.add("#");
                    } else {
                        newLines.add("# " + gl);
                    }
                }
                newLines.add(GUIDE_END_MARKER);
                // 3) суффикс после END_MARKER (старые строки после endIdx)
                newLines.add(""); // blank line после маркеров
                for (int i = endIdx + 1; i < lines.size(); i++) newLines.add(lines.get(i));
                Files.write(configFile.toPath(), newLines, StandardCharsets.UTF_8);
                ConsoleLogger.info("[Guide] Updated embedded plugin-guide in config.yml (was "
                        + (endIdx - beginIdx + 1) + " lines, now " + (newLines.size() - lines.size() + (endIdx - beginIdx + 1)) + ").");
            } else {
                ConsoleLogger.info("[Guide] Embedded plugin-guide is up-to-date (hash " + guideHash.substring(0, 12) + ").");
                return;
            }

            // Обновляем/вставляем _meta: { guide_hash: ... } — через raw text,
            // чтобы гарантировать что блок ОКАЖЕТСЯ в самом низу файла
            // (snakeyaml HashMap order не гарантирован).
            upsertMetaBlockAtEnd(configFile, guideHash);

            // После ЛЮБОЙ записи нужно держать _meta последним — другие могут вызвать
            // plugin.getConfig().save() (ConfigRepairManager / Migration) и snakeyaml
            // переставит ключи HashMap'ом. Чистим ДУБЛИ: оставляем ОДИН блок,
            // и тот что в самом низу (это наш).
            dedupeMetaBlocks(configFile);

            // Перезагрузим Bukkit config, чтобы in-memory модель (getConfig().getString,
            // getString("messages.*") и т.д.) шла от свежего файла.
            try {
                plugin.reloadConfig();
            } catch (Exception e) {
                ConsoleLogger.warn("[Guide] reloadConfig after file rewrite failed: " + e.getMessage());
            }
        } catch (Exception e) {
            ConsoleLogger.error("[Guide] Failed to ensure embedded guide: " + e.getMessage());
            FileLogger.logError("Guide", "ensureEmbeddedGuide failed: " + e.getMessage(), null, e);
        }
    }

    /**
     * Мигрирует устаревшие файлы из dataFolder в config.yml: если есть plugin-guide.txt
     * или plugin-guide.hash — удаляет их (содержимое будет заменено из JAR при
     * {@link #ensureEmbeddedGuide}). Всегда безопасно вызывать — отсутствие файлов OK.
     */
    public static void migrateLegacyFiles(Main plugin) {
        File legacy = new File(plugin.getDataFolder(), LEGACY_GUIDE_FILE);
        File legacyHash = new File(plugin.getDataFolder(), LEGACY_HASH_FILE);
        if (legacy.exists()) {
            if (!legacy.delete()) {
                ConsoleLogger.warn("[Guide] Failed to delete legacy " + LEGACY_GUIDE_FILE);
            } else {
                ConsoleLogger.info("[Guide] Deleted legacy " + LEGACY_GUIDE_FILE);
            }
        }
        if (legacyHash.exists()) {
            if (!legacyHash.delete()) {
                ConsoleLogger.warn("[Guide] Failed to delete legacy " + LEGACY_HASH_FILE);
            } else {
                ConsoleLogger.info("[Guide] Deleted legacy " + LEGACY_HASH_FILE);
            }
        }
    }

    /**
     * Вставляет/обновляет блок {@code _meta: { guide_hash: ... }} В САМОМ НИЗУ config.yml
     * с баннером «НЕ РЕДАКТИРОВАТЬ» выше него. Используем raw text manipulation,
     * а не snakeyaml — порядок ключей в snakeyaml HashMap НЕ гарантирован.
     * <p>
     * Алгоритм:
     * <ol>
     *   <li>Читаем файл построчно.</li>
     *   <li>Если есть баннер + существующий _meta — заменяем старый блок на новый in-place.</li>
     *   <li>Если только баннер без _meta — добавляем _meta после баннера.</li>
     *   <li>Если ничего нет — дописываем баннер + _meta в самый конец файла.</li>
     *   <li>Гарантируем что ПОСЛЕ блока _meta ничего не идёт (последняя непустая строка файла).</li>
     * </ol>
     */
    private static void upsertMetaBlockAtEnd(File configFile, String hash) {
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8));
            int bannerIdx = indexOfLine(lines, META_BANNER);
            int metaIdx = indexOfLine(lines, "_meta:");

            if (bannerIdx >= 0 && metaIdx >= 0) {
                // Оба есть — заменяем содержимое _meta по месту.
                int startMeta = metaIdx;
                int endMeta = startMeta;
                while (endMeta + 1 < lines.size()) {
                    String nxt = lines.get(endMeta + 1);
                    if (nxt.startsWith("  ") || nxt.startsWith("\t")) {
                        endMeta++;
                    } else {
                        break;
                    }
                }
                List<String> newMeta = new ArrayList<>();
                newMeta.add("_meta:");
                newMeta.add("  guide_hash: \"" + hash + "\"");
                List<String> merged = new ArrayList<>(lines.size());
                merged.addAll(lines.subList(0, bannerIdx + 1));
                // Всегда вставляем пустую строку между баннером и _meta для читаемости
                merged.add("");
                merged.addAll(newMeta);
                if (endMeta + 1 < lines.size()) {
                    // после _meta есть «хвост» — оставляем пустую строку-разделитель
                    if (!lines.get(endMeta + 1).isEmpty()) merged.add("");
                    merged.addAll(lines.subList(endMeta + 1, lines.size()));
                }
                lines = merged;
                Files.write(configFile.toPath(), lines, StandardCharsets.UTF_8);
                ConsoleLogger.info("[Guide] Updated _meta.guide_hash in-place (hash " + hash.substring(0, 12) + ").");
            } else if (bannerIdx >= 0) {
                // Баннер есть, _meta нет — дописываем _meta после баннера
                List<String> newLines = new ArrayList<>(lines);
                newLines.add(bannerIdx + 1, ""); // пустая строка
                newLines.add(bannerIdx + 2, "_meta:");
                newLines.add(bannerIdx + 3, "  guide_hash: \"" + hash + "\"");
                Files.write(configFile.toPath(), newLines, StandardCharsets.UTF_8);
                ConsoleLogger.info("[Guide] Inserted _meta after existing banner.");
            } else {
                // Совсем нет — добавляем баннер + _meta в самый конец файла
                List<String> newLines = new ArrayList<>(lines);
                if (!newLines.isEmpty() && !newLines.get(newLines.size() - 1).isEmpty()) {
                    newLines.add("");
                }
                newLines.add(META_BANNER);
                newLines.add("");
                newLines.add("_meta:");
                newLines.add("  guide_hash: \"" + hash + "\"");
                Files.write(configFile.toPath(), newLines, StandardCharsets.UTF_8);
                ConsoleLogger.info("[Guide] Appended _meta block at end of config.yml.");
            }
        } catch (Exception e) {
            ConsoleLogger.error("[Guide] Failed to upsert _meta block: " + e.getMessage());
            FileLogger.logError("Guide", "upsertMetaBlockAtEnd failed: " + e.getMessage(), null, e);
        }
    }

    // ============================================================
    // Утилиты
    // ============================================================

    /** Читает текст JAR-ресурса {@code plugin-guide.txt} или null. */
    private static String loadGuideTextFromJar(Main plugin) {
        try (InputStreamReader reader = new InputStreamReader(
                plugin.getResource(GUIDE_RESOURCE), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) > 0) sb.append(buf, 0, n);
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** SHA-256 hex-стринг. */
    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) hex.append(String.format("%02x", b & 0xFF));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }

    /**
     * Извлекает из {@code lines[beginIdx+1 .. endIdx-1]} (содержимое между
     * {@link #GUIDE_BEGIN_MARKER} и {@link #GUIDE_END_MARKER}) каноничную форму
     * исходного plugin-guide.txt: снимает префикс {@code "# "} или {@code "#"} с
     * каждой строки и соединяет результат через {@code "\n"} (без хвостового
     * перевода строки). Это строгий инверс пути ЗАПИСИ в ensureEmbeddedGuide, и
     * вместе они гарантируют что SHA-256 эмбедженного диапазона совпадает с
     * SHA-256 raw guide text из JAR — иначе гайд бы перезаписывался на каждом
     * старте.
     * <p>
     * Известное ограничение: префикс снимается только ОДИН раз. Если исходный
     * plugin-guide.txt содержит markdown-style «{@code ###SubSubHeader}» (тройной
     * хэш в начале строки), после эмбеда и обратного strip'а получится
     * «{@code ##SubSubHeader}» — один хэш теряется, хеш не совпадает, гайд будет
     * переэмбеживаться. На практике plugin-guide.txt не использует {@code ###}
     * (только {@code ##} разделы), но если потребуется — перейди на префикс
     * «{@code #>}» (YAML-комментарий, не конфликтует с markdown).
     * <p>
     * Помечено package-private (не private) чтобы тесты могли вызывать напрямую
     * (reflection не используется, потому что оба класса в com.ultimateimprovments.config).
     */
    static String reconstructRawGuideText(List<String> lines, int beginIdx, int endIdx) {
        // Без capacity-hint: пустой диапазон (beginIdx+1 >= endIdx) даёт пустую String и не
        // бросает IllegalArgumentException. ArrayList растёт от default 10 через ~9 удвоений
        // до ~5866 (типичный гайд — ~2940 строк) — дешевле, чем поддерживать формулу capacity.
        List<String> stripped = new ArrayList<>();
        for (int i = beginIdx + 1; i < endIdx; i++) {
            String line = lines.get(i);
            if (line.startsWith("# ")) {
                stripped.add(line.substring(2));
            } else if (line.equals("#")) {
                stripped.add("");
            } else if (line.startsWith("#")) {
                stripped.add(line.substring(1));
            } else {
                stripped.add(line);
            }
        }
        return String.join("\n", stripped);
    }

    /**
     * Удаляет ВСЕ вхождения блока {@code _meta:} кроме последнего (это то что
     * {@link #upsertMetaBlockAtEnd} только что записал в самый низ). Защита от
     * случая, когда кто-то снаружи вызвал {@code plugin.getConfig().save()} и
     * snakeyaml HashMap order переставил ключи — после такой перестановки может
     * появиться дубль _meta: блока в середине файла.
     */
    private static void dedupeMetaBlocks(File configFile) {
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8));
            int lastMeta = -1;
            int firstMeta = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().equals("_meta:")) {
                    if (firstMeta < 0) firstMeta = i;
                    lastMeta = i;
                }
            }
            if (firstMeta < 0 || firstMeta == lastMeta) {
                return; // нет или один — ОК
            }
            // Удаляем все ВХОЖДЕНИЯ _meta: кроме последнего (снизу вверх, чтобы индексы не съезжали)
            // + удаляем под-ключи _meta (строки с 2-space-indent)
            List<Integer> toRemove = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                if (i == lastMeta) continue;
                if (lines.get(i).trim().equals("_meta:")) {
                    toRemove.add(i);
                    // Удалить ВСЕ под-ключи сразу после (indented) — пока есть 2-space indent
                    int j = i + 1;
                    while (j < lines.size() && (lines.get(j).startsWith("  ") || lines.get(j).startsWith("\t"))) {
                        toRemove.add(j);
                        j++;
                    }
                }
            }
            // Сортируем по убыванию и удаляем (чтобы индексы не съезжали)
            toRemove.sort((a, b) -> b - a);
            for (int idx : toRemove) {
                lines.remove(idx);
            }
            Files.write(configFile.toPath(), lines, StandardCharsets.UTF_8);
            ConsoleLogger.info("[Guide] Removed " + toRemove.size() + " duplicate _meta line(s) — kept last block only.");
        } catch (Exception e) {
            ConsoleLogger.warn("[Guide] dedupeMetaBlocks failed: " + e.getMessage());
        }
    }

    /**
     * Поиск строки в списке (нормализованно: trim + trim trailing CR для CRLF случае).
     * Это защищает от сюрпризов когда кто-то сохранил файл с CRLF и в константе LF.
     */
    private static int indexOfLine(List<String> lines, String marker) {
        String normMarker = marker.endsWith("\r") ? marker : marker.trim();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            // убираем trailing CR, если есть (CRLF vs LF)
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            if (line.equals(normMarker)) return i;
        }
        return -1;
    }

    /** Утилита для других мест — возвращает текущий хеш гайда (для логов). */
    public static String currentGuideHash(Main plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return null;
        return plugin.getConfig().getString(META_KEY + "." + META_HASH_KEY, null);
    }
}
