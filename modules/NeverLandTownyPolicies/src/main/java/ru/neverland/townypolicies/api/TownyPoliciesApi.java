package ru.neverland.townypolicies.api;
import java.util.*;
public interface TownyPoliciesApi {
    double effect(UUID town,String key);
    double productionMultiplier(UUID town,String project);
    double upkeepMultiplier(UUID town,String project);
    double taxMultiplier(UUID town);
    double tariff(UUID town,double manual,double maximum);
    boolean tariffManaged(UUID town);
    boolean importsAllowed(UUID buyer,UUID seller,boolean sameNation);
    Optional<PoliciesSnapshot> policies(UUID town);
    Optional<PoliciesSnapshot> residentPolicies(UUID resident);
}
