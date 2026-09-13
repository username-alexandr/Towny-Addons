package ru.neverland.townycrime.api;
import java.util.*;
/** Main-thread v1. Immutable JDK snapshots. Missing/paused inputs reject new income quotes.
 * shopIncomeBasisPoints returns 5000..10000; the caller freezes net cents before payment.
 * Snapshot fields: town(UUID), level/target/happiness/guard(Double), workers(Integer),
 * paused(Boolean), status(String), nextCycle(Long), incomeBasisPoints(Integer),
 * incident(Map: id,kind,resource,amount,phase,until,active), or an empty incident map.
 */
public interface TownyCrimeApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities(){return Set.of("crime","towns","shopIncomeBasisPoints");}
    Optional<Map<String,Object>> crime(UUID town);
    Collection<Map<String,Object>> towns();
    int shopIncomeBasisPoints(UUID town);
}
