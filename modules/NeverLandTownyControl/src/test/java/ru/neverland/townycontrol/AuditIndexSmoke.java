package ru.neverland.townycontrol;

import java.nio.file.*;
import java.util.*;
import ru.neverland.core.*;

public final class AuditIndexSmoke {
    static int checks;
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    static AuditRecord record(int n,String module,String kind,String outcome,String operation,long at,String from,String to,String money){return new AuditRecord(UUID.nameUUIDFromBytes((module+":"+n).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),at,module,kind,outcome,operation,AuditRecord.Party.system(),new AuditRecord.Party("PLAYER",from,"Sender",from),new AuditRecord.Party("TOWN",to,"Receiver",to),"",0,money,"");}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("nlt-audit-index-");Path dir=root.resolve("NeverLandTownyControl/audit");var journal=new AuditJournal(dir);
        long now=java.time.Instant.parse("2026-10-29T12:00:00Z").toEpochMilli();
        var a=record(1,"NeverLandTownyControl","BANK_TRANSFER","COMPLETED","pay",now-60_000,"A","B","1200");
        var b=record(2,"NeverLandTownyControl","BANK_TRANSFER","COMPLETED","pay2",now,"B","C","1180");journal.append(a);journal.append(b);
        check(AuditIndex.read(root,(db,s)->s.replaced())==1,"first rebuild");
        check(AuditIndex.read(root,(db,s)->s.replaced())==0,"unchanged monthly sources skip decoding");
        var page=AuditReader.page(root,new AuditQuery(Map.of(),1),1,1);check(page.matched()==2&&page.rows().equals(List.of(b)),"indexed latest page");
        check(AuditReader.page(root,new AuditQuery(Map.of(),2),2,1).rows().equals(List.of(a)),"second page no duplication");
        for(var f:List.of(Map.of("id","pay"),Map.of("id",a.id()),Map.of("town","A"),Map.of("player","A"),Map.of("from","A"),Map.of("to","B")))check(AuditReader.page(root,new AuditQuery(f,1),1,36).rows().equals(List.of(a)),"SQL candidate filter equals exact Java "+f);
        check(AuditReader.page(root,new AuditQuery(Map.of("module","Control","kind","bank_transfer","outcome","completed"),1),1,36).matched()==2,"case-insensitive module/kind/status");
        check(AuditReader.page(root,new AuditQuery(Map.of("since","2026-10-29","until","2026-10-29"),1),1,36).matched()==2,"Moscow date range");
        check(AuditReader.page(root,new AuditQuery(Map.of("id","' OR 1=1 --"),1),1,36).rows().isEmpty(),"parameterized query");
        var c=record(3,"NeverLandTownyControl","BUILD_CONTRIBUTION","DONE","third",now+1000,"C","D","");journal.append(c);
        check(AuditIndex.read(root,(db,s)->s.replaced())==1,"changed monthly file revalidated");
        check(AuditReader.page(root,new AuditQuery(Map.of(),1),1,36).matched()==3,"appended source visible");
        Path file=AuditJournal.files(dir).get(0);String valid=Files.readString(file);Files.writeString(file,"torn",StandardOpenOption.APPEND);
        boolean failed=false;try{AuditReader.page(root,new AuditQuery(Map.of("kind","ABSENT"),1),1,36);}catch(java.io.IOException expected){failed=true;}check(failed,"corrupt source outside selected filter blocks stale result");
        Files.writeString(file,valid);check(AuditReader.page(root,new AuditQuery(Map.of(),1),1,36).matched()==3,"rollback then repair rebuilds");
        byte[] before=Files.readAllBytes(file);Files.delete(root.resolve("NeverLandTownyControl/audit-index/derived-v1.sqlite"));
        check(AuditReader.page(root,new AuditQuery(Map.of(),1),1,36).matched()==3,"disposable index reconstructible");check(Arrays.equals(before,Files.readAllBytes(file)),"queries never rewrite original journals");
        AuditIndex.read(root,(db,s)->{try(var q=db.createStatement()){q.executeUpdate("DELETE FROM records WHERE ordinal=1");}return null;});
        failed=false;try{AuditReader.page(root,new AuditQuery(Map.of(),1),1,36);}catch(java.io.IOException expected){failed=true;}check(failed,"incomplete derived cache is not returned as complete");
        check(AuditIndex.rebuild(root).rows()==3,"explicit rebuild restores all source evidence");
        check(AuditIndex.read(root,(db,s)->{var q=AuditIndex.select(new AuditQuery(Map.of("id",a.id()),1));try(var statement=db.prepareStatement("EXPLAIN QUERY PLAN SELECT encoded FROM records WHERE "+q.sql())){for(int i=0;i<q.args().size();i++)statement.setObject(i+1,q.args().get(i));try(var result=statement.executeQuery()){boolean found=false;while(result.next())found|=result.getString(4).contains("USING INDEX idx_ci_");return found;}}}),"ID query actually uses SQLite indexes");
        Files.delete(file);check(AuditReader.page(root,new AuditQuery(Map.of(),1),1,36).matched()==0,"removed source not reported as live evidence");
        Path live=Files.createTempFile("audit-append-snapshot-",".tsv");Files.writeString(live,AuditJournal.encode(a)+"\n"+AuditJournal.encode(b)+"\n");long length=Files.size(live);var seen=new ArrayList<AuditRecord>();
        AuditIndex.scanSnapshot(live,length,(r,line)->{seen.add(r);if(seen.size()==1)Files.writeString(live,AuditJournal.encode(c)+"\n",StandardOpenOption.APPEND);});
        check(seen.equals(List.of(a,b)),"concurrent append excluded from exact committed-length snapshot");seen.clear();AuditIndex.scanSnapshot(live,Files.size(live),(r,line)->seen.add(r));check(seen.equals(List.of(a,b,c)),"next snapshot includes appended record");
        Files.writeString(live,"partial",StandardOpenOption.APPEND);failed=false;try{AuditIndex.scanSnapshot(live,Files.size(live),(r,line)->{});}catch(java.io.IOException expected){failed=true;}check(failed,"captured torn tail cannot publish partial results");
        try(var guard=SuiteState.guard()){failed=false;try{AuditReader.page(root,new AuditQuery(Map.of(),1),1,36);}catch(IllegalStateException expected){failed=true;}check(failed,"no DB work under suite guard");}
        var cards=AuditSignals.inspect(List.of(a,b),now);check(cards.size()==1&&cards.get(0).type()==AuditSignals.Type.CHAIN,"two confirmed similar transfers yield review hint");
        check(AuditSignals.inspect(List.of(a,b,a),now).size()==1,"same receipt deduplicated before chain");
        var leg=record(4,"NeverLandTownyControl","BANK_LEG","CREDIT","pay2",now,"B","C","1200");check(AuditSignals.inspect(List.of(a,leg),now).isEmpty(),"bank legs cannot double count transfer");
        var command=record(5,"NeverLandTownyControl","BANK_COMMAND","OBSERVED","pay2",now,"B","C","1200");check(AuditSignals.inspect(List.of(a,command),now).isEmpty(),"observed command not proof of transfer");
        var distant=record(6,"NeverLandTownyControl","BANK_TRANSFER","COMPLETED","pay2",now+900_001,"B","C","1200");check(AuditSignals.inspect(List.of(a,distant),now+900_001).isEmpty(),"chain time window");
        var unlike=record(7,"NeverLandTownyControl","BANK_TRANSFER","COMPLETED","pay2",now,"B","C","1600");check(AuditSignals.inspect(List.of(a,unlike),now).isEmpty(),"different amount outside rule");
        var review=record(8,"NeverLandTownyMarket","LIMIT_ORDER","REVIEW","r",now-700_000,"A","B","1200");check(AuditSignals.inspect(List.of(review),now).get(0).type()==AuditSignals.Type.REVIEW,"old review surfaced");
        check(AuditSignals.inspect(List.of(review),now-200_000).isEmpty(),"recent pending not flagged");
        var done=record(9,"NeverLandTownyMarket","LIMIT_ORDER","DONE","r",now-10_000,"A","B","1200");check(AuditSignals.inspect(List.of(done,review),now).isEmpty(),"later same-module operation resolution removes review");
        var simultaneous=record(11,"NeverLandTownyMarket","LIMIT_ORDER","DONE","r",review.at(),"A","B","1200");check(AuditSignals.inspect(List.of(simultaneous,review),now).get(0).evidence().size()==2,"equal-time statuses cannot establish completion order");
        var other=record(10,"NeverLandTownyTrade","LIMIT_ORDER","DONE","r",now-10_000,"A","B","1200");check(AuditSignals.inspect(List.of(review,other),now).size()==1,"foreign module operation does not resolve review");
        var paying=record(13,"NeverLandTownyTrade","CARAVAN_DEAL","PAYING","caravan",now-700_000,"A","B","110");
        var sellerPaid=record(14,"NeverLandTownyTrade","TRADE_PAYMENT","COMPLETED","caravan",now-100_000,"B","A","80");
        var unfinished=AuditSignals.inspect(List.of(paying,sellerPaid),now);
        check(unfinished.size()==1&&unfinished.get(0).type()==AuditSignals.Type.REVIEW&&unfinished.get(0).evidence().equals(List.of(paying)),"completed seller leg cannot hide caravan PAYING while compensation or tariff is pending");
        check(unfinished.get(0).reason().contains("CARAVAN_DEAL"),"review identifies the exact unfinished workflow kind");
        var caravanComplete=record(15,"NeverLandTownyTrade","CARAVAN_DEAL","COMPLETE","caravan",now-10_000,"A","B","110");
        check(AuditSignals.inspect(List.of(paying,sellerPaid,caravanComplete),now).isEmpty(),"actual final record of the same workflow kind clears its pending review");
        var debitPending=record(16,"NeverLandTownyTrade","TRADE_PAYMENT","PENDING","caravan",now-700_000,"A","B","20");
        check(AuditSignals.inspect(List.of(paying,debitPending),now).size()==2,"independent unfinished kinds sharing one operation remain separately reviewable");
        var unusual=record(12,"NeverLandTownyControl","BANK_TRANSFER","COMPLETED","huge",now,"A","B","1E+1000000");failed=false;try{AuditSignals.inspect(List.of(unusual),now);}catch(IllegalArgumentException expectedFailure){failed=true;}check(failed,"exponent cannot amplify monetary comparison memory");
        var expected=AuditSignals.inspect(List.of(a,b,review,other),now);var mutable=new ArrayList<>(List.of(a,b,review,other));for(int i=0;i<1000;i++){Collections.shuffle(mutable,new Random(i));check(AuditSignals.inspect(mutable,now).equals(expected),"order independent "+i);}
        System.out.println("AuditIndexSmoke PASS "+checks+" checks (1000 shuffled evidence orders)");
    }
}
