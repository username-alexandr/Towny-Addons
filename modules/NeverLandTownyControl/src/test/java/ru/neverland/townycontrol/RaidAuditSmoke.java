package ru.neverland.townycontrol;
import java.util.*;import ru.neverland.core.*;
public final class RaidAuditSmoke {
    public static void main(String[] args){int checks=0;for(String kind:List.of("RAID_CAMP","RAID_DECLARED","RAID_CORE","RAID_LOOT_MONEY","RAID_LOOT_RESOURCES","RAID_LOOT_STATE","RAID_LOOT_REVIEW","RAID_LOOT_RECONCILE")){
        var r=new AuditRecord(UUID.randomUUID().toString(),1,"NeverLandTownySiegesPlus",kind,"REVIEW_CREDIT","claim",AuditRecord.Party.system(),AuditRecord.Party.unknown(),AuditRecord.Party.unknown(),"",0,"100.00","reason");
        if(!AuditQuery.parse(new String[]{"category=RAIDS"}).test(r)||AuditCategory.OTHER.test(r)||AuditCategory.kind(kind).equals(kind))throw new AssertionError(kind);checks++;
    }if(!AuditCategory.outcome("REVIEW_DEBIT").contains("сверка"))throw new AssertionError("unreadable review");System.out.println("RaidAuditSmoke PASS: "+(++checks)+" assertions; category, labels and review");}
}
