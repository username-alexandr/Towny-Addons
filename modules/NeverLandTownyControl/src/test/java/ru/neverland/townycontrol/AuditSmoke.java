package ru.neverland.townycontrol;

import ru.neverland.core.*;
import java.nio.file.*;
import java.util.*;

public final class AuditSmoke {
    private static int checks;
    private static void check(boolean b,String text){if(!b)throw new AssertionError(text);checks++;}
    private static void rejects(Work action)throws Exception{try{action.run();throw new AssertionError("invalid input accepted");}catch(java.io.IOException|IllegalArgumentException expected){checks++;}}
    interface Work{void run()throws Exception;}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("audit-smoke-");String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString(),actor=UUID.randomUUID().toString();
        var from=new AuditRecord.Party("TOWN",a,"Город\tА\n=SUM(1)",a);var to=new AuditRecord.Party("TOWN",b,"Город Б",b);var person=new AuditRecord.Party("PLAYER",actor,"Alex",a);
        var r=new AuditRecord(AuditRecord.id("Trade","operation","COMPLETE"),java.time.Instant.parse("2026-09-17T12:00:00Z").toEpochMilli(),"NeverLandTownyTrade","SUPPLY_DEAL","COMPLETE","operation",person,from,to,"STONE:custom\\data\r\n",32,"12.34","contract=1\nвторая строка\t\\");
        check(AuditJournal.decode(AuditJournal.encode(r)).equals(r),"Unicode, tabs, newlines and item metadata roundtrip");
        var journal=new AuditJournal(dir);check(journal.append(r),"first append");check(!journal.append(r),"duplicate in same process");
        var restart=new AuditJournal(dir);check(!restart.append(r),"dedup after process restart");
        var renamed=new AuditRecord(r.id(),r.at()+1000,r.module(),r.kind(),r.outcome(),r.operation(),person,new AuditRecord.Party("TOWN",a,"Новое имя",a),to,r.asset(),r.quantity(),r.money(),"повтор после восстановления");check(!restart.append(renamed),"replay preserves original snapshot after rename");
        var conflict=new AuditRecord(r.id(),r.at(),r.module(),r.kind(),r.outcome(),r.operation(),person,from,to,r.asset(),r.quantity()+1,r.money(),r.details());rejects(()->restart.append(conflict));
        List<AuditRecord> read=new ArrayList<>();AuditJournal.scan(dir,read::add);check(read.equals(List.of(r)),"no duplicate or changed historical names");
        var cancelled=new AuditRecord(AuditRecord.id("Trade","cancel","CANCELLED"),r.at(),r.module(),r.kind(),"CANCELLED","cancel",person,from,to,r.asset(),32,"12.34","Отменено");restart.append(cancelled);
        check(AuditQuery.parse(new String[]{"town="+a,"player="+actor,"intercity=true","outcome=COMPLETE"}).test(r),"joint participant filters");
        check(!AuditQuery.parse(new String[]{"outcome=COMPLETE"}).test(cancelled),"cancelled transaction never counted as completed");
        check(AuditQuery.parse(new String[]{"since=2026-09-17","until=2026-09-17","module=Trade"}).test(r),"UTC date and module alias");
        check(!AuditQuery.parse(new String[]{"from="+b}).test(r),"direction respected");check(AuditQuery.parse(new String[]{"id=operation"}).test(r),"whole operation lookup");
        for(String input:List.of("intercity=maybe","until=broken","kind=","page=101","bogus=yes"))rejects(()->AuditQuery.parse(new String[]{input}));
        rejects(()->AuditQuery.parse(new String[]{"since=2026-09-18","until=2026-09-17"}));
        check(AuditJournal.csv("  =SUM(1)").startsWith("\"'"),"CSV formula protection after whitespace");check(AuditJournal.csv("x\"y\nz").equals("\"x\"\"y z\""),"CSV quote and newline escaping");
        var file=AuditJournal.files(dir).get(0);String contents=Files.readString(file);Files.writeString(file,contents.substring(0,contents.length()-1));rejects(()->new AuditJournal(dir).load());check(Files.readString(file).equals(contents.substring(0,contents.length()-1)),"torn tail is never silently erased");
        Files.writeString(file,contents.replace("12.34","13.34"));rejects(()->AuditJournal.scan(dir,x->{}));Files.writeString(file,contents);
        Path blocked=Files.createTempFile("audit-not-dir-", ".file");rejects(()->new AuditJournal(blocked).append(r));
        var nextMonth=new AuditRecord(AuditRecord.id("Trade","next","COMPLETE"),r.at()+32*86400000L,r.module(),r.kind(),r.outcome(),"next",person,from,to,r.asset(),32,"12.34","");restart.append(nextMonth);check(AuditJournal.files(dir).size()==2,"monthly rotation without deleting old records");
        var latest=new AuditJournal(dir);latest.load();check(latest.size()==3,"full index across rotated files");
        Path nativeDir=Files.createTempDirectory("audit-native-");var nativeJournal=new AuditJournal(nativeDir);nativeJournal.appendUnique(r);nativeJournal.appendUnique(cancelled);check(nativeJournal.size()==0,"native bank traffic does not grow a receipt index");var nativeRows=new ArrayList<AuditRecord>();AuditJournal.scan(nativeDir,nativeRows::add);check(nativeRows.size()==2,"native unique observations remain readable");
        System.out.println("AuditSmoke PASS: "+checks+" checks; restart, collision, corruption, filters and CSV");
    }
}
