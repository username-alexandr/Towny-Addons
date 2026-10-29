package ru.neverland.townymarket.personal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import ru.neverland.core.*;
public final class PersonalAuditSmoke{
    static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static LimitAudit.Entry entry(String id){var person=new AuditRecord.Party("PLAYER",id,"Captured name","");return new LimitAudit.Entry("DONE",id,"CONFIRMED",person,person,person,"STONE",1,"0.01","immutable");}
    public static void main(String[]args)throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var written=new CopyOnWriteArrayList<LimitAudit.Entry>();var gaps=new CopyOnWriteArrayList<String>();
        var queue=new LimitAudit(e->{if(SuiteState.held())throw new AssertionError("audit worker owns suite guard");entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException error){throw new RuntimeException(error);}written.add(e);return true;},gaps::add,1);
        queue.submit(entry("first"));check(entered.await(2,TimeUnit.SECONDS),"dedicated audit worker starts");check(SuiteState.tryRun("audit physical delay",()->{}),"blocked audit writer cannot occupy suite state");queue.submit(entry("second"));queue.submit(entry("overflow"));check(gaps.size()==1&&gaps.get(0).contains("AUDIT GAP"),"bounded full queue explicitly reports missing evidence");var closed=queue.closeAsync();check(!closed.toCompletableFuture().isDone()&&written.isEmpty(),"shutdown receipt waits for every accepted audit write");release.countDown();closed.toCompletableFuture().get(5,TimeUnit.SECONDS);check(written.size()==2&&written.get(0).operation().equals("first")&&written.get(1).operation().equals("second"),"accepted immutable entries drain in order");queue.submit(entry("late"));check(gaps.size()==2&&written.size()==2,"late audit reports gap and never retries business actions");
        var calls=new AtomicInteger();var refused=new LimitAudit(e->{calls.incrementAndGet();return false;},gaps::add,1);refused.submit(entry("file-failed"));refused.closeAsync().toCompletableFuture().get(5,TimeUnit.SECONDS);check(calls.get()==1&&gaps.size()==3,"file failure reports explicit gap without repeat");
        System.out.println("PersonalAuditSmoke PASS "+checks+" checks");
    }
}
