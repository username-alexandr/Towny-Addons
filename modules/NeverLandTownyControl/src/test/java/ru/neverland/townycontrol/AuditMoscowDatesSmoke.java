package ru.neverland.townycontrol;
import java.time.*;
import java.util.*;
import ru.neverland.core.AuditRecord;

public final class AuditMoscowDatesSmoke {
    static AuditRecord row(String instant){return new AuditRecord(UUID.randomUUID().toString(),Instant.parse(instant).toEpochMilli(),"NeverLandTownyControl","CAMP_GUEST","GRANTED","test",AuditRecord.Party.system(),AuditRecord.Party.system(),AuditRecord.Party.system(),"",0,"","");}
    public static void main(String[] args){
        var day=AuditQuery.parse(new String[]{"since=2026-10-28","until=2026-10-28"});
        if(day.test(row("2026-10-27T20:59:59.999Z"))||!day.test(row("2026-10-27T21:00:00Z"))||!day.test(row("2026-10-28T20:59:59.999Z"))||day.test(row("2026-10-28T21:00:00Z")))throw new AssertionError("Moscow date boundary");
        if(AuditCategory.values().length>36)throw new AssertionError("Category menu must be paginated before exceeding 36 buttons");
        System.out.println("AuditMoscowDatesSmoke PASS: exact Moscow day boundaries and menu capacity");
    }
}
