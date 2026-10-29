package ru.neverland.townycontrol;
import java.util.*;import ru.neverland.core.*;
public final class ArmyAuditSmoke {
 public static void main(String[] args){int checks=0;for(String kind:List.of("ARMY_ROLE","ARMY_ORDER","ARMY_KIT")){
  var row=new AuditRecord(UUID.randomUUID().toString(),1,"NeverLandTownyArmy",kind,"REVIEW","kit-id",AuditRecord.Party.system(),AuditRecord.Party.unknown(),AuditRecord.Party.unknown(),"",0,"","reason");
  if(!AuditQuery.parse(new String[]{"category=ARMY"}).test(row)||AuditCategory.OTHER.test(row)||AuditCategory.RAIDS.test(row)||AuditCategory.kind(kind).equals(kind))throw new AssertionError(kind);checks++;
 }var from=AuditRecord.Party.system();String op=UUID.randomUUID().toString();var pending=new AuditRecord(UUID.randomUUID().toString(),1,"NeverLandTownyArmy","ARMY_KIT","PENDING",op,from,from,from,"",0,"","pending");var done=new AuditRecord(UUID.randomUUID().toString(),2,"NeverLandTownyArmy","ARMY_KIT","DELIVERED",op,from,from,from,"",0,"","done");
 if(AuditSignals.inspect(List.of(pending),1000000).size()!=1||!AuditSignals.inspect(List.of(pending,done),1000000).isEmpty())throw new AssertionError("terminal kit must close exactly its review card");checks++;
 if(AuditCategory.outcome("PENDING").equals("PENDING"))throw new AssertionError("untranslated pending");System.out.println("ArmyAuditSmoke PASS "+(++checks)+" checks");
 }
}
