package ru.neverland.townychronicles.service;

import java.nio.file.*;
import java.util.*;
import ru.neverland.core.*;
import ru.neverland.townychronicles.chapters.CityChapters;

public final class ContributionHistorySmoke {
    static int checks;static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    static final UUID T=UUID.nameUUIDFromBytes("history-town".getBytes()),P=UUID.nameUUIDFromBytes("history-player".getBytes());
    static AuditRecord record(int id,long time,String kind){var person=new AuditRecord.Party("PLAYER",P.toString(),"Builder",T.toString());var city=new AuditRecord.Party("TOWN",T.toString(),"City",T.toString());String op="history-"+id;boolean thanks=kind.equals("CITY_COMMENDATION");return new AuditRecord(AuditRecord.id("NeverLandTownyChronicles",op,"receipt"),time,"NeverLandTownyChronicles",kind,"COMPLETED",op,person,thanks?city:person,thanks?person:city,"",1,"","Thanks for helping");}
    public static void main(String[] args)throws Exception{
        var source=Files.createTempDirectory("chronicle-stream-");var records=new ArrayList<AuditRecord>();for(int i=0;i<1005;i++)records.add(record(i,1000+i,"CITY_COMMENDATION"));records.add(record(3000,5000,"BUILD_CONTRIBUTION"));
        var text=new StringBuilder();for(var record:records)text.append(AuditJournal.encode(record)).append('\n');Files.writeString(source.resolve("2026-10.tsv"),text.toString());
        var result=ContributionHistory.read(List.of(source),"NeverLandTownyChronicles");check(result.thanks().size()==1000,"bounded restoration list");check(result.thanks().get(0).at()==1005&&result.thanks().get(999).at()==2004,"restoration selects newest but publishes in chronology");
        var member=result.members().get(T).get(0);check(member.thanks()==1005&&member.materials()==1,"aggregate includes all receipts despite bounded restoration");check(result.members().equals(CityContributions.aggregate(records)),"streaming equals exact batch aggregate");
        var pending=records.get(0);var beforeAck=ContributionHistory.read(List.of(source),"NeverLandTownyChronicles",r->!r.id().equals(pending.id()));check(beforeAck.members().get(T).get(0).thanks()==1004,"bytes visible before local write ACK cannot publish pending contribution");
        var accumulator=new CityContributions.Accumulator();var duplicate=record(3000,5000,"BUILD_CONTRIBUTION");accumulator.accept(duplicate);accumulator.accept(duplicate);check(accumulator.finish().get(T).get(0).materials()==1,"streaming deduplicates receipts");
        boolean rejected=false;var conflict=new AuditRecord(duplicate.id(),duplicate.at(),duplicate.module(),duplicate.kind(),duplicate.outcome(),duplicate.operation(),duplicate.actor(),duplicate.from(),duplicate.to(),"",2,"",duplicate.details());try{accumulator.accept(conflict);}catch(IllegalStateException expected){rejected=true;}check(rejected,"contradictory public attribution refuses snapshot");
        var capped=new CityContributions.Accumulator();for(int i=0;i<CityContributions.MAX_MEMBERS;i++){var r=record(i,1,"BUILD_CONTRIBUTION");var player=new AuditRecord.Party("PLAYER",UUID.nameUUIDFromBytes(("p"+i).getBytes()).toString(),"Player",T.toString());capped.accept(new AuditRecord(r.id(),1,r.module(),r.kind(),r.outcome(),r.operation(),player,player,r.to(),"",1,"",""));}
        rejected=false;try{capped.accept(record(99_999,1,"BUILD_CONTRIBUTION"));}catch(IllegalStateException expected){rejected=true;}check(rejected,"member budget rejects refresh without partial total");
        try(var guard=SuiteState.guard()){rejected=false;try{ContributionHistory.read(List.of(source),"NeverLandTownyChronicles");}catch(IllegalStateException expected){rejected=true;}check(rejected,"source IO cannot start under shared guard");}
        Files.writeString(source.resolve("2026-10.tsv"),"broken",StandardOpenOption.APPEND);rejected=false;try{ContributionHistory.read(List.of(source),"NeverLandTownyChronicles");}catch(java.io.IOException expected){rejected=true;}check(rejected,"corrupt source refuses new complete snapshot");
        var manyFiles=Files.createTempDirectory("chronicle-history-files-");for(int i=0;i<=ContributionHistory.MAX_FILES;i++)Files.writeString(manyFiles.resolve(String.format("%04d-%02d.tsv",2000+i/12,1+i%12)),"");rejected=false;try{ContributionHistory.read(List.of(manyFiles),"NeverLandTownyChronicles");}catch(java.io.IOException expected){rejected=true;}check(rejected,"file budget prevents unbounded scan");
        System.out.println("ContributionHistorySmoke PASS "+checks+" checks");
    }
}
