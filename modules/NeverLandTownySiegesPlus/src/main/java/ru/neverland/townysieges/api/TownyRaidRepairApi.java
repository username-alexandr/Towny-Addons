package ru.neverland.townysieges.api;
import java.util.*;
import org.bukkit.entity.Player;
public interface TownyRaidRepairApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities(){return Set.of("open","hasRepairDebt","repairDebtRevision");}
    void open(Player player,String project);
    /** Exact SQLite query; callers must not join on a Folia or Suite thread. */
    java.util.concurrent.CompletionStage<Boolean> hasRepairDebt(UUID town,String project);
    /** Capture before the query and compare on the owner region before changing any block. */
    long repairDebtRevision();
}
