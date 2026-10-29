package ru.neverland.townychronicles.service;

import java.util.*;
import java.math.BigDecimal;
import ru.neverland.core.AuditRecord;

/** Public aggregate only. Never publishes private bank records, raw item data or administrative notes. */
public final class CityContributions {
    public record Member(UUID player,String name,long materials,BigDecimal deposits,long repairs,BigDecimal repairBudget,long defense,long thanks,long last){}
    public static final int MAX_TOWNS=2048,MAX_MEMBERS=20_000,MAX_RECEIPTS=100_000;
    private static final class Mutable{UUID id;String name="",nameReceipt="";long materials,repairs,defense,thanks,last;BigDecimal deposits=BigDecimal.ZERO,repairBudget=BigDecimal.ZERO;Member freeze(){return new Member(id,name,materials,deposits,repairs,repairBudget,defense,thanks,last);}}
    /** Streaming and exact: hitting a capacity rejects this refresh instead of publishing partial totals. */
    public static final class Accumulator {
        private final Map<UUID,Map<UUID,Mutable>> towns=new HashMap<>();private final Map<String,List<String>> seen=new HashMap<>();private int memberCount;
        public void accept(AuditRecord r){
            String type="",city="";AuditRecord.Party player=r.actor();
            if(Set.of("BUILD_CONTRIBUTION","CONTRACT_DELIVERY","STORAGE_PLAYER").contains(r.kind())&&Set.of("COMPLETED","COMPLETE","OBSERVED").contains(r.outcome())&&r.from().type().equals("PLAYER")&&r.to().type().equals("TOWN")){type="materials";city=r.to().id();player=r.from();}
            else if(r.kind().equals("BANK_TRANSFER")&&r.outcome().equals("COMPLETED")&&r.from().type().equals("PLAYER")&&r.to().type().equals("TOWN")){type="deposit";city=r.to().id();player=r.from();}
            else if(r.kind().equals("BUILDING_REPAIR")&&r.outcome().equals("DONE")&&r.from().type().equals("TOWN")&&player.type().equals("PLAYER")){type="repair";city=r.from().id();}
            else if(r.kind().equals("CITY_DEFENSE")&&r.outcome().equals("COMPLETED")&&r.from().type().equals("TOWN")&&player.type().equals("PLAYER")){type="defense";city=r.from().id();}
            else if(r.kind().equals("CITY_COMMENDATION")&&r.outcome().equals("COMPLETED")&&r.from().type().equals("TOWN")&&r.to().type().equals("PLAYER")){type="thanks";city=r.from().id();player=r.to();}
            if(type.isEmpty())return;
            final UUID town,id;final BigDecimal money;
            try{town=UUID.fromString(city);id=UUID.fromString(player.id());money=r.money().isEmpty()?BigDecimal.ZERO:new BigDecimal(r.money());}
            catch(IllegalArgumentException ignored){return;} // No invented attribution for imported records without stable IDs.
            String receipt=r.module()+":"+r.id();List<String> identity=List.of(type,town.toString(),id.toString(),Long.toString(r.quantity()),money.toPlainString());
            var previous=seen.get(receipt);if(previous!=null){if(!previous.equals(identity))throw new IllegalStateException("Противоречащие квитанции вклада жителей");return;}
            if(seen.size()>=MAX_RECEIPTS)throw new IllegalStateException("Слишком много квитанций вклада жителей; сохранён прежний снимок");seen.put(receipt,identity);
            var members=towns.get(town);if(members==null){if(towns.size()>=MAX_TOWNS)throw new IllegalStateException("Слишком много городов в истории вклада");members=new HashMap<>();towns.put(town,members);}
            var m=members.get(id);if(m==null){if(memberCount>=MAX_MEMBERS)throw new IllegalStateException("Слишком много жителей в истории вклада");m=new Mutable();m.id=id;members.put(id,m);memberCount++;}
            if(r.at()>m.last||r.at()==m.last&&receipt.compareTo(m.nameReceipt)>0){m.name=player.name();m.last=r.at();m.nameReceipt=receipt;}
            switch(type){case "materials"->m.materials=Math.addExact(m.materials,r.quantity());case "deposit"->m.deposits=m.deposits.add(money);case "repair"->{m.repairs=Math.incrementExact(m.repairs);m.repairBudget=m.repairBudget.add(money);}case "defense"->m.defense=Math.incrementExact(m.defense);case "thanks"->m.thanks=Math.incrementExact(m.thanks);default->throw new AssertionError(type);}
        }
        public Map<UUID,List<Member>> finish(){var out=new HashMap<UUID,List<Member>>();towns.forEach((town,members)->out.put(town,members.values().stream().map(Mutable::freeze).sorted(Comparator.comparingLong(Member::last).reversed().thenComparing(m->m.player().toString())).toList()));return Map.copyOf(out);}
    }
    public static Map<UUID,List<Member>> aggregate(Collection<AuditRecord> records){var accumulator=new Accumulator();records.forEach(accumulator::accept);return accumulator.finish();}
    private CityContributions(){}
}
