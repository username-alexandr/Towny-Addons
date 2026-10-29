package ru.neverland.townychronicles.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import ru.neverland.core.*;
import ru.neverland.townychronicles.chapters.CityChapters;

/** One bounded streaming pass; exceeding a budget rejects the refresh instead of truncating totals. */
public final class ContributionHistory {
    public record Snapshot(Map<UUID,List<CityContributions.Member>> members,CityChapters.Evidence chapters,List<AuditRecord> thanks){}
    public static final int MAX_FILES=512,MAX_SCANNED=250_000,MAX_THANKS=1000;
    public static final long MAX_BYTES=256L*1024*1024,MAX_NANOS=30_000_000_000L;
    static Snapshot read(List<Path> directories,String module)throws IOException{return read(directories,module,r->true);}
    static Snapshot read(List<Path> directories,String module,java.util.function.Predicate<AuditRecord> confirmed)throws IOException{
        if(SuiteState.held())throw new IllegalStateException("History scan under SuiteState");
        long deadline=System.nanoTime()+MAX_NANOS;var aggregate=new CityContributions.Accumulator();var evidence=new CityChapters.EvidenceAccumulator();
        var thanks=new TreeSet<AuditRecord>(Comparator.comparingLong(AuditRecord::at).thenComparing(AuditRecord::id));
        long bytes=0;int files=0;for(var dir:directories)for(var file:AuditJournal.files(dir)){
            if(++files>MAX_FILES||(bytes=Math.addExact(bytes,Files.size(file)))>MAX_BYTES)throw new IOException("История превысила безопасный объём чтения; прежний снимок сохранён");
        }
        int[] scanned={0};
        for(var directory:directories){
            AuditJournal.scan(directory,r->{
                if(++scanned[0]>MAX_SCANNED||System.nanoTime()>deadline||Thread.currentThread().isInterrupted())throw new IllegalStateException("Превышен бюджет обновления истории; прежний снимок сохранён");
                if(!confirmed.test(r))return;aggregate.accept(r);evidence.accept(r);
                if(r.module().equals(module)&&r.kind().equals("CITY_COMMENDATION")&&r.outcome().equals("COMPLETED")){
                    thanks.add(r);if(thanks.size()>MAX_THANKS)thanks.pollFirst();
                }
            });
        }
        return new Snapshot(aggregate.finish(),evidence.finish(),List.copyOf(thanks));
    }
    private ContributionHistory(){}
}
