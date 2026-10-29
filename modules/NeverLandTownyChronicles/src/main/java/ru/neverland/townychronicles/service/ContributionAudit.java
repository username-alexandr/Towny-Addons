package ru.neverland.townychronicles.service;

import java.util.concurrent.*;
import java.util.function.Consumer;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;

/** Bounded receipt writer. No successful publication until the actual audit file acknowledges it. */
public final class ContributionAudit {
    @FunctionalInterface interface Sink {boolean append(AuditRecord record)throws Exception;}
    private final ThreadPoolExecutor worker;private final Sink sink;private final Consumer<String> errors;
    private final CompletableFuture<Void> stopped=new CompletableFuture<>();private boolean closing;private volatile boolean fault;
    public static final class NotAccepted extends IllegalStateException{NotAccepted(String text){super(text);}}
    public boolean fault(){return fault;}
    public ContributionAudit(JavaPlugin plugin){this(r->AuditTrail.record(plugin,r),text->plugin.getLogger().severe(text),64);}
    ContributionAudit(Sink sink,Consumer<String> errors,int capacity){
        this.sink=sink;this.errors=errors;worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(capacity),r->{var t=new Thread(r,"NeverLand-Chronicle-Audit");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){stopped.complete(null);}};
    }
    public synchronized CompletionStage<AuditRecord> submit(AuditRecord record){
        var receipt=new CompletableFuture<AuditRecord>();
        if(closing||fault){receipt.completeExceptionally(new NotAccepted(closing?"Запись летописи останавливается":"Журнал летописи ожидает проверки"));return receipt;}
        try{worker.execute(()->{
            if(fault){receipt.completeExceptionally(new NotAccepted("Журнал летописи ожидает проверки"));return;}
            try{if(SuiteState.held())throw new IllegalStateException("Audit write under SuiteState");if(!sink.append(record))throw new java.io.IOException("Файл аудита не подтвердил запись");receipt.complete(record);}
            catch(Exception ex){fault=true;errors.accept("AUDIT GAP: летопись "+record.id()+" не сохранена: "+ex.getMessage());receipt.completeExceptionally(ex);}
        });}catch(RejectedExecutionException full){receipt.completeExceptionally(new NotAccepted("Очередь записи летописи заполнена. Повторите позже"));}
        return receipt;
    }
    public synchronized CompletionStage<Void> prepareClose(){closing=true;worker.shutdown();return stopped;}
}
