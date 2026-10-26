package com.ultimateimprovments.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreboardNickFilterTest {

    private static final ScoreboardNickFilter.Mode BLACK = ScoreboardNickFilter.Mode.BLACKLIST;
    private static final ScoreboardNickFilter.Mode WHITE = ScoreboardNickFilter.Mode.WHITELIST;

    private static ScoreboardNickFilter.Result blacklist(String text, List<String> wildcards, List<String> regexes) {
        return ScoreboardNickFilter.check(text, BLACK, wildcards, regexes);
    }

    private static ScoreboardNickFilter.Result whitelist(String text, List<String> wildcards, List<String> regexes) {
        return ScoreboardNickFilter.check(text, WHITE, wildcards, regexes);
    }

    @Nested
    @DisplayName("wildcard rules")
    class Wildcards {

        @Test
        @DisplayName("a plain word blocks a standalone word only")
        void plainWordIsStandalone() {
            assertFalse(blacklist("my test nick", List.of("test"), List.of()).allowed());
            assertFalse(blacklist("test", List.of("test"), List.of()).allowed());
            assertFalse(blacklist("test!", List.of("test"), List.of()).allowed());
            assertTrue(blacklist("testing", List.of("test"), List.of()).allowed());
            assertTrue(blacklist("protest", List.of("test"), List.of()).allowed());
        }

        @Test
        @DisplayName("rules are case-insensitive")
        void caseInsensitive() {
            assertFalse(blacklist("My TeSt Nick", List.of("test"), List.of()).allowed());
            assertFalse(blacklist("NICK", List.of(), List.of("ni.*")).allowed());
        }

        @Test
        @DisplayName("trailing star anchors at the start of the text")
        void trailingStar() {
            assertFalse(blacklist("admin123", List.of("admin*"), List.of()).allowed());
            assertTrue(blacklist("superadmin", List.of("admin*"), List.of()).allowed());
        }

        @Test
        @DisplayName("leading star anchors at the end of the text")
        void leadingStar() {
            assertFalse(blacklist("superadmin", List.of("*admin"), List.of()).allowed());
            assertTrue(blacklist("admin123", List.of("*admin"), List.of()).allowed());
            assertTrue(blacklist("@dmin", List.of("*admin"), List.of()).allowed());
        }

        @Test
        @DisplayName("two stars match anywhere, even inside a word")
        void bothStars() {
            assertFalse(blacklist("badass", List.of("*ass*"), List.of()).allowed());
            assertFalse(blacklist("x", List.of("*x*"), List.of()).allowed());
            assertTrue(blacklist("nice", List.of("*ass*"), List.of()).allowed());
        }

        @Test
        @DisplayName("an inner star behaves like a glob")
        void innerStar() {
            assertFalse(blacklist("tist", List.of("t*st"), List.of()).allowed());
            assertFalse(blacklist("teeeest", List.of("t*st"), List.of()).allowed());
            assertTrue(blacklist("test test!", List.of("t*st"), List.of()).allowed());
        }

        @Test
        @DisplayName("blank and star-only rules are ignored")
        void blankRules() {
            assertTrue(blacklist("anything", List.of("", "  ", "*"), List.of()).allowed());
            assertNull(blacklist("anything", List.of(""), List.of()).hit());
        }
    }

    @Nested
    @DisplayName("regex rules")
    class Regexes {

        @Test
        @DisplayName("a matching regex blocks in blacklist mode and reports its rule")
        void regexBlocks() {
            ScoreboardNickFilter.Result result = blacklist("nick\u200bname", List.of(), List.of("\\p{Cf}"));
            assertFalse(result.allowed());
            assertEquals(ScoreboardNickFilter.SECTION_REGEX, result.hit().section());
            assertEquals("\\p{Cf}", result.hit().rule());
        }

        @Test
        @DisplayName("an uncompilable regex is ignored instead of locking everyone out")
        void invalidRegexIgnored() {
            assertTrue(blacklist("nick", List.of(), List.of("([a-z")).allowed());
            assertTrue(whitelist("nick", List.of(), List.of("([a-z")).allowed());
            assertTrue(ScoreboardNickFilter.isInvalid("([a-z"));
        }
    }

    @Nested
    @DisplayName("modes")
    class Modes {

        @Test
        @DisplayName("blacklist blocks on ANY match and reports that rule")
        void blacklistAnyMatch() {
            ScoreboardNickFilter.Result result = blacklist("cool nick", List.of("cool"), List.of("^n.*$"));
            assertFalse(result.allowed());
            assertEquals(ScoreboardNickFilter.SECTION_WILDCARD, result.hit().section());
            assertEquals("cool", result.hit().rule());
        }

        @Test
        @DisplayName("blacklist allows a text matching no rule")
        void blacklistAllows() {
            ScoreboardNickFilter.Result result = blacklist("nice nick", List.of("bad"), List.of("^x"));
            assertTrue(result.allowed());
            assertNull(result.hit());
        }

        @Test
        @DisplayName("whitelist allows only a text matching EVERY rule")
        void whitelistNeedsAllRules() {
            assertTrue(whitelist("abc123", List.of("abc*"), List.of("^[a-z0-9]+$")).allowed());
            ScoreboardNickFilter.Result failed = whitelist("abc 123", List.of("abc*"), List.of("^[a-z0-9]+$"));
            assertFalse(failed.allowed());
            assertEquals(ScoreboardNickFilter.SECTION_REGEX, failed.hit().section());
            assertEquals("^[a-z0-9]+$", failed.hit().rule());
        }

        @Test
        @DisplayName("whitelist with an empty rule set allows everything")
        void whitelistEmptyRules() {
            assertTrue(whitelist("anything at all", List.of(), List.of()).allowed());
        }

        @Test
        @DisplayName("whitelist reports the wildcard rule that failed first")
        void whitelistWildcardFailure() {
            ScoreboardNickFilter.Result failed = whitelist("zzz", List.of("abc*"), List.of());
            assertFalse(failed.allowed());
            assertEquals(ScoreboardNickFilter.SECTION_WILDCARD, failed.hit().section());
            assertEquals("abc*", failed.hit().rule());
        }

        @Test
        @DisplayName("a null mode falls back to blacklist")
        void nullMode() {
            assertFalse(ScoreboardNickFilter.check("bad", null, List.of("bad"), List.of()).allowed());
        }
    }
}
