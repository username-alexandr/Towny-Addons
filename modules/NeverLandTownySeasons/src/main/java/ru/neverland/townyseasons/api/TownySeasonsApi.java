package ru.neverland.townyseasons.api;

import java.util.*;

/** Main-thread only. Immutable JDK snapshots; absent world/town and failed providers throw. */
public interface TownySeasonsApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities() { return Set.of("healthy","calendar","townCalendar","productionMultiplier","eventWeight"); }
    boolean healthy();
    Map<String,Object> calendar(UUID world);
    Map<String,Object> townCalendar(UUID town);
    double productionMultiplier(UUID town,String building);
    double eventWeight(UUID town,String eventMode);
}
