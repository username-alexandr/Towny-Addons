package ru.neverland.townyjobs.api;
import java.util.UUID;
/** Main-thread live queries. Professions never grant building ownership or income for item actions. */
public interface TownyJobsApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("bonus", "supports", "townBonus", "workers"); }
    double bonus(UUID town,String project);
    double townBonus(UUID town,String effect);
    int workers(UUID town,String project);
    boolean supports(String project);
}
