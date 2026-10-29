package com.ultimateimprovments.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the configurable chat abuse limits implemented in {@link ChatText}:
 * total character budget, CAPS share and the "random characters" share.
 * <p>
 * The samples below are the calibration set used when choosing the defaults
 * (50% CAPS, 50% random): normal sentences, long normal words, coordinate
 * lists and URLs must never be flagged, while machine-made soup must be.
 * Several assertions are deliberately written as ranges/booleans because the
 * exact percentage of a heuristic metric is not a stable contract.
 */
class ChatLimitsTest {

    // ========================= CHARACTER BUDGET =========================

    @Test
    @DisplayName("A message that fits the budget is returned unchanged")
    void lengthLimitKeepsShortMessages() {
        assertEquals("hello", ChatText.charsToKeep("hello", 10));
        assertEquals("hello", ChatText.charsToKeep("hello", 5));
        assertEquals("hello", ChatText.charsToKeep("hello", 0));
        assertEquals("hello", ChatText.charsToKeep("hello", -1));
        assertEquals(null, ChatText.charsToKeep(null, 10));
    }

    @Test
    @DisplayName("An oversized message is cut to the budget including the ellipsis")
    void lengthLimitCutsOversizedMessages() {
        String cut = ChatText.charsToKeep("abcdefghij", 5);
        assertEquals("abcd" + ChatText.TRUNCATED_SUFFIX, cut);
        assertEquals(5, cut.length());

        // The budget is counted in code points: one emoji is one character
        String emoji = "\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00";
        assertEquals(6, emoji.codePointCount(0, emoji.length()));
        String cutEmoji = ChatText.charsToKeep(emoji, 3);
        assertEquals(3, cutEmoji.codePointCount(0, cutEmoji.length()));
        assertTrue(cutEmoji.endsWith(ChatText.TRUNCATED_SUFFIX));
    }

    // ========================= CAPS =========================

    @Test
    @DisplayName("CAPS share counts letters only")
    void capsPercentCountsLettersOnly() {
        assertEquals(100, ChatText.capsPercent("ПРИВЕТ ВСЕМ"));
        assertEquals(0, ChatText.capsPercent("привет всем"));
        // "Привет всем" has exactly one capital (П) out of 10 letters
        assertEquals(10, ChatText.capsPercent("Привет всем"));
        assertEquals(20, ChatText.capsPercent("Привет Всем"));
        // Digits, spaces and punctuation never dilute the share
        assertEquals(100, ChatText.capsPercent("ДА! 12345 ???"));
        assertEquals(0, ChatText.capsPercent("12345 ???"));
        assertEquals(0, ChatText.capsPercent(""));
        assertEquals(0, ChatText.capsPercent(null));
    }

    @Test
    @DisplayName("Letter count counts letters only")
    void letterCountCountsLetters() {
        assertEquals(6, ChatText.letterCount("ПРИВЕТ"));
        assertEquals(26, ChatText.letterCount("ПРИВЕТ ВСЕМ ПРИВЕТ ВСЕМ ПРИВЕТ"));
        assertEquals(0, ChatText.letterCount("12345 ???"));
    }

    @Test
    @DisplayName("Character count backs the CAPS ignore-threshold")
    void charCountCountsCodePoints() {
        assertEquals(6, ChatText.charCount("ПРИВЕТ"));
        assertEquals(11, ChatText.charCount("ГО НА АРЕНУ"));
        // One emoji is one character
        assertEquals(2, ChatText.charCount("\uD83D\uDE00\uD83D\uDE00"));
        assertEquals(0, ChatText.charCount(""));
        assertEquals(0, ChatText.charCount(null));
    }

    @Test
    @DisplayName("Cyrillic detection backs the optional no-Cyrillic mode")
    void cyrillicDetection() {
        assertTrue(ChatText.containsCyrillic("привет"));
        assertTrue(ChatText.containsCyrillic("hello привет"));
        assertTrue(ChatText.containsCyrillic("ЁЖИК"));
        assertFalse(ChatText.containsCyrillic("hello world 123"));
        assertFalse(ChatText.containsCyrillic(""));
        assertFalse(ChatText.containsCyrillic(null));
    }

