package ru.neverland.townydistricts.api;
import java.util.*;
import ru.neverland.townydistricts.model.*;
/** Immutable snapshots; building multipliers include the capped district combination. */
public interface TownyDistrictsApi {
    double multiplier(UUID town,String project);
    Collection<District> districts(UUID town);
    Optional<District> districtAt(UUID world,int blockX,int blockZ);
}
