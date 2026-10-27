package ru.neverland.townysieges.api;
import java.util.*;
import org.bukkit.entity.Player;
public interface TownyRaidRepairApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities(){return Set.of("open");}
    void open(Player player,String project);
}
