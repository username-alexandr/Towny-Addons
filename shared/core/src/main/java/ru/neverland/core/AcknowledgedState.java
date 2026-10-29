package ru.neverland.core;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Compare/commit an immutable image; no new state is visible before its durable acknowledgement. */
public final class AcknowledgedState<T> {
    @FunctionalInterface public interface Encoder<T>{String encode(T image)throws IOException;}
    private final Path file;private final Encoder<T> encoder;private final DurableSnapshots writes;
    private T state;private boolean loaded,busy,closing,failed;
    public AcknowledgedState(Path file,T initial,Encoder<T> encoder,String thread){this.file=file;this.state=Objects.requireNonNull(initial);this.encoder=encoder;writes=new DurableSnapshots(file,thread,2);}
    /** Called only after the owning repository validates its existing data. */
    public void loaded(T image){try(var guard=SuiteState.guard()){if(busy||closing)throw new IllegalStateException("Запись уже используется");state=Objects.requireNonNull(image);loaded=true;failed=false;AtomicFiles.loaded(file);}}
    public T get(){try(var guard=SuiteState.guard()){return state;}}
    public boolean busy(){try(var guard=SuiteState.guard()){return busy;}}
    public boolean writable(){try(var guard=SuiteState.guard()){return loaded&&!closing&&!failed&&AtomicFiles.writable(file);}}
    public CompletionStage<T> commit(T expected,T next){try(var guard=SuiteState.guard()){
        if(!writable())return CompletableFuture.failedFuture(new IOException("Хранилище недоступно или останавливается"));
        if(busy)return CompletableFuture.failedFuture(new IllegalStateException("Предыдущее изменение ещё сохраняется"));
        if(!state.equals(expected))return CompletableFuture.failedFuture(new IllegalStateException("Данные изменились; откройте меню заново"));
        Objects.requireNonNull(next);if(state.equals(next))return CompletableFuture.completedFuture(state);busy=true;
        return writes.submit(()->encoder.encode(next)).handle((receipt,error)->{try(var committed=SuiteState.guard()){busy=false;if(error!=null){failed=true;throw new CompletionException(error);}state=next;return next;}});
    }}
    public CompletionStage<Void> prepareClose(){try(var guard=SuiteState.guard()){closing=true;return writes.closeAsync();}}
    public void awaitClosed(){var done=prepareClose().toCompletableFuture();if(SuiteState.held()&&!done.isDone())throw new IllegalStateException("Сначала завершите запись вне общей блокировки");try{done.get(10,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("Ожидание записи прервано",error);}catch(ExecutionException|TimeoutException error){throw new IllegalStateException("Запись ещё не завершена",error);}}
}
