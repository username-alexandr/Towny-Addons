package ru.neverland.townydiplomacy.api;

import java.util.*;
import ru.neverland.core.ApiContract;

/** Main-thread, read-only contract. Treaty maps contain JDK scalar values only. */
public interface TownyDiplomacyApi extends ApiContract {
    @Override default int apiVersion() { return 1; }
    @Override default Set<String> capabilities() { return Set.of("healthy","treaties","tradeBlocked","hostileBlocked","tariffMultiplier","overlord","defenders","relations"); }
    boolean healthy();
    List<Map<String,String>> treaties(UUID town);
    boolean tradeBlocked(UUID first,UUID second);
    boolean hostileBlocked(UUID first,UUID second);
    double tariffMultiplier(UUID first,UUID second,UUID tariffTown);
    UUID overlord(UUID town);
    Set<UUID> defenders(UUID town);
    Set<String> relations(UUID first,UUID second);
}
