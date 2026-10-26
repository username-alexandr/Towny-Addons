package ru.neverland.morstownstick.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Coalesces held-button repeats without delaying selection of another cell. */
public final class SelectionClickGate {
    private static final long QUIET_NANOS = 400_000_000L;
    private final Map<UUID, Click> previous = new HashMap<>();

    public boolean accept(UUID player, Object cell, long now) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        Click last = previous.put(player, new Click(cell, now));
        return last == null || !last.cell().equals(cell) || now - last.time() >= QUIET_NANOS;
    } }

    public void clear(UUID player) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { previous.remove(player); } }

    private record Click(Object cell, long time) {}
}
