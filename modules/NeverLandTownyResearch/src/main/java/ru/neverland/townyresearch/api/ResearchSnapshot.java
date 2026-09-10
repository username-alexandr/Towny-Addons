package ru.neverland.townyresearch.api;
import ru.neverland.townyresearch.model.CityStudy;
import java.util.*;
public record ResearchSnapshot(UUID townId,String townName,CityStudy state,long knowledge,long reserve,boolean paused,String status,Map<String,Integer> buildings){public ResearchSnapshot{buildings=Map.copyOf(buildings);}}
