package ru.neverland.townybuilds.api;

import java.io.IOException;
import java.util.*;

/** Main-thread raid escrow; one durable operation per captured town/raid. */
public interface RaidStorageApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities(){return Set.of("healthy","core","reserve","settle","status");}
    boolean healthy();
    Optional<RegisteredBuildingBlock> core(UUID town);
    Map<String,Object> reserve(UUID operation,UUID defender,UUID attacker,UUID actor,int basisPoints)throws IOException;
    Map<String,Object> settle(UUID operation,boolean deliver)throws IOException;
    Map<String,Object> status(UUID operation);
}
