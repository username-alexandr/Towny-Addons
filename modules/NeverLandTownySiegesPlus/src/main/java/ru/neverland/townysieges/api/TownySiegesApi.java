package ru.neverland.townysieges.api;

import java.util.*;

/** Main-thread, read-only API. Immutable JDK values; queries never start wars or modify buildings. */
public interface TownySiegesApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities() { return Set.of("healthy","warStatus","defenses","restrictions"); }
    boolean healthy();
    Map<String,String> warStatus();
    Collection<Map<String,Object>> defenses(UUID town);
    /** Online player's current restrictions at the supplied position; offline players return an empty map. */
    Map<String,Object> restrictions(UUID player,UUID world,double x,double y,double z);
}
