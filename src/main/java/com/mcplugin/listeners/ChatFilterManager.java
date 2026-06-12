package com.mcplugin.listeners;

import com.mcplugin.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Фильтр чата (защита от матов).
 *
 * Проверяет сообщения игроков на наличие запрещённых слов из конфига.
 * Поддерживает wildcard-шаблоны:
 *   - "блять"     → точное совпадение слова (целиком, регистронезависимо)
 *   - "блять*"    → слово начинается с "блять" (после могут быть любые символы)
 *   - "*блять"    → слово заканчивается на "блять" (до могут быть любые символы)
 *   - "*блять*"   → слово содержится в сообщении
 *
 * Использует Unicode-свойства (\p{L}) для корректной обработки кириллицы.
 * Регистронезависимость — полная.
 * При обнаружении: сообщение отменяется, игроку отправляется предупреждение.
 */
public class ChatFilterManager implements Listener {

    private static ChatFilterManager instance;

    private boolean enabled;
    private String warningMessage;
    private List<Pattern> compiledPatterns;
    private List<String> patternSources;   // исходное правило из конфига (для отображения)

    // Для подсветки запрещённых слов в сообщении
    private List<String> highlightWords;
    private List<Pattern> highlightPatterns;

    public ChatFilterManager() {
        instance = this;
        reloadConfig();
    }

    public static void reloadConfigStatic() {
        if (instance != null) {
            instance.reloadConfig();
        }
    }

    /**
     * Загружает/перезагружает настройки фильтра из config.yml.
     *
     * Загружает два источника шаблонов:
     *   1. {@code chat_filter.words} — простые слова с wildcard (*): "блять", "*хуй*", "пидор*"
     *   2. {@code chat_filter.regex_patterns} — сложные Java regex-выражения: "\\b(?:блять|хуй)\\p{L}*"
     */
    public void reloadConfig() {
        FileConfiguration cfg = Main.getInstance().getConfig();

        enabled = cfg.getBoolean("chat_filter.enabled", true);
        warningMessage = cfg.getString("chat_filter.message", "");

        compiledPatterns = new ArrayList<>();
        patternSources = new ArrayList<>();
        highlightWords = new ArrayList<>();
        highlightPatterns = new ArrayList<>();

        // 1. Загружаем простые слова с wildcard
        List<String> rawWords = cfg.getStringList("chat_filter.words");
        if (rawWords == null) {
            rawWords = new ArrayList<>();
        }
        for (String raw : rawWords) {
            Pattern p = compileWordPattern(raw);
            if (p != null) {
                compiledPatterns.add(p);
                patternSources.add("§aслово§f: §c" + raw.trim() + "§f");
                // Сохраняем чистое слово для подсветки
                String clean = raw.trim().replace("*", "");
                if (!clean.isEmpty()) {
                    highlightWords.add(clean);
                }
            }
        }

        // 2. Загружаем сложные regex-выражения
        List<String> rawRegexes = cfg.getStringList("chat_filter.regex_patterns");
        if (rawRegexes != null) {
            for (String raw : rawRegexes) {
                Pattern p = compileRegexPattern(raw);
                if (p != null) {
                    compiledPatterns.add(p);
                    patternSources.add("§aregex§f: §7" + raw.trim() + "§f");
                    // Создаём «находилку» без .* по краям для точного определения позиций
                    Pattern finder = compileHighlightFinder(raw);
                    if (finder != null) {
                        highlightPatterns.add(finder);
                    }
                }
            }
        }

        Main.getInstance().getLogger().info("[CHAT-FILTER] Loaded " + compiledPatterns.size() + " pattern(s) ("
                + rawWords.size() + " word(s) + "
                + (rawRegexes != null ? rawRegexes.size() : 0) + " regex(es)).");
    }

    /**
     * Превращает строку с опциональным wildcard (*) в регулярное выражение.
     *
     * Использует Unicode-свойство \p{L} для границ слов, чтобы корректно
     * обрабатывать кириллицу (в отличие от \b, которое работает только с [a-zA-Z0-9_]).
     *
     * Примеры:
     *   "блять"     → (?i).*(?<!\p{L})блять(?!\p{L}).*
     *   "блять*"    → (?i).*(?<!\p{L})блять.*
     *   "*блять"    → (?i).*блять(?!\p{L}).*
     *   "*блять*"   → (?i).*блять.*
     */
    private Pattern compileWordPattern(String raw) {
        if (raw == null || raw.isEmpty()) return null;

        String word = raw.trim();
        boolean startsWithStar = word.startsWith("*");
        boolean endsWithStar = word.endsWith("*");

        // Убираем wildcard-символы для получения чистого слова
        if (startsWithStar) word = word.substring(1);
        if (endsWithStar) word = word.substring(0, word.length() - (endsWithStar ? 1 : 0));

        if (word.isEmpty()) return null;

        // Экранируем специальные символы regex
        String escaped = Pattern.quote(word);

        StringBuilder regex = new StringBuilder("(?i).*");

        if (!startsWithStar) {
            // Начало слова: перед словом не должно быть буквы (Unicode-aware)
            regex.append("(?<!\\p{L})");
        }
        regex.append(escaped);
        if (!endsWithStar) {
            // Конец слова: после слова не должно быть буквы (Unicode-aware)
            regex.append("(?!\\p{L})");
        }

        regex.append(".*");

        try {
            return Pattern.compile(regex.toString());
        } catch (PatternSyntaxException e) {
            Main.getInstance().getLogger().warning("[CHAT-FILTER] Invalid pattern: " + raw + " (" + e.getMessage() + ")");
            return null;
        }
    }

