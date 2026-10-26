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

    public static String canonicalKey(String key) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        if (key == null) return "";
        String normalized = key.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) normalized = normalized.substring(10);
        normalized = normalized.replace(' ', '_');
        return normalized.equals("CHAIN") ? "IRON_CHAIN" : normalized;
    } }

    public static boolean automaticLabel(String key, String label) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if (label == null || label.isBlank()) return true;
        String plain = label.replaceAll("(?i)[&§]#[0-9a-f]{6}|[&§][0-9a-fk-orx]", "").trim();
        return canonicalKey(plain).equals(canonicalKey(key));
    } }

    public static String canonicalBlockData(String data) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        if (data == null) return null;
        return data.replaceFirst("^(?:minecraft:)?chain(?=\\[|$)", "minecraft:iron_chain");
    } }

    public void reset() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { names.clear(); names.putAll(BUILTIN); } }

    public void override(String key, String label) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        String normalized = canonicalKey(key);
        if (label == null || label.isBlank()) return;
        // Old generated English fallbacks must not hide the bundled Russian translation.
        if (names.containsKey(normalized) && automaticLabel(key, label)) return;
        names.put(normalized, label);
    } }

    public String name(String key) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        return names.getOrDefault(canonicalKey(key), "Неизвестный материал");
    } }

    public String configuredName(String key, String label) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        return automaticLabel(key, label) ? name(key) : label;
    } }

    public static boolean hasBuiltin(String key) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { return BUILTIN.containsKey(canonicalKey(key)); } }

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
