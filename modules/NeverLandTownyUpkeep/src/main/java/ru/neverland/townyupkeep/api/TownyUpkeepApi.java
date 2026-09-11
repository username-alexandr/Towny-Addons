package ru.neverland.townyupkeep.api;
import java.util.*;
public interface TownyUpkeepApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("active", "buildings"); }
    boolean active(UUID town,String project);
    List<UpkeepSnapshot> buildings(UUID town);
}
