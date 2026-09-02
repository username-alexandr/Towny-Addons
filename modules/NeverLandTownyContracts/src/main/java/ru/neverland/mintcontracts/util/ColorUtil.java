package ru.neverland.mintcontracts.util;

import org.bukkit.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private ColorUtil() {}
    public static String color(String input) {
        if (input == null) return "";
        Matcher matcher = HEX.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            StringBuilder value = new StringBuilder("§x");
            for (char digit : matcher.group(1).toCharArray()) value.append('§').append(digit);
            matcher.appendReplacement(output, Matcher.quoteReplacement(value.toString()));
        }
        matcher.appendTail(output);
        return ChatColor.translateAlternateColorCodes('&', output.toString());
    }
    public static String strip(String input) { return ChatColor.stripColor(color(input)); }
}
