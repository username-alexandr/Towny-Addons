package ru.neverland.townychronicles.chapters;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import ru.neverland.core.AuditRecord;
import ru.neverland.townychronicles.model.*;

/** Read-only, reproducible chapters. Every fact retains its original evidence ID. */
public final class CityChapters {
    public enum Theme { REIGN, WAR, RECOVERY }
    public record Fact(String id, long at, String title, String actor, String source,
                       ChronicleCategory category, BigDecimal repairCost) {}
    public record Chapter(String id, Theme theme, String title, long from, long until,
                          List<Fact> facts, BigDecimal repairCost, int unknownCosts) {
        public Chapter { facts=List.copyOf(facts); }
    }
    public record Evidence(Map<UUID,List<Fact>> towns, Set<UUID> truncated) {
        public Evidence { var copy=new HashMap<UUID,List<Fact>>();towns.forEach((id,list)->copy.put(id,List.copyOf(list)));towns=Map.copyOf(copy);truncated=Set.copyOf(truncated); }
    }
    public static final int MAX_FACTS=2000;
    private static final ZoneId ZONE=ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter MONTH=DateTimeFormatter.ofPattern("LLLL yyyy",Locale.forLanguageTag("ru"));
    private static final Comparator<Fact> ORDER=Comparator.comparingLong(Fact::at).thenComparingInt(f->f.category()==ChronicleCategory.MAYOR?0:1).thenComparing(Fact::id);

