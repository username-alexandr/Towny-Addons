package ru.neverland.core;

import org.bukkit.plugin.Plugin;
import java.util.ArrayList;

/** Reject mixed monitor/guard binaries before a new module reads or mutates player data. */
public final class SuiteProtocol {
    private SuiteProtocol() {}
    public static void verify(Plugin caller) {
        var incompatible = new ArrayList<String>();
        for (Plugin plugin : caller.getServer().getPluginManager().getPlugins()) {
            if (!plugin.getName().startsWith("NeverLandTowny")) continue;
            try {
                Class<?> state = Class.forName("ru.neverland.core.SuiteState", false, plugin.getClass().getClassLoader());
                if (!state.getProtectionDomain().getCodeSource().getLocation().equals(plugin.getClass().getProtectionDomain().getCodeSource().getLocation())) continue;
                if (!Integer.valueOf(SuiteState.protocolVersion()).equals(state.getMethod("protocolVersion").invoke(null))) incompatible.add(plugin.getName());
            } catch (ClassNotFoundException ignored) {
                // Independent addons without an embedded ledger are outside this protocol.
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
                incompatible.add(plugin.getName());
            }
        }
        if (!incompatible.isEmpty()) throw new IllegalStateException("Несовместимые версии общего ядра: " + String.join(", ", incompatible)
                + ". Замените все установленные JAR NeverLandTowny из одного комплекта и полностью перезапустите сервер. Данные не изменены.");
    }
}
