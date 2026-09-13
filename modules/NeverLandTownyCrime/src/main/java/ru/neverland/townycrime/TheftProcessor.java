package ru.neverland.townycrime;
import java.util.*;
/** A persisted plan precedes reservation; a persisted outcome precedes receipt cleanup. */
public final class TheftProcessor {
    public interface Store{CrimeState get(UUID town);void put(CrimeState state)throws Exception;}
    public interface Resources{String status(UUID id)throws Exception;boolean reserve(UUID id,UUID town,Map<String,Long> amounts)throws Exception;void consume(UUID id)throws Exception;void forget(UUID id)throws Exception;}
    private TheftProcessor(){}
    public static boolean resume(UUID town,Store store,Resources resources)throws Exception{
        var state=store.get(town);var i=state.incident();if(i==null||!i.pending())return false;
        if(i.phase().equals("PLANNED")){
            if(i.kind().equals("EXTORTION")){store.put(state.incident(i.phase("APPLIED")));return true;}
            String status=resources.status(i.id());
            if(status.equals("NONE")){
                if(!resources.reserve(i.id(),town,Map.of(i.resource(),i.amount()))){store.put(state.incident(i.phase("SKIPPED")));return false;}
                status=resources.status(i.id());
            }
            if(status.equals("HELD")){resources.consume(i.id());status=resources.status(i.id());}
            if(status.equals("CONSUMED")){store.put(state.incident(i.phase("APPLIED")));return true;}
            if(status.equals("RELEASED")){store.put(state.incident(i.phase("SKIPPED")));return false;}
            throw new IllegalStateException("Неизвестный результат кражи: "+status);
        }
        if(i.kind().equals("BURGLARY"))resources.forget(i.id());
        store.put(state.incident(i.phase(i.phase().equals("APPLIED")?"CLOSED":"CANCELLED")));return false;
    }
}
