package ru.neverland.townyspecialization.api;
import ru.neverland.townyspecialization.model.CityChoice;
import java.util.*;
public record SpecializationSnapshot(UUID townId,String townName,int townLevel,int hallLevel,CityChoice state,int uniqueLevel,Map<String,Double> bonuses,boolean paused,String status){public SpecializationSnapshot{bonuses=Map.copyOf(bonuses);}}
