package ru.neverland.mintcontracts.api;

import java.util.*;
import java.util.concurrent.CompletionStage;
import ru.neverland.core.ApiContract;

/** Server construction integrations: asynchronous, immutable results; never join on a Folia thread. */
public interface ServerMaterialsApi extends ApiContract {
    @Override default Set<String> capabilities(){return Set.of("balances","reserve","consume","release");}
    CompletionStage<Map<String,Long>> balances();
    CompletionStage<String> reserve(UUID operation,String project,Map<String,Long> costs,String actor,String reason);
    CompletionStage<String> consume(UUID operation,String actor,String reason);
    CompletionStage<String> release(UUID operation,String actor,String reason);
}
