package ru.neverland.townybuilds;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import ru.neverland.core.EffectJournal;

public final class EffectJournalSmoke {
    interface Action { void run() throws Exception; }
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void reject(Action action)throws Exception{try{action.run();}catch(Exception expected){checks++;return;}throw new AssertionError("expected failure");}
    static EffectJournal load(Path file)throws Exception{var journal=new EffectJournal(file);journal.load();return journal;}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("effect-journal-");Path file=dir.resolve("effects.yml");var calls=new AtomicInteger();UUID id=UUID.randomUUID();
        var unloaded=new EffectJournal(file);reject(()->unloaded.execute(id,"payment",()->{calls.incrementAndGet();return true;}));check(calls.get()==0,"unloaded journal cannot call bank");
        var first=load(file);first.execute(id,"payment",()->{calls.incrementAndGet();return true;});
        var second=load(file);second.execute(id,"payment",()->{calls.incrementAndGet();return true;});check(calls.get()==1,"confirmed effect survives restart without replay");
        reject(()->second.execute(id,"different amount",()->true));
        UUID uncertain=UUID.randomUUID();reject(()->second.execute(uncertain,"uncertain",()->{calls.incrementAndGet();throw new IllegalStateException("bank reply lost");}));
        var recovered=load(file);reject(()->recovered.execute(uncertain,"uncertain",()->{calls.incrementAndGet();return true;}));check(calls.get()==2&&recovered.state(uncertain)==EffectJournal.State.PENDING,"unknown bank result is durable and never retried");
        recovered.resolve(uncertain,true);recovered.execute(uncertain,"uncertain",()->{calls.incrementAndGet();return true;});check(calls.get()==2,"received reconciliation cannot repay");
        UUID declined=UUID.randomUUID();check(!recovered.execute(declined,"declined",()->false),"definite rejection returned");check(recovered.state(declined)==EffectJournal.State.READY,"definite rejection permits retry");
        recovered.execute(declined,"declined",()->true);
        Path blocked=dir.resolve("blocked");Files.createDirectory(blocked);Files.writeString(blocked.resolve("keep"),"keep");var before=load(dir.resolve("before.yml"));
        Path beforeFile=dir.resolve("before.yml");Files.createDirectory(beforeFile);Files.writeString(beforeFile.resolve("keep"),"keep");
        reject(()->before.execute(UUID.randomUUID(),"before bank",()->{calls.incrementAndGet();return true;}));check(calls.get()==2&&!before.writable(),"failed pending save blocks external effect");
        Path afterFile=dir.resolve("after.yml"),backup=dir.resolve("pending.yml");var after=load(afterFile);UUID late=UUID.randomUUID();
        reject(()->after.execute(late,"after bank",()->{calls.incrementAndGet();Files.move(afterFile,backup);Files.createDirectory(afterFile);Files.writeString(afterFile.resolve("keep"),"keep");return true;}));
        check(!after.writable(),"failed confirmation closes journal");Files.delete(afterFile.resolve("keep"));Files.delete(afterFile);Files.move(backup,afterFile);
        var lateRecovered=load(afterFile);check(lateRecovered.state(late)==EffectJournal.State.PENDING,"last durable state remains pending after completed external effect");
        reject(()->lateRecovered.execute(late,"after bank",()->{calls.incrementAndGet();return true;}));check(calls.get()==3,"confirmation failure cannot duplicate bank operation");
        Files.writeString(file,"schema: 1\neffects: broken\n");byte[] corrupt=Files.readAllBytes(file);var broken=new EffectJournal(file);reject(broken::load);reject(()->broken.resolve(id,true));check(Arrays.equals(corrupt,Files.readAllBytes(file)),"corrupt journal is preserved");
        System.out.println("EffectJournalSmoke OK: "+checks+" checks");
    }
}
