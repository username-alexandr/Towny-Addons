package ru.neverland.mintcontracts;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;
import org.bukkit.Material;
import ru.neverland.mintcontracts.model.*;
import ru.neverland.mintcontracts.service.*;

public final class MunicipalContractsSmoke {
    private static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
    private interface Op{void run()throws Exception;}
    private static void fails(Op op)throws Exception{try{op.run();}catch(Exception ex){return;}throw new AssertionError("expected failure");}
    private static final class Money implements MunicipalPayments.Store{
        Map<UUID,MunicipalPayment> rows=new HashMap<>();boolean failIntent,failResult;
        public MunicipalPayment get(UUID id){return rows.get(id);}public void put(MunicipalPayment p)throws Exception{if(failIntent)throw new IOException("disk unavailable");rows.put(p.id(),p);}public void result(MunicipalPayment p,boolean paid)throws Exception{if(failResult)throw new IOException("acknowledgement lost");rows.put(p.id(),p);}
    }
    private static final class Delivery implements MunicipalDeliveries.Store,MunicipalDeliveries.Warehouse{
        Map<UUID,DeliveryIntent> rows=new HashMap<>();Set<UUID> receipts=new HashSet<>();int physical,progress,takes;boolean failIntent,failComplete,full,failAck;
        public DeliveryIntent get(UUID id){return rows.get(id);}public void put(DeliveryIntent d)throws Exception{if(failIntent)throw new IOException("disk unavailable");rows.put(d.id(),d);}public void complete(DeliveryIntent d)throws Exception{if(failComplete)throw new IOException("crash before contract progress");rows.put(d.id(),d);progress+=d.amount();}
        public String deposit(DeliveryIntent d){if(receipts.contains(d.id()))return "DELIVERED";if(full)return "FULL";physical+=d.amount();receipts.add(d.id());return "DELIVERED";}
        public void acknowledge(DeliveryIntent d)throws Exception{if(failAck)throw new IOException("ack failed");receipts.remove(d.id());}
    }
    public static void main(String[] args)throws Exception{
        CompanyContractsSmoke.main(args);UUID city=UUID.randomUUID(),actor=UUID.randomUUID(),id=UUID.randomUUID(),world=UUID.randomUUID();long[] transfers={0};Money store=new Money();
        MunicipalPayment reward=new MunicipalPayment(id,UUID.randomUUID(),city,actor,MunicipalPayment.Kind.REWARD,12345,MunicipalPayment.Phase.READY,1);store.put(reward);
        var money=new MunicipalPayments(store,p->{transfers[0]+=p.cents();return true;});store.failIntent=true;fails(()->money.process(id));check(transfers[0]==0,"no bank before durable intent");store.failIntent=false;store.failResult=true;fails(()->money.process(id));check(transfers[0]==12345&&store.get(id).phase()==MunicipalPayment.Phase.PENDING,"credit survives acknowledgement loss");store.failResult=false;
        new MunicipalPayments(store,p->{transfers[0]+=p.cents();return true;}).process(id);check(transfers[0]==12345,"restart never repeats uncertain bank operation");money.resolve(id,true);money.process(id);check(transfers[0]==12345&&store.get(id).phase()==MunicipalPayment.Phase.DONE,"manual applied reconciliation without credit");
        store.put(reward);new MunicipalPayments(store,p->{throw new IOException("unknown bank result");}).process(id);check(store.get(id).phase()==MunicipalPayment.Phase.PENDING,"bank exception remains pending");money.resolve(id,false);check(store.get(id).phase()==MunicipalPayment.Phase.READY,"rejected reward may retry safely");
        var reserve=new MunicipalPayment(UUID.randomUUID(),UUID.randomUUID(),city,city,MunicipalPayment.Kind.RESERVE,100,MunicipalPayment.Phase.READY,1);store.put(reserve);new MunicipalPayments(store,p->false).process(reserve.id());check(store.get(reserve.id()).phase()==MunicipalPayment.Phase.REJECTED,"unfunded publication rejected");
        check(MunicipalPayment.amount("12.34")==1234,"exact cents");fails(()->MunicipalPayment.amount("NaN"));fails(()->MunicipalPayment.amount("0.001"));fails(()->MunicipalPayment.amount("-1"));
        Delivery delivery=new Delivery();var jobs=new MunicipalDeliveries(delivery,delivery);var shipment=new DeliveryIntent(UUID.randomUUID(),UUID.randomUUID(),city,actor,"c2FtcGxl",1000,DeliveryIntent.Phase.PLAYER_PENDING,1,false);
        delivery.failIntent=true;fails(()->jobs.start(shipment,()->{delivery.takes++;return true;}));check(delivery.takes==0,"inventory untouched before durable delivery intent");delivery.failIntent=false;delivery.failComplete=true;
        fails(()->jobs.start(shipment,()->{delivery.takes++;return true;}));check(delivery.physical==1000&&delivery.progress==0&&delivery.takes==1,"crash between physical warehouse receipt and progress");delivery.failComplete=false;delivery.failAck=true;fails(()->jobs.process(shipment.id()));check(delivery.physical==1000&&delivery.progress==1000,"receipt prevents duplicated delivery");delivery.failAck=false;
        new MunicipalDeliveries(delivery,delivery).process(shipment.id());jobs.process(shipment.id());check(delivery.physical==1000&&delivery.progress==1000&&delivery.get(shipment.id()).acknowledged(),"restart and duplicate completion cannot create items or progress");
        var uncertain=new DeliveryIntent(UUID.randomUUID(),shipment.contract(),city,actor,"c2FtcGxl",5,DeliveryIntent.Phase.PLAYER_PENDING,2,false);jobs.start(uncertain,()->{throw new IOException("player save uncertain");});jobs.process(uncertain.id());check(delivery.get(uncertain.id()).phase()==DeliveryIntent.Phase.PLAYER_PENDING&&delivery.physical==1000,"ambiguous inventory removal never auto repeats");jobs.resolve(uncertain.id(),true);delivery.full=true;jobs.process(uncertain.id());check(delivery.get(uncertain.id()).phase()==DeliveryIntent.Phase.TAKEN,"full warehouse retains cargo intent");delivery.full=false;jobs.process(uncertain.id());check(delivery.physical==1005&&delivery.progress==1005,"capacity restoration completes once");
        WorkArea area=new WorkArea(world,-2,64,0,0,0,Set.of("-2:0","-1:0"));Map<String,WorkProof> proofs=Map.of("-2:0",new WorkProof(actor,1000),"-1:0",new WorkProof(actor,1000));
        check(!RoadAudit.inspect(area,proofs,1050,60,(x,y,z)->RoadAudit.Cell.VALID).complete(),"road must stay stable");
        var valid=RoadAudit.inspect(area,proofs,1060,60,(x,y,z)->RoadAudit.Cell.VALID);check(valid.complete()&&valid.contributions().get(actor)==2,"full road credits distinct required positions");
        check(!RoadAudit.inspect(area,proofs,2000,60,(x,y,z)->x==0?RoadAudit.Cell.INVALID:RoadAudit.Cell.VALID).complete(),"pre-existing paving must also remain intact");
        var unloaded=RoadAudit.inspect(area,proofs,2000,60,(x,y,z)->RoadAudit.Cell.UNKNOWN);check(!unloaded.complete()&&unloaded.invalidProofs().isEmpty()&&unloaded.contributions().isEmpty(),"unloaded terrain neither completes nor destroys proofs");
        var broken=RoadAudit.inspect(area,proofs,2000,60,(x,y,z)->x==-1?RoadAudit.Cell.INVALID:RoadAudit.Cell.VALID);check(!broken.complete()&&broken.invalidProofs().equals(Set.of("-1:0")),"replaced or unsupported road revokes proof");
        fails(()->WorkArea.rectangle(0,0,1000000,1000000,256));fails(()->new WorkArea(world,0,0,0,1,1,Set.of("-1:0")));
        ScoutTracker scouts=new ScoutTracker();scouts.move(actor,world,-1>>4,-17>>4,1000);check(scouts.ready(actor,world,-1,-2,31000,30000)!=null,"negative block coordinates map to correct chunks");scouts.move(actor,world,-1,-2,20000);check(scouts.ready(actor,world,-1,-2,31000,30000)!=null,"same chunk movement preserves dwell");scouts.reset(actor);check(scouts.ready(actor,world,-1,-2,61000,30000)==null,"teleport invalidates dwell");scouts.move(actor,world,0,0,61000);scouts.move(actor,world,1,0,62000);check(scouts.ready(actor,world,1,0,91000,30000)==null,"new chunk restarts dwell");check(new ScoutTracker().players().isEmpty(),"restart does not restore unfinished visits");
        check(!ScoutTracker.elapsed(new ScoutTracker.Visit(world,0,0,1000),50000,51000,30000),"time before contract creation never fulfils scouting dwell");check(ScoutTracker.elapsed(new ScoutTracker.Visit(world,0,0,1000),50000,80000,30000),"dwell after publication qualifies");
        var c=new ActiveContract(UUID.randomUUID(),city,"municipal_smoke",1,10000,0,2,123.45,Map.of());c.snapshot(new ContractDefinition("municipal_smoke","Проверочная дорога",ContractType.ROAD,Material.STONE_BRICKS,0,List.of(),"STONE_BRICKS",null,2,123.45,10));c.area(area);c.proof("-2:0",new WorkProof(actor,1000));c.replaceWorkProgress(Map.of(actor,1));
        Path folder=Files.createTempDirectory("municipal-smoke"),file=folder.resolve("data.yml");var repo=new ContractRepository(file.toFile(),java.util.logging.Logger.getAnonymousLogger());repo.add(c);repo.payment(reward.phase(MunicipalPayment.Phase.PENDING));repo.saveOrThrow();repo=new ContractRepository(file.toFile(),java.util.logging.Logger.getAnonymousLogger());repo.load();var restored=repo.find(city,c.id().toString());check(restored.snapshot().name().equals("Проверочная дорога")&&restored.area().equals(area)&&restored.proofs().equals(c.proofs())&&restored.progress()==1,"immutable terms, region and contributor proofs survive restart");check(repo.payments().get(id).phase()==MunicipalPayment.Phase.PENDING,"uncertain money survives repository restart");
        repo.addHistory(restored,ContractStatus.CANCELLED,0,123.45,30,10001);repo.saveOrThrow();repo.load();check(repo.history(city).get(0).name().equals("Проверочная дорога"),"custom history keeps Russian name");Files.delete(file);Files.delete(folder);
        System.out.println("MunicipalContractsSmoke OK: durable money, delivery crash windows, road verification, scout dwell, immutable conditions and restart");
    }
}
