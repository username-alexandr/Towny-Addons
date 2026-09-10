package ru.neverland.townyresources.api;
import java.util.*;
/** Read-only city ledger; does not convert Minecraft items or expose mutable balances. */
public interface TownyResourcesApi {
    Optional<ResourceSnapshot> resources(UUID townId);
    Optional<ResourceSnapshot> residentResources(UUID residentId);
    Collection<ResourceSnapshot> towns();
}
