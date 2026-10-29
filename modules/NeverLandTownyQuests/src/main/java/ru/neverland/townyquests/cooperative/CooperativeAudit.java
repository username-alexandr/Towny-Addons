package ru.neverland.townyquests.cooperative;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;
import ru.neverland.core.*;

/** SQL project history is authoritative; this bounded administrative projection never blocks SuiteState. */
final class CooperativeAudit {
    record Entry(String action,String project,String title,AuditRecord.Party actor){}
    @FunctionalInterface interface Sink {boolean write(Entry entry);}
    private final ThreadPoolExecutor worker;
    private final CompletableFuture<Void> stopped=new CompletableFuture<>();
    private final Sink sink;
    private final Consumer<String> gap;
    private boolean closing,failed;
    private int pending;
    CooperativeAudit(Plugin plugin){this(entry->AuditTrail.record(plugin,entry.action(),entry.project(),"CITY_PROJECT",entry.action(),entry.actor(),AuditRecord.Party.system(),AuditRecord.Party.system(),"",0,"","Общая стройка: "+entry.title()),plugin.getLogger()::severe,256);}
    CooperativeAudit(Sink sink,Consumer<String> gap,int capacity){
        this.sink=sink;this.gap=gap;
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(capacity),task->{var t=new Thread(task,"NeverLand-cooperative-audit");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){
            @Override protected void terminated(){synchronized(CooperativeAudit.this){if(failed)stopped.completeExceptionally(new IOException("Не все записи аудита общих строек подтверждены; история сметы сохранена в Contracts"));else stopped.complete(null);}}
        };
    }
    /** Register while accepting the command. Shutdown waits for these SQL results before draining audit. */
    void track(CompletionStage<Map<String,Object>> operation,String action,AuditRecord.Party actor){
        synchronized(this){if(closing){miss(action,"неизвестно","раздел уже останавливается");return;}pending++;}
        operation.whenComplete((row,error)->{
            synchronized(this){
                try{if(error==null){var entry=new Entry(action,(String)row.get("id"),(String)row.get("title"),actor);submit(entry);}}
                catch(Throwable failure){miss(action,"неизвестно","не удалось обработать подтверждение "+failure.getClass().getSimpleName());}
                finally{pending--;if(closing&&pending==0)worker.shutdown();}
            }
        });
    }
    private void submit(Entry entry){
        try{worker.execute(()->{
            try{if(SuiteState.held())throw new IllegalStateException("Audit under SuiteState");if(!sink.write(entry))miss(entry.action(),entry.project(),"файл не подтвердил запись");}
            catch(Throwable failure){miss(entry.action(),entry.project(),"ошибка записи "+failure.getClass().getSimpleName());}
        });}catch(RejectedExecutionException full){miss(entry.action(),entry.project(),"очередь заполнена");}
    }
    private synchronized void miss(String action,String project,String reason){failed=true;gap.accept("AUDIT GAP: общая стройка "+project+" / "+action+": "+reason+". История и материалы остаются в server-materials.db; операция не повторяется.");}
    synchronized CompletionStage<Void> closeAsync(){closing=true;if(pending==0)worker.shutdown();return stopped;}
}
