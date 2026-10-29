package ru.neverland.mintespionage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import ru.neverland.core.*;
import ru.neverland.mintespionage.intelligence.*;
import static ru.neverland.mintespionage.intelligence.IntelEvidence.*;
public final class IntelPersistenceSmoke {
    static int checks;interface Work{void run()throws Exception;}static void check(boolean good,String why){checks++;if(!good)throw new AssertionError(why);}static void denied(Work action,String why)throws Exception{try{action.run();}catch(Exception expected){check(true,why);return;}throw new AssertionError(why);}static <T>T done(CompletionStage<T> task)throws Exception{return task.toCompletableFuture().get(5,TimeUnit.SECONDS);}
    public static void main(String[] args)throws Exception{
        var file=Files.createTempDirectory("spy-intel-").resolve("intelligence.yml");var repo=new IntelRepository(file);repo.load();var receipt=IntelEvidenceSmoke.receipt(UUID.randomUUID(),UUID.randomUUID(),100,Fact.unknown("money","Казна","Towny.economy",100,"Провайдер недоступен"));var field=AtomicFiles.class.getDeclaredField("writers");field.setAccessible(true);@SuppressWarnings("unchecked")var locks=(Map<Path,ReentrantLock>)field.get(null);var lock=locks.computeIfAbsent(file.toAbsolutePath().normalize(),key->new ReentrantLock());lock.lock();CompletionStage<State> pending;CompletionStage<Void> close;
        try{pending=repo.commit(receipt);check(repo.busy()&&!pending.toCompletableFuture().isDone()&&repo.state().receipts().isEmpty(),"blocked physical write cannot publish result");check(SuiteState.tryRun("espionage delayed disk",()->{}),"blocked disk releases shared ledger");denied(()->done(repo.commit(receipt)),"overlapping writes refused");close=repo.prepareClose();check(!close.toCompletableFuture().isDone(),"shutdown drains accepted outcome");}finally{lock.unlock();}
        done(close);var confirmed=done(pending);check(confirmed.receipts().get(receipt.mission()).equals(receipt)&&!confirmed.applied().contains(receipt.mission()),"capture remains separate from core publication");var restored=new IntelRepository(file);restored.load();check(restored.state().equals(confirmed),"restart preserves exact outcome, unknown source and observation time");done(restored.applied(receipt.mission()));check(restored.state().applied().contains(receipt.mission()),"core publication is durable marker");
        Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("keep"),"block");var second=IntelEvidenceSmoke.receipt(receipt.owner(),receipt.target(),200,Fact.exact("money","Казна","Towny.economy",200,10,2));var before=restored.state();denied(()->done(restored.commit(second)),"physical replacement failure propagated");check(restored.readable()&&!restored.writable()&&restored.state().equals(before),"write error preserves confirmed evidence and stops new outcomes");done(restored.prepareClose());
        var corrupt=file.resolveSibling("corrupt.yml");Files.writeString(corrupt,"schema: 99\nreceipts: damaged\n");var bad=new IntelRepository(corrupt);denied(bad::load,"unknown schema fails closed");check(!bad.readable()&&!bad.writable()&&Files.readString(corrupt).equals("schema: 99\nreceipts: damaged\n"),"optional corruption not overwritten");done(bad.prepareClose());System.out.println("IntelPersistenceSmoke PASS "+checks+" checks");
    }
}
