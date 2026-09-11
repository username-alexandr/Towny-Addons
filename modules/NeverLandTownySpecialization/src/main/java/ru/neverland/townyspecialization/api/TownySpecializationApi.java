package ru.neverland.townyspecialization.api;
import java.util.*;
public interface TownySpecializationApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("bonus", "canUseBuilding", "productionBonus", "residentSpecialization", "specialization", "towns"); }
    boolean canUseBuilding(UUID town,String project);
    double bonus(UUID town,String effect);
    double productionBonus(UUID town,String project);
    Optional<SpecializationSnapshot> specialization(UUID town);
    Optional<SpecializationSnapshot> residentSpecialization(UUID resident);
    Collection<SpecializationSnapshot> towns();
}
