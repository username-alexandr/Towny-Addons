package ru.neverland.townyupkeep.api;
import java.util.*;
public interface TownyUpkeepApi {
    boolean active(UUID town,String project);
    List<UpkeepSnapshot> buildings(UUID town);
}