    /**
     * Компилирует строку как готовое Java regex-выражение.
     *
     * Автоматически добавляет флаг (?i) для регистронезависимости,
     * но если автор regex уже указал (?i) вручную — флаг не дублируется.
     *
     * Важно: паттерн применяется к ВСЕМУ сообщению через Matcher.matches(),
     * поэтому для поиска подстроки используйте .* в начале и конце:
     *   ".*\\b(?:блять|хуй)\\b.*"
     * или просто добавьте .* в regex вручную.
     */
    private Pattern compileRegexPattern(String raw) {
        if (raw == null || raw.isEmpty()) return null;

        String regex = raw.trim();

        // Добавляем (?i) в начало, если его ещё нет
        if (!regex.startsWith("(?i)") && !regex.startsWith("(?-i)")) {
            regex = "(?i)" + regex;
        }

        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            Main.getInstance().getLogger().warning("[CHAT-FILTER] Invalid regex: " + raw + " (" + e.getMessage() + ")");
            return null;
        }
    }

    /**
     * Создаёт облегчённый паттерн для {@code find()}, без «.*» по краям,
     * чтобы точно определить позиции совпадения в сообщении.
     */
    private Pattern compileHighlightFinder(String raw) {
        if (raw == null || raw.isEmpty()) return null;

        String stripped = raw.trim();

        // Убираем ведущий .* и хвостовой .*
        if (stripped.startsWith(".*")) stripped = stripped.substring(2);
        if (stripped.endsWith(".*")) stripped = stripped.substring(0, stripped.length() - 2);

        if (stripped.isEmpty()) return null;

        // Добавляем (?i) если ещё нет
        if (!stripped.startsWith("(?i)") && !stripped.startsWith("(?-i)")) {
            stripped = "(?i)" + stripped;
        }

        try {
            return Pattern.compile(stripped);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    /**
     * Строит строку с подсветкой запрещённых участков красным (§c…§r).
     * Находит все вхождения слов из highlightWords и совпадения highlightPatterns,
     * объединяет перекрывающиеся диапазоны и вставляет цветовые коды.
     */
    private String highlightBadWords(String message) {
        if (message == null || message.isEmpty()) return message;

        List<int[]> ranges = new ArrayList<>();
        String lowerMsg = message.toLowerCase();

        // 1. Ищем вхождения простых слов
        for (String word : highlightWords) {
            String lowerWord = word.toLowerCase();
            int idx = 0;
            while ((idx = lowerMsg.indexOf(lowerWord, idx)) != -1) {
                ranges.add(new int[]{idx, idx + word.length()});
                idx += word.length();
            }
        }

        // 2. Ищем совпадения regex-паттернов через find()
        for (Pattern p : highlightPatterns) {
            Matcher m = p.matcher(message);
            while (m.find()) {
                ranges.add(new int[]{m.start(), m.end()});
            }
        }

        if (ranges.isEmpty()) {
            return message;
        }

        // Сортируем по начальной позиции
        ranges.sort((a, b) -> Integer.compare(a[0], b[0]));

        // Объединяем перекрывающиеся диапазоны
        List<int[]> merged = new ArrayList<>();
        for (int[] r : ranges) {
            if (merged.isEmpty()) {
                merged.add(new int[]{r[0], r[1]});
            } else {
                int[] last = merged.get(merged.size() - 1);
                if (r[0] <= last[1]) {
                    last[1] = Math.max(last[1], r[1]);
                } else {
                    merged.add(new int[]{r[0], r[1]});
                }
            }
        }

        // Строим итоговую строку с §c…§r
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        for (int[] r : merged) {
            sb.append(message, lastEnd, r[0]);
            sb.append("§c");
            sb.append(message, r[0], r[1]);
            sb.append("§r");
            lastEnd = r[1];
        }
        sb.append(message.substring(lastEnd));

        return sb.toString();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();

        // Если у игрока есть право на байпасс — пропускаем
        if (player.hasPermission("mcplugin.chat.filter.bypass")) return;

        String message = event.getMessage();

        for (int i = 0; i < compiledPatterns.size(); i++) {
            Pattern pattern = compiledPatterns.get(i);
            if (pattern.matcher(message).matches()) {
                event.setCancelled(true);

                String source = patternSources.get(i);
                String plainSource = source.replaceAll("§[0-9a-fklmnor]", ""); // убираем цвет для консоли

                // Сообщение с подсветкой
                String highlighted = highlightBadWords(message);

                // Лог в консоль (с подсвеченным сообщением)
                Main.getInstance().getLogger().warning("[CHAT-FILTER] " + player.getName()
                        + " нарушил правило «" + plainSource + "»: " + highlighted);

                // Сообщение игроку
                player.sendMessage(warningMessage);
                player.sendMessage("§7→ §f" + highlighted);
                player.sendMessage("§7└ §fСовпадение: " + source);
                return;
            }
        }
    }
}
