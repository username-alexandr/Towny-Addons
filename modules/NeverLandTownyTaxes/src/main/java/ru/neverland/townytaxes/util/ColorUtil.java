package ru.neverland.townytaxes.util;

import org.bukkit.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private ColorUtil() {}
    public static String color(String input) {
        if (input == null) return "";
        Matcher matcher = HEX.matcher(input); StringBuffer result = new StringBuffer();
        while (matcher.find()) { StringBuilder replacement = new StringBuilder("&x");
            for (char value : matcher.group(1).toCharArray()) replacement.append('&').append(value);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString())); }
        matcher.appendTail(result); return ChatColor.translateAlternateColorCodes('&', result.toString());
    }
    public static String strip(String input) { return ChatColor.stripColor(color(input)); }
}
