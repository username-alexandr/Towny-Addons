package ru.neverland.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class EffectJournal {
   private final Path file;
   private Map<UUID, EffectJournal.Entry> entries = Map.of();
   private boolean loaded;

   public EffectJournal(Path file) {
      this.file = file;
   }

   public static UUID id(String value) {
      return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
   }

   public boolean writable() {
      return this.loaded && AtomicFiles.writable(this.file);
   }

   public Collection<EffectJournal.Entry> entries() {
      return this.entries.values();
   }

   public EffectJournal.State state(UUID id) {
      EffectJournal.Entry entry = this.entries.get(id);
      return entry == null ? EffectJournal.State.READY : entry.state();
   }

   public void load() throws IOException {
      this.loaded = false;
      Map<UUID, EffectJournal.Entry> next = new LinkedHashMap<>();
      if (Files.exists(this.file)) {
         try {
            YamlConfiguration y = new YamlConfiguration();
            y.load(this.file.toFile());
            if (y.getInt("schema") != 1 || !y.isConfigurationSection("effects")) {
               throw new IOException("Неверная схема журнала операций");
            }

            ConfigurationSection root = y.getConfigurationSection("effects");

            for (String key : root.getKeys(false)) {
               ConfigurationSection s = root.getConfigurationSection(key);
               if (s == null || !s.isString("description") || !s.isString("state")) {
                  throw new IOException("Повреждена операция");
               }

               UUID id = UUID.fromString(key);
               next.put(id, new EffectJournal.Entry(id, s.getString("description"), EffectJournal.State.valueOf(s.getString("state"))));
            }
         } catch (Exception var8) {
            throw new IOException("Журнал операций повреждён: " + this.file.getFileName(), var8);
         }
      }

      this.entries = Map.copyOf(next);
      AtomicFiles.loaded(this.file);
      this.loaded = true;
   }

   private void put(EffectJournal.Entry value) throws IOException {
      if (!this.writable()) {
         throw new IOException("Журнал операций заблокирован после ошибки записи");
      } else {
         LinkedHashMap<UUID, EffectJournal.Entry> next = new LinkedHashMap<>(this.entries);
         next.put(value.id(), value);
         AtomicFiles.write(this.file, () -> {
            YamlConfiguration y = new YamlConfiguration();
            y.set("schema", 1);
            y.createSection("effects");

            for (EffectJournal.Entry e : next.values()) {
               String p = "effects." + e.id();
               y.set(p + ".description", e.description());
               y.set(p + ".state", e.state().name());
            }

            return y.saveToString();
         });
         this.entries = Map.copyOf(next);
      }
   }

   public boolean execute(UUID id, String description, EffectJournal.Action action) throws Exception {
      if (!this.writable()) {
         throw new IOException("Журнал операций недоступен");
      } else {
         EffectJournal.Entry old = this.entries.get(id);
         if (old != null && !old.description().equals(description)) {
            throw new IllegalArgumentException("ID операции уже используется для других условий");
         } else if (this.state(id) == EffectJournal.State.DONE) {
            return true;
         } else if (this.state(id) == EffectJournal.State.PENDING) {
            throw new IllegalStateException("Операция " + id + " требует сверки: " + description);
         } else {
            this.put(new EffectJournal.Entry(id, description, EffectJournal.State.PENDING));
            boolean success = action.apply();
            this.put(new EffectJournal.Entry(id, description, success ? EffectJournal.State.DONE : EffectJournal.State.READY));
            return success;
         }
      }
   }

   public void resolve(UUID id, boolean applied) throws IOException {
      EffectJournal.Entry old = this.entries.get(id);
      if (old != null && old.state() == EffectJournal.State.PENDING) {
         this.put(new EffectJournal.Entry(id, old.description(), applied ? EffectJournal.State.DONE : EffectJournal.State.READY));
      } else {
         throw new IllegalArgumentException("Нет операции, ожидающей сверки");
      }
   }

   public void review(UUID id, String description) throws IOException {
      if (!this.entries.containsKey(id)) {
         this.put(new EffectJournal.Entry(id, description, EffectJournal.State.PENDING));
      }
   }

   public void retain(Set<UUID> needed) throws IOException {
      if (!this.writable()) {
         throw new IOException("Журнал операций недоступен");
      } else {
         LinkedHashMap<UUID, EffectJournal.Entry> next = new LinkedHashMap<>(this.entries);
         next.entrySet().removeIf(e -> !needed.contains(e.getKey()) && e.getValue().state() != EffectJournal.State.PENDING);
         if (next.size() != this.entries.size()) {
            AtomicFiles.write(this.file, () -> {
               YamlConfiguration y = new YamlConfiguration();
               y.set("schema", 1);
               y.createSection("effects");

               for (EffectJournal.Entry e : next.values()) {
                  String p = "effects." + e.id();
                  y.set(p + ".description", e.description());
                  y.set(p + ".state", e.state().name());
               }

               return y.saveToString();
            });
            this.entries = Map.copyOf(next);
         }
      }
   }

   @FunctionalInterface
   public interface Action {
      boolean apply() throws Exception;
   }

   public record Entry(UUID id, String description, EffectJournal.State state) {
      public Entry(UUID id, String description, EffectJournal.State state) {
         Objects.requireNonNull(id);
         Objects.requireNonNull(state);
         if (description != null && !description.isBlank()) {
            this.id = id;
            this.description = description;
            this.state = state;
         } else {
            throw new IllegalArgumentException("Нет описания операции");
         }
      }
   }

   public static enum State {
      READY,
      PENDING,
      DONE;
   }
}
