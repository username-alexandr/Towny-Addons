package ru.neverland.townyupkeep.api;
import java.util.*;
public interface TownyUpkeepApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("active", "buildings", "plannedBills", "savings"); }
    boolean active(UUID town,String project);
    List<UpkeepSnapshot> buildings(UUID town);
    default Collection<Map<String,Object>> plannedBills(UUID town,int seconds){throw new UnsupportedOperationException();}
    default Map<String,Object> savings(UUID town,Set<String> paused,int seconds){throw new UnsupportedOperationException();}
}
