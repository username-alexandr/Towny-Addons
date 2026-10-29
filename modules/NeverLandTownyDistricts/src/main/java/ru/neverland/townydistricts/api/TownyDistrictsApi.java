package ru.neverland.townydistricts.api;
import java.util.*;
import ru.neverland.townydistricts.model.*;
/** Immutable snapshots; building multipliers include the capped district combination. */
public interface TownyDistrictsApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("districtAt", "districts", "multiplier", "mobFarmPlots", "mobProtectionAt", "assessment", "patrolLayout"); }
    /** Read-only primitive report. Pending/unavailable data must never be treated as zero services. */
    default Map<String,Object> assessment(UUID town){return Map.of("available",false,"pending",false,"rows",Map.of());}
    /** Cached physical layout only: never refreshes, reads files or scans the world. Dirty/stale is unavailable. */
    default Map<String,Object> patrolLayout(UUID town){return Map.of("available",false,"pending",false,"rows",List.of());}
    double multiplier(UUID town,String project);
    Collection<District> districts(UUID town);
    Optional<District> districtAt(UUID world,int blockX,int blockZ);
    /** Native Towny plots with local or forced mob permission, across every world. */
    default Collection<MobFarmPlot> mobFarmPlots(UUID town){return List.of();}
    default Optional<MobFarmPlot> mobProtectionAt(UUID world,int blockX,int blockZ){return Optional.empty();}
}
