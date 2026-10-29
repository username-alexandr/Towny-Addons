package ru.neverland.reputation;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.MemoryConfiguration;
import ru.neverland.core.ReputationOutcome;
import ru.neverland.reputation.model.*;
import ru.neverland.reputation.service.*;
import static ru.neverland.reputation.model.ReputationLedger.*;

public final class ExplanationSmoke {
    static int checks;static final long DAY=ReputationExplanation.DAY,NOW=100*DAY;static final UUID TOWN=UUID.randomUUID();static final Subject SUBJECT=new Subject(ReputationScope.TOWN,TOWN);
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    static State add(State state,String id,long at,ReputationAspect aspect,int requested){return apply(state,new ReputationOutcome(id,"TOWN",TOWN,aspect==ReputationAspect.TRADE?"SUPPLY_COMPLETED":"ADMIN",at,"Проверенное обязательство"),aspect,requested,30).state();}
    static ReputationExplanation.Explanation explain(State state,ReputationAspect aspect,int page){return new ReputationExplanation(state).explain(SUBJECT,aspect,NOW,page,28);}
    public static void main(String[] args)throws Exception{
        var initial=new State(Map.of(SUBJECT,new Profile(17,0,0,List.of())),Map.of(),Map.of());var state=add(initial,"supply:old",NOW-35*DAY,ReputationAspect.TRADE,3);state=add(state,"supply:30",NOW-30*DAY,ReputationAspect.TRADE,3);state=add(state,"supply:7",NOW-7*DAY,ReputationAspect.TRADE,-20);state=add(state,"supply:today",NOW,ReputationAspect.TRADE,3);state=add(state,"supply:capped",NOW,ReputationAspect.TRADE,1000);state=add(state,"admin:dip",NOW,ReputationAspect.DIPLOMATIC,-5);
        var trade=explain(state,ReputationAspect.TRADE,0);check(trade.score()==16&&trade.initial()==0&&trade.evidence()==16&&trade.consistent(),"complete durable applied changes exactly reconstruct trade score");
        check(trade.recent7()==10&&trade.days7to30()==3&&trade.older30()==3,"exact seven- and thirty-day boundaries partition contributions without loss");
        check(trade.positive()==36&&trade.negative()==-20&&trade.limited()==1,"requested points cannot masquerade as actually applied capped score");
        check(trade.rows().stream().anyMatch(r->r.receipt().equals("supply:capped")&&r.requested()==1000&&r.applied()==27),"each result retains actual receipt and its limited amount");
        var dip=explain(state,ReputationAspect.DIPLOMATIC,0);check(dip.score()==12&&dip.initial()==17&&dip.evidence()==-5,"legacy starting rating is explained separately from new receipts");
        var none=explain(state,ReputationAspect.MILITARY,0);check(none.count()==0&&none.score()==0&&none.rows().isEmpty()&&none.pages()==1,"empty aspect is explicit and has one empty page");
        var many=initial;for(int i=0;i<80;i++)many=add(many,"contract:"+i,(10+i)*DAY,ReputationAspect.TRADE,3);check(many.profile(SUBJECT).history().size()==60,"ordinary short history remains unchanged");var all=explain(many,ReputationAspect.TRADE,0);check(all.count()==80&&all.evidence()==240&&all.rows().size()==28&&all.pages()==3,"explanation reads all receipts beyond the last sixty history entries");
        var last=explain(many,ReputationAspect.TRADE,Integer.MAX_VALUE);check(last.page()==2&&last.rows().size()==24&&last.rows().get(last.rows().size()-1).receipt().equals("contract:0"),"bounded pagination keeps earliest evidence reachable without integer overflow");
        var index=new ReputationExplanation(many);var snapshot=index.explain(SUBJECT,ReputationAspect.TRADE,NOW,0,28);var later=index.explain(SUBJECT,ReputationAspect.TRADE,NOW+40*DAY,0,28);check(snapshot.score()==later.score()&&snapshot.evidence()==later.evidence()&&later.recent7()==0&&later.days7to30()==0&&later.older30()==240,"age changes explanation buckets without silently decaying retained points");
        var future=add(initial,"clock:future",NOW+60000,ReputationAspect.TRADE,3);var clock=explain(future,ReputationAspect.TRADE,0).rows().get(0);check(clock.future()&&clock.ageDays()==0,"clock drift is flagged without negative age");
        var otherId=UUID.randomUUID();var other=apply(many,new ReputationOutcome("other:receipt","PLAYER",otherId,"PLAYER_ENDORSE",NOW,"Другой профиль"),ReputationAspect.DIPLOMATIC,5,30).state();check(explain(other,ReputationAspect.TRADE,0).count()==80,"other subject and scope cannot contaminate town evidence");
        var corruptProfiles=new HashMap<>(many.profiles());corruptProfiles.put(SUBJECT,new Profile(17,500,0,List.of()));check(!explain(new State(corruptProfiles,many.receipts(),many.daily()),ReputationAspect.TRADE,0).consistent(),"unexplained non-diplomatic starting score is flagged for administrator review");
        var directory=Files.createTempDirectory("neverland-reputation-explain-");var file=directory.resolve("profiles.yml");var store=new ProfileRepository(file);store.load(List.of());store.commit(many);var profiles=new ReputationProfiles(store,new MemoryConfiguration());check(profiles.explain(ReputationScope.TOWN,TOWN,ReputationAspect.TRADE,NOW,0,28).count()==80,"actual profile service builds receipt index from confirmed repository");
        var changed=add(many,"contract:updated",NOW,ReputationAspect.TRADE,3);store.commit(changed);check(profiles.explain(ReputationScope.TOWN,TOWN,ReputationAspect.TRADE,NOW,0,28).count()==81,"new confirmed snapshot invalidates service explanation index");
        var restored=new ProfileRepository(file);restored.load(List.of());var resumed=explain(restored.state(),ReputationAspect.TRADE,0);check(resumed.count()==81&&resumed.score()==243&&restored.state().equals(changed),"real file restart preserves original scores, receipts, source IDs and explanation");
        var payload=profiles.explanation("TOWN",TOWN,"TRADE",0);check(payload.get("count").equals(81)&&((List<?>)payload.get("rows")).size()==28,"primitive API payload is paginated and matches all stored evidence");
        check(new ReputationExplanation(many).explain(SUBJECT,ReputationAspect.TRADE,NOW,0,28).equals(snapshot),"rebuilding the read index never changes the ledger");
        for(int i=0;i<10000;i++){long at=NOW+(i%100)*DAY;var e=index.explain(SUBJECT,ReputationAspect.TRADE,at,i%4,28);if(e.recent7()+e.days7to30()+e.older30()!=e.evidence()||e.initial()+e.evidence()!=e.score())throw new AssertionError("decomposition not conserved");}
        System.out.println("ExplanationSmoke PASS "+checks+" checks and 10000 time/page decompositions");
    }
}
