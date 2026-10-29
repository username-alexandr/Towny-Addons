package ru.neverland.townydistricts.service;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import ru.neverland.townydistricts.model.DistrictAssessment.*;

/** One bounded CPU worker, immutable inputs and no Bukkit, Towny, disk or SuiteState on that worker. */
public final class DistrictPlanner {
    public record Reading(String status,Result result,long at,String issue){}
    private record Computed(Result result,long at){}
    private record Ticket(Input input,CompletableFuture<Computed> future){}
    private final Map<UUID,Ticket> tickets=new LinkedHashMap<>();
    private final ThreadPoolExecutor worker;private final CompletableFuture<Void> terminated=new CompletableFuture<>();private final Function<Input,Result> calculate;private final java.util.function.Consumer<Throwable> diagnostic;private boolean closing;
    public DistrictPlanner(){this(ru.neverland.townydistricts.model.DistrictAssessment::calculate);}
    DistrictPlanner(Function<Input,Result> calculate){this(calculate,error->{});}
    DistrictPlanner(Function<Input,Result> calculate,java.util.function.Consumer<Throwable> diagnostic){this.calculate=calculate;this.diagnostic=diagnostic;worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(16),r->{var t=new Thread(r,"NeverLand-district-assessment");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){terminated.complete(null);}};}
    public synchronized Reading read(UUID town,Input input){
        Objects.requireNonNull(town);Objects.requireNonNull(input);
        if(closing)return new Reading("CLOSED",null,0,"Оценка районов останавливается");var ticket=tickets.get(town);
        if(ticket!=null&&!ticket.future().isDone())return new Reading("PENDING",null,0,"Оценка готовится. Обновите окно через несколько секунд");
        if(ticket!=null&&ticket.input().equals(input))try{var ready=ticket.future().join();return new Reading("READY",ready.result(),ready.at(),"");}catch(CompletionException error){return new Reading("UNAVAILABLE",null,0,"Не удалось оценить районы. Сообщите администрации сервера");}
        if(ticket==null&&tickets.size()>=128){var iterator=tickets.entrySet().iterator();boolean removed=false;while(iterator.hasNext())if(iterator.next().getValue().future().isDone()){iterator.remove();removed=true;break;}if(!removed)return new Reading("PENDING",null,0,"Очередь оценки занята. Попробуйте немного позже");}
        var future=new CompletableFuture<Computed>();var next=new Ticket(input,future);
        try{worker.execute(()->{try{future.complete(new Computed(calculate.apply(input),System.currentTimeMillis()));}catch(Throwable error){future.completeExceptionally(error);if(!(error instanceof CancellationException))try{diagnostic.accept(error);}catch(RuntimeException ignored){}}});}catch(RejectedExecutionException full){return new Reading("PENDING",null,0,"Очередь оценки занята. Попробуйте немного позже");}
        tickets.put(town,next);return new Reading("PENDING",null,0,"Оценка готовится. Обновите окно через несколько секунд");
    }
    public synchronized CompletionStage<Void> prepareClose(){if(!closing){closing=true;worker.shutdownNow();tickets.values().forEach(t->t.future().completeExceptionally(new CancellationException("Оценка остановлена")));}return terminated;}
    public void awaitClosed(){var completion=prepareClose().toCompletableFuture();if(ru.neverland.core.SuiteState.held()&&!completion.isDone())throw new IllegalStateException("Закройте оценку вне общей блокировки");try{completion.get(10,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("Ожидание оценки прервано",error);}catch(ExecutionException|TimeoutException error){throw new IllegalStateException("Оценка районов ещё не завершена",error);}}
}
