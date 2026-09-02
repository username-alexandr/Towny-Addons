package ru.neverland.mintespionage.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** Safely copies data from the pre-NeverLand plugin folder on the first renamed start. */
public final class LegacyDataMigrator {
    private LegacyDataMigrator() { }

    public static void migrate(JavaPlugin plugin, String legacyPluginName) {
        Path current = plugin.getDataFolder().toPath();
        Path plugins = current.getParent();
        if (plugins == null || Files.exists(current)) return;
        Path legacy = plugins.resolve(legacyPluginName);
        if (!Files.isDirectory(legacy)) return;
        try (Stream<Path> paths = Files.walk(legacy)) {
            for (Path source : paths.sorted(Comparator.naturalOrder()).toList()) {
                Path target = current.resolve(legacy.relativize(source).toString());
                if (Files.isDirectory(source)) Files.createDirectories(target);
                else if (!Files.exists(target)) Files.copy(source, target);
            }
            plugin.getLogger().info("Данные перенесены из папки " + legacyPluginName + ". Старая папка сохранена как резервная копия.");
        } catch (IOException exception) {
            plugin.getLogger().severe("Не удалось перенести данные из " + legacyPluginName + ": " + exception.getMessage());
        }
    }
}
