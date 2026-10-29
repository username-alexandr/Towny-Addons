package ru.neverland.townyideologies.reforms;

import java.util.UUID;
import java.util.concurrent.*;
import org.bukkit.plugin.Plugin;
import ru.neverland.core.*;

/** Only immutable parties cross to this bounded disk worker; never read Towny from its task. */
public final class ReformAudit {
    private final Plugin plugin;private final ThreadPoolExecutor worker;private final CompletableFuture<Void> closed=new CompletableFuture<>();
    public ReformAudit(Plugin plugin){this.plugin=plugin;worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),task->{var t=new Thread(task,"NeverLand-ideology-audit");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){closed.complete(null);}};}
    public void record(ReformBook.Reform reform,String outcome,String note){
        final AuditRecord.Party actor,city;try(var guard=SuiteState.guard()){actor=AuditTrail.player(reform.actor());city=new AuditRecord.Party("TOWN",reform.town().toString(),reform.target().townName(),reform.town().toString());}
        String step="reform:"+outcome+":"+UUID.randomUUID();try{worker.execute(()->AuditTrail.record(plugin,step,reform.id().toString(),"CITY_IDEOLOGY",outcome,actor,city,city,reform.target().ideologyId(),reform.target().level(),AuditTrail.cents(reform.cents()),note));}
        catch(RejectedExecutionException error){plugin.getLogger().severe("AUDIT GAP: реформа "+reform.id()+" / "+outcome+" не попала в журнал; платёж не повторяется");}
    }
    public CompletionStage<Void> closeAsync(){worker.shutdown();return closed;}
}
