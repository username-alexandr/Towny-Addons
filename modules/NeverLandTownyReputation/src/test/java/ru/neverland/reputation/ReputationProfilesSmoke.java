package ru.neverland.reputation;

import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.ReputationOutcome;
import ru.neverland.reputation.model.*;
import ru.neverland.reputation.service.*;
import static ru.neverland.reputation.model.ReputationLedger.*;

public final class ReputationProfilesSmoke {
    private static int checks;
    public static void main(String[] args)throws Exception {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
        Subject town=new Subject(ReputationScope.TOWN,a);long now=System.currentTimeMillis();
        State state=State.empty();
        var breach=ReputationOutcome.town("breach",a,"SUPPLY_MISSED",now,"contract");
        state=ReputationLedger.apply(state,breach,ReputationAspect.TRADE,-10,30).state();
        check(state.profile(town).trade()==-10 && state.profile(town).diplomatic()==0 && state.profile(town).military()==0,"three independent tracks");
        check(state.profile(new Subject(ReputationScope.TOWN,b)).trade()==0,"innocent counterparty unaffected");
        check(ReputationLedger.apply(state,breach,ReputationAspect.TRADE,-99,30).state()==state,"retry after config change preserves receipt");
        State once=state;failure(()->ReputationLedger.apply(once,new ReputationOutcome(breach.id(),"TOWN",b,"SUPPLY_MISSED",now,"contract"),ReputationAspect.TRADE,-10,30));
        for(int i=0;i<10;i++)state=ReputationLedger.apply(state,ReputationOutcome.town("success"+i,a,"SUPPLY_COMPLETED",now,"delivery"),ReputationAspect.TRADE,5,30).state();
        check(state.profile(town).trade()==20,"positive daily cap shared across outcomes");
        state=ReputationLedger.apply(state,ReputationOutcome.town("negative-after-cap",a,"SUPPLY_MISSED",now,"delivery"),ReputationAspect.TRADE,-10,30).state();
        check(state.profile(town).trade()==10,"positive cap never suppresses breaches");
        var saturated=ReputationOutcome.town("saturated",a,"SUPPLY_MISSED",now,"delivery");
        state=ReputationLedger.apply(state,saturated,ReputationAspect.TRADE,Integer.MIN_VALUE,30).state();
        check(state.profile(town).trade()==-1000,"negative overflow clamps safely");
        state=ReputationLedger.apply(state,ReputationOutcome.town("military",a,"RAID_VICTORY",now,"raid"),ReputationAspect.MILITARY,Integer.MAX_VALUE,30).state();
        check(state.profile(town).military()==30 && state.profile(town).trade()==-1000,"military has separate cap and score");
        check(Math.abs(ReputationLedger.feeMultiplier(-100,2,.15)-1.1)<1e-10,"minus 100 yields 10 percent surcharge");
        check(ReputationLedger.feeMultiplier(-1000,2,.15)==2 && ReputationLedger.feeMultiplier(0,2,.15)==1 && ReputationLedger.feeMultiplier(1000,2,.15)==.85,"neutral, maximum surcharge and bounded discount");
        failure(()->ReputationLedger.feeMultiplier(0,Double.NaN,.15));failure(()->ReputationLedger.feeMultiplier(0,2,1));
        Path dir=Files.createTempDirectory("reputation-profiles-");
        try {
            var ab=new ReputationRecord(RelationKey.of(ReputationScope.TOWN,a,b),"A","B");ab.score(100);
            var ac=new ReputationRecord(RelationKey.of(ReputationScope.TOWN,a,c),"A","C");ac.score(300);
            var player=new ReputationRecord(RelationKey.of(ReputationScope.PLAYER,a,b),"A","B");player.score(40);
            Path file=dir.resolve("profiles.yml");var repo=new ProfileRepository(file);repo.load(List.of(ab,ac,player));
            check(repo.state().profile(town).diplomatic()==200 && repo.state().profile(town).trade()==0,"legacy average migrates only to diplomacy");
            check(repo.state().profile(new Subject(ReputationScope.PLAYER,a)).diplomatic()==0 && repo.state().profile(new Subject(ReputationScope.PLAYER,b)).diplomatic()==40,"feedback migrates to recipient only");
            var service=new ReputationProfiles(repo,new YamlConfiguration());
            check(service.record("TOWN",a,"SUPPLY_MISSED",breach.id(),now,"contract").equals("APPLIED"),"durable provider acceptance");
            var reopened=new ProfileRepository(file);reopened.load(List.of());
            check(reopened.state().profile(town).diplomatic()==200 && reopened.state().profile(town).trade()==-10,"one-time migration and all scores survive restart");
            var restarted=new ReputationProfiles(reopened,new YamlConfiguration());
            check(restarted.record("TOWN",a,"SUPPLY_MISSED",breach.id(),now,"contract").equals("DUPLICATE") && restarted.get(ReputationScope.TOWN,a).trade()==-10,"receipt survives actual YAML restart");
            failure(()->restarted.record("TOWN",a,"SUPPLY_MISSED",breach.id(),now+1,"contract"));
            failure(()->restarted.record("TOWN",a,"SUPPLY_MISSED","future",now+86_400_000,"future"));
            failure(()->restarted.record("TOWN",a,"UNKNOWN","unknown",now,"unknown"));
            restarted.administer(ReputationScope.TOWN,a,ReputationAspect.TRADE,-1000,true,"console");
            check(restarted.tradeFeeMultiplier(a)==2,"admin change affects actual fee policy");
            restarted.administer(ReputationScope.TOWN,a,ReputationAspect.MILITARY,100,true,"console");
            check(restarted.get(ReputationScope.TOWN,a).trade()==-1000,"admin chooses a single aspect");
            byte[] valid=Files.readAllBytes(file);State before=reopened.state();
            Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("keep"),"original");
            failure(()->restarted.record("TOWN",a,"SUPPLY_COMPLETED","disk-failure",now,"disk"));
            check(!restarted.healthy() && reopened.state()==before,"failed atomic write freezes storage and never publishes memory changes");
            failure(()->restarted.tradeFeeMultiplier(a));check(Files.readString(file.resolve("keep")).equals("original"),"failed replacement preserves original");
            Files.delete(file.resolve("keep"));Files.delete(file);Files.write(file,valid);reopened.load(List.of());
            check(restarted.healthy(),"validated reload restores writes");
            var corrupt=YamlConfiguration.loadConfiguration(file.toFile());corrupt.set("profiles","broken");Files.writeString(file,corrupt.saveToString());
            failure(()->reopened.load(List.of()));check(!reopened.healthy() && Files.readString(file).contains("broken"),"corruption cannot reset profiles");
        }finally{try(var paths=Files.walk(dir)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
        System.out.println("ReputationProfilesSmoke OK: "+checks+" checks");
    }
    interface Action{void run()throws Exception;}
    static void failure(Action action)throws Exception{boolean failed=false;try{action.run();}catch(Exception ex){failed=true;}check(failed,"expected safe rejection");}
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
}
