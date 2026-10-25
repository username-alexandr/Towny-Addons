package ru.neverland.mintevents.model;

import java.util.List;
import java.util.UUID;

/** Immutable selection, including the incident at each position: an old menu cannot clear a new fire. */
public record FireRepairPlot(UUID town, UUID world, int x, int z, int size, List<FireDamage> damage) {
    public FireRepairPlot {
        damage = List.copyOf(damage);
        if (town == null || world == null || size <= 0 || damage.isEmpty()) throw new IllegalArgumentException("Пустой участок ремонта");
        for (FireDamage d : damage) if (!contains(town, world, x, z, size, d)) throw new IllegalArgumentException("Повреждение вне участка");
        if (damage.stream().map(FireDamage::id).distinct().count() != damage.size()) throw new IllegalArgumentException("Повтор повреждения");
    }
    public boolean contains(FireDamage d) { return contains(town, world, x, z, size, d); }
    private static boolean contains(UUID town, UUID world, int x, int z, int size, FireDamage d) {
        return town.equals(d.town()) && world.equals(d.world()) && Math.floorDiv(d.x(), size) == x && Math.floorDiv(d.z(), size) == z;
    }
}
