package ru.neverland.mintcamps.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern FORMATTING = Pattern.compile("(?i)[&§](?:x(?:[&§][0-9a-f]){6}|[0-9a-fk-or])");
    private static final String VALID_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    private ColorUtil() {
    }

    public static Component component(String input) {
        String value = legacy(input);
        Component result = Component.empty();
        StringBuilder text = new StringBuilder();
        TextColor color = null;
        boolean obfuscated = false;
        boolean bold = false;
        boolean strikethrough = false;
        boolean underlined = false;
        boolean italic = false;

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character != '§' || index + 1 >= value.length()) {
                text.append(character);
                continue;
            }

            char code = Character.toLowerCase(value.charAt(++index));
            if (code == 'x' && index + 12 < value.length()) {
                StringBuilder hex = new StringBuilder(6);
                int cursor = index + 1;
                boolean valid = true;
                for (int digit = 0; digit < 6; digit++) {
                    if (cursor + 1 >= value.length() || value.charAt(cursor) != '§'
                            || Character.digit(value.charAt(cursor + 1), 16) < 0) {
                        valid = false;
                        break;
                    }
                    hex.append(value.charAt(cursor + 1));
                    cursor += 2;
                }
                if (valid) {
                    result = append(result, text, color, obfuscated, bold, strikethrough, underlined, italic);
                    color = TextColor.color(Integer.parseInt(hex.toString(), 16));
                    obfuscated = bold = strikethrough = underlined = italic = false;
                    index = cursor - 1;
                    continue;
                }
            }

            result = append(result, text, color, obfuscated, bold, strikethrough, underlined, italic);
            TextColor named = namedColor(code);
            if (named != null) {
                color = named;
                obfuscated = bold = strikethrough = underlined = italic = false;
                continue;
            }
            switch (code) {
                case 'k' -> obfuscated = true;
                case 'l' -> bold = true;
                case 'm' -> strikethrough = true;
                case 'n' -> underlined = true;
                case 'o' -> italic = true;
                case 'r' -> {
                    color = null;
                    obfuscated = bold = strikethrough = underlined = italic = false;
                }
                default -> {
                    text.append('§').append(code);
                }
            }
        }
        return append(result, text, color, obfuscated, bold, strikethrough, underlined, italic);
    }

    public static String legacy(String input) {
        char[] characters = expandHex(input == null ? "" : input).toCharArray();
        for (int index = 0; index < characters.length - 1; index++) {
            if (characters[index] == '&' && VALID_CODES.indexOf(characters[index + 1]) >= 0) {
                characters[index] = '§';
            }
        }
        return new String(characters);
    }

    public static String plain(String input) {
        return FORMATTING.matcher(expandHex(input == null ? "" : input)).replaceAll("");
    }

    public static String plain(Component component) {
        if (component == null) return "";
        StringBuilder result = new StringBuilder();
        appendPlain(component, result);
        return result.toString();
    }

    public static String expandHex(String input) {
        Matcher matcher = HEX.matcher(input == null ? "" : input);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder("&x");
            for (char character : matcher.group(1).toCharArray()) replacement.append('&').append(character);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static Component append(Component result, StringBuilder text, TextColor color,
                                    boolean obfuscated, boolean bold, boolean strikethrough,
                                    boolean underlined, boolean italic) {
        if (text.isEmpty()) return result;
        Component part = Component.text(text.toString());
        text.setLength(0);
        if (color != null) part = part.color(color);
        if (obfuscated) part = part.decorate(TextDecoration.OBFUSCATED);
        if (bold) part = part.decorate(TextDecoration.BOLD);
        if (strikethrough) part = part.decorate(TextDecoration.STRIKETHROUGH);
        if (underlined) part = part.decorate(TextDecoration.UNDERLINED);
        if (italic) part = part.decorate(TextDecoration.ITALIC);
        return result.append(part);
    }

    private static void appendPlain(Component component, StringBuilder result) {
        if (component instanceof TextComponent text) result.append(text.content());
        for (Component child : component.children()) appendPlain(child, result);
    }

    private static TextColor namedColor(char code) {
        return switch (code) {
            case '0' -> NamedTextColor.BLACK;
            case '1' -> NamedTextColor.DARK_BLUE;
            case '2' -> NamedTextColor.DARK_GREEN;
            case '3' -> NamedTextColor.DARK_AQUA;
            case '4' -> NamedTextColor.DARK_RED;
            case '5' -> NamedTextColor.DARK_PURPLE;
            case '6' -> NamedTextColor.GOLD;
            case '7' -> NamedTextColor.GRAY;
            case '8' -> NamedTextColor.DARK_GRAY;
            case '9' -> NamedTextColor.BLUE;
            case 'a' -> NamedTextColor.GREEN;
            case 'b' -> NamedTextColor.AQUA;
            case 'c' -> NamedTextColor.RED;
            case 'd' -> NamedTextColor.LIGHT_PURPLE;
            case 'e' -> NamedTextColor.YELLOW;
            case 'f' -> NamedTextColor.WHITE;
            default -> null;
        };
    }
}
