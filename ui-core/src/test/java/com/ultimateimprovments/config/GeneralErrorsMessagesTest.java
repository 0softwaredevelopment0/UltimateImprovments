package com.ultimateimprovments.config;

import com.moandjiezana.toml.Toml;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the default command-error texts (CommandErrors 001-005/011/018).
 * <p>
 * Regression: the TOML template used to hold {@code error_001}-style keys
 * while the code reads {@code general.errors.001}, so the configurable texts
 * never loaded (bare TOML keys cannot start with a digit — the template keys
 * are quoted). Also covers error 018 (module disabled) in both language
 * sections and toml4j's acceptance of bare numeric keys written by the
 * repair path ({@code CommentedTomlTemplate}).
 */
class GeneralErrorsMessagesTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> errorsSection(Map<String, Object> root, String messagesKey) {
        Map<String, Object> messages = (Map<String, Object>) root.get(messagesKey);
        assertNotNull(messages, messagesKey + " section missing");
        Map<String, Object> general = (Map<String, Object>) messages.get("general");
        assertNotNull(general, messagesKey + ".general section missing");
        Map<String, Object> errors = (Map<String, Object>) general.get("errors");
        assertNotNull(errors, messagesKey + ".general.errors section missing");
        return errors;
    }

    @Test
    @DisplayName("Bundled UI-Core.toml has the error texts (incl. 018) in BOTH language sections")
    void bundledTemplateHasErrorTextsInBothLanguages() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("config/UI-Core.toml")) {
            assertNotNull(in, "bundled config/UI-Core.toml missing from resources");
            Map<String, Object> root = new Toml()
                    .read(new InputStreamReader(in, StandardCharsets.UTF_8)).toMap();

            Map<String, Object> ru = errorsSection(root, "messages");
            Map<String, Object> en = errorsSection(root, "messages_en");

            // Quoted numeric keys for the default codes (001-005, 011)
            for (String code : new String[]{"001", "002", "003", "004", "005", "011"}) {
                assertNotNull(ru.get(code), "messages.general.errors." + code + " (ru) missing");
                assertNotNull(en.get(code), "messages_en.general.errors." + code + " (en) missing");
            }

            // Error 018 — module disabled — plus its %module% clause
            assertNotNull(ru.get("018"), "messages.general.errors.018 (ru) missing");
            assertNotNull(ru.get("module"), "messages.general.errors.module (ru) missing");
            assertNotNull(en.get("018"), "messages_en.general.errors.018 (en) missing");
            assertNotNull(en.get("module"), "messages_en.general.errors.module (en) missing");

            assertTrue(((String) ru.get("018")).contains("модулю"),
                    "ru 018 text should mention the disabled module");
            assertTrue(((String) en.get("018")).contains("module"),
                    "en 018 text should mention the disabled module");
            assertTrue(((String) ru.get("module")).contains("%module%"));
            assertTrue(((String) en.get("module")).contains("%module%"));
        }
    }

    @Test
    @DisplayName("toml4j parses bare numeric keys (repair path emits them unquoted)")
    void toml4jParsesBareNumericKeys() {
        Toml parsed = new Toml().read("[messages.general.errors]\n018 = \"module disabled\"\n");
        assertEquals("module disabled", parsed.getString("messages.general.errors.018"));
    }
}