    @Test
    @DisplayName("Non-ASCII detection backs the optional ASCII-only mode")
    void nonAsciiDetection() {
        assertTrue(ChatText.containsNonAscii("привет"));
        assertTrue(ChatText.containsNonAscii("hello world \uD83D\uDE00"));
        assertTrue(ChatText.containsNonAscii("caf\u00E9"));
        assertTrue(ChatText.containsNonAscii("и"));
        assertFalse(ChatText.containsNonAscii("hello world 123 !?"));
        assertFalse(ChatText.containsNonAscii(""));
        assertFalse(ChatText.containsNonAscii(null));
    }

    // ========================= RANDOMNESS =========================

    @Test
    @DisplayName("Normal sentences are never reported as random")
    void normalSentencesAreNotRandom() {
        String[] normal = {
                "Привет, как дела? Давай поиграем вечером.",
                "Скиньте координаты базы плиз",
                "я на спавне, подходи",
                "hello world how are you doing today my friend",
                "Народ, кто знает где найти много алмазов? Хочу построить ферму.",
                "Ищу напарника для похода в Незер, есть желающие?",
                "Го в дискорд, там всё объясню подробнее про механику",
                "Мой ник Steve123, добавь в друзья пожалуйста",
                "Discord: Steve#1234, добавляйтесь",
                "Да, конечно! Я приду в 20:30, жди.",
                "лагерь на -240 64 -1180, приходи",
                "хочу купить участок рядом с морем, почём?",
                "the quick brown fox jumps over the lazy dog",
        };
        for (String s : normal) {
            assertEquals(0, ChatText.randomnessPercent(s), "must not be flagged: " + s);
        }
    }

    @Test
    @DisplayName("Long normal words are not random (entropy alone would flag them)")
    void longNormalWordsAreNotRandom() {
        String[] words = {
                "Суперкалифраджилистикэкспиалидоция",
                "координаты",
                "пожалуйста",
                "добавляйтесь",
                "контрстрайк",
                "автоматизировать",
                "Народ, кто знает где найти много алмазов?",
        };
        for (String s : words) {
            assertEquals(0, ChatText.randomnessPercent(s), "must not be flagged: " + s);
        }
    }

    @Test
    @DisplayName("Links are exempt: they legitimately mix letters, digits and symbols")
    void linksAreExempt() {
        assertEquals(0, ChatText.randomnessPercent("https://youtu.be/dQw4w9WgXcQ"));
        assertEquals(0, ChatText.randomnessPercent("http://example.com/very/long/path?with=params&and=more"));
        assertEquals(0, ChatText.randomnessPercent("www.example.com/a8fJ2kLm9Qp"));
    }

    @Test
    @DisplayName("Machine-made character soup is reported as fully random")
    void randomSoupIsFlagged() {
        String[] soup = {
                "asdkjghqwe123zxcv",
                "aK3#mP9$vQ2!xL7&",
                "f83jd9sk2lqp0zmxn4b7",
                "Xy7#kL2@qW9!zR4$",
                "a8fJ2kLm9QpXz3RtYu7VbN4cD6sW1eGh5KjM0oL",
                "Zx9Qw8Er7Ty6Ui5Op4As3Df2Gh1Jk0LmNzXcVbNm",
                "qwertyuiopasdfghjklzxcvbnm1234567890",
                "йцукенгшщзхъфывапролджэячсмитьбю",
                "deadbeefcafebabe0123456789abcdef",
                "aGVsbG8gd29ybGQgdGhpcyBpcyBhIHRlc3Q=",
        };
        for (String s : soup) {
            assertEquals(100, ChatText.randomnessPercent(s), "must be flagged: " + s);
        }
    }

