package ru.neverland.core;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

public final class Diagnostics {
   private Diagnostics() {
   }

   public static void show(CommandSender sender) {
      Set<String> lines = new TreeSet<>();

      for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
         if (plugin.getName().startsWith("NeverLandTowny")) {
            try {
               Class<?> contract = Class.forName("ru.neverland.core.LocalDiagnostics", false, plugin.getClass().getClassLoader());
               Map<?, ?> snapshot = (Map<?, ?>)contract.getMethod("snapshot").invoke(null);
               collect(snapshot, lines);
            } catch (RuntimeException | ReflectiveOperationException var8) {
               lines.add("Диагностика " + plugin.getName() + ": " + var8.getClass().getSimpleName());
            }
         }
      }

      sender.sendMessage("NeverLand: состояние интеграций и синхронных записей (с запуска сервера)");

      for (String line : lines) {
         sender.sendMessage(line);
      }
   }

   private static void collect(Map<?, ?> snapshot, Set<String> lines) {
      try {
         Object var4 = snapshot.get("integrations");
         if (var4 instanceof List) {
            for (Object value : (List)var4) {
               if (value instanceof Map<?, ?> m) {
                  lines.add("API " + m.get("plugin") + " / " + m.get("contract") + ": " + m.get("state") + " " + m.get("detail"));
               }
            }
         }

         var4 = snapshot.get("storage");
         if (var4 instanceof List) {
            for (Object valuex : (List)var4) {
               if (valuex instanceof Map<?, ?> m) {
                  long writes = ((Number)m.get("writes")).longValue();
                  long failures = ((Number)m.get("failures")).longValue();
                  double average = ((Number)m.get("totalNanos")).doubleValue() / Math.max(1L, writes + failures) / 1000000.0;
                  double max = ((Number)m.get("maximumNanos")).doubleValue() / 1000000.0;
                  lines.add(
                     String.format(
                        Locale.ROOT,
                        "Файл %s: записей %d, ошибок %d, среднее %.2f мс, максимум %.2f мс, байт %s",
                        m.get("file"),
                        writes,
                        failures,
                        average,
                        max,
                        m.get("bytes")
                     )
                  );
               }
            }
         }
      } catch (RuntimeException var14) {
         lines.add("Диагностика хранилища : " + var14.getClass().getSimpleName());
      }
   }
}
