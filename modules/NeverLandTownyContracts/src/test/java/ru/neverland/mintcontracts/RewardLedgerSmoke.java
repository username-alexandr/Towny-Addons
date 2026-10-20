package ru.neverland.mintcontracts;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import ru.neverland.earnings.RewardLedger;
import static ru.neverland.earnings.RewardLedger.State.*;

public final class RewardLedgerSmoke {
    private static int checks;
    private static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private interface Attempt{void run()throws Exception;}
    private static void rejects(Attempt f,String why)throws Exception{try{f.run();}catch(Exception expected){checks++;return;}throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("nlt-reward-test");AtomicLong now=new AtomicLong(100000000);UUID player=UUID.randomUUID(),other=UUID.randomUUID();
        var ledger=new RewardLedger(root.resolve("ledger"),150000,200000,now::get);ledger.load();
        AtomicInteger calls=new AtomicInteger();RewardLedger.Bank bank=new RewardLedger.Bank(){public boolean ready(UUID p){return true;}public boolean deposit(RewardLedger.Entry e){calls.incrementAndGet();return true;}};
        UUID id=UUID.randomUUID();ledger.reserve(id,player,100000,"orders:first",false);ok(ledger.used(player)==100000,"pending consumes budget");
        ledger.reserve(id,player,100000,"orders:first",false);ok(ledger.all().size()==1,"reserve idempotent");
        rejects(()->ledger.reserve(id,other,100000,"orders:first",false),"same ID cannot change owner");
        rejects(()->ledger.reserve(UUID.randomUUID(),player,50001,"orders:too-much",false),"player cap");
        UUID id2=UUID.randomUUID();ledger.reserve(id2,other,100000,"orders:second",false);
        rejects(()->ledger.reserve(UUID.randomUUID(),other,1,"orders:server-cap",false),"server cap");
        ledger.submit(id,bank);ledger.submit(id,bank);ok(calls.get()==1&&ledger.get(id).state()==PAID,"deposit once");
        var restarted=new RewardLedger(root.resolve("ledger"),150000,200000,now::get);restarted.load();restarted.submit(id,bank);ok(calls.get()==1,"paid receipt survives restart");
        now.addAndGet(RewardLedger.DAY-1);ok(restarted.used(player)==100000,"rolling window before boundary");now.incrementAndGet();ok(restarted.used(player)==0,"rolling window exact boundary");ok(restarted.used(other)==100000,"old unpaid obligation keeps reservation");
        var unavailable=new RewardLedger.Bank(){public boolean ready(UUID p){return false;}public boolean deposit(RewardLedger.Entry e){throw new AssertionError("unavailable bank called");}};
        restarted.submit(id2,unavailable);ok(restarted.get(id2).state()==PENDING,"outage preserves pending");
        UUID held=UUID.randomUUID();restarted.reserve(held,player,1000,"orders:delivery",true);restarted.submit(held,bank);ok(calls.get()==1&&restarted.get(held).state()==HELD,"no payout before inventory receipt");restarted.release(held,"saved-player");restarted.release(held,"duplicate-receipt");restarted.submit(held,bank);ok(calls.get()==2,"delivery release once");
        UUID crash=UUID.randomUUID();restarted.reserve(crash,player,1000,"orders:crash",false);
        try{restarted.submit(crash,new RewardLedger.Bank(){public boolean ready(UUID p){return true;}public boolean deposit(RewardLedger.Entry e){calls.incrementAndGet();throw new AssertionError("simulated process death after deposit");}});}catch(AssertionError expected){}
        var recovered=new RewardLedger(root.resolve("ledger"),150000,200000,now::get);recovered.load();ok(recovered.get(crash).state()==REVIEW,"crash enters reconciliation");recovered.submit(crash,bank);ok(calls.get()==3,"ambiguous deposit never replayed");
        rejects(()->recovered.resolve(crash,"retry","admin","x"),"reconciliation needs reason");recovered.resolve(crash,"paid","admin","bank log verified");recovered.submit(crash,bank);ok(calls.get()==3&&recovered.get(crash).state()==PAID,"manual proof does not deposit");
        UUID reject=UUID.randomUUID();recovered.reserve(reject,player,1000,"orders:rejected",false);recovered.submit(reject,new RewardLedger.Bank(){public boolean ready(UUID p){return true;}public boolean deposit(RewardLedger.Entry e){return false;}});ok(recovered.get(reject).state()==REVIEW,"provider rejection is conservative");recovered.resolve(reject,"retry","admin","confirmed no bank transfer");recovered.submit(reject,bank);ok(calls.get()==4&&recovered.get(reject).state()==PAID,"explicit verified retry works");
        UUID untouched=UUID.randomUUID();recovered.reserve(untouched,player,100,"orders:untouched",true);recovered.resolve(untouched,"untouched","admin","inventory unchanged verified");ok(recovered.get(untouched).state()==VOID,"untouched escrow is void");rejects(()->recovered.resolve(held,"retry","admin","invalid already paid"),"paid cannot retry");
        Path concurrent=root.resolve("concurrent");var shared=new RewardLedger(concurrent,35000,35000,now::get);shared.load();var pool=Executors.newFixedThreadPool(8);AtomicInteger accepted=new AtomicInteger();List<Future<?>> futures=new ArrayList<>();
        for(int i=0;i<100;i++)futures.add(pool.submit(()->{try{shared.reserve(UUID.randomUUID(),player,1000,"jobs:parallel",false);accepted.incrementAndGet();}catch(IllegalArgumentException expected){}catch(Exception ex){throw new RuntimeException(ex);}}));for(var f:futures)f.get();pool.shutdown();ok(accepted.get()==35&&shared.used(player)==35000,"parallel regions cannot exceed cap");
        Path broken=root.resolve("broken");var disk=new RewardLedger(broken,10000,10000,now::get);disk.load();UUID brokenId=UUID.randomUUID();Files.createDirectory(broken.resolve(brokenId+".properties"));Files.writeString(broken.resolve(brokenId+".properties/blocker"),"fixture");rejects(()->disk.reserve(brokenId,player,100,"jobs:disk",false),"failed durable write");ok(disk.fault()&&disk.get(brokenId)==null,"failed reservation never published");rejects(()->disk.reserve(UUID.randomUUID(),player,100,"jobs:after-fault",false),"disk error freezes ledger");
        Path corrupt=root.resolve("corrupt");Files.createDirectories(corrupt);Files.writeString(corrupt.resolve(UUID.randomUUID()+".properties"),"schema=999\n");var bad=new RewardLedger(corrupt,10000,10000);rejects(bad::load,"corruption cannot reset history");ok(bad.fault(),"corrupt ledger frozen");
        ok(recovered.get(crash).history().stream().anyMatch(s->s.contains("admin admin paid")),"audit keeps operator and reason");
        System.out.println("REWARD_LEDGER_SMOKE_PASS: "+checks+" checks");
    }
}
