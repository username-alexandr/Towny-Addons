package ru.neverland.core;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Bounded, disposable CPU views of immutable inputs. Never use for payments, inventory or accepted gameplay writes. */
public final class DerivedViewCache<I,R> {
    public record Reading<R>(String status,R result,long at,String issue){}
    private record Computed<R>(R result,long at){}
    private record Ticket<I,R>(I input,CompletableFuture<Computed<R>> future){}
    private final Map<UUID,Ticket<I,R>> tickets=new LinkedHashMap<>();
    private final ThreadPoolExecutor worker;private final CompletableFuture<Void> terminated=new CompletableFuture<>();private final Function<I,R> calculate;private final Consumer<Throwable> diagnostic;private boolean closing;
    public DerivedViewCache(String name,Function<I,R> calculate,Consumer<Throwable> diagnostic){
        this.calculate=Objects.requireNonNull(calculate);this.diagnostic=Objects.requireNonNull(diagnostic);if(name==null||name.isBlank())throw new IllegalArgumentException("Нужно имя расчёта");
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(16),r->{var thread=new Thread(r,name);thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){terminated.complete(null);}};
    }
    public synchronized Reading<R> read(UUID key,I input){
        Objects.requireNonNull(key);Objects.requireNonNull(input);if(closing)return new Reading<>("CLOSED",null,0,"Расчёт останавливается");var ticket=tickets.get(key);
        if(ticket!=null&&!ticket.future().isDone())return new Reading<>("PENDING",null,0,"Расчёт готовится. Обновите окно через несколько секунд");
        if(ticket!=null&&ticket.input().equals(input))try{var ready=ticket.future().join();return new Reading<>("READY",ready.result(),ready.at(),"");}catch(CompletionException|CancellationException error){return new Reading<>("UNAVAILABLE",null,0,"Расчёт пока недоступен. Сообщите администрации сервера");}
        if(ticket==null&&tickets.size()>=128){var iterator=tickets.entrySet().iterator();boolean removed=false;while(iterator.hasNext())if(iterator.next().getValue().future().isDone()){iterator.remove();removed=true;break;}if(!removed)return new Reading<>("PENDING",null,0,"Очередь расчёта занята. Попробуйте немного позже");}
        var future=new CompletableFuture<Computed<R>>();var next=new Ticket<I,R>(input,future);
        try{worker.execute(()->{try{future.complete(new Computed<>(Objects.requireNonNull(calculate.apply(input)),System.currentTimeMillis()));}catch(Throwable error){future.completeExceptionally(error);if(!(error instanceof CancellationException))try{diagnostic.accept(error);}catch(RuntimeException ignored){}}});}catch(RejectedExecutionException full){return new Reading<>("PENDING",null,0,"Очередь расчёта занята. Попробуйте немного позже");}
        tickets.put(key,next);return new Reading<>("PENDING",null,0,"Расчёт готовится. Обновите окно через несколько секунд");
    }
    /** Only derived work is discarded. Any durable store must use its own drain/acknowledgement protocol. */
    public synchronized CompletionStage<Void> prepareClose(){if(!closing){closing=true;worker.shutdownNow();tickets.values().forEach(t->t.future().completeExceptionally(new CancellationException("Расчёт остановлен")));}return terminated;}
    public void awaitClosed(){var complete=prepareClose().toCompletableFuture();if(SuiteState.held()&&!complete.isDone())throw new IllegalStateException("Дождитесь расчёта вне общей блокировки");try{complete.get(10,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("Ожидание расчёта прервано",error);}catch(ExecutionException|TimeoutException error){throw new IllegalStateException("Расчёт ещё не завершён",error);}}
}
