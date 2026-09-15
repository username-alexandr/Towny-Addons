package ru.neverland.townyarmy;
import java.util.*;import static ru.neverland.townyarmy.ArmyModel.*;
/** One durable invoice per transfer; Army credit precedes provider receipt cleanup. */
public final class ArmySupplies {
    public interface Store{Transfer transfer(UUID id);void phase(Transfer value)throws Exception;void apply(UUID id)throws Exception;}
    public interface Resources{String status(UUID id)throws Exception;boolean reserve(UUID id,UUID town,Map<String,Long> amounts)throws Exception;void consume(UUID id)throws Exception;void forget(UUID id)throws Exception;}
    private ArmySupplies(){}
    public static void resume(UUID id,Store store,Resources resources)throws Exception{var t=store.transfer(id);if(t==null)throw new IllegalArgumentException("Поставка не найдена");
        if(t.phase()==Phase.PLANNED){String status=resources.status(id);if(status.equals("NONE")){if(!resources.reserve(id,t.town(),t.amounts())){store.phase(t.phase(Phase.DECLINED));return;}status=resources.status(id);}if(status.equals("HELD")){resources.consume(id);status=resources.status(id);}if(status.equals("CONSUMED")){store.apply(id);return;}if(status.equals("RELEASED")){store.phase(t.phase(Phase.DECLINED));return;}throw new IllegalStateException("Неизвестное состояние поставки: "+status);}
        if(t.phase()==Phase.APPLIED||t.phase()==Phase.DECLINED){resources.forget(id);store.phase(t.phase(t.phase()==Phase.APPLIED?Phase.CLOSED:Phase.CANCELLED));}
    }
}
