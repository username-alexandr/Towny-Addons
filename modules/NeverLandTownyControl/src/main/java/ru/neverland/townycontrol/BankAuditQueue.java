package ru.neverland.townycontrol;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;
import ru.neverland.core.*;

/** Immutable observations only. A failed audit must never repeat or roll back a bank operation. */
final class BankAuditQueue {
    @FunctionalInterface interface Sink{boolean write(AuditRecord record);}
    private final Sink sink;private final Consumer<String> warning;private final ThreadPoolExecutor worker;
    private final CompletableFuture<Void> closed=new CompletableFuture<>();private final AtomicLong lost=new AtomicLong(),written=new AtomicLong(),warnAt=new AtomicLong();
    private final long charLimit;private long pendingChars;private int pending;private boolean closing;
    BankAuditQueue(Plugin plugin){this(r->AuditTrail.bank(plugin,r),plugin.getLogger()::severe,512,4_194_304);}
    BankAuditQueue(Sink sink,Consumer<String> warning,int capacity,long charLimit){this.sink=sink;this.warning=warning;this.charLimit=charLimit;
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(capacity),r->{var t=new Thread(r,"NeverLand-bank-audit");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){closed.complete(null);}};
    }
    synchronized boolean submit(AuditRecord record){
        long chars=AuditSignals.characters(record);
        if(closing||chars>charLimit-pendingChars){gap(record,"очередь закрыта или превышен предел объёма");return false;}
        pending++;pendingChars+=chars;
        try{worker.execute(()->{try{if(SuiteState.held())throw new IllegalStateException("Bank audit under shared guard");if(sink.write(record))written.incrementAndGet();else gap(record,"нет подтверждения записи");}catch(Throwable error){gap(record,"ошибка записи: "+error.getClass().getSimpleName());}finally{synchronized(this){pending--;pendingChars-=chars;}}});return true;}
        catch(RejectedExecutionException full){pending--;pendingChars-=chars;gap(record,"очередь заполнена");return false;}
    }
    private void gap(AuditRecord record,String reason){long count=lost.incrementAndGet(),now=System.currentTimeMillis(),old=warnAt.get();if(old==0||now-old>=60_000)if(warnAt.compareAndSet(old,now))warning.accept("AUDIT GAP: банковская квитанция "+record.id()+" — "+reason+"; не сохранено за запуск: "+count+". Платёж не повторяется.");}
    synchronized CompletionStage<Void> closeAsync(){closing=true;worker.shutdown();return closed;}
    synchronized String status(){return "Банковский аудит: в очереди "+pending+", записано "+written.get()+", пропуски за запуск "+lost.get()+". Очередь не является журналом платежей.";}
}
