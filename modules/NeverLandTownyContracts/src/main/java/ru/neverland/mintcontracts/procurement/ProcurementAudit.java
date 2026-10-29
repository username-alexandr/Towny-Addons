package ru.neverland.mintcontracts.procurement;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import ru.neverland.core.*;

/** Durable SQL history is the outbox; retrying one immutable ID never repeats a goods operation. */
final class ProcurementAudit {
    private final ProcurementRepository repository;
    private final Predicate<AuditRecord> sink;
    private final Consumer<String> warning;
    private final ExecutorService writer=Executors.newSingleThreadExecutor(task->{var t=new Thread(task,"NeverLand-procurement-audit");t.setDaemon(true);return t;});
    private CompletableFuture<Void> running=CompletableFuture.completedFuture(null),closed;
    ProcurementAudit(ProcurementRepository repository,Predicate<AuditRecord> sink,Consumer<String> warning){this.repository=repository;this.sink=sink;this.warning=warning;}
    synchronized CompletionStage<Void> flush(){if(closed!=null)return closed;if(!running.isDone())return running;running=pass(false).toCompletableFuture();running.whenComplete((v,e)->{if(e!=null)warning.accept("Аудит закупок ожидает повторной записи; квитанции сохранены в procurement.sqlite: "+e.getMessage());});return running;}
    private CompletionStage<Void> pass(boolean drain){return repository.auditPending().thenCompose(rows->{if(rows.isEmpty())return CompletableFuture.completedFuture(null);return CompletableFuture.supplyAsync(()->{
        if(SuiteState.held())throw new IllegalStateException("Audit under SuiteState");var accepted=new ArrayList<String>();for(var row:rows){if(!sink.test(row))break;accepted.add(row.id());}return accepted;
    },writer).thenCompose(accepted->repository.auditAcknowledged(accepted).thenCompose(v->{if(accepted.size()!=rows.size())return CompletableFuture.failedFuture(new IOException("Файл административного журнала не подтвердил все квитанции"));return drain?pass(true):CompletableFuture.completedFuture(null);}));});}
    synchronized CompletionStage<Void> closeAsync(){if(closed!=null)return closed;
        // Failed initialization accepted no operations. Leave that source untouched and close the worker;
        // a later failure after successful initialization must still report an unflushed audit.
        closed=repository.ready().handle((v,error)->error==null).thenCompose(initialized->initialized?running.handle((v,e)->null).thenCompose(v->pass(true)):CompletableFuture.<Void>completedFuture(null)).toCompletableFuture();
        closed.whenComplete((v,e)->writer.shutdown());return closed;
    }
}
