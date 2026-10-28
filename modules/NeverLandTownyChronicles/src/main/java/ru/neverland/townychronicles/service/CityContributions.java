package ru.neverland.townychronicles.service;
import java.util.*;
import java.math.BigDecimal;
import ru.neverland.core.AuditRecord;

/** Public aggregate only. Never publishes private bank records, raw item data or administrative notes. */
public final class CityContributions {
    public record Member(UUID player,String name,long materials,BigDecimal deposits,long repairs,BigDecimal repairBudget,long defense,long thanks,long last){}
    private static final class Mutable{UUID id;String name;long materials,repairs,defense,thanks,last;BigDecimal deposits=BigDecimal.ZERO,repairBudget=BigDecimal.ZERO;Member freeze(){return new Member(id,name,materials,deposits,repairs,repairBudget,defense,thanks,last);}}
    public static Map<UUID,List<Member>> aggregate(Collection<AuditRecord> records){var towns=new HashMap<UUID,Map<UUID,Mutable>>();var seen=new HashSet<String>();for(var r:records){if(!seen.add(r.id()))continue;String type="",city="";AuditRecord.Party player=r.actor();
        if(Set.of("BUILD_CONTRIBUTION","CONTRACT_DELIVERY","STORAGE_PLAYER").contains(r.kind())&&Set.of("COMPLETED","COMPLETE","OBSERVED").contains(r.outcome())&&r.from().type().equals("PLAYER")&&r.to().type().equals("TOWN")){type="materials";city=r.to().id();player=r.from();}
        else if(r.kind().equals("BANK_TRANSFER")&&r.outcome().equals("COMPLETED")&&r.from().type().equals("PLAYER")&&r.to().type().equals("TOWN")){type="deposit";city=r.to().id();player=r.from();}
        else if(r.kind().equals("BUILDING_REPAIR")&&r.outcome().equals("DONE")&&r.from().type().equals("TOWN")&&player.type().equals("PLAYER")){type="repair";city=r.from().id();}
        else if(r.kind().equals("CITY_DEFENSE")&&r.outcome().equals("COMPLETED")&&r.from().type().equals("TOWN")&&player.type().equals("PLAYER")){type="defense";city=r.from().id();}
        else if(r.kind().equals("CITY_COMMENDATION")&&r.outcome().equals("COMPLETED")&&r.from().type().equals("TOWN")&&r.to().type().equals("PLAYER")){type="thanks";city=r.from().id();player=r.to();}
        if(type.isEmpty())continue;try{UUID town=UUID.fromString(city),id=UUID.fromString(player.id());var members=towns.computeIfAbsent(town,t->new HashMap<>());Mutable m=members.computeIfAbsent(id,p->{Mutable n=new Mutable();n.id=p;return n;});if(r.at()>=m.last){m.name=player.name();m.last=r.at();}switch(type){case "materials"->m.materials=Math.addExact(m.materials,r.quantity());case "deposit"->m.deposits=m.deposits.add(new BigDecimal(r.money()));case "repair"->{m.repairs++;if(!r.money().isEmpty())m.repairBudget=m.repairBudget.add(new BigDecimal(r.money()));}case "defense"->m.defense++;case "thanks"->m.thanks++;}}catch(IllegalArgumentException ignored){/* Old imported records with no stable account identity do not receive invented attribution. */}}
        var out=new HashMap<UUID,List<Member>>();towns.forEach((town,members)->out.put(town,members.values().stream().map(Mutable::freeze).sorted(Comparator.comparingLong(Member::last).reversed().thenComparing(m->m.player().toString())).toList()));return Map.copyOf(out);
    }
    private CityContributions(){}
}
