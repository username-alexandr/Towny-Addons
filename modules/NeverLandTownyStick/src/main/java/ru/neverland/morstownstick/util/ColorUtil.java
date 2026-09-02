package ru.neverland.morstownstick.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final String VALID_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    private ColorUtil() { }

    /**
     * Converts &#RRGGBB and classic &c codes straight to Minecraft section codes.
     * No Adventure serializer call is used, which keeps this compatible with
     * different Adventure revisions bundled by Paper and Purpur.
     */
    public static String legacy(String value) {
        String expanded = expandHex(value == null ? "" : value);
        char[] characters = expanded.toCharArray();
        for (int index = 0; index < characters.length - 1; index++) {
            if (characters[index] == '&' && VALID_CODES.indexOf(characters[index + 1]) >= 0)
                characters[index] = '\u00A7';
        }
        return new String(characters);
    }

    private static String expandHex(String input) {
        Matcher matcher = HEX.matcher(input);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder("&x");
            for (char character : matcher.group(1).toCharArray()) replacement.append('&').append(character);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
