package ru.neverland.townyspecialization.api;
import java.util.*;
public interface TownySpecializationApi {
    boolean canUseBuilding(UUID town,String project);
    double bonus(UUID town,String effect);
    double productionBonus(UUID town,String project);
    Optional<SpecializationSnapshot> specialization(UUID town);
    Optional<SpecializationSnapshot> residentSpecialization(UUID resident);
    Collection<SpecializationSnapshot> towns();
}
