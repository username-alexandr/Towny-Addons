package ru.neverland.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class SafeYaml {
   public static int intValue(ConfigurationSection s, String key) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
      return intValue(s, key, 0);
   } }

   public static int intValue(ConfigurationSection s, String key, int fallback) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
      return s.contains(key) ? Math.toIntExact(integer(s, key)) : fallback;
   } }

   public static long longValue(ConfigurationSection s, String key) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
      return longValue(s, key, 0L);
   } }

   public static long longValue(ConfigurationSection s, String key, long fallback) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
      return s.contains(key) ? integer(s, key) : fallback;
   } }

   public static double doubleValue(ConfigurationSection s, String key) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
      return doubleValue(s, key, 0.0);
   } }

   public static double doubleValue(ConfigurationSection s, String key, double fallback) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
      Object v = s.get(key);
      if (v == null) {
         return fallback;
      } else if (v instanceof Number n && Double.isFinite(n.doubleValue())) {
         return n.doubleValue();
      } else {
         throw new IllegalArgumentException("Нужно конечное число: " + key);
      }
   } }

   public static boolean booleanValue(ConfigurationSection s, String key) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
      return booleanValue(s, key, false);
   } }

   public static boolean booleanValue(ConfigurationSection s, String key, boolean fallback) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
      Object v = s.get(key);
      if (v == null) {
         return fallback;
      } else if (v instanceof Boolean b) {
         return b;
      } else {
         throw new IllegalArgumentException("Нужно логическое значение: " + key);
      }
   } }

   public static String stringValue(ConfigurationSection s, String key) { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {
      return stringValue(s, key, null);
   } }

   public static String stringValue(ConfigurationSection s, String key, String fallback) { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {
      Object v = s.get(key);
      if (v == null) {
         return fallback;
      } else if (v instanceof String t) {
         return t;
      } else {
         throw new IllegalArgumentException("Нужна строка: " + key);
      }
   } }

   public static List<String> strings(ConfigurationSection s, String key) { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {
      ArrayList<String> out = new ArrayList<>();

      for (Object v : list(s, key)) {
         if (!(v instanceof String t)) {
            throw new IllegalArgumentException("Повреждён список строк: " + key);
         }

         out.add(t);
      }

      return List.copyOf(out);
   } }

   public static void keys(ConfigurationSection s, String... allowed) { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {
      if (!Set.of(allowed).containsAll(s.getKeys(false))) {
         throw new IllegalArgumentException("Неизвестные разделы данных: " + s.getKeys(false));
      }
   } }

   private SafeYaml() {
   }

   public static YamlConfiguration load(Path file) { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {
      try {
         YamlConfiguration y = new YamlConfiguration();
         if (Files.exists(file)) {
            y.load(file.toFile());
         }

         return y;
      } catch (Exception var2) {
         throw new IllegalStateException("Повреждён файл " + file.getFileName() + "; загрузка остановлена", var2);
      }
   } }

   public static ConfigurationSection section(ConfigurationSection y, String key) { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {
      if (!y.contains(key)) {
         return null;
      } else {
         ConfigurationSection s = y.getConfigurationSection(key);
         if (s == null) {
            throw new IllegalArgumentException("Нужен раздел " + key);
         } else {
            return s;
         }
      }
   } }

   public static String text(ConfigurationSection y, String key) { try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {
      if (!y.isString(key)) {
         throw new IllegalArgumentException("Нужна строка " + key);
      } else {
         return y.getString(key);
      }
   } }

   public static long integer(ConfigurationSection y, String key) { try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {
      Object v = y.get(key);
      if (!(v instanceof Integer) && !(v instanceof Long)) {
         throw new IllegalArgumentException("Нужно целое " + key);
      } else {
         return ((Number)v).longValue();
      }
   } }

   public static double money(ConfigurationSection y, String key) { try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {
      if (y.get(key) instanceof Number n && Double.isFinite(n.doubleValue()) && !(n.doubleValue() < 0.0)) {
         return n.doubleValue();
      } else {
         throw new IllegalArgumentException("Неверная сумма " + key);
      }
   } }

   public static List<?> list(ConfigurationSection y, String key) { try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {
      if (!y.contains(key)) {
         return List.of();
      } else if (y.get(key) instanceof List<?> list) {
         return list;
      } else {
         throw new IllegalArgumentException("Нужен список " + key);
      }
   } }

   public static List<Map<?, ?>> maps(ConfigurationSection y, String key) { try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {
      List<Map<?, ?>> result = new ArrayList<>();

      for (Object v : list(y, key)) {
         if (!(v instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException("Повреждена запись " + key);
         }

         result.add(m);
      }

      return result;
   } }
}
