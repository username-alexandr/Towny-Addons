package ru.neverland.minttrade;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;
import ru.neverland.minttrade.contract.*;
import static ru.neverland.minttrade.contract.SupplyContract.*;
/** Executable fault-injection tests of the actual journal state machine, without a Minecraft server. */
public final class SupplyContractSmoke {
    static final UUID SELLER=UUID.randomUUID(),BUYER=UUID.randomUUID();static final long START=1_000_000L,RETRY=30_000;
    static SupplyContract proposal(){return SupplyContract.proposal(new Terms(UUID.randomUUID(),SELLER,BUYER,"AA==","Железный слиток",500,200000,7,START,START+DAY));}
    static final class Store implements SupplyProcessor.Store {
        SupplyContract value;Phase failPhase;boolean failFinish;
        Store(SupplyContract c){value=c;}public SupplyContract get(UUID id){return value;}
        public void put(SupplyContract c)throws IOException{if(c.attempt()!=null&&c.attempt().phase()==failPhase||failFinish&&c.attempt()==null)throw new IOException("disk full");value=c;}
    }
    static final class Bank implements SupplyProcessor.Gateway {
        int goods=1000,destination,debits,credits,pickups;long buyer=500000,seller;String stock="MISSING",gate;boolean bankRefuse,creditRefuse,debitCrash,creditCrash,full,busy,ackCrash;
        public String ready(SupplyContract c){return gate;}
        public String reserve(SupplyContract c){if(!stock.equals("MISSING"))return stock;if(busy)return "BUSY";if(goods<500)return "STOCK_LOW";goods-=500;pickups++;return stock="RESERVED";}
        public String settle(SupplyContract c,boolean deliver){if(!stock.equals("RESERVED"))return stock;if(full)return "FULL";if(deliver){destination+=500;stock="DELIVERED";}else{goods+=500;stock="RETURNED";}return stock;}
        public boolean debit(SupplyContract c){if(bankRefuse)return false;debits++;buyer-=200000;if(debitCrash)throw new IllegalStateException("lost debit reply");return true;}
        public boolean credit(SupplyContract c){if(creditRefuse)return false;credits++;seller+=200000;if(creditCrash)throw new IllegalStateException("lost credit reply");return true;}
        public void acknowledge(SupplyContract c)throws Exception{if(ackCrash)throw new IOException("receipt fsync");stock="MISSING";}
    }
    static void tick(Store s,Bank b,long at)throws Exception{SupplyProcessor.advance(s.value.terms().id(),at,RETRY,s,b);}
    static void finish(Store s,Bank b,long at)throws Exception{for(int i=0;i<8;i++)tick(s,b,at);}
    public static void main(String[] args)throws Exception{
        var p=proposal();failure(()->p.accept(SELLER,START));failure(()->p.accept(BUYER,START+DAY));var c=p.accept(BUYER,START);long due=c.nextDue();
        check(due==START+7*DAY,"first delivery one full signed period later");
        var s=new Store(c);var b=new Bank();tick(s,b,due-1);check(b.debits==0&&b.pickups==0,"no early supply");finish(s,b,due);
        check(b.goods==500&&b.destination==500&&b.buyer==300000&&b.seller==200000&&s.value.deliveries()==1,"exactly 500 iron for 2000 coins");
        finish(s,b,due);check(b.debits==1&&b.credits==1,"no repeated delivery in same interval");
        long later=due+40*DAY;finish(s,b,later);check(b.debits==2&&s.value.nextDue()==later+7*DAY,"downtime executes one supply and schedules from completion");
        check(s.value.history().size()==2,"completed receipts persisted in contract history");
        var paused=c.pause(SELLER,true).pause(BUYER,true).pause(SELLER,false);check(paused.pausedBy().equals(Set.of(BUYER)),"one city cannot clear another city's pause");failure(()->c.pause(UUID.randomUUID(),true));
        s=new Store(paused);b=new Bank();finish(s,b,due);check(b.debits==0,"paused supply never starts");
        s=new Store(c);b=new Bank();b.gate="Импорт ограничен";tick(s,b,due);check(b.pickups==0&&s.value.attempt()==null,"live sanctions/policy/budget gate prevents reserve");
        b.gate=null;tick(s,b,due+RETRY);s.value=s.value.cancel(SELLER);finish(s,b,due+RETRY);check(b.destination==500&&b.seller==200000&&s.value.status()==Status.CANCELLED,"cancellation completes already-paid supply");
        s=new Store(c);b=new Bank();b.busy=true;tick(s,b,due);check(b.debits==0&&b.goods==1000,"open inventory blocks supply");b.busy=false;s.value=s.value.pause(BUYER,true);finish(s,b,due+RETRY);check(b.debits==0&&b.goods==1000,"pause cancels unpaid attempt");
        s=new Store(c);b=new Bank();b.bankRefuse=true;finish(s,b,due);check(b.goods==1000&&b.destination==0&&s.value.deliveries()==0,"refused debit returns stock without delivery");
        s=new Store(c);b=new Bank();tick(s,b,due);b.full=true;tick(s,b,due);check(b.destination==0&&b.credits==0&&s.value.attempt().phase()==Phase.PAID,"full destination keeps paid cargo and delays seller credit");b.full=false;finish(s,b,due+RETRY);check(b.destination==500&&b.credits==1,"full storage resumes without another debit");
        s=new Store(c);b=new Bank();s.failPhase=Phase.DEBIT_PENDING;Store fs=s;Bank fb=b;failure(()->tick(fs,fb,due));check(b.debits==0&&s.value.attempt().phase()==Phase.PREPARED,"failed intent save never calls bank");s.failPhase=null;finish(s,b,due);check(b.pickups==1&&b.debits==1,"restart reuses reserved shipment UUID");
        s=new Store(c);b=new Bank();b.debitCrash=true;Store ds=s;Bank db=b;failure(()->tick(ds,db,due));finish(s,b,due+DAY);check(b.debits==1&&b.destination==0&&s.value.attempt().phase()==Phase.DEBIT_PENDING,"uncertain debit is not retried after restart");
        failure(()->SupplyProcessor.resolve(ds.value,UUID.randomUUID(),"debit-paid",due));
        s.value=SupplyProcessor.resolve(s.value,s.value.attempt().id(),"debit-paid",due);b.debitCrash=false;finish(s,b,due);check(b.debits==1&&b.destination==500&&b.credits==1,"verified paid debit resumes once");
        s=new Store(c);b=new Bank();tick(s,b,due);tick(s,b,due);b.creditCrash=true;Store cs=s;Bank cb=b;failure(()->tick(cs,cb,due));finish(s,b,due+DAY);check(b.credits==1&&s.value.attempt().phase()==Phase.CREDIT_PENDING,"uncertain seller credit never repeats");
        s.value=SupplyProcessor.resolve(s.value,s.value.attempt().id(),"credit-paid",due);finish(s,b,due);check(s.value.deliveries()==1&&b.credits==1,"verified seller credit finalizes receipt");
        s=new Store(c);b=new Bank();s.failPhase=Phase.PAID;Store ps=s;Bank pb=b;failure(()->tick(ps,pb,due));check(b.debits==1&&s.value.attempt().phase()==Phase.DEBIT_PENDING,"post-debit disk failure retains ambiguity");s.failPhase=null;finish(s,b,due+DAY);check(b.debits==1,"post-debit failed save cannot charge twice");
        s=new Store(c);b=new Bank();tick(s,b,due);tick(s,b,due);s.failPhase=Phase.COMPLETE;Store es=s;Bank eb=b;failure(()->tick(es,eb,due));s.failPhase=null;finish(s,b,due+DAY);check(b.credits==1&&s.value.deliveries()==0,"post-credit disk failure cannot mint money");
        s=new Store(c);b=new Bank();for(int i=0;i<3;i++)tick(s,b,due);s.failFinish=true;Store as=s;Bank ab=b;failure(()->tick(as,ab,due));s.failFinish=false;finish(s,b,due);check(b.debits==1&&b.credits==1&&s.value.deliveries()==1,"crash between acknowledgement and final journal save is safe");
        s=new Store(c);b=new Bank();tick(s,b,due);tick(s,b,due);b.creditRefuse=true;tick(s,b,due);check(s.value.attempt().phase()==Phase.DELIVERED&&b.credits==0,"refused credit retries without goods duplication");b.creditRefuse=false;finish(s,b,due+RETRY);check(b.destination==500&&b.credits==1,"credit retry finishes one delivery");
        // A rule change between the saved intent and a replay must return an already reserved batch.
        s=new Store(c);b=new Bank();s.failPhase=Phase.DEBIT_PENDING;Store gs=s;Bank gb=b;failure(()->tick(gs,gb,due));s.failPhase=null;b.gate="Санкции";finish(s,b,due);check(b.goods==1000&&b.debits==0,"changed gate returns pre-crash unpaid reserve");
        s=new Store(c);b=new Bank();s.value=c.attempt(new Attempt(UUID.randomUUID(),Phase.DEBIT_PENDING,due),due,"unknown");b.stock="RESERVED";b.goods=500;
        s.value=SupplyProcessor.resolve(s.value,s.value.attempt().id(),"debit-unpaid",due);finish(s,b,due);check(b.goods==1000&&b.debits==0&&b.destination==0,"verified unpaid debit returns goods");
        s=new Store(c);b=new Bank();tick(s,b,due);tick(s,b,due);s.value=s.value.attempt(s.value.attempt().phase(Phase.CREDIT_PENDING),due,"unknown");
        s.value=SupplyProcessor.resolve(s.value,s.value.attempt().id(),"credit-unpaid",due);finish(s,b,due);check(b.credits==1&&b.debits==1&&b.destination==500,"verified unpaid credit pays once without another cargo delivery");
        s=new Store(c);b=new Bank();for(int i=0;i<3;i++)tick(s,b,due);b.ackCrash=true;Store ks=s;Bank kb=b;failure(()->tick(ks,kb,due));b.ackCrash=false;finish(s,b,due);check(b.credits==1&&s.value.deliveries()==1,"failed receipt acknowledgement never repeats payment");
        for(String bad:List.of("NaN","Infinity","-2","0","1.001","1000000001"))failure(()->SupplyContract.price(bad));check(SupplyContract.price("2000,00")==200000,"exact comma decimal parsing");
        persistence(c);System.out.println("SupplyContractSmoke OK: bilateral consent, pauses, time, downtime, real goods/money conservation, all bank crash windows, replay, full/busy warehouses, cancellation and strict atomic persistence");
    }
    static void persistence(SupplyContract c)throws Exception{
        Path dir=Files.createTempDirectory("supply-test");try{
            Path path=dir.resolve("contracts.yml");var repo=new SupplyRepository(path);repo.load();repo.put(c);
            var loaded=new SupplyRepository(path);loaded.load();check(loaded.get(c.terms().id()).equals(c),"signed terms survive reload");
            var s=new Store(c);var b=new Bank();b.debitCrash=true;failure(()->tick(s,b,c.nextDue()));repo.put(s.value);loaded.load();check(loaded.get(c.terms().id()).attempt().phase()==Phase.DEBIT_PENDING,"uncertain payment survives real YAML reload");
            Files.writeString(path,"schema: 1\ncontracts: broken\n");failure(loaded::load);check(!loaded.writable(),"corrupt journal fails closed");failure(()->loaded.put(c));check(Files.readString(path).contains("broken"),"corrupt data never replaced by an empty database");
            Files.writeString(path,"schema: 2\ncontracts: {}\n");failure(loaded::load);
            Path blocked=dir.resolve("blocked");Files.createDirectory(blocked);Files.writeString(blocked.resolve("keep"),"original");var yaml=new org.bukkit.configuration.file.YamlConfiguration();failure(()->SupplyRepository.atomic(yaml,blocked));check(Files.readString(blocked.resolve("keep")).equals("original"),"failed atomic replacement preserves original");
        }finally{try(var paths=Files.walk(dir)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    }
    interface Action{void run()throws Exception;}static void failure(Action a)throws Exception{boolean fail=false;try{a.run();}catch(Exception ex){fail=true;}check(fail,"expected rejection");}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
