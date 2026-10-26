package ru.neverland.townybuilds.util;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.Path;
import java.io.IOException;
public final class AtomicYamlFile {
    private AtomicYamlFile(){}
    public static void write(YamlConfiguration yaml,Path file)throws IOException { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {ru.neverland.core.AtomicFiles.write(file,yaml::saveToString);} }
}
