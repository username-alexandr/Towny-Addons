package ru.neverland.townycontrol;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import ru.neverland.core.*;

public final class BankAuditQueueSmoke {
    static int checks;static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    public static void main(String[] args)throws Exception{
        var first=AuditIndexSmoke.record(1,"NeverLandTownyControl","BANK_TRANSFER","COMPLETED","queued1",1800000000000L,"A","B","1200");
        var next=AuditIndexSmoke.record(2,"NeverLandTownyControl","BANK_TRANSFER","COMPLETED","queued2",1800000000001L,"B","C","1200");
        var directory=Files.createTempDirectory("bank-audit-queue-");var journal=new AuditJournal(directory);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var warnings=new CopyOnWriteArrayList<String>();
        var queue=new BankAuditQueue(r->{try{check(!SuiteState.held(),"writer never holds SuiteState");entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("test timed out");journal.appendUnique(r);return true;}catch(Exception e){throw new RuntimeException(e);}},warnings::add,1,10_000);
        check(queue.submit(first),"enqueue before writer");check(entered.await(2,TimeUnit.SECONDS),"real writer entered delayed append");
        try(var guard=SuiteState.guard()){check(queue.submit(next),"bank event can enqueue while suite held");check(!queue.submit(first),"bounded queue rejects overflow without blocking bank");}
        check(!queue.closeAsync().toCompletableFuture().isDone(),"shutdown does not acknowledge blocked audit");check(SuiteState.tryRun("ledger continues",()->{}),"ledger remains available while journal waits");release.countDown();queue.closeAsync().toCompletableFuture().get(5,TimeUnit.SECONDS);
        var rows=new ArrayList<AuditRecord>();AuditJournal.scan(directory,rows::add);check(rows.equals(List.of(first,next)),"close drains exact IDs and observed timestamps to real durable journal");
        check(!warnings.isEmpty()&&warnings.get(0).contains("AUDIT GAP"),"overflow explicitly reported");check(queue.status().contains("пропуски за запуск 1"),"gap counter stays inspectable");check(!queue.submit(first),"late observation cannot reopen stopped writer");
        var failed=new BankAuditQueue(r->false,warnings::add,1,10_000);check(failed.submit(first),"failed sink accepted for processing");failed.closeAsync().toCompletableFuture().get(5,TimeUnit.SECONDS);check(failed.status().contains("пропуски за запуск 1"),"failed write remains a gap without bank replay");
        var limited=new BankAuditQueue(r->true,warnings::add,1,10);check(!limited.submit(first),"byte budget rejects oversized captured receipt");limited.closeAsync().toCompletableFuture().get(5,TimeUnit.SECONDS);
        System.out.println("BankAuditQueueSmoke PASS "+checks+" checks");
    }
}
