package ru.neverland.mintevents;

import ru.neverland.mintevents.model.ActiveEvent;

import java.util.UUID;

public final class EventMathSmoke {
    public static void main(String[] args) {
        ActiveEvent event = new ActiveEvent(UUID.randomUUID(), "drought", 1_000, 61_000, 0, 100, 0.4, 0);
        if (event.addProgress(25) != 25) throw new AssertionError("progress");
        if (Math.abs(event.progressRatio() - 0.25) > 0.0001) throw new AssertionError("ratio");
        if (event.completed()) throw new AssertionError("premature completion");
        event.addProgress(500);
        if (!event.completed() || event.progress() != 100) throw new AssertionError("goal clamp");
        if (event.secondsLeft(31_000) != 30) throw new AssertionError("time left");
        System.out.println("EventMathSmoke OK");
    }
}