    @Test
    @DisplayName("Randomness is a share of the message, so mixed spam lands in between")
    void randomnessIsAShareOfTheMessage() {
        int mixed = ChatText.randomnessPercent("купи алмазы тут asdkjghqwe123zxcv");
        assertTrue(mixed >= 50 && mixed < 100, "expected a partial share, got " + mixed);

        // Short tokens are not judged at all
        assertEquals(0, ChatText.randomnessPercent("zxc qwe asd"));
        assertEquals(0, ChatText.randomnessPercent(""));
        assertEquals(0, ChatText.randomnessPercent(null));
    }

    @Test
    @DisplayName("A consonant cluster must be really untypable before it counts")
    void consonantRunThresholdIsSafeForRealWords() {
        // 8+ characters with a 7+ consonant run -> random-looking
        assertEquals(100, ChatText.randomnessPercent("sdfghjkl"));
        assertEquals(100, ChatText.randomnessPercent("бвгджзкл"));
        // Normal long-word clusters stay below the threshold
        assertEquals(0, ChatText.randomnessPercent("контрстрайк"));
        assertEquals(0, ChatText.randomnessPercent("автоматизировать"));
    }

    // ========================= DUPLICATES =========================

    @Test
    @DisplayName("Duplicate similarity ignores case and extra whitespace")
    void similarityIgnoresCaseAndWhitespace() {
        assertEquals(100, ChatText.similarityPercent("Привет  мир", "привет мир"));
        assertEquals(100, ChatText.similarityPercent("  hello   world ", "Hello World"));
        assertEquals(100, ChatText.similarityPercent("", ""));
        assertEquals(0, ChatText.similarityPercent("", "hello"));
        assertEquals(0, ChatText.similarityPercent(null, "hello"));
        assertEquals(0, ChatText.similarityPercent("hello", null));
    }

    @Test
    @DisplayName("Duplicate similarity is Levenshtein-based, emoji = one character")
    void similarityIsLevenshteinBased() {
        // One changed letter out of 10 -> 90%
        assertEquals(90, ChatText.similarityPercent("abcdefghij", "abcdefghiX"));
        // Two changed letters out of 10 -> 80%
        assertEquals(80, ChatText.similarityPercent("abcdefghij", "abcdefghXY"));
        // Unrelated text of the same length -> 0%
        assertEquals(0, ChatText.similarityPercent("abcdefghij", "zyxwvutsrq"));
        // One emoji of three missing -> 67%
        assertEquals(67, ChatText.similarityPercent("\uD83D\uDE00\uD83D\uDE00", "\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00"));
    }

    // ========================= MARKUP =========================

    @Test
    @DisplayName("MiniMessage markup is stripped, the visible text stays")
    void markupIsStripped() {
        String decorated = "<sprite:items:item/experience_bottle>"
                + "<gradient:#e1ff00:#00ff55><bold>toxic</bold></gradient>"
                + "<sprite:items:item/experience_bottle>   "
                + "<gradient:#e1ff00:#00ff55>хм";
        String plain = ChatText.stripMarkup(decorated);
        assertEquals("toxic   хм", plain);
        // Regression: exactly this message was blocked as "random text 100%"
        assertEquals(0, ChatText.randomnessPercent(plain));
        assertEquals(0, ChatText.capsPercent(plain));
    }

    @Test
    @DisplayName("Markup stripping keeps escaped tags, quoted '>' and unmatched '<'")
    void markupStrippingEdgeCases() {
        // An escaped tag renders as literal text on screen, so it stays
        assertEquals("\\<bold\\>", ChatText.stripMarkup("\\<bold\\>"));
        // A '>' inside a quoted argument does not close the tag
        assertEquals("text", ChatText.stripMarkup("<hover:show_text:'a > b'>text"));
        // An unmatched '<' is literal
        assertEquals("3 < 5", ChatText.stripMarkup("3 < 5"));
        // Legacy section codes are stripped together with their letter
        assertEquals("red", ChatText.stripMarkup("\u00A7cred"));
        assertEquals("", ChatText.stripMarkup(""));
        assertEquals("", ChatText.stripMarkup(null));
    }
}
