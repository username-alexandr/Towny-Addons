package ru.neverland.townyenvironment.api;

import java.util.*;

/** Main-thread API. Paused or unobserved cities have neutral effects; pollution is preserved. */
public interface TownyEnvironmentApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities() { return Set.of("environment", "happiness", "agricultureMultiplier","forecast","districts"); }
    /** Read-only cached district sources; safe for async callers. */
    default Map<String,Object> districts(UUID town){return Map.of("available",false,"pending",false,"issue","Обновите модуль экологии","rows",Map.of(),"totals",Map.of());}
    Map<String, Object> environment(UUID town);
    double happiness(UUID town);
    /** Multiplies FOOD output only, only for configured agricultural buildings. */
    double agricultureMultiplier(UUID town, String building);
    default Map<String,Object> forecast(UUID town,String building,int targetLevel,int horizonSeconds){return Map.of("ready",false,"status","Обновите модуль экологии");}
}
