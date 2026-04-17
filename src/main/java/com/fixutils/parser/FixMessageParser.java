package com.fixutils.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class FixMessageParser {
    private FixMessageParser() {
        /* This utility class should not be instantiated */
    }


    /**
     * Parses a raw FIX message string into a list of TagValuePair.
     *
     * @param message   the raw FIX message string
     * @param delimiter the separator string/character (e.g., "|", "^", "~", "\u0001", or custom string)
     * @return Ordered list of parsed tag-value pairs
     */
    public static List<TagValuePair> parse(String message, String delimiter) {
        List<TagValuePair> result = new ArrayList<>();
        if (message == null || message.trim().isEmpty() || delimiter == null || delimiter.isEmpty()) {
            return result;
        }

        // We use Pattern.quote so special regex chars like | or ^ are treated literally
        String[] pairs = message.split(Pattern.quote(delimiter));

        for (String pair : pairs) {
            String trimmedPair = pair.trim();
            if (trimmedPair.isEmpty()) {
                continue;
            }

            int eqPos = trimmedPair.indexOf('=');
            if (eqPos > 0) { // must have '=' and tag cannot be empty
                try {
                    String tagStr = trimmedPair.substring(0, eqPos).trim();
                    String valStr = trimmedPair.substring(eqPos + 1).trim();
                    int tag = Integer.parseInt(tagStr);
                    result.add(new TagValuePair(tag, valStr));
                } catch (NumberFormatException ignored) {
                    // Ignore elements that don't start with a valid integer tag
                }
            }
        }

        return result;
    }
}
