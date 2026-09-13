package ru.neverland.townyelections.api;
import java.util.*;
public interface TownyElectionsApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities() { return Set.of("healthy","snapshot","elective"); }
    boolean healthy();
    boolean elective(UUID town);
    /** Public results and schedule only; individual ballots are private. */
    Map<String,String> snapshot(UUID town);
}
