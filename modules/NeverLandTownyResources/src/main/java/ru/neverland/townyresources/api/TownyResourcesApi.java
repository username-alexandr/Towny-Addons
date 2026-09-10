package ru.neverland.townyresources.api;
import java.util.*;
/** Immutable snapshots; reservation mutations require the server thread and durable receipts. */
public interface TownyResourcesApi {
    Optional<ResourceSnapshot> resources(UUID townId);
    Optional<ResourceSnapshot> residentResources(UUID residentId);
    Collection<ResourceSnapshot> towns();
    /** Resource values use thousandths, keyed by resource ID. Protects city and population reserves. */
    boolean reserveResources(UUID invoice, UUID town, Map<String,Long> amounts) throws java.io.IOException;
    void settleResources(UUID invoice, boolean consume) throws java.io.IOException;
    String reservationStatus(UUID invoice);
    Map<UUID,String> reservations();
    void forgetReservation(UUID invoice) throws java.io.IOException;
}