    /** Deliberately allowlists public outcomes; coordinates, administrative notes and balances never leave the audit. */
    public static Evidence publicEvidence(Collection<AuditRecord> records) {
        var accumulator=new EvidenceAccumulator();records.forEach(accumulator::accept);return accumulator.finish();
    }
    /** Retains only newest facts per city while separately bounding receipts and city count. */
    public static final class EvidenceAccumulator {
        private final Map<UUID,NavigableSet<Fact>> towns=new HashMap<>();private final Set<UUID> truncated=new HashSet<>();
        private final Map<String,String> receipts=new HashMap<>();private int retained;
        public void accept(AuditRecord r){
            if(!r.module().equals("NeverLandTownySiegesPlus"))return;
            if(r.kind().equals("BUILDING_REPAIR")&&r.outcome().equals("DONE")&&r.from().type().equals("TOWN")) {
                if(!receipt(r))return;BigDecimal cost=null;
                try {var value=new BigDecimal(r.money());if(value.signum()>=0&&value.precision()<=20&&value.scale()>=0&&value.scale()<=2)cost=value;}catch(NumberFormatException ignored){}
                add(r.from().id(),new Fact("audit:"+r.id(),r.at(),"Завершён ремонт здания",r.actor().type().equals("PLAYER")?r.actor().name():"",r.module(),ChronicleCategory.RECOVERY,cost));
            }else if(r.from().type().equals("TOWN")&&r.to().type().equals("TOWN")){
                String title=switch(r.kind()+":"+r.outcome()){case "RAID_DECLARED:READY"->"Объявлен рейд";case "RAID_CANCELLED:CANCELLED"->"Рейд остановлен администрацией";default->"";};
                if(title.isEmpty()||!receipt(r))return;
                for(var city:List.of(r.from(),r.to()))add(city.id(),new Fact("audit:"+r.id(),r.at(),title,"",r.module(),ChronicleCategory.WAR,null));
            }
        }
        private boolean receipt(AuditRecord r){
            String identity=fingerprint(List.of(r.module(),r.kind(),r.outcome(),r.operation(),r.from().id(),r.to().id(),r.money(),Long.toString(r.at()),r.actor().type(),r.actor().name()));
            var previous=receipts.get(r.id());if(previous!=null){if(!previous.equals(identity))throw new IllegalStateException("Противоречащие квитанции летописи");return false;}
            if(receipts.size()>=100_000)throw new IllegalStateException("Слишком много квитанций глав летописи");receipts.put(r.id(),identity);return true;
        }
        private void add(String raw,Fact fact){
            final UUID city;try{city=UUID.fromString(raw);}catch(IllegalArgumentException ignored){return;}
            var values=towns.get(city);if(values==null){if(towns.size()>=2048)throw new IllegalStateException("Слишком много городов в главах летописи");values=new TreeSet<>(ORDER);towns.put(city,values);}
            if(values.add(fact))retained++;if(values.size()>MAX_FACTS){values.pollFirst();retained--;truncated.add(city);}if(retained>50_000)throw new IllegalStateException("Слишком много фактов в главах летописи; прежний снимок сохранён");
        }
        private static String fingerprint(List<String> fields){try{var digest=java.security.MessageDigest.getInstance("SHA-256");for(String field:fields){byte[] bytes=field.getBytes(StandardCharsets.UTF_8);digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}return HexFormat.of().formatHex(digest.digest());}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
        public Evidence finish(){var result=new HashMap<UUID,List<Fact>>();towns.forEach((city,values)->result.put(city,values.descendingSet().stream().toList()));return new Evidence(result,truncated);}
    }
    public static List<Chapter> build(UUID town,List<ChronicleEntry> entries,List<Fact> receipts,long asOf){
        var facts=new TreeMap<String,Fact>();
        for(var e:entries)if(e.townId().equals(town)&&e.timestamp()<=asOf)put(facts,new Fact("chronicle:"+e.id(),e.timestamp(),e.title(),e.actor(),e.source(),e.category(),null));
        for(var f:receipts)if(f.at()<=asOf)put(facts,f);
        var sorted=facts.values().stream().sorted(ORDER).toList();var result=new ArrayList<Chapter>();
        // Mayor facts mark observed boundaries only. Never infer a missing founder's term from today's mayor.
        var reign=new ArrayList<Fact>();String title="До первой записи о смене мэра",boundary="before";long from=sorted.isEmpty()?0:sorted.get(0).at();
        for(var fact:sorted){
            if(fact.category()==ChronicleCategory.MAYOR){
                if(!reign.isEmpty())result.add(chapter(town,Theme.REIGN,boundary,title,from,fact.at(),reign));
                reign=new ArrayList<>();boundary=fact.id();title=fact.actor().isBlank()?"Правление: имя не записано":"Правление: "+fact.actor();from=fact.at();
            }
            reign.add(fact);
        }
        if(!reign.isEmpty())result.add(chapter(town,Theme.REIGN,boundary,title,from,asOf,reign));
        for(var theme:List.of(Theme.WAR,Theme.RECOVERY)){
            var groups=new TreeMap<YearMonth,List<Fact>>();
            for(var fact:sorted)if(fact.category()==(theme==Theme.WAR?ChronicleCategory.WAR:ChronicleCategory.RECOVERY))groups.computeIfAbsent(YearMonth.from(Instant.ofEpochMilli(fact.at()).atZone(ZONE)),k->new ArrayList<>()).add(fact);
            groups.forEach((month,values)->result.add(chapter(town,theme,month.toString(),(theme==Theme.WAR?"Войны • ":"Восстановление • ")+month.atDay(1).format(MONTH),month.atDay(1).atStartOfDay(ZONE).toInstant().toEpochMilli(),Math.min(asOf,month.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant().toEpochMilli()),values)));
        }
        return result.stream().sorted(Comparator.comparingLong(Chapter::from).reversed().thenComparing(Chapter::id)).toList();
    }
    private static void put(Map<String,Fact> facts,Fact fact){var old=facts.putIfAbsent(fact.id(),fact);if(old!=null&&!old.equals(fact))throw new IllegalStateException("Противоречащие записи летописи");}
    private static Chapter chapter(UUID town,Theme theme,String boundary,String title,long from,long until,List<Fact> facts){
        BigDecimal cost=BigDecimal.ZERO;int unknown=0;
        for(var f:facts)if(f.category()==ChronicleCategory.RECOVERY&&f.id().startsWith("audit:")){if(f.repairCost()!=null)cost=cost.add(f.repairCost());else unknown++;}
        String id=UUID.nameUUIDFromBytes((town+":"+theme+":"+boundary).getBytes(StandardCharsets.UTF_8)).toString();
        return new Chapter(id,theme,title,from,until,facts.stream().sorted(ORDER.reversed()).toList(),cost,unknown);
    }
    private CityChapters(){}
}
