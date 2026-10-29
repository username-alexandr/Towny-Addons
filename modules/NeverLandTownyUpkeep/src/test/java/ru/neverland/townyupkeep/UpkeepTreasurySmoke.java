package ru.neverland.townyupkeep;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import ru.neverland.townyupkeep.data.UpkeepRepository;
import ru.neverland.townyupkeep.integration.UpkeepTreasury;
import ru.neverland.townyupkeep.model.*;
import ru.neverland.townyupkeep.model.Entry.*;
import ru.neverland.townyupkeep.model.PaymentProcessor.Outcome;

public final class UpkeepTreasurySmoke {
    static int checks;
    static void check(boolean result,String label){if(!result)throw new AssertionError(label);checks++;}
    interface Attempt{void run()throws Exception;}
    static void fails(Attempt action,String label)throws Exception{try{action.run();}catch(Exception expected){checks++;return;}throw new AssertionError(label);}
    static final class Fixture implements PaymentProcessor.Store,PaymentProcessor.Gateway {
        final Key key=new Key(UUID.randomUUID(),"guard");
        Entry saved=new Entry(false,0,"Оплата",new Invoice(UUID.randomUUID(),new Cost(1500,Map.of("food",3000L)),3600,Phase.PREPARED));
        String category="army";Outcome outcome=Outcome.WAITING;int writes,fail=-1,polls,charges,settled,released;boolean held,paid,unavailable;final Set<UUID> bank=new HashSet<>();
        public Entry get(Key key){return saved;}
        public void put(Key key,Entry next)throws Exception{if(++writes==fail)throw new IOException("injected missing ACK");saved=next;}
        public boolean asynchronous(){return true;}public String category(Key key){return category;}
        public boolean reserve(UUID id,UUID town,Map<String,Long> resources){held=true;return true;}
        public void settle(UUID id,boolean consume){if(!held)return;held=false;if(consume)settled++;else released++;}
        public boolean canPay(UUID town,long cents){throw new AssertionError("stale legacy balance must not select Treasury route");}
        public boolean withdraw(UUID town,long cents,UUID id){throw new AssertionError("legacy bank must never execute");}
        public void forget(UUID id){}
        public Outcome poll(Key key,Invoice bill){if(unavailable)throw new IllegalStateException("Требуется сверка новых банковских наблюдений");check(saved.invoice().phase()==Phase.MONEY_REQUESTED,"durable invoice precedes any Treasury request");polls++;if(outcome==Outcome.PAID&&bank.add(bill.id()))charges++;return outcome;}
        void run()throws Exception{new PaymentProcessor(this,this).process(key,10,300);}
    }
    static Map<String,Object> proof(Fixture f,String status){return new HashMap<>(Map.ofEntries(
        Map.entry("operation",f.saved.invoice().id().toString()),Map.entry("town",f.key.town().toString()),Map.entry("cents",1500L),Map.entry("category",f.saved.invoice().category()),Map.entry("source","upkeep"),Map.entry("owner","NeverLandTownyUpkeep"),Map.entry("actor",new UUID(0,0).toString()),Map.entry("reason","NeverLand Upkeep "+f.saved.invoice().id()),Map.entry("kind","DEBIT"),Map.entry("status",status),Map.entry("backingAvailable",true)));}
    public static void main(String[] args)throws Exception{
        var waiting=new Fixture();waiting.run();waiting.run();check(waiting.held&&waiting.saved.invoice().phase()==Phase.MONEY_REQUESTED&&waiting.settled==0,"waiting holds resources and no active interval");
        waiting.category="construction";waiting.run();check(waiting.saved.invoice().category().equals("army"),"accepted invoice category does not follow later provider mapping changes");
        waiting.unavailable=true;waiting.run();check(waiting.held&&waiting.saved.invoice().phase()==Phase.MONEY_REQUESTED,"temporary synchronous provider refusal keeps pending ID and resources");waiting.unavailable=false;
        waiting.outcome=Outcome.REVIEW;waiting.run();check(waiting.held&&waiting.released==0,"unknown result never releases resource collateral");
        fails(()->new PaymentProcessor(waiting,waiting).resolve(waiting.key,true),"legacy admin resolver cannot override Treasury receipt");
        waiting.outcome=Outcome.PAID;waiting.run();waiting.run();check(waiting.charges==1&&waiting.settled==1&&waiting.saved.active()&&waiting.saved.due()==3610,"one accepted receipt activates one paid interval");
        for(int write=1;write<=3;write++){
            var f=new Fixture();f.outcome=Outcome.PAID;f.fail=write;fails(f::run,"invoice ACK failure "+write);f.fail=-1;f.run();f.run();check(f.charges==1&&f.settled==1&&f.saved.active(),"ACK loss reconciles same invoice without extra charge "+write);
        }
        var rejected=new Fixture();rejected.outcome=Outcome.REJECTED;rejected.run();rejected.run();check(rejected.charges==0&&rejected.released==1&&!rejected.saved.active(),"confirmed rejection returns resources once");
        var legacy=new Fixture();legacy.saved=legacy.saved.phase(Phase.MONEY_PENDING);legacy.run();check(legacy.polls==0&&legacy.saved.invoice().phase()==Phase.MONEY_PENDING,"old uncertain invoice is never resubmitted through Treasury");
        var f=new Fixture();f.saved=f.saved.requested("army");var good=proof(f,"PAID");check(UpkeepTreasury.proof(f.key,f.saved.invoice(),good)==Outcome.PAID,"exact paid backing accepted");
        for(String field:List.of("operation","town","cents","category","source","owner","actor","reason","kind")){var bad=new HashMap<>(good);bad.put(field,field.equals("cents")?1501L:"wrong");fails(()->UpkeepTreasury.proof(f.key,f.saved.invoice(),bad),"foreign payload "+field);}
        for(var status:Map.of("PREPARED",Outcome.WAITING,"INVOKING",Outcome.WAITING,"REVIEW",Outcome.REVIEW,"REJECTED",Outcome.REJECTED).entrySet())check(UpkeepTreasury.proof(f.key,f.saved.invoice(),proof(f,status.getKey()))==status.getValue(),"state "+status.getKey());
        good.put("backingAvailable",false);check(UpkeepTreasury.proof(f.key,f.saved.invoice(),good)==Outcome.REVIEW,"already refunded debit cannot activate upkeep");
        Path file=Files.createTempDirectory("upkeep-treasury").resolve("upkeep.yml");var repo=new UpkeepRepository(file);repo.load();repo.save(5,Map.of(f.key,f.saved));var reopened=new UpkeepRepository(file);reopened.load();check(reopened.entries().get(f.key).equals(f.saved),"actual YAML retains exact route amount and invoice ID on restart");
        System.out.println("UpkeepTreasurySmoke PASS "+checks+" checks; durable intent, old receipt isolation, ACK recovery, resource preservation and exact proof");
    }
}
