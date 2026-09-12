package ru.neverland.minttrade;
import java.nio.file.*;
import java.util.*;
import org.bukkit.inventory.ItemStack;
import ru.neverland.core.EffectJournal;
import ru.neverland.minttrade.model.*;
import ru.neverland.minttrade.service.CaravanProcessor;

public final class CaravanReliabilitySmoke {
    static final UUID SELLER=UUID.randomUUID(),BUYER=UUID.randomUUID(),TOLL=UUID.randomUUID();
    static int checks;
    interface Action {void run()throws Exception;}
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void reject(Action action)throws Exception{try{action.run();}catch(Exception expected){checks++;return;}throw new AssertionError("expected failure");}
    static final class Item extends ItemStack {Item(){super();}public Item clone(){return new Item();}}
    static Caravan caravan(){return new Caravan(UUID.randomUUID(),SELLER,BUYER,"iron",new Item(),50,50,100,110,Map.of(TOLL,10D),List.of(),1,1000,500,false,true,CaravanStatus.ACTIVE);}
    static final class Store implements CaravanProcessor.Store {
        int finishes;
        public void save(){}
        public void finish(Caravan c,CaravanStatus status){finishes++;c.settlement(status==CaravanStatus.COMPLETED?"COMPLETE":"CANCELLED");}
    }
    static final class Gateway implements CaravanProcessor.Gateway {
        final Path file;EffectJournal journal;final Set<UUID> stockReceipts=new HashSet<>();
        int sellerStock=100,buyerStock,debits,sellerCredits,tollCredits,refunds;
        double buyerMoney=1000,sellerMoney,tollMoney;
        boolean lostDebit,lostDelivery,lostToll,declined,full;
        Gateway()throws Exception{file=Files.createTempDirectory("caravan-effects-").resolve("effects.yml");reload();}
        void reload()throws Exception{journal=new EffectJournal(file);journal.load();}
        public boolean reserve(Caravan c){if(stockReceipts.add(c.operation("take")))sellerStock-=c.totalCargo();return true;}
        public boolean debit(Caravan c)throws Exception{return journal.execute(c.operation("debit"),"buyer "+c.escrow(),()->{
            if(declined)return false;debits++;buyerMoney-=c.escrow();if(lostDebit)throw new IllegalStateException("lost debit reply");return true;
        });}
        public boolean deliver(Caravan c){if(full)return false;if(stockReceipts.add(c.operation("delivery")))buyerStock+=c.remainingCargo();if(lostDelivery){lostDelivery=false;throw new IllegalStateException("lost warehouse reply");}return true;}
        public boolean credit(Caravan c,UUID town,double amount,String kind)throws Exception{return journal.execute(c.operation(kind+":"+town),kind+" "+amount,()->{
            if(kind.equals("seller")){sellerCredits++;sellerMoney+=amount;}
            else if(kind.equals("tariff")){tollCredits++;tollMoney+=amount;if(lostToll)throw new IllegalStateException("lost toll reply");}
            else {refunds++;buyerMoney+=amount;}return true;
        });}
        public boolean sourceTaken(Caravan c){return stockReceipts.contains(c.operation("take"));}
        public boolean funded(Caravan c){return journal.state(c.operation("debit"))==EffectJournal.State.DONE;}
        public boolean returnStock(Caravan c){if(stockReceipts.add(c.operation("return")))sellerStock+=c.remainingCargo();return true;}
    }
    public static void main(String[] args)throws Exception{
        var c=caravan();var store=new Store();var gateway=new Gateway();gateway.lostDebit=true;
        reject(()->CaravanProcessor.advance(c,store,gateway));check(c.settlement().equals("PREPARED")&&gateway.sellerStock==50&&gateway.buyerMoney==890,"cargo reserved and debit ambiguous");
        gateway.reload();reject(()->CaravanProcessor.advance(c,store,gateway));check(gateway.sellerStock==50&&gateway.debits==1,"recovery repeats neither reservation nor uncertain debit");
        gateway.journal.resolve(c.operation("debit"),true);CaravanProcessor.advance(c,store,gateway);check(c.settlement().equals("ACTIVE"),"received debit permits departure");
        c.settlement("DELIVERING");gateway.full=true;CaravanProcessor.advance(c,store,gateway);check(gateway.buyerStock==0&&c.settlement().equals("DELIVERING"),"full warehouse preserves paid cargo");
        gateway.full=false;gateway.lostDelivery=true;reject(()->CaravanProcessor.advance(c,store,gateway));CaravanProcessor.advance(c,store,gateway);check(gateway.buyerStock==50&&c.settlement().equals("PAYING"),"warehouse receipt makes interrupted unload idempotent");
        gateway.lostToll=true;reject(()->CaravanProcessor.advance(c,store,gateway));gateway.reload();reject(()->CaravanProcessor.advance(c,store,gateway));check(gateway.sellerCredits==1&&gateway.tollCredits==1,"partial settlement never repeats confirmed seller or ambiguous toll");
        gateway.journal.resolve(c.operation("tariff:"+TOLL),true);CaravanProcessor.advance(c,store,gateway);CaravanProcessor.advance(c,store,gateway);
        check(c.terminal()&&store.finishes==1&&gateway.sellerMoney==100&&gateway.tollMoney==10&&gateway.buyerMoney==890,"exact final balances and single completion");
        var declined=caravan();var rejectStore=new Store();var rejectGateway=new Gateway();rejectGateway.declined=true;
        CaravanProcessor.advance(declined,rejectStore,rejectGateway);CaravanProcessor.advance(declined,rejectStore,rejectGateway);CaravanProcessor.advance(declined,rejectStore,rejectGateway);
        check(declined.settlement().equals("CANCELLED")&&rejectGateway.sellerStock==100&&rejectGateway.refunds==0&&rejectGateway.buyerMoney==1000,"definite rejection returns cargo without unearned refund");
        var cancelled=caravan();var cancelStore=new Store();var cancelGateway=new Gateway();CaravanProcessor.advance(cancelled,cancelStore,cancelGateway);cancelled.settlement("RETURNING");
        CaravanProcessor.advance(cancelled,cancelStore,cancelGateway);CaravanProcessor.advance(cancelled,cancelStore,cancelGateway);
        check(cancelGateway.sellerStock==100&&cancelGateway.buyerMoney==1000&&cancelGateway.refunds==1,"paid cancellation refunds and returns cargo once");
        var legacy=caravan();legacy.settlement("LEGACY_REVIEW");var legacyGateway=new Gateway();CaravanProcessor.advance(legacy,new Store(),legacyGateway);
        check(legacyGateway.debits==0&&legacyGateway.sellerStock==100,"legacy caravan cannot move before reconciliation");
        System.out.println("CaravanReliabilitySmoke OK: "+checks+" checks");
    }
}
