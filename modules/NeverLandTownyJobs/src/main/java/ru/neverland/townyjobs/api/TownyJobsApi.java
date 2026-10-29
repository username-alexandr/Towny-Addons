package ru.neverland.townyjobs.api;
import java.util.UUID;
/** Live city bonuses and acknowledged mastery. Monetary action rewards remain in the separate capped payroll. */
public interface TownyJobsApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("bonus", "supports", "townBonus", "workers", "mastery"); }
    default java.util.Map<String,Object> mastery(UUID actor){return java.util.Map.of("available",false);}
    double bonus(UUID town,String project);
    double townBonus(UUID town,String effect);
    int workers(UUID town,String project);
    boolean supports(String project);
}
