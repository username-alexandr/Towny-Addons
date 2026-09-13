package ru.neverland.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

/** Presentation only: callers supply menu icons; stored/player items are never decorated. */
public final class MenuStyle {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final TextColor TEXT = TextColor.color(0xD5DCE7);
    private static final Pattern ACTION = Pattern.compile("(?iu)^(?:[←→›•]\\s*)?(?:лкм|пкм|shift|нажмите|клик|/|назад|закрыть|подтвердить|вернуться).*");
    private MenuStyle() { }

    public static Inventory previousMenu(Player player) {
        Inventory previous = player.getOpenInventory().getTopInventory();
        InventoryHolder holder = previous.getHolder();
        if (holder == null || !holder.getClass().getName().startsWith("ru.neverland.")
                || holder.getClass().getName().contains(".storage.")) return null;
        return previous;
    }
    public static void returnTo(Player player, Inventory previous) {
        if (previous != null && previous.getHolder() != null) {
            try {
                if (JavaPlugin.getProvidingPlugin(previous.getHolder().getClass()).isEnabled()) {
                    player.openInventory(previous);
                    return;
                }
            } catch (IllegalArgumentException | IllegalStateException ignored) { }
        }
        player.closeInventory();
    }

    public static Inventory inventory(Plugin plugin, InventoryHolder holder, int size, String title) {
        return inventory(plugin, holder, size, decode(title));
    }
    public static Inventory inventory(Plugin plugin, InventoryHolder holder, int size, Component title) {
        String color = plugin instanceof JavaPlugin java ? java.getConfig().getString("menu-style.title-color", "#F4F7FC") : "#F4F7FC";
        return Bukkit.createInventory(holder, size, title(title, color));
    }
    public static Component title(Component input, String configuredColor) {
        TextColor color = TextColor.fromHexString(configuredColor == null ? "" : configuredColor);
        if (color == null) color = TextColor.color(0xF4F7FC);
        return Component.text(plain(input).replace('\n', ' ').strip(), color).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false);
    }
    public static String nameLegacy(String name) { return LEGACY.serialize(nameComponent(decode(name))); }
    public static Component nameComponent(Component name) {
        if (plain(name).isBlank()) return Component.text(" ");
        return readable(name).colorIfAbsent(TextColor.color(0xF4F7FC)).decorate(TextDecoration.BOLD);
    }
    public static List<String> loreStrings(List<String> lines) {
        return loreComponents(lines == null ? List.of() : lines.stream().map(MenuStyle::decode).toList()).stream().map(LEGACY::serialize).toList();
    }
    public static List<Component> loreComponents(List<? extends Component> lines) {
        List<Component> result = new ArrayList<>();
        boolean previousAction = false;
        for (Component original : lines == null ? List.<Component>of() : lines) {
            String text = plain(original).strip();
            if (text.isEmpty()) { blank(result); previousAction = false; continue; }
            boolean action = ACTION.matcher(text).matches();
            if (action && !previousAction) blank(result);
            Component line = readable(original).colorIfAbsent(TEXT);
            if (action && !text.startsWith("→") && !text.startsWith("←")) line = Component.text("→ ", TextColor.color(0xFFE699)).append(line);
            if (onlyText(line)) {
                for (String wrapped : wrapLegacy(LEGACY.serialize(line), 44)) result.add(readable(LEGACY.deserialize(wrapped)).colorIfAbsent(TEXT));
            } else result.add(line); // Preserve client-side translations and custom fonts.
            previousAction = action;
        }
        while (!result.isEmpty() && plain(result.get(result.size()-1)).isBlank()) result.remove(result.size()-1);
        return List.copyOf(result);
    }
    private static void blank(List<Component> lines) {
        if (!lines.isEmpty() && !plain(lines.get(lines.size()-1)).isBlank()) lines.add(Component.empty());
    }
    public static Component decode(String input) {
        String text = input == null ? "" : input;
        // Both already translated section codes and configuration ampersand/hex codes occur in the suite.
        return LEGACY.deserialize(LEGACY.serialize(AMPERSAND.deserialize(text.replace('§', '&'))));
    }
    private static String plain(Component input) { return PlainTextComponentSerializer.plainText().serialize(input == null ? Component.empty() : input); }
    private static boolean onlyText(Component input) { return input instanceof TextComponent && input.font() == null && input.children().stream().allMatch(MenuStyle::onlyText); }
    private static Component readable(Component input) {
        if (input == null) return Component.empty();
        TextColor color = input.color();
        if (color != null) {
            double luminance = .2126*color.red()+.7152*color.green()+.0722*color.blue();
            if (luminance < 165) {
                double mix = (165-luminance)/(255-luminance);
                color = TextColor.color((int)Math.round(color.red()+(255-color.red())*mix), (int)Math.round(color.green()+(255-color.green())*mix), (int)Math.round(color.blue()+(255-color.blue())*mix));
            }
        }
        return input.color(color).decoration(TextDecoration.ITALIC, false).children(input.children().stream().map(MenuStyle::readable).toList());
    }
    static List<String> wrapLegacy(String input, int width) {
        List<String> result = new ArrayList<>();
        for (String paragraph : input.split("\\R", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" +")) {
                if (word.isEmpty()) continue;
                int used = ChatColor.stripColor(line.toString()).length(), count = ChatColor.stripColor(word).length();
                if (used > 0 && used+1+count > width) { result.add(line.toString()); String colors=ChatColor.getLastColors(line.toString()); line=new StringBuilder(colors+"  "); }
                else if (used > 0) line.append(' ');
                // IDs/commands stay intact; ordinary prose wraps on word boundaries.
                line.append(word);
            }
            result.add(line.toString());
        }
        return result;
    }
}
