package ru.neverland.townybuilds.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private ColorUtil() { }
    public static Component component(String value) {
        String legacy = legacy(value); Component result = Component.empty(); TextColor color = null; Set<TextDecoration> decorations = EnumSet.noneOf(TextDecoration.class); StringBuilder text = new StringBuilder();
        for (int index = 0; index < legacy.length(); index++) {
            char current = legacy.charAt(index); if (current != '§' || index + 1 >= legacy.length()) { text.append(current); continue; }
            result = append(result, text, color, decorations); char code = Character.toLowerCase(legacy.charAt(++index));
            if (code == 'x' && index + 12 < legacy.length()) { StringBuilder hex = new StringBuilder(); boolean valid = true; for (int part = 0; part < 6; part++) { if (legacy.charAt(index + 1) != '§') { valid = false; break; } hex.append(legacy.charAt(index + 2)); index += 2; } if (valid) { try { color = TextColor.color(Integer.parseInt(hex.toString(), 16)); decorations.clear(); continue; } catch (NumberFormatException ignored) { } } }
            TextColor named = named(code); if (named != null) { color = named; decorations.clear(); continue; }
            switch (code) { case 'k' -> decorations.add(TextDecoration.OBFUSCATED); case 'l' -> decorations.add(TextDecoration.BOLD); case 'm' -> decorations.add(TextDecoration.STRIKETHROUGH); case 'n' -> decorations.add(TextDecoration.UNDERLINED); case 'o' -> decorations.add(TextDecoration.ITALIC); case 'r' -> { color = null; decorations.clear(); } default -> { } }
        }
        return append(result, text, color, decorations);
    }
    private static Component append(Component root, StringBuilder text, TextColor color, Set<TextDecoration> decorations) { if (text.length() == 0) return root; Component part = Component.text(text.toString()); text.setLength(0); if (color != null) part = part.color(color); for (TextDecoration decoration : decorations) part = part.decorate(decoration); return root.append(part); }
    private static TextColor named(char code) { return switch (code) { case '0' -> NamedTextColor.BLACK; case '1' -> NamedTextColor.DARK_BLUE; case '2' -> NamedTextColor.DARK_GREEN; case '3' -> NamedTextColor.DARK_AQUA; case '4' -> NamedTextColor.DARK_RED; case '5' -> NamedTextColor.DARK_PURPLE; case '6' -> NamedTextColor.GOLD; case '7' -> NamedTextColor.GRAY; case '8' -> NamedTextColor.DARK_GRAY; case '9' -> NamedTextColor.BLUE; case 'a' -> NamedTextColor.GREEN; case 'b' -> NamedTextColor.AQUA; case 'c' -> NamedTextColor.RED; case 'd' -> NamedTextColor.LIGHT_PURPLE; case 'e' -> NamedTextColor.YELLOW; case 'f' -> NamedTextColor.WHITE; default -> null; }; }
    public static String legacy(String value) { if (value == null) return ""; Matcher matcher = HEX.matcher(value); StringBuffer result = new StringBuffer(); while (matcher.find()) { StringBuilder replacement = new StringBuilder("&x"); for (char character : matcher.group(1).toCharArray()) replacement.append('&').append(character); matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString())); } matcher.appendTail(result); return ChatColor.translateAlternateColorCodes('&', result.toString()); }
    public static String plain(String value) { return ChatColor.stripColor(legacy(value)); }
    public static String plain(Component value) { StringBuilder result = new StringBuilder(); appendPlain(value, result); return result.toString(); }
    private static void appendPlain(Component component, StringBuilder output) { if (component instanceof TextComponent text) output.append(text.content()); for (Component child : component.children()) appendPlain(child, output); }
}
