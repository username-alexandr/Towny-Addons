package ru.neverland.townycontrol;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import ru.neverland.core.*;
public final class AcknowledgedStateSmoke {
    static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static <T>T done(CompletionStage<T> future)throws Exception{return future.toCompletableFuture().get(4,TimeUnit.SECONDS);}
    public static void main(String[] args)throws Exception{
        var folder=Files.createTempDirectory("ack-state-");var file=folder.resolve("state.txt");var empty=Map.<String,Integer>of();var first=Map.of("mode",1);var second=Map.of("mode",2);
        var state=new AcknowledgedState<Map<String,Integer>>(file,empty,Map::toString,"ack-state-fixture");check(state.commit(empty,first).toCompletableFuture().isCompletedExceptionally(),"unvalidated state cannot overwrite source");state.loaded(empty);done(state.commit(empty,first));check(state.get().equals(first)&&Files.readString(file).equals(first.toString()),"state publishes only after acknowledged file commit");
        check(state.commit(empty,second).toCompletableFuture().isCompletedExceptionally(),"stale image cannot overwrite a later decision");
        var writers=AtomicFiles.class.getDeclaredField("writers");writers.setAccessible(true);@SuppressWarnings("unchecked")var locks=(java.util.concurrent.ConcurrentMap<Path,java.util.concurrent.locks.ReentrantLock>)writers.get(null);var lock=locks.computeIfAbsent(file.toAbsolutePath().normalize(),p->new java.util.concurrent.locks.ReentrantLock());lock.lock();CompletionStage<Map<String,Integer>> pending;CompletionStage<Void> closing;
        try{pending=state.commit(first,second);try(var guard=SuiteState.guard()){check(state.get().equals(first)&&!pending.toCompletableFuture().isDone(),"slow actual file writer does not publish unacknowledged state or hold suite ledger");check(state.commit(first,Map.of("other",3)).toCompletableFuture().isCompletedExceptionally(),"overlapping mutation cannot overwrite queued state");closing=state.prepareClose();check(!closing.toCompletableFuture().isDone(),"controlled shutdown waits for accepted file operation");}}finally{lock.unlock();}
        done(closing);check(done(pending).equals(second)&&state.get().equals(second),"shutdown drains accepted operation and its publication callback");check(state.commit(second,first).toCompletableFuture().isCompletedExceptionally(),"closed repository rejects new mutation");
        var invalid=new AcknowledgedState<Map<String,Integer>>(folder.resolve("never-loaded.txt"),empty,Map::toString,"ack-unloaded-fixture");done(invalid.prepareClose());check(!Files.exists(folder.resolve("never-loaded.txt")),"shutdown never replaces an unvalidated file with an empty image");
        var brokenPath=folder.resolve("blocked");Files.createDirectory(brokenPath);Files.writeString(brokenPath.resolve("keep"),"preserve");var broken=new AcknowledgedState<Map<String,Integer>>(brokenPath,first,Map::toString,"ack-failed-fixture");broken.loaded(first);boolean refused=false;try{done(broken.commit(first,second));}catch(ExecutionException expected){refused=true;}check(refused&&broken.get().equals(first)&&!broken.writable(),"real atomic replacement failure preserves confirmed state and pauses future mutations");done(broken.prepareClose());
        System.out.println("AcknowledgedStateSmoke PASS "+checks+" checks");
    }
}
