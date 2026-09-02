package ru.neverland.archaeology.api;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record MuseumSnapshot(UUID townId, String townName, int points, Map<String, Integer> available,
                             Map<String, Integer> donated, Set<String> completedCollections) { }
