package ru.neverland.mintcontracts.service;
import java.util.UUID;
import ru.neverland.mintcontracts.model.DeliveryIntent;
import static ru.neverland.mintcontracts.model.DeliveryIntent.Phase;

public final class MunicipalDeliveries {
    public interface Store{DeliveryIntent get(UUID id);void put(DeliveryIntent d)throws Exception;void complete(DeliveryIntent d)throws Exception;}
    public interface Warehouse{String deposit(DeliveryIntent d)throws Exception;void acknowledge(DeliveryIntent d)throws Exception;}
    @FunctionalInterface public interface Take{boolean take()throws Exception;}
    private final Store store;private final Warehouse warehouse;
    public MunicipalDeliveries(Store store,Warehouse warehouse){this.store=store;this.warehouse=warehouse;}
    public void start(DeliveryIntent d,Take take)throws Exception{
        if(store.get(d.id())!=null)throw new IllegalArgumentException("Поставка уже существует");
        store.put(d);boolean removed;
        try{removed=take.take();}catch(Exception ex){return;}
        store.put(d.phase(removed?Phase.TAKEN:Phase.REJECTED));if(removed)process(d.id());
    }
    public void process(UUID id)throws Exception {
        DeliveryIntent d=store.get(id);if(d==null)return;
        if(d.phase()==Phase.TAKEN){if(!"DELIVERED".equals(warehouse.deposit(d)))return;store.complete(d.phase(Phase.COMPLETE));d=store.get(id);}
        if(d.phase()==Phase.COMPLETE&&!d.acknowledged()){warehouse.acknowledge(d);store.put(d.acknowledge());}
    }
    public void resolve(UUID id,boolean taken)throws Exception {
        var d=store.get(id);if(d==null||d.phase()!=Phase.PLAYER_PENDING)throw new IllegalArgumentException("Поставка не ожидает сверки инвентаря");
        store.put(d.phase(taken?Phase.TAKEN:Phase.REJECTED));
    }
}
