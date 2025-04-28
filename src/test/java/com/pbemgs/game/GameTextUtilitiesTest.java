package com.pbemgs.game;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GameTextUtilitiesTest {
    @Test
    public void testBasicColonSyntax() {
        String input = "players:2";
        Map<String, String> result = GameTextUtilities.parseOptions(input);
        assertEquals(1, result.size());
        assertEquals("2", result.get("players"));
    }

    @Test
    public void testBasicEqualSyntax() {
        String input = "board=random";
        Map<String, String> result = GameTextUtilities.parseOptions(input);
        assertEquals(1, result.size());
        assertEquals("random", result.get("board"));
    }

    @Test
    public void testParenthesisSyntax() {
        String input = "difficulty(hard)";
        Map<String, String> result = GameTextUtilities.parseOptions(input);
        assertEquals(1, result.size());
        assertEquals("hard", result.get("difficulty"));
    }

    @Test
    public void testCaseSensitivity() {
        Map<String, String> options = GameTextUtilities.parseOptions("DIFFICULTY(Hard)");
        assertEquals(1, options.size());
        assertEquals("hard", options.get("difficulty")); // Key should be normalized to lowercase
    }

    @Test
    public void testMixedSeparators() {
        String input = "players: 2, board=random\nmode (challenge)";
        Map<String, String> result = GameTextUtilities.parseOptions(input);
        assertEquals(3, result.size());
        assertEquals("2", result.get("players"));
        assertEquals("random", result.get("board"));
        assertEquals("challenge", result.get("mode"));
    }

    @Test
    public void testSpacesBetweenPairs() {
        String input = "size=4 speed:fast theme(blue)";
        Map<String, String> result = GameTextUtilities.parseOptions(input);
        assertEquals(3, result.size());
        assertEquals("4", result.get("size"));
        assertEquals("fast", result.get("speed"));
        assertEquals("blue", result.get("theme"));
    }

    @Test
    public void testExtraSpacesHandling() {
        String input = "  players :  3  , board  =  default  \n mode  (  tournament  ) ";
        Map<String, String> result = GameTextUtilities.parseOptions(input);
        assertEquals(3, result.size());
        assertEquals("3", result.get("players"));
        assertEquals("default", result.get("board"));
        assertEquals("tournament", result.get("mode"));
    }

    @Test
    public void testSpecialCharactersInValues() {
        Map<String, String> options = GameTextUtilities.parseOptions("challenge(high-score-mode)");
        assertEquals(1, options.size());
        assertEquals("high-score-mode", options.get("challenge")); // Values should allow special characters
    }

    @Test
    public void testDuplicateKeys() {
        Map<String, String> options = GameTextUtilities.parseOptions("players=2, players=4");
        assertEquals(1, options.size());
        assertEquals("4", options.get("players")); // Last value should overwrite
    }

    @Test
    public void testEmptyValues() {
        Map<String, String> options = GameTextUtilities.parseOptions("difficulty=");
        assertFalse(options.containsKey("difficulty")); // Should not include empty values
    }

    @Test
    public void testInvalidEntries() {
        String input = "players:2, invalid, board=random";
        Map<String, String> result = GameTextUtilities.parseOptions(input);
        assertEquals(2, result.size());
        assertEquals("2", result.get("players"));
        assertEquals("random", result.get("board"));
        assertFalse(result.containsKey("invalid"));
    }

    @Test
    public void testOnlySeparators() {
        Map<String, String> options = GameTextUtilities.parseOptions(",");
        assertTrue(options.isEmpty()); // A string of just separators should return an empty map

        options = GameTextUtilities.parseOptions("\n");
        assertTrue(options.isEmpty());
    }

    @Test
    public void testInvalidInput() {
        String input = "this is not an option\njust some text";
        Map<String, String> result = GameTextUtilities.parseOptions(input);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testEmptyInput() {
        String input = "";
        Map<String, String> result = GameTextUtilities.parseOptions(input);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testNullInput() {
        String input = null;
        Map<String, String> result = GameTextUtilities.parseOptions(input);
        assertTrue(result.isEmpty());
    }

}
