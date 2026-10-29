package ru.neverland.townycitizens;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import ru.neverland.core.*;
import ru.neverland.townycitizens.model.*;
import static ru.neverland.townycitizens.model.Admissions.*;

public final class AdmissionsSmoke {
    static int checks;static final UUID T=UUID.randomUUID(),P=UUID.randomUUID(),S=UUID.randomUUID(),M=UUID.randomUUID();
    static void check(boolean good,String why){checks++;if(!good)throw new AssertionError(why);}
    interface Work{void run()throws Exception;}
    static void denied(Work work,String why)throws Exception{try{work.run();}catch(Exception expected){check(true,why);return;}throw new AssertionError(why);}
    static <T>T done(CompletionStage<T> value)throws Exception{return value.toCompletableFuture().get(5,TimeUnit.SECONDS);}
    static Application app(State state,CitizenshipStatus status,int days,long registered){return new Application(UUID.randomUUID(),T,P,"Applicant",status,days,365,state.board().terms(T),registered,"Хочу помогать городу",DAY*10,Phase.OPEN,Map.of(),null,"",0,"",1);}
    public static void main(String[] args)throws Exception{
        var empty=State.empty();var rules=new Terms(1,2,"Помогать городу",1);var initial=policy(empty,T,rules,0);var a=app(initial,CitizenshipStatus.TEMPORARY,7,DAY*9);var submitted=submit(initial,a);
        check(submit(submitted,a)==submitted,"same submission ID is idempotent");
        denied(()->submit(submitted,app(initial,CitizenshipStatus.CITIZEN,0,DAY)),"one open application per town and applicant");
        denied(()->sponsor(submitted,a.id(),1,new Sponsor(P,"Applicant",DAY*10),false),"self sponsorship forbidden");
        var supported=sponsor(submitted,a.id(),1,new Sponsor(S,"Sponsor",DAY*10),false);
        check(sponsor(supported,a.id(),2,new Sponsor(S,"Sponsor",DAY*11),false)==supported,"duplicate sponsor is no-op");
        var revoked=sponsor(supported,a.id(),2,new Sponsor(S,"Sponsor",DAY*11),true);
        check(revoked.board().applications().get(a.id()).sponsors().isEmpty(),"sponsor can revoke without status penalty");
        var supportedApp=supported.board().applications().get(a.id());
        check(!blockers(supportedApp,Set.of(S),true,true,false,DAY*11-1).isEmpty(),"registration age exact lower boundary");
        check(blockers(supportedApp,Set.of(S),true,true,false,DAY*11).isEmpty(),"age satisfied at boundary");
        check(!blockers(supportedApp,Set.of(),true,true,false,DAY*11).isEmpty(),"former or expired sponsor does not count");
        check(!blockers(supportedApp,Set.of(S),true,false,false,DAY*11).isEmpty(),"citizenship cannot invent Towny membership");
        check(!blockers(supportedApp,Set.of(S),true,true,true,DAY*11).isEmpty(),"mayor cannot become temporary");
        check(!blockers(app(initial,CitizenshipStatus.CITIZEN,0,0),Set.of(),true,true,false,DAY*11).isEmpty(),"unknown registration cannot satisfy minimum age");
        var stricter=policy(supported,T,new Terms(5,30,"Новые правила",2),1);
        check(stricter.board().applications().get(a.id()).terms().equals(rules),"old terms remain frozen");
        denied(()->submit(stricter,app(initial,CitizenshipStatus.HONORARY,0,DAY)),"stale quoted terms rejected");
        var approved=decide(stricter,a.id(),2,Phase.APPROVED,M,"Mayor","Одобрено",DAY*11,Set.of(S),true,true,false);
        check(approved.record(T,P).expiresAt()==DAY*18&&approved.board().applications().get(a.id()).phase()==Phase.APPROVED&&approved.board().history().size()==1,"status, approval and history in one image");
        denied(()->decide(approved,a.id(),2,Phase.APPROVED,M,"Mayor","Повтор",DAY*12,Set.of(S),true,true,false),"repeat cannot extend expiry or duplicate history");
        var rejected=decide(supported,a.id(),2,Phase.REJECTED,M,"Mayor","Позже",DAY*11,Set.of(),false,false,false);
        check(rejected.records().isEmpty()&&rejected.board().history().isEmpty(),"rejection never demotes or creates status");
        denied(()->decide(submitted,a.id(),1,Phase.WITHDRAWN,S,"Sponsor","",DAY*11,Set.of(),false,false,false),"only applicant can withdraw");
        check(decide(submitted,a.id(),1,Phase.WITHDRAWN,P,"Applicant","Отзыв",DAY*11,Set.of(),false,false,false).records().isEmpty(),"withdrawal is neutral");
        var honorary=app(empty,CitizenshipStatus.HONORARY,0,0);check(blockers(honorary,Set.of(),true,false,false,DAY*11).isEmpty(),"honorary guest does not require membership");
        var dir=Files.createTempDirectory("admissions-");var file=dir.resolve("citizens.yml");
        // Import an authentic schema-1 layout without rewriting it just by reading.
        String legacy="schema: 1\nrecords:\n  '"+T+"/"+P+"':\n    town: '"+T+"'\n    resident: '"+P+"'\n    status: TEMPORARY\n    expires-at: 1500000000\n    changed-at: 100\n    actor: mayor\n    reason: old\n";Files.writeString(file,legacy);var repo=new CitizensRepository(file);repo.load();check(repo.get(T,P).expiresAt()==1500000000&&Files.readString(file).equals(legacy),"schema1 reading preserves data and file");
        done(repo.commit(repo.state(),approved));var reload=new CitizensRepository(file);reload.load();check(reload.state().equals(approved),"schema2 restarts with frozen terms, sponsors, decision and history");
        String original=Files.readString(file),shifted=ModuleTimers.shifted(original,ModuleTimers.rules("NeverLandTownyCitizens").get("citizens.yml"),123456);
        var shiftedFile=dir.resolve("shifted.yml");Files.writeString(shiftedFile,shifted);var paused=new CitizensRepository(shiftedFile);paused.load();check(paused.get(T,P).expiresAt()==approved.record(T,P).expiresAt()+123456&&paused.state().board().equals(approved.board()),"module pause shifts expiry but not application or historical dates");
        var writers=AtomicFiles.class.getDeclaredField("writers");writers.setAccessible(true);@SuppressWarnings("unchecked")var locks=(Map<Path,java.util.concurrent.locks.ReentrantLock>)writers.get(null);var lock=locks.computeIfAbsent(file.toAbsolutePath().normalize(),p->new java.util.concurrent.locks.ReentrantLock());lock.lock();CompletionStage<State> pending;CompletionStage<Void> closed;
        var finalImage=Admissions.record(approved,new CitizenshipRecord(T,P,CitizenshipStatus.HONORARY,0,DAY*12,"mayor","Manual"),M,"Mayor");
        try{pending=reload.commit(approved,finalImage);check(!pending.toCompletableFuture().isDone()&&reload.state().equals(approved)&&reload.busy(),"actual blocked file never publishes pending status");check(SuiteState.tryRun("admissions slow disk",()->{}),"slow file never holds suite lock");denied(()->done(reload.commit(approved,empty)),"overlapping write rejected");closed=reload.prepareClose();check(!closed.toCompletableFuture().isDone(),"shutdown drains queued write");}finally{lock.unlock();}
        done(closed);check(done(pending).equals(finalImage),"accepted write published before close finishes");
        var failed=new CitizensRepository(file);failed.load();Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("keep"),"preserve");denied(()->done(failed.commit(finalImage,approved)),"real atomic replacement failure propagated");check(failed.readable()&&!failed.writable()&&failed.state().equals(finalImage),"failure retains confirmed rights and blocks new writes");
        for(var bad:List.of("schema: 88\nrecords: {}\n","schema: 2\nrecords: {}\nadmissions-version: 1\nadmissions: '{'\n")){var path=dir.resolve(UUID.randomUUID()+".yml");Files.writeString(path,bad);var invalid=new CitizensRepository(path);denied(invalid::load,"malformed state not silently reset");check(!invalid.readable(),"invalid image never becomes healthy");done(invalid.prepareClose());}
        denied(()->new State(Map.of(),approved.board()),"approved application requires retained status record");
        done(repo.prepareClose());done(paused.prepareClose());done(failed.prepareClose());
        System.out.println("AdmissionsSmoke PASS "+checks+" checks");
    }
}
