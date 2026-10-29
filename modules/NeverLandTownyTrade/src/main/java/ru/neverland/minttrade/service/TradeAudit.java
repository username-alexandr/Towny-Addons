package ru.neverland.minttrade.service;

import java.util.concurrent.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;

/** Audit acknowledgements are independent of bank instructions and never repeat a transfer. */
final class TradeAudit {
    record Entry(String step,String operation,String kind,String outcome,AuditRecord.Party actor,AuditRecord.Party from,AuditRecord.Party to,String asset,long quantity,String money,String details){}
    private static final long MAX_BYTES=8L*1024*1024;private final java.util.concurrent.atomic.AtomicLong queuedBytes=new java.util.concurrent.atomic.AtomicLong();
    private final JavaPlugin plugin;private final ThreadPoolExecutor worker;private final CompletableFuture<Void> closed=new CompletableFuture<>();
    TradeAudit(JavaPlugin plugin){this.plugin=plugin;worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),r->{var t=new Thread(r,"nlt-caravan-audit");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){closed.complete(null);}};}
    CompletionStage<Boolean> append(Entry e){
        var done=new CompletableFuture<Boolean>();long bytes=bytes(e),total=queuedBytes.addAndGet(bytes);
        if(total>MAX_BYTES){queuedBytes.addAndGet(-bytes);gap(e,"превышен объём очереди");return CompletableFuture.completedFuture(false);}
        try{worker.execute(()->{try{
            if(SuiteState.held())throw new IllegalStateException("Audit under SuiteState");
            boolean ok=AuditTrail.record(plugin,e.step(),e.operation(),e.kind(),e.outcome(),e.actor(),e.from(),e.to(),e.asset(),e.quantity(),e.money(),e.details());
            if(!ok)gap(e,"журнал не подтвердил запись");done.complete(ok);
        }catch(Throwable ex){gap(e,ex.getClass().getSimpleName());done.complete(false);}finally{queuedBytes.addAndGet(-bytes);}});}
        catch(RejectedExecutionException ex){queuedBytes.addAndGet(-bytes);gap(e,"очередь занята или закрыта");done.complete(false);}return done;
    }
    private static long bytes(Entry e){long size=1024;for(String text:new String[]{e.step(),e.operation(),e.kind(),e.outcome(),e.asset(),e.money(),e.details(),String.valueOf(e.actor()),String.valueOf(e.from()),String.valueOf(e.to())})if(text!=null)size+=2L*text.length();return size;}
    private void gap(Entry e,String reason){plugin.getLogger().severe("AUDIT GAP: караван "+e.operation()+" / "+e.step()+": "+reason+". Банковский платёж не повторяется.");}
    CompletionStage<Void> closeAsync(){worker.shutdown();return closed;}
}
