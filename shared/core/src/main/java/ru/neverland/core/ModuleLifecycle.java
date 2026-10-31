package ru.neverland.core;

import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;

/** Must be the first statement of onEnable, before data migration, listeners, menus or tasks. */
public final class ModuleLifecycle {
    private static final java.util.Set<JavaPlugin> started = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private ModuleLifecycle() {}
    /** Skip cleanup for a plugin whose enable body was deliberately never entered. */
    public static boolean end(JavaPlugin plugin) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { SuiteScheduler.get().cancelTasks(plugin);ModuleShutdown.remove(plugin);return started.remove(plugin); } }
    public static boolean begin(JavaPlugin plugin) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        try {
            SuiteProtocol.verify(plugin);
            Path folder=plugin.getDataFolder().toPath();Path control=ModulePauseStore.file(folder.getParent());
            var entry=ModulePauseStore.load(control).modules().get(plugin.getName());
            if(entry!=null&&entry.disabled()) {
                plugin.getLogger().warning("Аддон отключён администратором; данные и задачи сохранены. /nltmodules enable "+plugin.getName()+", затем перезапуск сервера.");
                plugin.getServer().getPluginManager().disablePlugin(plugin);return false;
            }
            // Each addon owns its editable messages. Existing administrator changes are never overwritten.
            try(var bundled=plugin.getResource("messages.yml")) {
                if(bundled!=null&&!Files.exists(folder.resolve("messages.yml")))plugin.saveResource("messages.yml",false);
            } catch(Exception translationError) {
                plugin.getLogger().warning("Файл сообщений не создан; используются встроенные фразы: " + translationError.getMessage());
            }
            if(entry!=null&&entry.pausedAt()>0) {
                ModuleTimers.resume(folder,plugin.getName(),entry.pausedAt(),System.currentTimeMillis());
                ModulePauseStore.resumed(control,plugin.getName(),entry.pausedAt());
                plugin.getLogger().info("Таймеры восстановлены после административной паузы без учёта времени отключения.");
            }
            SuiteState.thaw(plugin.getName());
            WorldTasks.initialize(plugin);
            CommandOutbox.start(plugin);
            started.add(plugin);
            return true;
        } catch(Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,"Запуск остановлен: не удалось безопасно восстановить модуль",e);
            plugin.getServer().getPluginManager().disablePlugin(plugin);return false;
        }
    } }
}
