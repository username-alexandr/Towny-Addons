package ru.neverland.townyquests.cooperative;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import ru.neverland.core.*;

/** Real blocked writer and late SQL acknowledgements; no tick/ledger thread waits for audit disk. */
public final class CooperativeAuditSmoke {
    static int checks;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    static Map<String,Object> row(String id){return Map.of("id",id,"title","Смета "+id);}
    static void done(CompletionStage<Void> future)throws Exception{future.toCompletableFuture().get(5,TimeUnit.SECONDS);}
    public static void main(String[] args)throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var rows=new CopyOnWriteArrayList<CooperativeAudit.Entry>();var gaps=new CopyOnWriteArrayList<String>();var held=new AtomicBoolean();
        var audit=new CooperativeAudit(entry->{held.set(SuiteState.held());entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("writer not released");}catch(InterruptedException error){throw new AssertionError(error);}rows.add(entry);return true;},gaps::add,1);
        var actor=new AuditRecord.Party("PLAYER",UUID.randomUUID().toString(),"OriginalName","OriginalTown");
        try{
            try(var guard=SuiteState.guard()){audit.track(CompletableFuture.completedFuture(row("one")),"PLAN",actor);}
            check(entered.await(5,TimeUnit.SECONDS),"real disk worker started");
            try(var guard=SuiteState.guard()){check(true,"ledger remains available while audit writer is blocked");audit.track(CompletableFuture.completedFuture(row("two")),"PLAN",actor);audit.track(CompletableFuture.completedFuture(row("three")),"PLAN",actor);}
            var close=audit.closeAsync();check(!close.toCompletableFuture().isDone(),"shutdown waits for outstanding writes outside guard");check(gaps.size()==1&&gaps.get(0).contains("three"),"bounded full queue produces explicit audit gap without retrying project");
            release.countDown();boolean failed=false;try{done(close);}catch(ExecutionException expected){failed=true;}check(failed,"shutdown does not falsely acknowledge missing audit record");
            check(!held.get()&&rows.size()==2&&rows.get(0).actor().equals(actor),"only immutable captured actor reaches worker and accepted rows drain");
        }finally{release.countDown();}
        var writes=new AtomicInteger();var pending=new CompletableFuture<Map<String,Object>>();var late=new CooperativeAudit(entry->{check(!SuiteState.held(),"late audit outside guard");writes.incrementAndGet();return true;},gaps::add,2);
        late.track(pending,"COMPLETED",actor);var stopped=late.closeAsync();check(!stopped.toCompletableFuture().isDone(),"close waits for previously submitted SQL confirmation");pending.complete(row("late"));done(stopped);check(writes.get()==1,"acknowledgement arriving during shutdown still audited exactly once");
        var rejected=new CooperativeAudit(entry->{throw new AssertionError("failed SQL cannot be audited as success");},gaps::add,1);rejected.track(CompletableFuture.failedFuture(new IllegalArgumentException("invalid project")),"PLAN",actor);done(rejected.closeAsync());check(true,"rejected source mutation creates no success audit");
        var bad=new CooperativeAudit(entry->false,gaps::add,1);bad.track(CompletableFuture.completedFuture(row("disk-fault")),"CANCELLED",actor);boolean failed=false;try{done(bad.closeAsync());}catch(ExecutionException expected){failed=true;}check(failed&&gaps.stream().anyMatch(s->s.contains("disk-fault")),"unacknowledged write is visible and never changes original SQL operation");
        System.out.println("CooperativeAuditSmoke PASS "+checks+" checks");
    }
}
