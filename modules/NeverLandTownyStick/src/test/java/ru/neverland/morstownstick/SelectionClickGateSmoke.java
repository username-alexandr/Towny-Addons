package ru.neverland.morstownstick;

import java.util.UUID;
import ru.neverland.morstownstick.service.SelectionClickGate;

public final class SelectionClickGateSmoke {
    public static void main(String[] args) {
        SelectionClickGate gate = new SelectionClickGate();
        UUID player = UUID.randomUUID();
        check(gate.accept(player, "world:1:1", 0), "first click");
        for (long t = 100_000_000; t <= 1_000_000_000; t += 100_000_000)
            check(!gate.accept(player, "world:1:1", t), "held button must not toggle off");
        check(gate.accept(player, "world:2:1", 1_100_000_000), "next cell without delay");
        check(gate.accept(player, "world:2:1", 1_600_000_000), "deliberate second click");
        check(gate.accept(UUID.randomUUID(), "world:2:1", 1_600_000_001), "independent player");
        gate.clear(player);
        check(gate.accept(player, "world:2:1", 1_600_000_002), "quit resets gate");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
