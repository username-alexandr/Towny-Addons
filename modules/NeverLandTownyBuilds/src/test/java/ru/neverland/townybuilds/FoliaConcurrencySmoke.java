package ru.neverland.townybuilds;

import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import ru.neverland.core.*;

/** Verifies shaded copies and external bank callbacks, not scheduler mocks. */
public final class FoliaConcurrencySmoke {
    public static void main(String[] args)throws Exception {
        URL location=SuiteState.class.getProtectionDomain().getCodeSource().getLocation();
        try(URLClassLoader isolated=new URLClassLoader(new URL[]{location},ClassLoader.getPlatformClassLoader())){
            Class<?> copy=Class.forName("ru.neverland.core.SuiteState",true,isolated);
            assert copy!=SuiteState.class;
            assert copy.getMethod("lock").invoke(null)==SuiteState.lock():"Shaded addons must share one ledger";
            SuiteState.freeze("test-addon");
            assert Boolean.TRUE.equals(copy.getMethod("frozen",String.class).invoke(null,"test-addon"));
            copy.getMethod("thaw",String.class).invoke(null,"test-addon");assert !SuiteState.frozen("test-addon");
        }
        Object account=new Object();PaymentEvidence evidence=new PaymentEvidence(account,true,"token",1250);
        var executor=Executors.newSingleThreadExecutor();
        try {
            synchronized(SuiteState.lock()) {
                // Towny invokes observers with its own account monitor held. Such callbacks cannot request the ledger.
                executor.submit(()->{synchronized(account){evidence.observe(account,true,12.50,"token");}}).get(2,TimeUnit.SECONDS);
                assert evidence.observed();
                Path folder=Files.createTempDirectory("folia-audit-");
                try {
                    AuditJournal journal=new AuditJournal(folder);
                    AuditRecord entry=new AuditRecord(java.util.UUID.randomUUID().toString(),1,"probe","BANK","CREDIT","op",AuditRecord.Party.unknown(),AuditRecord.Party.unknown(),AuditRecord.Party.unknown(),"",0,"12.5","receipt");
                    executor.submit(()->{try{journal.appendUnique(entry);}catch(Exception e){throw new RuntimeException(e);}}).get(2,TimeUnit.SECONDS);
                    assert AuditJournal.files(folder).size()==1;
                }finally {try(var files=Files.walk(folder)){for(Path p:files.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}}
            }
        }finally{executor.shutdownNow();}
        System.out.println("FoliaConcurrencySmoke PASS: shaded monitor, shared freeze, bank callback and independent durable audit");
    }
}
