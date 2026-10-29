package ru.neverland.townymarket.personal;

import java.util.concurrent.*;
import java.util.function.Consumer;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import static ru.neverland.townymarket.personal.LimitBook.*;

/** Capture immutable account names under the short ledger guard; append audit files only on a bounded worker. */
public final class LimitAudit {
    record Entry(String step,String operation,String outcome,AuditRecord.Party actor,AuditRecord.Party from,AuditRecord.Party to,String asset,long quantity,String money,String details){}
    @FunctionalInterface interface Sink{boolean write(Entry entry);}
    private final ThreadPoolExecutor worker;private final CompletableFuture<Void> stopped=new CompletableFuture<>();private final Sink sink;private final Consumer<String> gap;private boolean closing;
    public LimitAudit(JavaPlugin plugin){this(entry->AuditTrail.record(plugin,entry.step(),entry.operation(),"MARKET_DEAL",entry.outcome(),entry.actor(),entry.from(),entry.to(),entry.asset(),entry.quantity(),entry.money(),entry.details()),message->plugin.getLogger().severe(message),256);}
    LimitAudit(Sink sink,Consumer<String> gap,int capacity){this.sink=sink;this.gap=gap;worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(capacity),task->{var thread=new Thread(task,"NeverLand-personal-market-audit");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){stopped.complete(null);}};}
    public void receipt(Receipt receipt,String status,String note){final Entry entry;try(var guard=SuiteState.guard()){var player=AuditTrail.player(receipt.owner());entry=new Entry(receipt.kind().name()+":"+status,receipt.id().toString(),status,player,player,player,receipt.product(),receipt.quantity(),AuditTrail.cents(receipt.cents()),"Личный рынок; "+note);}submit(entry);}
    public void fill(Fill fill){final Entry entry;try(var guard=SuiteState.guard()){var buyer=AuditTrail.player(fill.buyer());var seller=AuditTrail.player(fill.seller());entry=new Entry("matched",fill.id().toString(),"COMPLETED",buyer,seller,buyer,fill.product(),fill.quantity(),AuditTrail.cents(fill.quantity()*fill.unitCents()),"Личные заявки: "+fill.buy()+" / "+fill.sell()+"; обеспеченный внутренний расчёт");}submit(entry);}
    synchronized void submit(Entry entry){if(closing){miss(entry,"очередь останавливается");return;}try{worker.execute(()->{try{if(SuiteState.held())throw new IllegalStateException("Audit file write under suite guard");if(!sink.write(entry))miss(entry,"файл аудита не подтвердил запись");}catch(Throwable error){miss(entry,"ошибка записи: "+error.getClass().getSimpleName());}});}catch(RejectedExecutionException full){miss(entry,"очередь заполнена");}}
    private void miss(Entry entry,String reason){gap.accept("AUDIT GAP: личный рынок "+entry.operation()+" / "+entry.step()+": "+reason+". Финансовая квитанция остаётся в personal-orders.yml/архиве; операция не повторяется.");}
    public synchronized CompletionStage<Void> closeAsync(){closing=true;worker.shutdown();return stopped;}
}
