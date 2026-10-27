package ru.neverland.mintcontracts.orders;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import ru.neverland.core.AtomicFiles;
import ru.neverland.core.SafeYaml;
import ru.neverland.earnings.ActionGuard;
import ru.neverland.mintcontracts.orders.PublicOrders.Template;
import ru.neverland.mintcontracts.orders.PublicOrders.Type;

/** Versioned, additive migrations preserve custom terms and previously removed templates. */
public final class OrderCatalog {
    public static final int CATALOG_VERSION = 4;
    public static final int DEFAULT_PLAYER_LIMIT = 3000;
    public static final int DEFAULT_BOARD_SIZE = 18;
    public static final String MIGRATION_BACKUP = "orders.pre-catalog-v4.yml";
    private OrderCatalog() {}

    public static boolean migrate(YamlConfiguration cfg, YamlConfiguration defaults) {
        int version = SafeYaml.intValue(cfg, "catalog-version", 1);
        if (version < 1 || version > CATALOG_VERSION) throw new IllegalArgumentException("Неизвестная версия каталога заказов");
        if (version == CATALOG_VERSION) return false;
        var templates = Objects.requireNonNull(defaults.getConfigurationSection("templates"));
        for (String id : templates.getKeys(false)) {
            String path = "templates." + id;
            int introduced = SafeYaml.intValue(templates.getConfigurationSection(id), "introduced-in", 2);
            if (!cfg.contains(path) && version < introduced) {
                cfg.createSection(path, templates.getConfigurationSection(id).getValues(false));
            } else if (version < 2 && cfg.contains(path) && !cfg.contains(path + ".rarity")) {
                cfg.set(path + ".rarity", templates.getString(id + ".rarity"));
            }
        }
        if (version < 2) {
            for (OrderRarity rarity : OrderRarity.values()) {
                String path = "rarity-weights." + rarity.name();
                if (!cfg.contains(path)) cfg.set(path, rarity.defaultWeight);
            }
            int size = SafeYaml.intValue(cfg, "board-size", 4);
            cfg.set("board-size", size == 4 ? 12 : Math.min(size, 45));
            cfg.set("refresh-seconds", null);
        }
        if(version<3){int size = SafeYaml.intValue(cfg, "board-size", 12);
        if (size == 12) cfg.set("board-size", DEFAULT_BOARD_SIZE);
        // Upgrade the previous default only; never lower or replace a custom personal cap.
        if (SafeYaml.doubleValue(cfg, "limits.player-24h", 1500) == 1500) {
            double serverLimit = SafeYaml.doubleValue(cfg, "limits.server-24h", 100000);
            if (serverLimit >= 1500) cfg.set("limits.player-24h", Math.min(DEFAULT_PLAYER_LIMIT, serverLimit));
        }
        }
        cfg.set("catalog-version", CATALOG_VERSION);
        return true;
    }

    public static void saveMigration(Path file, YamlConfiguration cfg) throws IOException {
        Path backup = file.resolveSibling(MIGRATION_BACKUP);
        if (!Files.exists(backup)) Files.copy(file, backup);
        AtomicFiles.loaded(file);
        AtomicFiles.write(file, cfg::saveToString);
    }

    public static Map<OrderRarity, Integer> weights(ConfigurationSection cfg) {
        var weights = new EnumMap<OrderRarity, Integer>(OrderRarity.class);
        for (OrderRarity rarity : OrderRarity.values()) {
            int weight = SafeYaml.intValue(cfg, "rarity-weights." + rarity.name(), rarity.defaultWeight);
            if (weight < 0 || weight > 100000) throw new IllegalArgumentException("Вес редкости: 0..100000");
            weights.put(rarity, weight);
        }
        return Map.copyOf(weights);
    }

    public static List<Template> templates(ConfigurationSection cfg) {
        var result = new ArrayList<Template>();
        var section = SafeYaml.section(cfg, "templates");
        if (section == null) throw new IllegalArgumentException("Нет templates в orders.yml");
        for (String id : section.getKeys(false)) {
            if (!id.matches("[a-z0-9_]{1,48}")) throw new IllegalArgumentException("Неверный ID заказа: " + id);
            var s = Objects.requireNonNull(section.getConfigurationSection(id), "Нужен раздел заказа " + id);
            if (!SafeYaml.booleanValue(s, "enabled", true)) continue;
            Type type = Type.valueOf(SafeYaml.text(s, "type"));
            String target = SafeYaml.text(s, "target");
            if (type == Type.HUNTING) {
                var entity = EntityType.valueOf(target);
                if (entity.getEntityClass() == null || !org.bukkit.entity.Enemy.class.isAssignableFrom(entity.getEntityClass()))
                    throw new IllegalArgumentException("HUNTING допускает только враждебных мобов: " + target);
            } else {
                Material material = Material.valueOf(target);
                if (type == Type.DELIVERY && Set.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR).contains(material))
                    throw new IllegalArgumentException("DELIVERY требует предмет: " + target);
                if (type == Type.MINING && !ActionGuard.ore(material)) throw new IllegalArgumentException("MINING допускает только руды");
                if (type == Type.FISHING && !Set.of("COD", "SALMON", "PUFFERFISH", "TROPICAL_FISH").contains(target))
                    throw new IllegalArgumentException("FISHING допускает рыбу");
            }
            result.add(new Template(id, SafeYaml.stringValue(s, "title", id), type, target,
                    positive(s, "amount", 1, 100000), PublicOrders.money(s, "reward", 50),
                    positive(s, "duration-seconds", 7200, 604800) * 1000L,
                    OrderRarity.valueOf(SafeYaml.stringValue(s, "rarity", "COMMON"))));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Каталог заказов пуст");
        return List.copyOf(result);
    }

    /** Paper's item registry is only available on the live server, unlike the calendar/storage logic. */
    public static void validateItems(List<Template> templates) {
        for (Template template : templates) if (template.type() == Type.DELIVERY && !Material.valueOf(template.target()).isItem())
            throw new IllegalArgumentException("DELIVERY требует предмет: " + template.target());
    }

    static int positive(ConfigurationSection cfg, String key, int fallback, int maximum) {
        int value = SafeYaml.intValue(cfg, key, fallback);
        if (value <= 0 || value > maximum) throw new IllegalArgumentException(key + ": 1.." + maximum);
        return value;
    }
}
