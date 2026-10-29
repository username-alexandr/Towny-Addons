package ru.neverland.townyupkeep.data;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyupkeep.model.*;
import ru.neverland.townyupkeep.model.Entry.*;
import java.util.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
public final class UpkeepRepository {
    private final Path file;private boolean writable;private long clock;private Map<Key,Entry> entries=Map.of();private Set<Key> paused=Set.of();
    public Set<Key> paused(){try(var guard=ru.neverland.core.SuiteState.guard()){return paused;}}
    public UpkeepRepository(Path file){this.file=file;}
    public long clock(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return clock;} }public Map<Key,Entry> entries(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return entries;} }
    public void load()throws Exception{ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        writable=false;if(!Files.exists(file)){ru.neverland.core.AtomicFiles.loaded(file);writable=true;return;}var y=new YamlConfiguration();y.load(file.toFile());
        long schema=number(y,"schema");if((schema!=1&&schema!=2&&schema!=3)||!y.isConfigurationSection("towns"))throw new IOException("Неверная схема upkeep-data.yml");long time=number(y,"online-seconds");if(time<0)throw new IOException("Неверные часы обслуживания");
        Map<Key,Entry> next=new HashMap<>();Set<UUID> invoices=new HashSet<>();var towns=y.getConfigurationSection("towns");
        for(String id:towns.getKeys(false)){var t=towns.getConfigurationSection(id);if(t==null)throw new IOException("Неверный город");UUID town=UUID.fromString(id);
            for(String project:t.getKeys(false)){var v=t.getConfigurationSection(project);if(v==null||!(v.get("active") instanceof Boolean))throw new IOException("Неверное состояние здания");Invoice bill=null;
                if(v.contains("invoice")){var b=v.getConfigurationSection("invoice");if(b==null||!b.isConfigurationSection("resources"))throw new IOException("Неверный счёт");Map<String,Long> amounts=new HashMap<>();for(String r:b.getConfigurationSection("resources").getKeys(false))amounts.put(r,number(b,"resources."+r));
                    bill=new Invoice(UUID.fromString(b.getString("id")),new Cost(number(b,"money-cents"),amounts),Math.toIntExact(number(b,"period")),Phase.valueOf(b.getString("phase")),b.getString("category",""));if(!invoices.add(bill.id()))throw new IOException("Повтор ID счёта");}
                next.put(new Key(town,project),new Entry(v.getBoolean("active"),number(v,"due"),v.getString("reason"),bill));}}
        var stopped=new HashSet<Key>();if(schema>=2){Object raw=y.get("paused");if(!(raw instanceof List<?> list))throw new IOException("Нет списка остановленных зданий");for(Object value:list){if(!(value instanceof String text))throw new IOException("Неверное остановленное здание");String[] parts=text.split("/",-1);if(parts.length!=2)throw new IOException("Неверное остановленное здание");var key=new Key(UUID.fromString(parts[0]),parts[1]);if(!next.containsKey(key)||!stopped.add(key)||next.get(key).invoice()!=null)throw new IOException("Остановка не соответствует состоянию здания");}}
        entries=Map.copyOf(next);paused=Set.copyOf(stopped);clock=time;ru.neverland.core.AtomicFiles.loaded(file);writable=true;
    } }
    private static long number(org.bukkit.configuration.ConfigurationSection y,String key)throws IOException{try{return new java.math.BigDecimal(String.valueOf(y.get(key))).longValueExact();}catch(Exception ex){throw new IOException("Неверное число: "+key,ex);}}
    public void save(long time,Map<Key,Entry> next)throws IOException{try(var guard=ru.neverland.core.SuiteState.guard()){var keep=new HashSet<>(paused);keep.retainAll(next.keySet());write(time,next,keep);}}
    public void suspend(long time,Key key,boolean stop)throws IOException{try(var guard=ru.neverland.core.SuiteState.guard()){
        var entry=entries.get(key);if(entry==null)throw new IllegalArgumentException("Здание не найдено");if(entry.invoice()!=null)throw new IllegalStateException("Дождитесь завершения текущей оплаты или сверки");
        var next=new HashSet<>(paused);if(stop)next.add(key);else next.remove(key);write(time,entries,next);
    }}
    private void write(long time,Map<Key,Entry> next,Set<Key> stopped)throws IOException{
        if(!writable||!ru.neverland.core.AtomicFiles.writable(file))throw new IOException("База не загружена; запись запрещена");if(time<clock)throw new IllegalArgumentException("Часы не могут идти назад");next=Map.copyOf(next);stopped=Set.copyOf(stopped);if(time==clock&&next.equals(entries)&&stopped.equals(paused))return;
        for(var key:stopped)if(!next.containsKey(key)||next.get(key).invoice()!=null)throw new IllegalArgumentException("Нельзя остановить здание с незавершённой оплатой");
        var y=new YamlConfiguration();y.set("schema",3);y.set("online-seconds",time);y.set("paused",stopped.stream().map(k->k.town()+"/"+k.project()).sorted().toList());y.createSection("towns");
        for(var e:next.entrySet()){String k="towns."+e.getKey().town()+"."+e.getKey().project()+".";var v=e.getValue();y.set(k+"active",v.active());y.set(k+"due",v.due());y.set(k+"reason",v.reason());
            if(v.invoice()!=null){var b=v.invoice();k+="invoice.";y.set(k+"id",b.id().toString());y.set(k+"money-cents",b.cost().money());y.createSection(k+"resources",b.cost().resources());y.set(k+"period",b.period());y.set(k+"phase",b.phase().name());if(!b.category().isEmpty())y.set(k+"category",b.category());}}
        ru.neverland.core.AtomicFiles.write(file,y::saveToString);entries=next;clock=time;paused=stopped;
    }
}
