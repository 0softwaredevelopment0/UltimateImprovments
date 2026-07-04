package com.mcplugin.infrastructure.config;

import com.mcplugin.infrastructure.util.ConsoleLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * Обнаруживает и удаляет дубликаты root-level ключей в YAML-файлах.
 * <p>
 * SnakeYAML (используемый Bukkit) при загрузке файла с дублирующимися ключами
 * берёт ПОСЛЕДНЕЕ значение, игнорируя все предыдущие. Это приводит к тому,
 * что правки пользователя в первой секции теряются, если есть дубликат.
 * <p>
 * Алгоритм:
 * <ol>
 *   <li>Сканирует файл построчно</li>
 *   <li>Находит все root-level ключи (строки без отступа, соответствующие {@code key:})</li>
 *   <li>Если ключ встречается повторно — удаляет ПОСЛЕДНИЙ дубликат целиком
 *       (включая все его под-ключи до следующего root-level ключа)</li>
 *   <li>Сохраняет очищенный файл на диск</li>
 * </ol>
 */
public class YamlDuplicateCleaner {

    private YamlDuplicateCleaner() {}

    /**
     * Сканирует YAML-файл на дубликаты root-level ключей и удаляет все,
     * кроме ПЕРВОГО вхождения.
     *
     * @param file     файл для обработки
     * @param fileName отображаемое имя файла в логах
     * @return true если дубликаты были найдены и удалены
     */
    public static boolean cleanDuplicates(File file, String fileName) {
        if (!file.exists()) return false;

        try {
            List<String> lines = Files.readAllLines(file.toPath());
            List<Section> sections = findRootSections(lines);

            // Ищем дубликаты: группируем по ключу, первый — оставляем, остальные — на удаление
            Map<String, Section> firstOccurrence = new LinkedHashMap<>();
            Set<Integer> linesToRemove = new HashSet<>();
            int duplicateCount = 0;

            for (Section section : sections) {
                if (firstOccurrence.containsKey(section.key)) {
                    duplicateCount++;
                    ConsoleLogger.warn("[YamlCleaner] ⚠ Removing duplicate key '" + section.key
                            + "' in " + fileName + " (line " + (section.start + 1) + ")");
                    for (int i = section.start; i <= section.end; i++) {
                        linesToRemove.add(i);
                    }
                } else {
                    firstOccurrence.put(section.key, section);
                }
            }

            if (linesToRemove.isEmpty()) return false;

            // Собираем очищенный файл (пропускаем удалённые строки)
            List<String> cleaned = new ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                if (!linesToRemove.contains(i)) {
                    cleaned.add(lines.get(i));
                }
            }

            Files.write(file.toPath(), cleaned);

            ConsoleLogger.warn("[YamlCleaner] ✔ Removed " + duplicateCount + " duplicate section(s) ("
                    + linesToRemove.size() + " lines) from " + fileName);
            return true;

        } catch (IOException e) {
            ConsoleLogger.warn("[YamlCleaner] ✗ Failed to clean duplicates in " + fileName + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Находит все root-level секции в YAML-файле.
     * <p>
     * Секция = строка с корневым ключом + все строки до следующего корневого ключа (или конца файла).
     */
    private static List<Section> findRootSections(List<String> lines) {
        // Находим индексы всех root-level ключей
        List<Integer> keyLines = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (isRootKey(lines.get(i))) {
                keyLines.add(i);
            }
        }

        if (keyLines.isEmpty()) return Collections.emptyList();

        // Строим секции: от ключа до следующего ключа (или конца файла)
        List<Section> sections = new ArrayList<>(keyLines.size());
        for (int k = 0; k < keyLines.size(); k++) {
            int start = keyLines.get(k);
            int end = (k + 1 < keyLines.size()) ? keyLines.get(k + 1) - 1 : lines.size() - 1;
            String key = extractKey(lines.get(start));
            sections.add(new Section(key, start, end));
        }

        return sections;
    }

    /**
     * Проверяет, является ли строка root-level YAML ключом:
     * <ul>
     *   <li>Без ведущих пробелов/табуляции</li>
     *   <li>Не комментарий</li>
     *   <li>Не элемент списка ({@code - })</li>
     *   <li>Соответствует шаблону {@code [word_chars]:}</li>
     * </ul>
     */
    private static boolean isRootKey(String line) {
        if (line == null || line.isEmpty()) return false;
        // Не начинается с пробела или табуляции
        char first = line.charAt(0);
        if (first == ' ' || first == '\t') return false;
        // Не комментарий
        String trimmed = line.trim();
        if (trimmed.startsWith("#")) return false;
        // Не элемент списка на root-уровне
        if (trimmed.startsWith("- ")) return false;
        // Должен быть YAML ключом: буквы/цифры/подчёркивание/дефис, затем двоеточие
        return trimmed.matches("^[a-zA-Z_][a-zA-Z0-9_-]*:.*");
    }

    /**
     * Извлекает имя ключа из строки вида {@code key: value}.
     */
    private static String extractKey(String line) {
        String trimmed = line.trim();
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            return trimmed.substring(0, colon);
        }
        return trimmed;
    }

    /**
     * Внутренний класс, описывающий секцию root-level ключа в YAML-файле.
     */
    private static class Section {
        final String key;
        final int start;
        final int end;

        Section(String key, int start, int end) {
            this.key = key;
            this.start = start;
            this.end = end;
        }
    }
}
