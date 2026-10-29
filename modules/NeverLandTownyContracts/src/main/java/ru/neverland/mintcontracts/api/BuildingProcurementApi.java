package ru.neverland.mintcontracts.api;

import java.util.*;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;
import ru.neverland.core.ApiContract;

/** City-funded building deliveries. Player authorization runs on the owning scheduler; replies never wait for disk. */
public interface BuildingProcurementApi extends ApiContract {
    @Override default Set<String> capabilities(){return Set.of("offers","quote","confirm","status","cancel","requests");}
    CompletionStage<List<Map<String,Object>>> offers(Player player,String project);
    CompletionStage<Map<String,Object>> quote(Player player,String project,String itemKey);
    CompletionStage<Map<String,Object>> confirm(Player player,UUID quote);
    CompletionStage<Map<String,Object>> status(Player player,UUID request);
    CompletionStage<Map<String,Object>> cancel(Player player,UUID request);
    CompletionStage<List<Map<String,Object>>> requests(Player player,int page);
}
