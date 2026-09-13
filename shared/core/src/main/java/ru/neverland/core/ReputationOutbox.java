package ru.neverland.core;

import java.util.*;
import java.util.function.BooleanSupplier;
import org.bukkit.configuration.ConfigurationSection;

/** Written in the SAME atomic snapshot as its source operation. Provider receipts make ack retries safe. */
public final class ReputationOutbox {
    private final Map<String, ReputationOutcome> pending = new LinkedHashMap<>();
    public void add(ReputationOutcome outcome) {
        var previous = pending.putIfAbsent(outcome.id(), outcome);
        if (previous != null && !previous.equals(outcome)) throw new IllegalArgumentException("Конфликт события репутации: " + outcome.id());
    }
    public int size() { return pending.size(); }
    public void load(ConfigurationSection yaml) {
        var next = new ReputationOutbox();
        for (var row : SafeYaml.maps(yaml, "reputation-outbox")) {
            var s = new org.bukkit.configuration.MemoryConfiguration(); row.forEach((k,v) -> s.set(String.valueOf(k), v));
            SafeYaml.keys(s, "id", "scope", "subject", "rule", "at", "context");
            var outcome = new ReputationOutcome(SafeYaml.text(s, "id"), SafeYaml.text(s, "scope"), UUID.fromString(SafeYaml.text(s, "subject")),
                    SafeYaml.text(s, "rule"), SafeYaml.integer(s, "at"), SafeYaml.text(s, "context"));
            if (next.pending.containsKey(outcome.id())) throw new IllegalArgumentException("Повтор события репутации");
            next.add(outcome);
        }
        pending.clear(); pending.putAll(next.pending);
    }
    public void write(ConfigurationSection yaml) {
        yaml.set("reputation-outbox", pending.values().stream().map(o -> Map.of("id", o.id(), "scope", o.scope(), "subject", o.subject().toString(),
                "rule", o.rule(), "at", o.at(), "context", o.context())).toList());
    }
    public void flush(BooleanSupplier healthy, Runnable persist) {
        if (!healthy.getAsBoolean()) return;
        // Bound synchronous work; unacknowledged outcomes remain durable for the next tick.
        for (var outcome : List.copyOf(pending.values()).stream().limit(16).toList()) {
            if (!healthy.getAsBoolean() || !ReputationAccess.deliver(outcome)) return;
            pending.remove(outcome.id());
            try { persist.run(); }
            catch (RuntimeException ex) { pending.put(outcome.id(), outcome); throw ex; }
        }
    }
}
