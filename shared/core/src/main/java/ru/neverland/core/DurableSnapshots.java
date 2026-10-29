package ru.neverland.core;

import java.io.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Ordered bounded writes. A receipt completes only after fsync and atomic replacement. */
public final class DurableSnapshots {
    private final Path file;
    private final ThreadPoolExecutor worker;
    private final AtomicLong accepted=new AtomicLong(),committed=new AtomicLong();
    private record FinalImage(long id,AtomicFiles.Snapshot image,CompletableFuture<Long> receipt){}
    private final java.util.concurrent.atomic.AtomicReference<FinalImage> finalImage=new java.util.concurrent.atomic.AtomicReference<>();
    private volatile boolean closing;
    private final CompletableFuture<Void> terminated=new CompletableFuture<>();
    public DurableSnapshots(Path file,String name,int capacity){
        if(capacity<1||capacity>64)throw new IllegalArgumentException("Snapshot capacity must be 1..64");
        this.file=file;
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(capacity),r->{Thread t=new Thread(r,name);t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){
            @Override protected void terminated(){terminated.complete(null);}
            @Override protected void afterExecute(Runnable action,Throwable error){
                if(closing&&getQueue().isEmpty()){
                    var last=finalImage.getAndSet(null);if(last!=null)write(last.id(),last.image(),last.receipt());
                }
            }
        };
    }
    /** Capture all mutable state before calling; encode may run much later, without the ledger. */
    public synchronized CompletionStage<Long> submit(AtomicFiles.Snapshot immutable){
        var receipt=new CompletableFuture<Long>();if(closing){receipt.completeExceptionally(new IOException("Сохранение останавливается"));return receipt;}long id=accepted.incrementAndGet();
        try{worker.execute(()->write(id,immutable,receipt));}catch(RejectedExecutionException error){receipt.completeExceptionally(new IOException("Очередь сохранения заполнена или остановлена",error));}
        return receipt;
    }
    private void write(long id,AtomicFiles.Snapshot immutable,CompletableFuture<Long> receipt){
        try{AtomicFiles.writeDetached(file,immutable.encode());committed.set(id);receipt.complete(id);}
        catch(Throwable error){receipt.completeExceptionally(error);}
    }
    /** One reserved final slot. Even a full regular queue cannot drop the shutdown image. */
    public synchronized CompletionStage<Long> finish(AtomicFiles.Snapshot immutable){
        if(closing)return CompletableFuture.failedFuture(new IOException("Сохранение уже останавливается"));
        var receipt=new CompletableFuture<Long>();finalImage.set(new FinalImage(accepted.incrementAndGet(),immutable,receipt));closing=true;
        try{worker.execute(()->{});}catch(RejectedExecutionException full){/* Existing tasks execute the final image after the queue drains. */}
        worker.shutdown();return receipt;
    }
    public long committed(){return committed.get();}
    public int queued(){return worker.getQueue().size();}
    /** Immutable owners have no unsaved image: drain accepted writes without adding a shutdown write. */
    public synchronized CompletionStage<Void> closeAsync(){closing=true;worker.shutdown();return terminated;}
    /** Shutdown may wait only after releasing the ledger. Accepted writes are never discarded. */
    public boolean drain(Duration timeout)throws InterruptedException{
        if(SuiteState.held())throw new IllegalStateException("Release the suite ledger before draining writes");
        closing=true;worker.shutdown();return worker.awaitTermination(Math.max(1,timeout.toMillis()),TimeUnit.MILLISECONDS);
    }
}
