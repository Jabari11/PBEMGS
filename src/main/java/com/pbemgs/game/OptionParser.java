package com.pbemgs.game;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OptionParser {
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
}
