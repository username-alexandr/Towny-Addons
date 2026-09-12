package ru.neverland.mintexpeditions.service;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import ru.neverland.core.EffectJournal;

public final class RewardPersistenceSmoke {
    interface Action { void run() throws Exception; }
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void reject(Action action)throws Exception{try{action.run();}catch(Exception expected){checks++;return;}throw new AssertionError("expected failure");}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("reward-persistence-");var repo=new ExpeditionRepository(dir);repo.load();UUID owner=UUID.randomUUID(),id=UUID.randomUUID();var bank=new AtomicInteger();
        repo.reward(id,owner,List.of(),19.99);repo.save();var batch=repo.rewards(owner).get(0);reject(()->repo.completeReward(batch));
        repo.effects().execute(batch.moneyId(),"reward",()->{bank.incrementAndGet();return true;});
        Path file=dir.resolve("data.yml"),backup=dir.resolve("backup.yml");Files.move(file,backup);Files.createDirectory(file);Files.writeString(file.resolve("keep"),"keep");
        reject(()->repo.completeReward(batch));check(!repo.writable(),"completion save failure blocks mutations");Files.delete(file.resolve("keep"));Files.delete(file);Files.move(backup,file);
        var restored=new ExpeditionRepository(dir);restored.load();check(restored.hasReward(owner)&&restored.money(owner)==0,"paid reward restored without unpaid money");
        restored.effects().execute(batch.moneyId(),"reward",()->{bank.incrementAndGet();return true;});check(bank.get()==1,"completed bank effect is not repeated");restored.completeReward(restored.rewards(owner).get(0));restored.pruneEffects();
        var clean=new ExpeditionRepository(dir);clean.load();check(!clean.hasReward(owner),"owner removal survives reload");
        Path legacy=Files.createTempDirectory("legacy-reward-");Files.writeString(legacy.resolve("data.yml"),"rewards:\n  "+owner+":\n    money: 7.0\n    items: []\n");
        var migrated=new ExpeditionRepository(legacy);migrated.load();var old=migrated.rewards(owner).get(0);check(migrated.effects().state(old.moneyId())==EffectJournal.State.PENDING,"legacy payout requires review");migrated.effects().resolve(old.moneyId(),false);
        var resumed=new ExpeditionRepository(legacy);resumed.load();check(resumed.effects().state(old.moneyId())==EffectJournal.State.READY,"migration never resets a saved reconciliation");
        Files.writeString(file,"schema: 2\nreward-batches: damaged\n");byte[] corrupt=Files.readAllBytes(file);reject(clean::load);reject(()->clean.reward(UUID.randomUUID(),owner,List.of(),1));check(Arrays.equals(corrupt,Files.readAllBytes(file)),"damaged primary file is not overwritten");
        reject(()->new RewardBatch(id,owner,List.of(),Double.NaN));
        System.out.println("RewardPersistenceSmoke OK: "+checks+" checks");
    }
}
