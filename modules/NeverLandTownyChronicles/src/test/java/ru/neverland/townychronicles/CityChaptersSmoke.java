package ru.neverland.townychronicles;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import ru.neverland.core.AuditRecord;
import ru.neverland.townychronicles.chapters.CityChapters;
import ru.neverland.townychronicles.model.*;

public final class CityChaptersSmoke {
    private static final UUID T=UUID.nameUUIDFromBytes("town".getBytes()),OTHER=UUID.nameUUIDFromBytes("other".getBytes());
    private static int checks;
    private static void check(boolean pass,String name){checks++;if(!pass)throw new AssertionError(name);}
    private static ChronicleEntry entry(String key,long at,ChronicleCategory category,String actor){return new ChronicleEntry(UUID.nameUUIDFromBytes(key.getBytes()),T,"Town",category,at,key,List.of("legacy detail"),actor,"Towny");}
    private static AuditRecord record(String op,long at,String module,String kind,String outcome,UUID town,String money){return new AuditRecord(AuditRecord.id(module,op,outcome),at,module,kind,outcome,op,new AuditRecord.Party("PLAYER",OTHER.toString(),"Builder",""),new AuditRecord.Party("TOWN",town.toString(),"Town",town.toString()),new AuditRecord.Party("TOWN",OTHER.toString(),"Other",OTHER.toString()),"",0,money,"PRIVATE coordinates secret bank notes");}
    public static void main(String[] args){
        long base=Instant.parse("2026-10-31T20:59:00Z").toEpochMilli(),change=base+120_000;
        var entries=List.of(entry("founding",base-1000,ChronicleCategory.FOUNDING,"founder"),entry("first-mayor",base,ChronicleCategory.MAYOR,"Alex"),entry("achievement",base+1000,ChronicleCategory.ACHIEVEMENT,""),entry("second-mayor",change,ChronicleCategory.MAYOR,"Bob"),entry("war",change+1000,ChronicleCategory.WAR,""),entry("future",change+1_000_000,ChronicleCategory.WONDER,""));
        var repair=record("repair",change+2000,"NeverLandTownySiegesPlus","BUILDING_REPAIR","DONE",T,"125.50");
        var evidence=CityChapters.publicEvidence(List.of(repair,repair,record("pending",change,"NeverLandTownySiegesPlus","BUILDING_REPAIR","REVIEW_DEBIT",T,"900"),record("wrong-module",change,"OtherPlugin","BUILDING_REPAIR","DONE",T,"400"),record("other-town",change,"NeverLandTownySiegesPlus","BUILDING_REPAIR","DONE",OTHER,"12"),record("declare",base,"NeverLandTownySiegesPlus","RAID_DECLARED","READY",T,"")));
        check(evidence.towns().get(T).size()==2,"only exact outcomes and dedup");
        check(evidence.towns().get(T).stream().noneMatch(f->f.title().contains("PRIVATE")||f.source().contains("secret")),"private evidence excluded");
        var chapters=CityChapters.build(T,entries,evidence.towns().get(T),change+3000);
        var reigns=chapters.stream().filter(c->c.theme()==CityChapters.Theme.REIGN).toList();check(reigns.size()==3,"observed reign boundaries");
        check(reigns.stream().anyMatch(c->c.title().equals("До первой записи о смене мэра")&&c.facts().size()==1),"founder is not invented mayor");
        var bob=reigns.stream().filter(c->c.title().endsWith("Bob")).findFirst().orElseThrow();check(bob.facts().size()==3,"boundary exact membership and future excluded");check(bob.repairCost().compareTo(new BigDecimal("125.50"))==0,"repair amount once");
        check(chapters.stream().filter(c->c.theme()==CityChapters.Theme.WAR).count()==2,"Moscow midnight month split");
        check(chapters.stream().filter(c->c.theme()==CityChapters.Theme.RECOVERY).count()==1,"recovery has actual receipt");
        check(chapters.stream().flatMap(c->c.facts().stream()).noneMatch(f->f.title().contains("побед")),"no invented war winner");
        var noAudit=CityChapters.build(T,entries,List.of(),change+3000);check(noAudit.stream().noneMatch(c->c.theme()==CityChapters.Theme.RECOVERY),"unknown audit not fabricated recovery");
        var shuffled=new ArrayList<>(entries);for(int i=0;i<1000;i++){Collections.shuffle(shuffled,new Random(i));check(CityChapters.build(T,shuffled,evidence.towns().get(T),change+3000).equals(chapters),"deterministic restart/order "+i);}
        var duplicate=new ArrayList<>(entries);duplicate.add(entries.get(1));check(CityChapters.build(T,duplicate,evidence.towns().get(T),change+3000).equals(chapters),"duplicate chronicle ID counted once");
        var conflict=new ArrayList<>(entries);var first=entries.get(0);conflict.add(new ChronicleEntry(first.id(),T,"Town",first.category(),first.timestamp(),"changed",List.of(),"",first.source()));boolean rejected=false;try{CityChapters.build(T,conflict,List.of(),change+3000);}catch(IllegalStateException expected){rejected=true;}check(rejected,"contradictory identity rejected");
        check(CityChapters.build(OTHER,entries,List.of(),change+3000).isEmpty(),"other towns excluded");
        var large=new ArrayList<AuditRecord>();for(int i=0;i<2005;i++)large.add(record("repair-"+i,base+i,"NeverLandTownySiegesPlus","BUILDING_REPAIR","DONE",T,"1"));var capped=CityChapters.publicEvidence(large);check(capped.towns().get(T).size()==2000&&capped.truncated().contains(T),"bounded evidence declares truncation");check(capped.towns().get(T).stream().noneMatch(f->f.at()<base+5),"oldest evidence omitted");
        var unknown=CityChapters.publicEvidence(List.of(record("no-sum",base,"NeverLandTownySiegesPlus","BUILDING_REPAIR","DONE",T,"")));var row=CityChapters.build(T,List.of(),unknown.towns().get(T),change).get(0);check(row.unknownCosts()==1&&row.repairCost().signum()==0,"missing sum explicitly unknown");
        check(entries.get(0).details().equals(List.of("legacy detail")),"source history unchanged");
        System.out.println("CityChaptersSmoke PASS "+checks+" checks, including 1000 reorderings");
    }
}
