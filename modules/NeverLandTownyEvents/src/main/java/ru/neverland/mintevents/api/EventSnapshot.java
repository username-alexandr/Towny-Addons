package ru.neverland.mintevents.api;

import java.util.UUID;

public record EventSnapshot(UUID townId, String eventId, String name, long endsAt,
                            int progress, int goal, double protection) {}
