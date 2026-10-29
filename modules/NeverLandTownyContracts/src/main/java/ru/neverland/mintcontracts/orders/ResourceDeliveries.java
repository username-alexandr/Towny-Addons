package ru.neverland.mintcontracts.orders;

import java.util.*;
import java.util.concurrent.*;
import ru.neverland.earnings.RewardLedger;

/** SQLite intent -> owning player save -> atomic stock credit -> durable reward release. */
public final class ResourceDeliveries {
    public interface Owner {<T> CompletionStage<T> call(Callable<T> operation);}
    public interface Inventory extends Owner {
        void check(ServerMaterials.Delivery delivery)throws Exception;
        void takeAndSave(ServerMaterials.Delivery delivery)throws Exception;
        boolean hasReceipt(ServerMaterials.Delivery delivery);
        void proveReceipt(ServerMaterials.Delivery delivery)throws Exception;
    }
    private final ServerMaterials materials;
    private final RewardLedger rewards;
    private final Owner ledgerOwner;
    public ResourceDeliveries(ServerMaterials materials,RewardLedger rewards,Owner ledgerOwner){this.materials=materials;this.rewards=rewards;this.ledgerOwner=ledgerOwner;}
    private void matching(ServerMaterials.Delivery d,RewardLedger.Entry e){
        if(e==null||!e.player().equals(d.player())||e.cents()!=d.cents()||!e.source().equals("orders:"+d.template()))throw new IllegalStateException("Поставка и журнал выплаты не совпадают; нужна проверка");
    }
    public CompletionStage<Void> deliver(ServerMaterials.Delivery delivery,Inventory actor){return deliver(delivery,actor,"","");}
    public CompletionStage<Void> deliver(ServerMaterials.Delivery delivery,Inventory actor,String project,String town){
        return materials.prepare(delivery).thenCompose(d->project.isEmpty()||!d.state().equals("PREPARED")?CompletableFuture.completedFuture(d):materials.cooperativeContext(d.id(),d.player(),town,project).thenApply(ignored->d)).thenCompose(d->actor.call(()->{
            RewardLedger.Entry entry=rewards.get(d.id());
            if(entry!=null){
                matching(d,entry);
                if(!d.state().equals("PREPARED"))return d;
                if(entry.state()!=RewardLedger.State.HELD||!actor.hasReceipt(d))throw new IllegalStateException("Сдача уже начата. Ресурсы повторно не списаны; нужна сверка.");
                actor.proveReceipt(d);return d;
            }
            if(!d.state().equals("PREPARED"))throw new IllegalStateException("Поставка без квитанции выплаты; нужна сверка");
            actor.check(d);
            rewards.reserve(d.id(),d.player(),d.cents(),"orders:"+d.template(),true);
            actor.takeAndSave(d);
            return d;
        })).thenCompose(d->d.state().equals("CONFIRMED")?CompletableFuture.completedFuture(d):materials.confirm(d.id(),"player inventory and persistent order receipt saved"))
                .thenCompose(this::settle);
    }
    public CompletionStage<Void> recover(ServerMaterials.Delivery d,Inventory actor){
        if(!d.state().equals("PREPARED"))return settle(d);
        return actor.call(()->{
            var entry=rewards.get(d.id());matching(d,entry);
            if(entry.state()!=RewardLedger.State.HELD||!actor.hasReceipt(d))throw new IllegalStateException("Сдача ожидает сверки сохранённого инвентаря");
            actor.proveReceipt(d);return d;
        }).thenCompose(value->materials.confirm(value.id(),"saved player receipt recovered after interruption")).thenCompose(this::settle);
    }
    public CompletionStage<Void> settle(ServerMaterials.Delivery d){
        if(d.state().equals("PREPARED"))return CompletableFuture.failedFuture(new IllegalArgumentException("Нет подтверждения передачи ресурсов"));
        return ledgerOwner.call(()->{
            var e=rewards.get(d.id());
            if(e==null&&d.state().equals("VOID"))return null;
            matching(d,e);
            if(d.state().equals("CONFIRMED")){
                if(e.state()==RewardLedger.State.VOID)throw new IllegalStateException("Ресурсы подтверждены, выплата отменена; нужна сверка");
                if(e.state()==RewardLedger.State.HELD)rewards.release(d.id(),"server materials database confirmed "+d.id());
            }else{
                if(e.state()!=RewardLedger.State.HELD&&e.state()!=RewardLedger.State.VOID)throw new IllegalStateException("Выплата уже передана в банк; отмена поставки запрещена");
                if(e.state()==RewardLedger.State.HELD)rewards.resolve(d.id(),"untouched","resources-recovery","administrator confirmed inventory untouched in materials database");
            }
            return null;
        }).thenCompose(ignored->materials.settled(d.id()));
    }
    public CompletionStage<Void> resolve(UUID id,boolean taken,String actor,String reason){
        return ledgerOwner.call(()->{
            var e=rewards.get(id);if(e==null||e.state()!=RewardLedger.State.HELD)throw new IllegalArgumentException("Поставка должна ожидать сверки ресурсов");return e;
        }).thenCompose(ignored->taken?materials.confirm(id,"admin "+actor+": "+reason):materials.voidDelivery(id,"admin "+actor+": "+reason)).thenCompose(this::settle);
    }
    public CompletionStage<Void> reconcile(){return materials.unsettled().thenCompose(rows->{
        var jobs=new ArrayList<CompletableFuture<Void>>();for(var d:rows)jobs.add(settle(d).toCompletableFuture());return CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new));
    });}
}
