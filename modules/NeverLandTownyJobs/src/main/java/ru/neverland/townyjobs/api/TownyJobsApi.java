package ru.neverland.townyjobs.api;
import java.util.UUID;
/** Main-thread live queries. Professions never grant building ownership or income for item actions. */
public interface TownyJobsApi {
    double bonus(UUID town,String project);
    double townBonus(UUID town,String effect);
    int workers(UUID town,String project);
    boolean supports(String project);
}
