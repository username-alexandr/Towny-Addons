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
   public static int intValue(ConfigurationSection s, String key) {
      return intValue(s, key, 0);
   }

   public static int intValue(ConfigurationSection s, String key, int fallback) {
      return s.contains(key) ? Math.toIntExact(integer(s, key)) : fallback;
   }

   public static long longValue(ConfigurationSection s, String key) {
      return longValue(s, key, 0L);
   }

   public static long longValue(ConfigurationSection s, String key, long fallback) {
      return s.contains(key) ? integer(s, key) : fallback;
   }

   public static double doubleValue(ConfigurationSection s, String key) {
      return doubleValue(s, key, 0.0);
   }

   public static double doubleValue(ConfigurationSection s, String key, double fallback) {
      Object v = s.get(key);
      if (v == null) {
         return fallback;
      } else if (v instanceof Number n && Double.isFinite(n.doubleValue())) {
         return n.doubleValue();
      } else {
         throw new IllegalArgumentException("Нужно конечное число: " + key);
      }
   }

   public static boolean booleanValue(ConfigurationSection s, String key) {
      return booleanValue(s, key, false);
   }

   public static boolean booleanValue(ConfigurationSection s, String key, boolean fallback) {
      Object v = s.get(key);
      if (v == null) {
         return fallback;
      } else if (v instanceof Boolean b) {
         return b;
      } else {
         throw new IllegalArgumentException("Нужно логическое значение: " + key);
      }
   }

   public static String stringValue(ConfigurationSection s, String key) {
      return stringValue(s, key, null);
   }

   public static String stringValue(ConfigurationSection s, String key, String fallback) {
      Object v = s.get(key);
      if (v == null) {
         return fallback;
      } else if (v instanceof String t) {
         return t;
      } else {
         throw new IllegalArgumentException("Нужна строка: " + key);
      }
   }

   public static List<String> strings(ConfigurationSection s, String key) {
      ArrayList<String> out = new ArrayList<>();

      for (Object v : list(s, key)) {
         if (!(v instanceof String t)) {
            throw new IllegalArgumentException("Повреждён список строк: " + key);
         }

         out.add(t);
      }

      return List.copyOf(out);
   }

   public static void keys(ConfigurationSection s, String... allowed) {
      if (!Set.of(allowed).containsAll(s.getKeys(false))) {
         throw new IllegalArgumentException("Неизвестные разделы данных: " + s.getKeys(false));
      }
   }

   private SafeYaml() {
   }

   public static YamlConfiguration load(Path file) {
      try {
         YamlConfiguration y = new YamlConfiguration();
         if (Files.exists(file)) {
            y.load(file.toFile());
         }

         return y;
      } catch (Exception var2) {
         throw new IllegalStateException("Повреждён файл " + file.getFileName() + "; загрузка остановлена", var2);
      }
   }

   public static ConfigurationSection section(ConfigurationSection y, String key) {
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
   }

   public static String text(ConfigurationSection y, String key) {
      if (!y.isString(key)) {
         throw new IllegalArgumentException("Нужна строка " + key);
      } else {
         return y.getString(key);
      }
   }

   public static long integer(ConfigurationSection y, String key) {
      Object v = y.get(key);
      if (!(v instanceof Integer) && !(v instanceof Long)) {
         throw new IllegalArgumentException("Нужно целое " + key);
      } else {
         return ((Number)v).longValue();
      }
   }

   public static double money(ConfigurationSection y, String key) {
      if (y.get(key) instanceof Number n && Double.isFinite(n.doubleValue()) && !(n.doubleValue() < 0.0)) {
         return n.doubleValue();
      } else {
         throw new IllegalArgumentException("Неверная сумма " + key);
      }
   }

   public static List<?> list(ConfigurationSection y, String key) {
      if (!y.contains(key)) {
         return List.of();
      } else if (y.get(key) instanceof List<?> list) {
         return list;
      } else {
         throw new IllegalArgumentException("Нужен список " + key);
      }
   }

   public static List<Map<?, ?>> maps(ConfigurationSection y, String key) {
      List<Map<?, ?>> result = new ArrayList<>();

      for (Object v : list(y, key)) {
         if (!(v instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException("Повреждена запись " + key);
         }

         result.add(m);
      }

      return result;
   }
}
