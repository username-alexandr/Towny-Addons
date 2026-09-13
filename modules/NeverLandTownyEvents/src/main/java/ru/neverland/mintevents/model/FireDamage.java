package ru.neverland.mintevents.model;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Exact pre-fire block state. The record is durable before the block disappears. */
public record FireDamage(UUID town, UUID world, int x, int y, int z, String blockData, long incident) {
    public FireDamage {
        Objects.requireNonNull(town); Objects.requireNonNull(world); Objects.requireNonNull(blockData);
        if (Math.abs((long)x)>30_000_000 || Math.abs((long)z)>30_000_000 || incident<0
                || !blockData.matches("minecraft:[a-z0-9_]+(?:\\[[a-z0-9_=,]+\\])?")) throw new IllegalArgumentException("Invalid fire damage record");
    }
    public UUID id() { return id(world,x,y,z); }
    public static UUID id(UUID world,int x,int y,int z) { return UUID.nameUUIDFromBytes((world+":"+x+":"+y+":"+z).getBytes(StandardCharsets.UTF_8)); }
    public String material() { return blockData.substring("minecraft:".length()).split("\\[",2)[0].toUpperCase(java.util.Locale.ROOT); }
}
