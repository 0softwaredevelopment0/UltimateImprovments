package com.ultimateimprovments.mechanics.security.auth;

import com.ultimateimprovments.config.MessagesManager;

import java.util.List;
import java.util.Locale;

/**
 * Password requirements shared by every path that sets a password: registration
 * (auth dialog), self password change (dialog) and admin resets
 * ({@code /ui auth chgpass} / the admin dialog).
 * <p>
 * Configured in {@code [auth.password]} of {@code UI-Auth.toml}:
 * <ul>
 *   <li>{@code min_length} / {@code max_length} — character count;</li>
 *   <li>{@code require_digit} — at least one 0-9;</li>
 *   <li>{@code require_case} — at least one lowercase AND one uppercase English letter;</li>
 *   <li>{@code require_special} — at least one character from {@link #SPECIAL_CHARS};</li>
 *   <li>{@code block_common} + {@code common_passwords} — reject well-known passwords.</li>
 * </ul>
 * Only English letters count for {@code require_case}, so Cyrillic (or any other
 * script) cannot satisfy the mixed-case rule by itself.
 */
public final class PasswordPolicy {

    /** ASCII special characters accepted as a "special character". */
    private static final String SPECIAL_CHARS = "!@#$%^&*()-_=+[]{};:'\",.<>/?\\|`~";

    private PasswordPolicy() {}

    /**
     * Validates a password against the configured policy.
     *
     * @param password the candidate password
     * @return {@code null} when the password is acceptable, otherwise a ready-to-send
     *         MiniMessage error text
     */
    public static String validate(String password) {
        if (password == null || password.isEmpty()) {
            return msg("auth.messages.password_empty",
                    "<red>❌ The password must not be empty.</red>");
        }

        int min = AuthConfig.getMinPasswordLength();
        int max = AuthConfig.getMaxPasswordLength();

        if (password.length() < min) {
            return msg("auth.messages.password_too_short",
                    "<red>❌ Password must be at least </red><yellow>%min%</yellow><red> characters!</red>")
                    .replace("%min%", String.valueOf(min));
        }
        if (password.length() > max) {
            return msg("auth.messages.password_too_long",
                    "<red>❌ Password must not exceed </red><yellow>%max%</yellow><red> characters!</red>")
                    .replace("%max%", String.valueOf(max));
        }
        if (AuthConfig.isRequireDigit() && !hasDigit(password)) {
            return msg("auth.messages.password_need_digit",
                    "<red>❌ The password must contain at least one digit (0-9).</red>");
        }
        if (AuthConfig.isRequireMixedCase() && !(hasLower(password) && hasUpper(password))) {
            return msg("auth.messages.password_need_case",
                    "<red>❌ The password must contain a lowercase and an uppercase English letter (a-z, A-Z).</red>");
        }
        if (AuthConfig.isRequireSpecialChar() && !hasSpecial(password)) {
            return msg("auth.messages.password_need_special",
                    "<red>❌ The password must contain a special character (!, @, #, $, %, &, *, ...).</red>");
        }
        if (AuthConfig.isBlockCommonPasswords() && isCommon(password)) {
            return msg("auth.messages.password_common",
                    "<red>❌ This password is too common — choose another one.</red>");
        }
        return null;
    }

    /** Convenience check without building an error message. */
    public static boolean isAcceptable(String password) {
        return validate(password) == null;
    }

    // =========================
    // CHARACTER CLASSES
    // =========================

    private static boolean hasDigit(String password) {
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (c >= '0' && c <= '9') return true;
        }
        return false;
    }

    private static boolean hasLower(String password) {
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (c >= 'a' && c <= 'z') return true;
        }
        return false;
    }

    private static boolean hasUpper(String password) {
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (c >= 'A' && c <= 'Z') return true;
        }
        return false;
    }

    private static boolean hasSpecial(String password) {
        for (int i = 0; i < password.length(); i++) {
            if (SPECIAL_CHARS.indexOf(password.charAt(i)) >= 0) return true;
        }
        return false;
    }

    private static boolean isCommon(String password) {
        String candidate = password.trim().toLowerCase(Locale.ROOT);
        List<String> common = AuthConfig.getCommonPasswords();
        return common.contains(candidate);
    }

    private static String msg(String key, String fallback) {
        return MessagesManager.getString(key, fallback);
    }
}
