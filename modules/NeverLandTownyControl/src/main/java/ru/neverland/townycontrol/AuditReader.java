package ru.neverland.townycontrol;

import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import ru.neverland.core.*;

/** A bounded page; every source is validated before any result becomes visible. */
final class AuditReader {
    record Page(List<AuditRecord> rows,long matched,long scanned,int journals){Page{rows=List.copyOf(rows);}}
    static List<Path> directories(Path root)throws java.io.IOException {
        try(var entries=Files.list(root)){return entries.filter(Files::isDirectory).filter(p->p.getFileName().toString().startsWith("NeverLandTowny")).map(p->p.resolve("audit")).filter(Files::isDirectory).sorted().toList();}
    }
    static Page page(Path root,Predicate<AuditRecord> filter,int page,int size)throws Exception {
        if(page<1||page>100||size<1||size>36)throw new IllegalArgumentException("Некорректная страница журнала");
        var dirs=directories(root);var order=Comparator.comparingLong(AuditRecord::at).thenComparing(AuditRecord::id).thenComparing(AuditRecord::module);
        var latest=new PriorityQueue<AuditRecord>(order);long[] counts={0,0};
        for(Path dir:dirs)AuditJournal.scan(dir,r->{counts[1]++;if(!filter.test(r))return;counts[0]++;latest.add(r);if(latest.size()>page*size)latest.remove();});
        return new Page(latest.stream().sorted(order.reversed()).skip((page-1L)*size).toList(),counts[0],counts[1],dirs.size());
    }
}
