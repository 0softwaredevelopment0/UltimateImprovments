package com.ultimateimprovments.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the chat hardening that prevents the "embedded line" abuse: a player
 * who smuggles a line break into the message (MiniMessage {@code <newline>} /
 * {@code <br>} tags or a raw Unicode line separator) must never get a line that
 * is rendered without the chat prefix. {@link ChatManager} renders every line
 * returned by {@link ChatText#splitLines} through the full format, so the
 * splitter must (a) find every line-break form and (b) cap the line count.
 */
class ChatTextTest {

    @Test
    @DisplayName("A one-line message stays one line")
    void singleLineStaysIntact() {
        assertEquals(List.of("hello world"), ChatText.splitLines("hello world", true, 3));
    }

    @Test
    @DisplayName("<newline> in a MiniMessage message becomes a separate line")
    void miniMessageNewlineTagSplits() {
        assertEquals(
                List.of("[Server] Restart in 5s", "real message"),
                ChatText.splitLines("<newline>[Server] Restart in 5s<newline>real message", true, 3));
        assertEquals(
                List.of("[Server] Restart in 5s", "real message"),
                ChatText.splitLines("[Server] Restart in 5s<NEWLINE/>real message", true, 3));
        assertEquals(
                List.of("a", "b"),
                ChatText.splitLines("a<br>b", true, 3));
        assertEquals(
                List.of("a", "b"),
                ChatText.splitLines("a <br /> b", true, 3));
    }

    @Test
    @DisplayName("Without player MiniMessage the tag stays literal text")
    void newlineTagIsLiteralWhenMiniMessageDisabled() {
        assertEquals(
                List.of("<newline>[Server] Restart in 5s"),
                ChatText.splitLines("<newline>[Server] Restart in 5s", false, 3));
    }

    @Test
    @DisplayName("Raw Unicode line separators always split, both modes")
    void rawLineSeparatorsSplit() {
        for (boolean miniMessage : new boolean[]{true, false}) {
            assertEquals(List.of("a", "b"), ChatText.splitLines("a\nb", miniMessage, 3));
            assertEquals(List.of("a", "b"), ChatText.splitLines("a\r\nb", miniMessage, 3));
            assertEquals(List.of("a", "b"), ChatText.splitLines("a\rb", miniMessage, 3));
            assertEquals(List.of("a", "b"), ChatText.splitLines("a\u2028b", miniMessage, 3));
            assertEquals(List.of("a", "b"), ChatText.splitLines("a\u0085b", miniMessage, 3));
        }
    }

    @Test
    @DisplayName("The line count is capped and the cut is marked with an ellipsis")
    void lineCountIsCapped() {
        List<String> lines = ChatText.splitLines("1\n2\n3\n4\n5", false, 3);
        assertEquals(List.of("1", "2", "3\u2026"), lines);

        // A cap of 1 keeps a single line and marks it truncated
        assertEquals(List.of("1\u2026"), ChatText.splitLines("1\n2", false, 1));

        // Invalid cap falls back to the default instead of dropping the message
        List<String> defaultCapped = ChatText.splitLines("1\n2\n3\n4\n5", false, 0);
        assertEquals(ChatText.DEFAULT_MAX_LINES, defaultCapped.size());
    }

    @Test
    @DisplayName("Blank input still yields one line so the chat format is shown")
    void blankInputYieldsOneEmptyLine() {
        assertEquals(List.of(""), ChatText.splitLines("", true, 3));
        assertEquals(List.of(""), ChatText.splitLines(null, true, 3));
        assertEquals(List.of(""), ChatText.splitLines("   \n\t\n", true, 3));
    }

    @Test
    @DisplayName("Player text is escaped so tags cannot be parsed, backslashes included")
    void escapeNeutralisesTagsAndBackslashes() {
        assertEquals("\\<newline\\>", ChatText.escapeMiniMessage("<newline>"));

        // A player-supplied backslash must not cancel the escape of the next '<'
        String escaped = ChatText.escapeMiniMessage("\\<newline>");
        assertEquals("\\\\\\<newline\\>", escaped);

        // Legacy section codes are removed (they bypass tag escaping)
        assertEquals("red text", ChatText.escapeMiniMessage("\u00A7cred text"));
        assertEquals("", ChatText.escapeMiniMessage(null));
        assertTrue(ChatText.escapeMiniMessage("plain").equals("plain"));
    }
}
