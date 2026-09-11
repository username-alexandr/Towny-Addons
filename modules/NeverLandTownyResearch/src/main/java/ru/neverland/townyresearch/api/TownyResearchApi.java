package ru.neverland.townyresearch.api;
import java.util.*;
public interface TownyResearchApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("bonus", "level", "name", "research", "residentResearch", "towns"); }
    String name(String technology);
    int level(UUID town,String technology);
    double bonus(UUID town,String technology);
    Optional<ResearchSnapshot> research(UUID town);
    Optional<ResearchSnapshot> residentResearch(UUID resident);
    Collection<ResearchSnapshot> towns();
}
