package ru.neverland.townychronicles.service;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.plugin.Plugin;
import ru.neverland.core.*;

public final class ContributionAuditSmoke {
    private static int checks;private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    private static AuditRecord record(String op,long at){return new AuditRecord(AuditRecord.id("NeverLandTownyChronicles",op,"thanks"),at,"NeverLandTownyChronicles","CITY_COMMENDATION","COMPLETED",op,AuditRecord.Party.system(),AuditRecord.Party.system(),AuditRecord.Party.system(),"",0,"","Спасибо за помощь");}
    public static void main(String[] args)throws Exception{
        Path directory=Files.createTempDirectory("chronicles-audit-ack-");
        Plugin plugin=(Plugin)java.lang.reflect.Proxy.newProxyInstance(Plugin.class.getClassLoader(),new Class<?>[]{Plugin.class},(proxy,method,values)->switch(method.getName()){case "getName"->"NeverLandTownyChronicles";case "getDataFolder"->directory.toFile();case "getLogger"->java.util.logging.Logger.getLogger("audit-test");default->throw new UnsupportedOperationException(method.getName());});
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var errors=new ArrayList<String>();
        var writer=new ContributionAudit(r->{entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("timeout");return AuditTrail.record(plugin,r);},errors::add,1);
        var original=record("first",123456789L);var first=writer.submit(original);check(entered.await(2,TimeUnit.SECONDS),"actual writer reached blocked sink");
        try(var guard=SuiteState.guard()){check(!first.toCompletableFuture().isDone(),"publication waits for disk ACK while suite remains available");}
        var second=writer.submit(record("second",223456789L));var rejected=writer.submit(record("third",323456789L));check(rejected.toCompletableFuture().isCompletedExceptionally(),"bounded queue does not claim rejected write");
        var stop=writer.prepareClose();check(!stop.toCompletableFuture().isDone(),"shutdown waits for accepted writes");check(writer.submit(record("closed",423456789L)).toCompletableFuture().isCompletedExceptionally(),"closing rejects new work");
        release.countDown();check(first.toCompletableFuture().get(3,TimeUnit.SECONDS).equals(original),"exact captured receipt returned after fsync");second.toCompletableFuture().get(3,TimeUnit.SECONDS);stop.toCompletableFuture().get(3,TimeUnit.SECONDS);
        var records=new ArrayList<AuditRecord>();AuditJournal.scan(directory.resolve("audit"),records::add);check(records.size()==2,"all accepted records persisted and rejected record absent");check(records.get(0).at()==original.at()&&records.get(0).id().equals(original.id()),"new shared overload preserves timestamp and ID");
        check(AuditTrail.record(plugin,original),"same immutable receipt is idempotent");records.clear();AuditJournal.scan(directory.resolve("audit"),records::add);check(records.size()==2,"idempotent replay does not duplicate audit");
        check(!AuditTrail.record(plugin,"bad","invalid","CITY_COMMENDATION","COMPLETED",AuditRecord.Party.system(),AuditRecord.Party.system(),AuditRecord.Party.system(),"",-1,"",""),"old overload still returns false for malformed record");
        var failed=new ContributionAudit(r->false,errors::add,1);boolean failure=false;try{failed.submit(record("failure",123)).toCompletableFuture().get(2,TimeUnit.SECONDS);}catch(ExecutionException expected){failure=true;}check(failure,"failed audit never acknowledges publication");check(failed.fault()&&failed.submit(record("after-failure",124)).toCompletableFuture().isCompletedExceptionally(),"write fault stops new audit publications until restart");failed.prepareClose().toCompletableFuture().get(2,TimeUnit.SECONDS);check(errors.size()==1&&errors.get(0).contains("AUDIT GAP"),"failed write is explicit");
        System.out.println("ContributionAuditSmoke PASS "+checks+" checks");
    }
}
