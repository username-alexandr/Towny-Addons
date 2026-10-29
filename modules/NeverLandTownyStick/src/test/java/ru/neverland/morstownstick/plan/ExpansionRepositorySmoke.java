package ru.neverland.morstownstick.plan;
import ru.neverland.core.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
public final class ExpansionRepositorySmoke {
    static int checks;static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    static <T>T await(CompletionStage<T> stage)throws Exception{return stage.toCompletableFuture().get(5,TimeUnit.SECONDS);}
    static void failed(CompletionStage<?> stage)throws Exception{try{await(stage);}catch(ExecutionException expected){checks++;return;}throw new AssertionError("must reject");}
    public static void main(String[] args)throws Exception{
        var dir=Files.createTempDirectory("expansion-smoke");var file=dir.resolve("plans.yml");var repo=new ExpansionRepository(file,r->true);await(repo.load());var original=repo.state();var plan=ExpansionPlanSmoke.draft();var next=original.with(plan);
        var f=AtomicFiles.class.getDeclaredField("writers");f.setAccessible(true);@SuppressWarnings("unchecked")var locks=(Map<Path,ReentrantLock>)f.get(null);var lock=locks.computeIfAbsent(file.toAbsolutePath(),p->new ReentrantLock());lock.lock();CompletionStage<ExpansionRepository.Snapshot> writing;
        try{try(var guard=SuiteState.guard()){writing=repo.commit(original,next,null);}check(repo.busy()&&repo.state().equals(original),"no unacknowledged publication");failed(repo.commit(original,next,null));var heartbeat=CompletableFuture.supplyAsync(()->SuiteState.tryRun("blocked expansion writer",()->{}));check(heartbeat.get(1,TimeUnit.SECONDS),"Suite heartbeat remains free during actual blocked write");check(!writing.toCompletableFuture().isDone(),"actual file write still blocked");}finally{lock.unlock();}
        await(writing);check(repo.state().equals(next)&&Files.isRegularFile(file),"ACK publishes exactly persisted image");failed(repo.commit(original,next,null));repo.awaitClosed();
        var restored=new ExpansionRepository(file,r->true);await(restored.load());check(restored.state().equals(next),"full repository restore exact snapshot");var intent=plan.approve(plan.mayor(),1,1001).intent(plan.mayor(),1,1002);await(restored.commit(restored.state(),restored.state().with(intent),null));restored.awaitClosed();
        var uncertain=new ExpansionRepository(file,r->false);await(uncertain.load());check(uncertain.state().plans().get(plan.id()).status()==ExpansionPlan.Status.INTENT,"unknown execution never resumes on load");var receipt=new AuditRecord(AuditRecord.id("NeverLandTownyStick",plan.id().toString(),"review"),1003,"NeverLandTownyStick","CITY_EXPANSION","REVIEW",plan.id().toString(),AuditRecord.Party.unknown(),AuditRecord.Party.unknown(),AuditRecord.Party.unknown(),"",0,"","failure fixture");
        var review=intent.result(List.of(),"unknown",1003);failed(uncertain.commit(uncertain.state(),uncertain.state().with(review),receipt));check(!uncertain.ready()&&uncertain.state().plans().get(plan.id()).status()==ExpansionPlan.Status.INTENT,"audit failure fails closed before visible acknowledgement");check(Files.readString(file).contains("REVIEW"),"durable uncertain outcome retained for restart review");uncertain.awaitClosed();
        var corrupt=dir.resolve("corrupt.yml");Files.writeString(corrupt,"schema: [broken\n");var bytes=Files.readAllBytes(corrupt);var broken=new ExpansionRepository(corrupt,r->true);failed(broken.load());check(!broken.ready()&&Arrays.equals(bytes,Files.readAllBytes(corrupt)),"corrupt data preserved byte for byte");broken.awaitClosed();
        var shutdown=new ExpansionRepository(dir.resolve("shutdown.yml"),r->true);await(shutdown.load());var finalWrite=shutdown.commit(shutdown.state(),next,null);var close=shutdown.prepareClose();await(finalWrite);await(close);check(Files.readString(dir.resolve("shutdown.yml")).contains(plan.id().toString()),"shutdown drains accepted write");
        System.out.println("ExpansionRepositorySmoke PASS "+checks);
    }
}
