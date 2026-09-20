package ru.neverland.townyenvironment.api;

import java.util.*;

/** Main-thread API. Paused or unobserved cities have neutral effects; pollution is preserved. */
public interface TownyEnvironmentApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities() { return Set.of("environment", "happiness", "agricultureMultiplier"); }
    Map<String, Object> environment(UUID town);
    double happiness(UUID town);
    /** Multiplies FOOD output only, only for configured agricultural buildings. */
    double agricultureMultiplier(UUID town, String building);
}
