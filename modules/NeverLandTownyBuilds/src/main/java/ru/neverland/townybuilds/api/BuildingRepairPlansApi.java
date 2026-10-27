package ru.neverland.townybuilds.api;
import java.util.*;
import org.bukkit.entity.Player;
/** Registered, completed geometry only; no world reads or mutation. */
public interface BuildingRepairPlansApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities(){return Set.of("plans","plan","canRepair");}
    Collection<Map<String,Object>> plans(UUID town);
    Map<String,Object> plan(UUID town,String project);
    boolean canRepair(Player actor,UUID town);
}
