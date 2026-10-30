package ru.neverland.townysieges;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Exercise exact offer IDs, restart uncertainty, raid closure and blocked combat on SQLite. */
public final class RansomStoreSmoke {
    private static int checks;
    private static void check(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
    private static <T>T done(CompletionStage<T> future)throws Exception{return future.toCompletableFuture().get(10,TimeUnit.SECONDS);}
    private static void denied(CompletionStage<?> future)throws Exception{try{done(future);throw new AssertionError("unexpected approval");}catch(ExecutionException expected){checks++;}}
    private static RaidStore.Raid war(long now){return new RaidStore.Raid(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),now-5000,now-4000,now+600000,now+900000,"OPEN");}
    private static void close(RaidStore store)throws Exception{store.close();done(store.stopped());}
    public static void main(String[] args)throws Exception{
        var errors=new ArrayList<Throwable>();long now=System.currentTimeMillis();Path file=Files.createTempDirectory("raid-ransom-").resolve("raids.db");
        var db=new RaidStore(file,errors::add);done(db.ready());var offers=new RansomStore(db);var war=war(now);done(db.start(war,"CHUNKS",1));
        denied(offers.propose(war.id(),war.attacker(),war.actor(),50000,now,60000));
        var proposed=done(offers.propose(war.id(),war.defender(),war.actor(),50000,now,60000));
        check(proposed.state().equals("OFFERED")&&proposed.cents()==50000,"frozen offer");
        denied(offers.propose(war.id(),war.defender(),war.actor(),10000,now,60000));
        denied(offers.accept(war.id(),UUID.randomUUID(),war.attacker(),war.actor(),now));
        denied(offers.accept(war.id(),proposed.id(),war.defender(),war.actor(),now));
        var accepted=done(offers.accept(war.id(),proposed.id(),war.attacker(),war.actor(),now));
        var beforeRestart=db;check(accepted.state().equals("ACCEPTED")&&done(db.submit(()->RansomStore.blocking(beforeRestart.connection(),war.id()))),"accepted offer blocks raid");
        denied(offers.accept(war.id(),proposed.id(),war.attacker(),war.actor(),now));
        denied(db.conclude(war.id(),"CANCELLED","admin",now));
        denied(db.conclude(war.id(),"EXPIRED","",war.ends()+1));
        denied(db.victory(war.id(),war.attacker(),war.actor(),now));
        var captures=new WarCaptureStore(db);var c=new WarCaptureStore.Capture(new WarCaptureStore.Key(war.id(),UUID.randomUUID(),1,1),war.attacker(),war.defender(),war.actor(),"HOLD",now,"CAPTURED");
        denied(captures.capture(c));
        var loot=new RaidLootStore(db);var permit=new RaidController.Permit(war.actor(),war.attacker(),war.defender(),war.id(),now,false);
        denied(loot.arm(permit,new RaidStore.Key(UUID.randomUUID(),1,2,3),new byte[0],new LootSettings(true,0,0)));
        done(offers.change(war.id(),proposed.id(),"ACCEPTED","DEBITING","intent"));
        close(db);db=new RaidStore(file,errors::add);done(db.ready());offers=new RansomStore(db);
        check(done(offers.get(war.id())).state().equals("REVIEW_DEBIT"),"restart never replays unknown debit");
        check(done(db.raids()).stream().anyMatch(r->r.id().equals(war.id())&&r.state().equals("OPEN")),"restart does not expire pending escrow");
        denied(offers.resolve(war.id(),proposed.id(),"credit",false,"admin","bank receipt missing"));
        var debit=done(offers.resolve(war.id(),proposed.id(),"debit",true,"admin","confirmed bank withdrawal"));
        check(debit.state().equals("DEBITED"),"confirmed debit creates funded liability");
        done(offers.change(war.id(),proposed.id(),"DEBITED","CREDITING","intent"));
        close(db);db=new RaidStore(file,errors::add);done(db.ready());offers=new RansomStore(db);
        check(done(offers.get(war.id())).state().equals("REVIEW_CREDIT"),"restart never replays unknown credit");
        done(offers.resolve(war.id(),proposed.id(),"credit",false,"admin","no deposit in ledger"));
        done(offers.change(war.id(),proposed.id(),"DEBITED","CREDITING","intent 2"));
        done(offers.change(war.id(),proposed.id(),"CREDITING","FUNDED","bank receipt"));
        done(db.surrender(war.id(),war.actor(),now));
        check(done(offers.get(war.id())).state().equals("DONE")&&done(db.raids()).stream().anyMatch(r->r.id().equals(war.id())&&r.state().equals("SURRENDERED")),"funded surrender and raid closure atomic");
        denied(db.surrender(war.id(),war.actor(),now));
        var afterRestart=db;check(!done(db.submit(()->RansomStore.blocking(afterRestart.connection(),war.id()))),"terminal receipt releases block");
        var second=war(now);done(db.start(second,"CORE",1));var expiring=done(offers.propose(second.id(),second.defender(),second.actor(),100,now,1000));
        check(done(offers.expireOffer(second.id(),expiring.id(),now+1000)).state().equals("VOID"),"unaccepted offer expires without bank call");
        done(db.conclude(second.id(),"CANCELLED","admin",now));
        var third=war(now);done(db.start(third,"CHUNKS",1));var returning=done(offers.propose(third.id(),third.defender(),third.actor(),12500,now,60000));
        done(offers.accept(third.id(),returning.id(),third.attacker(),third.actor(),now));
        done(offers.change(third.id(),returning.id(),"ACCEPTED","DEBITING","intent"));
        done(offers.change(third.id(),returning.id(),"DEBITING","DEBITED","debit confirmed"));
        done(offers.refund(third.id(),returning.id(),"admin","attacker account unavailable"));
        denied(offers.change(third.id(),returning.id(),"DEBITED","CREDITING","stale credit"));
        close(db);db=new RaidStore(file,errors::add);done(db.ready());offers=new RansomStore(db);
        check(done(offers.get(third.id())).state().equals("REVIEW_REFUND"),"interrupted refund does not replay a deposit");
        denied(db.conclude(third.id(),"CANCELLED","admin",now));
        done(offers.resolve(third.id(),returning.id(),"refund",true,"admin","defender account credited"));
        done(db.refundClosed(third.id(),now));
        check(done(offers.get(third.id())).state().equals("VOID")&&done(db.raids()).stream().anyMatch(r->r.id().equals(third.id())&&r.state().equals("CANCELLED")),"refund closes neutrally with same transaction");
        check(errors.isEmpty()&&db.healthy(),"expected denials keep storage healthy");close(db);
        System.out.println("RansomStoreSmoke PASS "+checks+" checks");
    }
}
