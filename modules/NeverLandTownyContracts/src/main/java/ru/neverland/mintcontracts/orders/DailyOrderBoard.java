package ru.neverland.mintcontracts.orders;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.AtomicFiles;
import ru.neverland.core.SafeYaml;
import ru.neverland.core.SuiteState;
import ru.neverland.mintcontracts.orders.PublicOrders.Template;
import ru.neverland.mintcontracts.orders.PublicOrders.Type;

/** A durable daily snapshot, shared by all players. No world/entity access. */
public final class DailyOrderBoard {
    public static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    public record Snapshot(LocalDate day, List<Template> templates) {
        public Snapshot { templates = List.copyOf(templates); }
        public Instant nextRefresh() { return day.plusDays(1).atStartOfDay(MOSCOW).toInstant(); }
    }
    private final Path file;
    private final List<Template> catalog;
    private final int size;
    private final Map<OrderRarity, Integer> weights;
    private final Clock clock;
    private final Random random;
    private Snapshot current;

    public DailyOrderBoard(Path file, List<Template> catalog, int size, Map<OrderRarity, Integer> weights) throws IOException {
        this(file, catalog, size, weights, Clock.systemUTC(), new java.security.SecureRandom());
    }

    public DailyOrderBoard(Path file, List<Template> catalog, int size, Map<OrderRarity, Integer> weights, Clock clock, Random random) throws IOException {
        this.file = file; this.catalog = List.copyOf(catalog); this.size = size;
        this.weights = Map.copyOf(weights); this.clock = clock; this.random = random;
        if (size < 1 || size > 45) throw new IllegalArgumentException("board-size: 1..45");
        if (catalog.stream().map(Template::id).distinct().count() != catalog.size()) throw new IllegalArgumentException("Повторяющийся ID заказа");
        for (OrderRarity rarity : OrderRarity.values()) if (weights.getOrDefault(rarity, -1) < 0 || weights.get(rarity) > 100000)
            throw new IllegalArgumentException("Неверный вес редкости " + rarity);
        if (catalog.stream().noneMatch(t -> weights.get(t.rarity()) > 0)) throw new IllegalArgumentException("Нет заказов с положительным весом");
        if (Files.exists(file)) {
            var y = SafeYaml.load(file);
            if (SafeYaml.intValue(y, "schema") != 1) throw new IOException("Неизвестная схема ежедневной доски");
            var templates = OrderCatalog.templates(y);
            if (templates.size() > 45) throw new IOException("Повреждён размер ежедневной доски");
            current = new Snapshot(LocalDate.parse(SafeYaml.text(y, "day")), templates);
        }
        AtomicFiles.loaded(file);
    }

    public Snapshot snapshot() throws IOException { synchronized (SuiteState.lock()) {
        LocalDate today = clock.instant().atZone(MOSCOW).toLocalDate();
        // A backwards clock adjustment must not offer another board for an already visited day.
        if (current != null && !today.isAfter(current.day())) return current;
        var available = new ArrayList<>(catalog.stream().filter(t -> weights.get(t.rarity()) > 0).toList());
        var selected = new ArrayList<Template>();
        var types = new ArrayList<>(Arrays.asList(Type.values()));
        Collections.shuffle(types, random);
        // With >=4 slots and eligible templates, all four activities are always represented.
        for (Type type : types) {
            if (selected.size() >= size) break;
            var candidates = available.stream().filter(t -> t.type() == type).toList();
            if (!candidates.isEmpty()) choose(candidates, available, selected);
        }
        while (selected.size() < size && !available.isEmpty()) choose(available, available, selected);
        selected.sort(Comparator.comparing(Template::rarity).thenComparing(Template::type).thenComparing(Template::id));
        var next = new Snapshot(today, selected);
        var y = new YamlConfiguration(); y.set("schema", 1); y.set("day", today.toString());
        for (Template t : selected) {
            String p = "templates." + t.id() + ".";
            y.set(p + "title", t.title()); y.set(p + "type", t.type().name()); y.set(p + "target", t.target());
            y.set(p + "amount", t.amount()); y.set(p + "reward", t.cents() / 100.0);
            y.set(p + "duration-seconds", t.duration() / 1000); y.set(p + "rarity", t.rarity().name());
        }
        AtomicFiles.write(file, y::saveToString);
        current = next; // Never expose a selection that was not saved successfully.
        return current;
    } }

    private void choose(List<Template> candidates, List<Template> available, List<Template> selected) {
        long total = candidates.stream().mapToLong(t -> weights.get(t.rarity())).sum();
        long roll = random.nextLong(total);
        Template choice = candidates.get(candidates.size() - 1);
        for (Template t : candidates) { roll -= weights.get(t.rarity()); if (roll < 0) { choice = t; break; } }
        selected.add(choice); available.remove(choice);
    }
}
