package ru.neverland.townydiplomacy;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.AtomicFiles;
import static ru.neverland.townydiplomacy.Treaty.*;

public final class DiplomacyRepository {
    public record Audit(UUID treaty,UUID first,UUID second,long at,String actor,String action) {
        public Audit {
            Objects.requireNonNull(treaty);Objects.requireNonNull(first);Objects.requireNonNull(second);
            if(at<0||actor==null||actor.isBlank()||action==null||action.isBlank())throw new IllegalArgumentException("Повреждён журнал дипломатии");
        }
    }
    public record Incident(UUID id,UUID attacker,UUID victim,Set<UUID> defenders,long at,String location) {
        public Incident {
            Objects.requireNonNull(id);Objects.requireNonNull(attacker);Objects.requireNonNull(victim);defenders=Set.copyOf(defenders);
            if(attacker.equals(victim)||at<0||location==null||location.length()>200||defenders.isEmpty()||defenders.contains(victim))throw new IllegalArgumentException("Повреждён оборонный инцидент");
        }
    }
    private final ru.neverland.core.ReputationOutbox reputation = new ru.neverland.core.ReputationOutbox();
    public void flushReputation(){reputation.flush(this::writable,()->{try{commit(treaties,List.of(),List.of());}catch(IOException ex){throw new java.io.UncheckedIOException(ex);}});}
    public void commit(Map<UUID,Treaty> values,List<Audit> additions,List<Incident> newIncidents,List<ru.neverland.core.ReputationOutcome> outcomes)throws IOException {
        if(!writable())throw new IOException("Реестр дипломатии остановлен");
        // Validate before adding events; malformed proposed snapshots must not leave an outbox entry.
        DiplomacyRules.validateSnapshot(values.values(),System.currentTimeMillis());
        outcomes.forEach(reputation::add);commit(values,additions,newIncidents);
    }
    private final Path file;
    private Map<UUID,Treaty> treaties=Map.of(); private List<Audit> history=List.of(); private List<Incident> incidents=List.of(); private boolean writable;
    public DiplomacyRepository(Path file) { this.file=file; }
    public boolean writable() { return writable&&AtomicFiles.writable(file); }
    public Map<UUID,Treaty> all() { return treaties; }
    public List<Audit> history() { return history; }
    public List<Incident> incidents() { return incidents; }
    public void load()throws Exception {
        writable=false;reputation.load(new YamlConfiguration());var next=new LinkedHashMap<UUID,Treaty>();var audit=new ArrayList<Audit>();var alerts=new ArrayList<Incident>();
        if(Files.exists(file)) {
            var y=new YamlConfiguration();y.load(file.toFile());
            reputation.load(y);
            if(number(y,"schema")!=1)throw new IOException("Неизвестная схема дипломатии");
            for(String key:section(y,"treaties").getKeys(false)) {
                var s=section(y,"treaties."+key);UUID id=UUID.fromString(key);
                var t=new Treaty(id,TreatyType.parse(string(s,"type")),uuid(s,"first"),uuid(s,"second"),uuid(s,"first-mayor"),uuid(s,"second-mayor"),
                        string(s,"actor"),string(s,"reason"),Phase.valueOf(string(s,"phase")),number(s,"created"),number(s,"offer-until"),number(s,"duration"),
                        number(s,"activated"),number(s,"expires"),number(s,"notice-until"),number(s,"notice-period"),Math.toIntExact(number(s,"discount-basis-points")),SanctionScope.valueOf(string(s,"sanction")));
                next.put(id,t);
            }
            for(String key:section(y,"history").getKeys(false)) {
                var s=section(y,"history."+key);audit.add(new Audit(uuid(s,"treaty"),uuid(s,"first"),uuid(s,"second"),number(s,"at"),string(s,"actor"),string(s,"action")));
            }
            for(String key:section(y,"incidents").getKeys(false)) {
                var s=section(y,"incidents."+key);Object raw=s.get("defenders");
                if(!(raw instanceof List<?> list)||list.stream().anyMatch(v->!(v instanceof String)))throw new IOException("Повреждены гаранты инцидента");
                var ids=new HashSet<UUID>();for(String value:s.getStringList("defenders"))if(!ids.add(UUID.fromString(value)))throw new IOException("Повтор гаранта");
                alerts.add(new Incident(UUID.fromString(key),uuid(s,"attacker"),uuid(s,"victim"),ids,number(s,"at"),string(s,"location")));
            }
            if(audit.size()>2000||alerts.size()>500)throw new IOException("Журнал превышает лимит");
        }
        DiplomacyRules.validateSnapshot(next.values(),System.currentTimeMillis());
        treaties=Map.copyOf(next);history=List.copyOf(audit);incidents=List.copyOf(alerts);AtomicFiles.loaded(file);writable=true;
    }
    public void commit(Map<UUID,Treaty> values,List<Audit> additions,List<Incident> newIncidents)throws IOException {
        if(!writable())throw new IOException("Реестр дипломатии остановлен; восстановите файл и перезапустите сервер");
        var next=new LinkedHashMap<>(values);
        for(var e:next.entrySet())if(!e.getKey().equals(e.getValue().id()))throw new IllegalArgumentException("Ключ договора не совпадает с ID");
        DiplomacyRules.validateSnapshot(next.values(),System.currentTimeMillis());
        var closed=next.values().stream().filter(t->t.phase()==Phase.ENDED).sorted(Comparator.comparingLong(Treaty::created).reversed().thenComparing(Treaty::id)).toList();
        closed.stream().skip(500).forEach(t->next.remove(t.id()));
        var audit=new ArrayList<>(history);audit.addAll(additions);if(audit.size()>2000)audit=new ArrayList<>(audit.subList(audit.size()-2000,audit.size()));
        var alerts=new ArrayList<>(incidents);alerts.addAll(newIncidents);if(alerts.size()>500)alerts=new ArrayList<>(alerts.subList(alerts.size()-500,alerts.size()));
        var y=new YamlConfiguration();reputation.write(y);y.set("schema",1);y.createSection("treaties");y.createSection("history");y.createSection("incidents");
        new TreeMap<>(next).forEach((id,t)->{
            String p="treaties."+id+".";y.set(p+"type",t.type().id());y.set(p+"first",t.first().toString());y.set(p+"second",t.second().toString());
            y.set(p+"first-mayor",t.firstMayor().toString());y.set(p+"second-mayor",t.secondMayor().toString());y.set(p+"actor",t.actor());y.set(p+"reason",t.reason());
            y.set(p+"phase",t.phase().name());y.set(p+"created",t.created());y.set(p+"offer-until",t.offerUntil());y.set(p+"duration",t.duration());
            y.set(p+"activated",t.activated());y.set(p+"expires",t.expires());y.set(p+"notice-until",t.noticeUntil());y.set(p+"notice-period",t.noticePeriod());
            y.set(p+"discount-basis-points",t.discountBasisPoints());y.set(p+"sanction",t.sanction().name());
        });
        for(int i=0;i<audit.size();i++) {
            var a=audit.get(i);String p="history."+i+".";y.set(p+"treaty",a.treaty().toString());y.set(p+"first",a.first().toString());y.set(p+"second",a.second().toString());
            y.set(p+"at",a.at());y.set(p+"actor",a.actor());y.set(p+"action",a.action());
        }
        for(var a:alerts) {
            String p="incidents."+a.id()+".";y.set(p+"attacker",a.attacker().toString());y.set(p+"victim",a.victim().toString());
            y.set(p+"defenders",a.defenders().stream().sorted().map(UUID::toString).toList());y.set(p+"at",a.at());y.set(p+"location",a.location());
        }
        try { AtomicFiles.write(file,y::saveToString);treaties=Map.copyOf(next);history=List.copyOf(audit);incidents=List.copyOf(alerts); }
        catch(IOException ex) { writable=false;throw ex; }
    }
    private static ConfigurationSection section(ConfigurationSection s,String key)throws IOException { var value=s.getConfigurationSection(key);if(value==null)throw new IOException("Повреждён раздел "+key);return value; }
    private static String string(ConfigurationSection s,String key)throws IOException { if(!(s.get(key) instanceof String text))throw new IOException("Ожидалась строка "+key);return text; }
    private static UUID uuid(ConfigurationSection s,String key)throws IOException { return UUID.fromString(string(s,key)); }
    private static long number(ConfigurationSection s,String key)throws IOException { Object v=s.get(key);if(!(v instanceof Integer||v instanceof Long)||((Number)v).longValue()<0)throw new IOException("Ожидалось целое неотрицательное число "+key);return ((Number)v).longValue(); }
}
