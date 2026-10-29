package ru.neverland.townytreasury.service;
import java.util.concurrent.*;import org.bukkit.plugin.Plugin;import ru.neverland.core.*;import ru.neverland.townytreasury.debits.AsyncDebit;
/** At most one outstanding gateway receipt; immutable snapshots only, no Towny/Player lookup on worker. */
final class DebitAudit {
    private final Plugin plugin;private final CompletableFuture<Void> closed=new CompletableFuture<>();private final ThreadPoolExecutor worker;
    DebitAudit(Plugin plugin){this.plugin=plugin;worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),r->{var t=new Thread(r,"nlt-treasury-debit-audit");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){closed.complete(null);}};}
    CompletionStage<Boolean> append(AsyncDebit d){var done=new CompletableFuture<Boolean>();try{worker.execute(()->{try{
        if(SuiteState.held())throw new IllegalStateException("Audit attempted under Suite");var t=d.terms();boolean refund=t.kind()==AsyncDebit.Kind.REFUND;var town=new AuditRecord.Party("TOWN",t.town().toString(),t.town().toString(),t.town().toString());var reserve=new AuditRecord.Party("ESCROW",t.operation().toString(),t.owner(),t.town().toString());
        done.complete(AuditTrail.record(plugin,d.state()+":"+d.changedAt(),t.operation().toString(),d.state()==AsyncDebit.State.REVIEW?"TREASURY_ASYNC_REVIEW":refund?"TREASURY_ASYNC_REFUND":"TREASURY_ASYNC_DEBIT",d.state().name(),new AuditRecord.Party("PLAYER",t.actor().toString(),t.actor().toString(),t.town().toString()),refund?reserve:town,refund?town:reserve,"",0,java.math.BigDecimal.valueOf(t.cents(),2).toPlainString(),"owner="+t.owner()+"; category="+t.category()+"; source="+t.source()+"; backing="+t.backingDebit()+"; "+t.reason()+"; "+d.review()));
    }catch(Throwable error){plugin.getLogger().warning("Квитанция казны ожидает повторной записи аудита: "+d.terms().operation());done.complete(false);}});}catch(RejectedExecutionException e){done.complete(false);}return done;}
    CompletionStage<Void> closeAsync(){worker.shutdown();return closed;}
}
