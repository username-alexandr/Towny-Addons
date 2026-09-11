package ru.neverland.townyupkeep.api;
/** Test-only compatibility surface for the optional plugin loaded by reflection. */
public interface TownyUpkeepApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("active", "buildings"); }
    boolean active(java.util.UUID town,String project);
}
