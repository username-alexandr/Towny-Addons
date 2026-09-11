package ru.neverland.townyresources.api;
import java.util.*;
/** Immutable snapshots; reservation mutations require the server thread and durable receipts. */
public interface TownyResourcesApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("productionWeeks", "reservationStatus", "reservations", "residentResources", "resources", "towns"); }
    Optional<ResourceSnapshot> resources(UUID townId);
    Optional<ResourceSnapshot> residentResources(UUID residentId);
    Collection<ResourceSnapshot> towns();
    /** 13 UTC calendar weeks; thousandths per resource, plus an integer cycle count. */
    default Map<String,Map<String,Long>> productionWeeks(UUID town){return Map.of();}
    /** Resource values use thousandths, keyed by resource ID. Protects city and population reserves. */
    boolean reserveResources(UUID invoice, UUID town, Map<String,Long> amounts) throws java.io.IOException;
    void settleResources(UUID invoice, boolean consume) throws java.io.IOException;
    String reservationStatus(UUID invoice);
    Map<UUID,String> reservations();
    void forgetReservation(UUID invoice) throws java.io.IOException;
}
