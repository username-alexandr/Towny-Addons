package ru.neverland.townycitizens.api;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ru.neverland.core.ApiContract;

/** Main-thread, read-only API. UUIDs always mean town first, player second. */
public interface TownyCitizensApi extends ApiContract {
    default Set<String> capabilities() { return Set.of("healthy", "status", "expiresAt", "allows", "taxMultiplier", "passportFields", "passport"); }
    boolean healthy();
    Map<String, String> passport(UUID resident);
    String status(UUID town, UUID resident);
    long expiresAt(UUID town, UUID resident);
    boolean allows(UUID town, UUID resident, String right);
    double taxMultiplier(UUID town, UUID resident);
    /** Passport adapters may display these fields without copying or modifying identity data. */
    Map<String, String> passportFields(UUID town, UUID resident);
}
