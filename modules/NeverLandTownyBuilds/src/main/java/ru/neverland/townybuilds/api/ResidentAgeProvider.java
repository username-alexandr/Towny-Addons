package ru.neverland.townybuilds.api;
import java.util.OptionalInt;
import java.util.UUID;
/** Register in Bukkit ServicesManager to supply a character's RP age (never the player's real age). */
public interface ResidentAgeProvider {
    OptionalInt age(UUID residentId);
}
