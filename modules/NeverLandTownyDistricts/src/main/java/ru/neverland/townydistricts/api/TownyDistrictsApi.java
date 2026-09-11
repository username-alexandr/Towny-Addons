package ru.neverland.townydistricts.api;
import java.util.*;
import ru.neverland.townydistricts.model.*;
/** Immutable snapshots; building multipliers include the capped district combination. */
public interface TownyDistrictsApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("districtAt", "districts", "multiplier"); }
    double multiplier(UUID town,String project);
    Collection<District> districts(UUID town);
    Optional<District> districtAt(UUID world,int blockX,int blockZ);
}
