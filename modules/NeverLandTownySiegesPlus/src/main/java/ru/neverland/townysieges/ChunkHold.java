package ru.neverland.townysieges;
import java.util.*;
/** Consecutive, observed occupation. Lag, absent attackers and defenders reset the clock. */
final class ChunkHold {
    record State(Set<UUID> holders,long observed,long millis){State{holders=Set.copyOf(holders);}}
    static State step(State previous,Set<UUID> attackers,boolean contested,long now){
        if(contested||attackers.isEmpty())return null;
        if(previous==null||now<previous.observed()||now-previous.observed()>2500||Collections.disjoint(previous.holders(),attackers))return new State(attackers,now,0);
        return new State(attackers,now,previous.millis()+Math.min(1500,now-previous.observed()));
    }
    static boolean complete(State state,int seconds){return state!=null&&state.millis()>=seconds*1000L;}
}
