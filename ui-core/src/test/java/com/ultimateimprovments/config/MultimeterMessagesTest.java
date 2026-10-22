package com.ultimateimprovments.config;

import com.moandjiezana.toml.Toml;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.MultimeterApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shared multimeter error introduced with the SHIFT+RMB interaction
 * (see {@code com.ultimateimprovments.util.MultimeterApi}).
 * <p>
 * The generic reader renders
 * {@code <plugin prefix> <dark_gray>[<yellow>METER<dark_gray>] <white>Can't
 * measure parameters of this block.} — the tag word and the body must stay
 * present (and in sync) in both language sections of {@code UI-Other.toml},
 * because the code falls back to hardcoded defaults only when a key is missing.
 */
class MultimeterMessagesTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> multimeterSection(Map<String, Object> root, String messagesKey) {
        Map<String, Object> messages = (Map<String, Object>) root.get(messagesKey);
        assertNotNull(messages, messagesKey + " section missing");
        Map<String, Object> structures = (Map<String, Object>) messages.get("structures");
        assertNotNull(structures, messagesKey + ".structures section missing");
        Map<String, Object> multimeter = (Map<String, Object>) structures.get("multimeter");
        assertNotNull(multimeter, messagesKey + ".structures.multimeter section missing");
        return multimeter;
    }

    @Test
    @DisplayName("Bundled UI-Other.toml has the multimeter [METER] tag and error in BOTH languages")
    void bundledTemplateHasMultimeterMessagesInBothLanguages() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("config/UI-Other.toml")) {
            assertNotNull(in, "bundled config/UI-Other.toml missing from resources");
            Map<String, Object> root = new Toml()
                    .read(new InputStreamReader(in, StandardCharsets.UTF_8)).toMap();

            Map<String, Object> ru = multimeterSection(root, "messages");
            Map<String, Object> en = multimeterSection(root, "messages_en");

            assertEquals("METER", ru.get("prefix"), "ru structures.multimeter.prefix");
            assertEquals("METER", en.get("prefix"), "en structures.multimeter.prefix");

            // The requested EN text is exact; the RU section must translate it.
            assertEquals("Can't measure parameters of this block.", en.get("cant_measure"),
                    "en structures.multimeter.cant_measure");
            String ruError = String.valueOf(ru.get("cant_measure"));
            assertTrue(ruError.contains("измерить") && ruError.contains("параметр"),
                    "ru structures.multimeter.cant_measure should translate the error: " + ruError);
        }
    }

    @Test
    @DisplayName("The composed error renders as '<plugin prefix> [METER] Can't measure parameters of this block.'")
    void composedErrorRendersAsRequested() {
        String plain = MessageUtil.toPlainText(MultimeterApi.cantMeasureMessage());
        assertEquals("[UI] [METER] Can't measure parameters of this block.", plain);
    }
}
