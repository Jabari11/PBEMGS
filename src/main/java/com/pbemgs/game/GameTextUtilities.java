package com.pbemgs.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GameTextUtilities {
    // Regular expression to match key-value pairs (supports ':', '=', and '()' formats)
    private static final Pattern OPTION_PATTERN = Pattern.compile(
            "\\s*(\\w+)\\s*[:=]\\s*(\\S+)\\s*|\\s*(\\w+)\\s*\\(([^)]+)\\)\\s*"
    );

    public static Map<String, String> parseOptions(String input) {
        Map<String, String> options = new HashMap<>();
        if (input == null || input.isEmpty()) {
            return options;
        }

        // Normalize separators to make parsing easier
        String normalizedInput = input.replaceAll("[\n,]", " ");

        Matcher matcher = OPTION_PATTERN.matcher(normalizedInput);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                String key = matcher.group(1).trim();
                String value = matcher.group(2).trim();
                options.put(key.toLowerCase(), value.toLowerCase());
            } else if (matcher.group(3) != null) {
                String key = matcher.group(3).trim();
                String value = matcher.group(4).trim();
                options.put(key.toLowerCase(), value.toLowerCase());
            }
        }

        return options;
    }

    /**
     *  Tokenize a single line to a list of strings, per the given regex.
     */
    public static List<String> tokenizeLine(String line, String regex) {
        if (line == null) {
            return new ArrayList<>();
        }

        // Normalize all Unicode whitespace to regular spaces
        line = line.replaceAll("\\p{IsWhite_Space}", " ");
        line = line.trim(); // Remove leading/trailing spaces

        if (line.isEmpty()) {
            return new ArrayList<>();
        }

        // Split on the provided regex (adjusted for normalized spaces)
        String[] rawTokens = line.split(regex);
        return Arrays.stream(rawTokens)
                .map(String::trim) // Ensure no residual whitespace
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     *  Default tokenizer - split on any number of spaces or dashes.
     */
    public static List<String> tokenizeLine(String line) {
        return tokenizeLine(line, "[ -]+"); // Default: one or more spaces or dash
    }
}
