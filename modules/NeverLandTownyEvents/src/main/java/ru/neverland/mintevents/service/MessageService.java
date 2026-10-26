package ru.neverland.mintevents.service;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintevents.util.ColorUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MessageService {
    // Old display names only: keep command aliases, permission nodes and PAPI tokens intact.
    private static final java.util.regex.Pattern LEGACY_BRAND = java.util.regex.Pattern.compile(
            "(?i)(?<![/_%])Mint(?:Towny)?Events(?![\\w%.])");
    private final JavaPlugin plugin;
    private YamlConfiguration yaml;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        try (InputStream stream = plugin.getResource("messages.yml")) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
                yaml.setDefaults(defaults);
                if (migrateLegacy(yaml, defaults)) yaml.save(new File(plugin.getDataFolder(), "messages.yml"));
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Не удалось загрузить встроенные сообщения: " + exception.getMessage());
        }
    } }

    public static boolean migrateLegacy(YamlConfiguration yaml, YamlConfiguration defaults) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        boolean changed = false;
        for (String key : yaml.getKeys(true)) {
            Object value = yaml.get(key);
            Object migrated = migrateLegacyValue(value);
            if (!java.util.Objects.equals(value, migrated)) {
                yaml.set(key, migrated);
                changed = true;
            }
        }
        // Upgrade the old default announcements; retain administrator wording.
        String old = yaml.getString("raid-wave-town", "");
        if (old.contains("%count%") && !old.contains("%wave%")) {
            yaml.set("raid-wave-town", old + " &7(волна %wave%/%waves%)"); changed = true;
        }
        return changed;
    } }

    private static Object migrateLegacyValue(Object value) {
        if (value instanceof String text) {
            return LEGACY_BRAND.matcher(text).replaceAll("NeverLand • События");
        }
        if (value instanceof List<?> list) {
            return list.stream().map(MessageService::migrateLegacyValue).toList();
        }
        return value;
    }

    public String raw(String key) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        return template(yaml, key);
    } }

    public static String template(YamlConfiguration yaml, String key) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        String value = yaml.getString(key);
        return value == null ? key : value;
    } }

    public String format(String key, Map<String, ?> placeholders) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        return formatText(raw(key), placeholders, true);
    } }

    public String formatText(String value, Map<String, ?> placeholders, boolean prefix) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        String text = value == null ? "" : value;
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            text = text.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        }
        String base = prefix ? java.util.Objects.toString(yaml.getString("prefix"), "") + text : text;
        return ColorUtil.color(base);
    } }

    public void send(CommandSender sender, String key) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        send(sender, key, Map.of());
    } }

    public void send(CommandSender sender, String key, Map<String, ?> placeholders) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
        sender.sendMessage(format(key, placeholders));
    } }

    public List<String> list(String key) { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {
        List<String> values = yaml.getStringList(key);
        List<String> result = new ArrayList<>(values.size());
        for (String value : values) result.add(ColorUtil.color(value));
        return result;
    } }
}
