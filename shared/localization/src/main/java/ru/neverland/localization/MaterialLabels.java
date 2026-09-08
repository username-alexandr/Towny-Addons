package ru.neverland.localization;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Russian vanilla labels bundled into each add-on; no extra runtime plugin is needed. */
public final class MaterialLabels {
    private static final Map<String, String> BUILTIN = loadBuiltin();
    private final Map<String, String> names = new HashMap<>(BUILTIN);

    public static String canonicalKey(String key) {
        if (key == null) return "";
        String normalized = key.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) normalized = normalized.substring(10);
        normalized = normalized.replace(' ', '_');
        return normalized.equals("CHAIN") ? "IRON_CHAIN" : normalized;
    }

    public static boolean automaticLabel(String key, String label) {
        if (label == null || label.isBlank()) return true;
        String plain = label.replaceAll("(?i)[&§]#[0-9a-f]{6}|[&§][0-9a-fk-orx]", "").trim();
        return canonicalKey(plain).equals(canonicalKey(key));
    }

    public static String canonicalBlockData(String data) {
        if (data == null) return null;
        return data.replaceFirst("^(?:minecraft:)?chain(?=\\[|$)", "minecraft:iron_chain");
    }

    public void reset() { names.clear(); names.putAll(BUILTIN); }

    public void override(String key, String label) {
        String normalized = canonicalKey(key);
        if (label == null || label.isBlank()) return;
        // Old generated English fallbacks must not hide the bundled Russian translation.
        if (names.containsKey(normalized) && automaticLabel(key, label)) return;
        names.put(normalized, label);
    }

    public String name(String key) {
        return names.getOrDefault(canonicalKey(key), "Неизвестный материал");
    }

    public String configuredName(String key, String label) {
        return automaticLabel(key, label) ? name(key) : label;
    }

    public static boolean hasBuiltin(String key) { return BUILTIN.containsKey(canonicalKey(key)); }

    private static Map<String, String> loadBuiltin() {
        Properties properties = new Properties();
        try (var stream = MaterialLabels.class.getResourceAsStream("/neverland-localization/materials-ru.properties")) {
            if (stream == null) throw new IllegalStateException("Missing bundled Russian material dictionary");
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bundled Russian material dictionary", exception);
        }
        Map<String, String> result = new HashMap<>();
        for (String key : properties.stringPropertyNames()) result.put(key, properties.getProperty(key));
        return Map.copyOf(result);
    }
}
