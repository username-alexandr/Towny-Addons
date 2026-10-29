package ru.neverland.townycouncil;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.locks.ReentrantLock;
import ru.neverland.core.*;import ru.neverland.townycouncil.spending.*;import static ru.neverland.townycouncil.spending.SpendingPolicies.*;

public final class SpendingPoliciesSmoke {
    static final UUID T=UUID.randomUUID(),R=UUID.randomUUID(),M=UUID.randomUUID();static int checks;
    interface Work{void run()throws Exception;}
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    static void denied(Work work,String why)throws Exception{try{work.run();}catch(Exception expected){check(true,why);return;}throw new AssertionError(why);}
    static <T>T done(CompletionStage<T> value)throws Exception{return value.toCompletableFuture().get(5,TimeUnit.SECONDS);}
    static Quote quote(long revision,long at,Limits limits){return new Quote(UUID.randomUUID(),T,R,M,"Mayor","Town","Resident",limits,revision,at,"Городской бюджет");}
    public static void main(String[] args)throws Exception{
        check(parse("1000,01")==100001&&parse("0")==0&&parse("1000000000")==MAX_CENTS,"exact decimal amounts, zero and maximum");
        for(String bad:List.of("-1","0.001","NaN","Infinity","1000000001"))denied(()->parse(bad),"invalid money rejected: "+bad);
        var empty=State.empty();var q=quote(0,1000,new Limits(50000,100000));var first=SpendingPolicies.apply(empty,q,1000);
        check(first.limits(T,R).equals(q.limits())&&first.revision()==1&&first.history().get(0).before()==null&&!first.history().get(0).audited(),"confirmed immutable limits and audit outbox share image");
        check(first.limits(UUID.randomUUID(),R).equals(Limits.unlimited()),"limits are city-local");
        denied(()->SpendingPolicies.apply(empty,q,999),"backward confirmation clock rejected");denied(()->SpendingPolicies.apply(empty,q,61001),"expired confirmation rejected");
        check(SpendingPolicies.apply(empty,q,61000).revision()==1,"exact 60-second confirmation boundary");
        denied(()->SpendingPolicies.apply(first,q,1001),"repeated or stale confirmation cannot rewrite policy");
        denied(()->SpendingPolicies.apply(first,quote(1,1001,q.limits()),1001),"unchanged rule does not create history");
        var second=SpendingPolicies.apply(first,quote(1,1001,Limits.unlimited()),1001);
        check(second.revision()==2&&second.history().get(1).before().equals(first.policies().get(T+"/"+R)),"removing cap retains exact previous rule and history");
        var ack=SpendingPolicies.acknowledged(second,Set.of(q.id()));check(ack.revision()==2&&ack.history().get(0).audited()&&!ack.history().get(1).audited(),"audit acknowledgement never changes policy revision");
        denied(()->new State(Map.of(),first.history(),1),"missing policy cannot silently mean unlimited");
        denied(()->new State(first.policies(),List.of(),1),"missing policy history rejected");
        denied(()->new State(second.policies(),List.of(second.history().get(1),second.history().get(0)),2),"reordered history rejected");
        var folder=Files.createTempDirectory("council-spending-");var path=folder.resolve("spending-limits.yml");var repo=new SpendingRepository(path);repo.load();
        var locksField=AtomicFiles.class.getDeclaredField("writers");locksField.setAccessible(true);@SuppressWarnings("unchecked")var locks=(Map<Path,ReentrantLock>)locksField.get(null);var lock=locks.computeIfAbsent(path.toAbsolutePath().normalize(),p->new ReentrantLock());lock.lock();CompletionStage<State> pending;CompletionStage<Void> closing;
        try{pending=repo.commit(empty,first);check(repo.busy()&&repo.state().equals(empty)&&!pending.toCompletableFuture().isDone(),"actual delayed file keeps old rules visible until ACK");check(SuiteState.tryRun("limits slow physical disk",()->{}),"writer does not hold shared Suite lock");denied(()->done(repo.commit(empty,second)),"overlapping write rejected");closing=repo.close();check(!closing.toCompletableFuture().isDone(),"shutdown drains accepted write");}finally{lock.unlock();}
        done(closing);check(done(pending).equals(first),"accepted policy is published before close completes");
        var restart=new SpendingRepository(path);restart.load();check(restart.state().equals(first),"full file reopen preserves limits and unaudited history");done(restart.commit(first,ack));done(restart.close());
        var saved=new SpendingRepository(path);saved.load();check(saved.state().equals(ack),"unlimited rule and prior audit ACK survive reopen");done(saved.close());
        var badPath=folder.resolve("bad.yml");String bad="schema: 9\nrevision: 0\npolicies: {}\nhistory: '[]'\n";Files.writeString(badPath,bad);var damaged=new SpendingRepository(badPath);denied(damaged::load,"unknown schema closes spending only");check(!damaged.readable()&&!damaged.writable(),"corrupt rules cannot advertise default unlimited");done(damaged.close());check(Files.readString(badPath).equals(bad),"shutdown preserves corrupt original bytes");
        System.out.println("SpendingPoliciesSmoke PASS "+checks+" checks");
    }
}
