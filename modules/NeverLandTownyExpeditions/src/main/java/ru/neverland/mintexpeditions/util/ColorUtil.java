package ru.neverland.mintexpeditions.util;
import org.bukkit.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public final class ColorUtil {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})"); private ColorUtil() {}
    public static String color(String input) { if (input == null) return ""; Matcher matcher = HEX.matcher(input); StringBuffer out = new StringBuffer(); while (matcher.find()) { StringBuilder value = new StringBuilder("&x"); for (char c : matcher.group(1).toCharArray()) value.append('&').append(c); matcher.appendReplacement(out, Matcher.quoteReplacement(value.toString())); } matcher.appendTail(out); return ChatColor.translateAlternateColorCodes('&', out.toString()); }
    public static String strip(String input) { return ChatColor.stripColor(color(input)); }
}
