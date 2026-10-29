package ru.neverland.townycouncil;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import ru.neverland.core.*;
import static ru.neverland.townycouncil.Delegations.*;
public final class DelegationsSmoke {
    static int checks;static final UUID T=UUID.randomUUID(),R=UUID.randomUUID(),M=UUID.randomUUID();
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    interface Work{void run()throws Exception;}
    static void denied(Work work,String why)throws Exception{try{work.run();}catch(Exception expected){check(true,why);return;}throw new AssertionError(why);}
    static <T>T done(CompletionStage<T> value)throws Exception{return value.toCompletableFuture().get(5,TimeUnit.SECONDS);}
    public static void main(String[] args)throws Exception{
        var grant=new Grant(UUID.randomUUID(),T,R,M,"build",100,HOUR+100,"Mayor","Помочь стройке",false,1);var empty=State.empty();var first=Delegations.grant(empty,grant,0);
        check(grant.active(M,true,true,100)&&grant.active(M,true,true,HOUR+99),"fixed interval is inclusive at start and exclusive at expiry");check(!grant.active(M,true,true,HOUR+100),"no grace after exact expiry");check(!grant.active(M,true,true,99),"backward clock cannot activate future grant");check(!grant.active(UUID.randomUUID(),true,true,100),"different current mayor cannot authorize old grant");check(!grant.active(M,false,true,100)&&!grant.active(M,true,false,100),"membership and office eligibility checked live");
        denied(()->Delegations.grant(first,grant,1),"repeated grant ID cannot refresh expiry");denied(()->Delegations.grant(first,new Grant(UUID.randomUUID(),T,R,M,"build",101,HOUR+101,"Mayor","same",false,1),1),"cannot stack active grant for same action");
        denied(()->Delegations.grant(first,new Grant(UUID.randomUUID(),T,R,M,"export",101,HOUR+101,"Mayor","stale",false,1),0),"stale image cannot overwrite another grant");
        var revoked=Delegations.revoke(first,grant.key(),grant.id(),1,M,"Mayor","Отзыв",200);check(revoked.grants().get(grant.key()).revoked()&&!revoked.grants().get(grant.key()).active(M,true,true,201)&&revoked.history().size()==2,"revocation and its history are one immutable image");check(Delegations.revoke(revoked,grant.key(),grant.id(),2,M,"Mayor","same",201)==revoked,"duplicate revoke has no repeated effect");
        denied(()->new Grant(UUID.randomUUID(),T,M,M,"build",1,2,"Mayor","self",false,1),"mayor never delegates to self");denied(()->new Grant(UUID.randomUUID(),T,R,M,"server.op",1,2,"Mayor","bad",false,1),"closed action list cannot manufacture server permissions");
        var dir=Files.createTempDirectory("council-delegations-");var file=dir.resolve("delegations.yml");var repository=new DelegationRepository(file);repository.load();done(repository.commit(empty,first));var loaded=new DelegationRepository(file);loaded.load();check(loaded.state().equals(first),"restart retains exact grant and history");
        String before=Files.readString(file);var shifted=ModuleTimers.shifted(before,ModuleTimers.rules("NeverLandTownyCouncil").get("delegations.yml"),721*HOUR);var shiftedPath=dir.resolve("shifted.yml");Files.writeString(shiftedPath,shifted);var resumed=new DelegationRepository(shiftedPath);resumed.load();check(resumed.state().grants().get(grant.key()).expiresAt()==grant.expiresAt()+721*HOUR&&resumed.state().grants().get(grant.key()).grantedAt()==grant.grantedAt()&&resumed.state().history().equals(first.history()),"long administrative pause extends only expiry, not historical grant dates");
        done(repository.commit(first,revoked));before=Files.readString(file);shifted=ModuleTimers.shifted(before,ModuleTimers.rules("NeverLandTownyCouncil").get("delegations.yml"),HOUR);check(before.equals(shifted),"revoked grant dates never move during module pause");
        var writers=AtomicFiles.class.getDeclaredField("writers");writers.setAccessible(true);@SuppressWarnings("unchecked")var locks=(Map<Path,java.util.concurrent.locks.ReentrantLock>)writers.get(null);var lock=locks.computeIfAbsent(file.toAbsolutePath().normalize(),p->new java.util.concurrent.locks.ReentrantLock());lock.lock();CompletionStage<State> pending;CompletionStage<Void> close;
        try{pending=loaded.commit(first,revoked);check(!pending.toCompletableFuture().isDone()&&loaded.state().equals(first),"unacknowledged revoke never publishes on slow actual disk");check(SuiteState.tryRun("delegations delayed disk",()->{}),"actual delayed writer never holds SuiteState");close=loaded.prepareClose();check(!close.toCompletableFuture().isDone(),"close waits for accepted revoke");}finally{lock.unlock();}done(close);check(done(pending).equals(revoked),"accepted revoke publishes before close completion");
        var failed=new DelegationRepository(file);failed.load();Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("keep"),"preserve");denied(()->done(failed.commit(revoked,empty)),"actual failed replacement is reported");check(failed.readable()&&!failed.writable()&&failed.state().equals(revoked),"disk fault preserves confirmed grants and stops new writes");
        var invalid=dir.resolve("bad.yml");Files.writeString(invalid,"schema: 9\nrevision: 0\ngrants: {}\nhistory: '[]'\n");var bad=new DelegationRepository(invalid);denied(bad::load,"unknown schema cannot reset permissions");
        done(repository.prepareClose());done(resumed.prepareClose());done(failed.prepareClose());done(bad.prepareClose());
        System.out.println("DelegationsSmoke PASS "+checks+" checks");
    }
}
