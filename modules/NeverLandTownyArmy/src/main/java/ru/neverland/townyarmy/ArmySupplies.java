package ru.neverland.townyarmy;
import java.util.*;import java.util.concurrent.*;import static ru.neverland.townyarmy.ArmyModel.*;
/** One durable invoice per transfer; Army credit precedes provider receipt cleanup. */
public final class ArmySupplies {
    public interface Store{Transfer transfer(UUID id);CompletionStage<Void> phase(Transfer value)throws Exception;CompletionStage<Void> apply(UUID id)throws Exception;}
    public interface Resources{String status(UUID id)throws Exception;boolean reserve(UUID id,UUID town,Map<String,Long> amounts)throws Exception;void consume(UUID id)throws Exception;void forget(UUID id)throws Exception;}
    private ArmySupplies(){}
    public static CompletionStage<Void> resume(UUID id,Store store,Resources resources)throws Exception{ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {var t=store.transfer(id);if(t==null)throw new IllegalArgumentException("Поставка не найдена");
        if(t.phase()==Phase.PLANNED){String status=resources.status(id);if(status.equals("NONE")){if(!resources.reserve(id,t.town(),t.amounts()))return store.phase(t.phase(Phase.DECLINED));status=resources.status(id);}if(status.equals("HELD")){resources.consume(id);status=resources.status(id);}if(status.equals("CONSUMED"))return store.apply(id);if(status.equals("RELEASED"))return store.phase(t.phase(Phase.DECLINED));throw new IllegalStateException("Неизвестное состояние поставки: "+status);}
        if(t.phase()==Phase.APPLIED||t.phase()==Phase.DECLINED){resources.forget(id);return store.phase(t.phase(t.phase()==Phase.APPLIED?Phase.CLOSED:Phase.CANCELLED));}
        return CompletableFuture.completedFuture(null);
    } }
}
