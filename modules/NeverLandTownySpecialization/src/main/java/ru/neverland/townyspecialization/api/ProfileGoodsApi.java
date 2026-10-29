package ru.neverland.townyspecialization.api;

import java.util.*;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;

/** Funded production goods only. Immutable JDK snapshots; all database work is asynchronous. */
public interface ProfileGoodsApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities(){return Set.of("ready","active","stock","entries","entry","quote","confirm","accept","cancel","adminCancel");}
    boolean ready();
    /** Last acknowledged bounded active snapshot; does not touch the database on the calling thread. */
    List<Map<String,Object>> active();
    CompletionStage<List<Map<String,Object>>> stock(UUID town,int page);
    CompletionStage<List<Map<String,Object>>> entries(UUID town,int page);
    CompletionStage<Map<String,Object>> entry(UUID operation);
    /** A null counterparty creates a seasonal reserve; otherwise an exact bilateral offer. No stock moves here. */
    CompletionStage<Map<String,Object>> quote(Player mayor,UUID operation,UUID counterparty,UUID world,String period,String title,Map<String,Long> give,Map<String,Long> receive,long expires);
    /** The initiating mayor confirms the exact quote. Its source goods move from available into escrow atomically. */
    CompletionStage<Map<String,Object>> confirm(Player mayor,Map<String,Object> quote);
    /** The other mayor confirms the same stored hash. Both towns and the unique receipt commit in one transaction. */
    CompletionStage<Map<String,Object>> accept(Player mayor,UUID operation,UUID receipt,String hash);
    CompletionStage<Map<String,Object>> cancel(Player mayor,UUID operation,String reason);
    CompletionStage<Map<String,Object>> adminCancel(UUID operation,UUID actor,String actorName,String reason);
}
