package ru.neverland.townycontrol;

import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import ru.neverland.core.*;

/** A bounded page; changed sources are fully validated before publishing the indexed snapshot. */
final class AuditReader {
    record Page(List<AuditRecord> rows,long matched,long scanned,int journals){Page{rows=List.copyOf(rows);}}
    static List<Path> directories(Path root)throws java.io.IOException {
        try(var entries=Files.list(root)){var dirs=entries.filter(Files::isDirectory).filter(p->p.getFileName().toString().startsWith("NeverLandTowny")).map(p->p.resolve("audit")).filter(Files::isDirectory).limit(129).sorted().toList();if(dirs.size()>128)throw new java.io.IOException("Больше 128 журналов модулей; проверьте каталог плагинов");return dirs;}
    }
    static Page page(Path root,Predicate<AuditRecord> filter,int page,int size)throws Exception {
        if(page<1||page>100||size<1||size>36)throw new IllegalArgumentException("Некорректная страница журнала");
        return AuditIndex.read(root,(db,stats)->{
            var rows=new ArrayList<AuditRecord>();long[] matched={0},chars={0};long start=(page-1L)*size;
            AuditIndex.scan(db,filter,r->{long n=matched[0]++;if(n>=start&&n<start+size){chars[0]+=AuditSignals.characters(r);if(chars[0]>4_194_304)throw new IllegalArgumentException("Страница слишком велика; сузьте фильтры или выгрузите CSV");rows.add(r);}});
            return new Page(rows,matched[0],stats.rows(),stats.journals());
        });
    }
}
