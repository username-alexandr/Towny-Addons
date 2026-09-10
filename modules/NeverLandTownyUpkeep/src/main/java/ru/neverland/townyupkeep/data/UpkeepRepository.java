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
    private final Path file;private boolean writable;private long clock;private Map<Key,Entry> entries=Map.of();
    public UpkeepRepository(Path file){this.file=file;}
    public long clock(){return clock;}public Map<Key,Entry> entries(){return entries;}
    public void load()throws Exception{
        writable=false;if(!Files.exists(file)){writable=true;return;}var y=new YamlConfiguration();y.load(file.toFile());
        if(number(y,"schema")!=1||!y.isConfigurationSection("towns"))throw new IOException("Неверная схема upkeep-data.yml");long time=number(y,"online-seconds");if(time<0)throw new IOException("Неверные часы обслуживания");
        Map<Key,Entry> next=new HashMap<>();Set<UUID> invoices=new HashSet<>();var towns=y.getConfigurationSection("towns");
        for(String id:towns.getKeys(false)){var t=towns.getConfigurationSection(id);if(t==null)throw new IOException("Неверный город");UUID town=UUID.fromString(id);
            for(String project:t.getKeys(false)){var v=t.getConfigurationSection(project);if(v==null||!(v.get("active") instanceof Boolean))throw new IOException("Неверное состояние здания");Invoice bill=null;
                if(v.contains("invoice")){var b=v.getConfigurationSection("invoice");if(b==null||!b.isConfigurationSection("resources"))throw new IOException("Неверный счёт");Map<String,Long> amounts=new HashMap<>();for(String r:b.getConfigurationSection("resources").getKeys(false))amounts.put(r,number(b,"resources."+r));
                    bill=new Invoice(UUID.fromString(b.getString("id")),new Cost(number(b,"money-cents"),amounts),Math.toIntExact(number(b,"period")),Phase.valueOf(b.getString("phase")));if(!invoices.add(bill.id()))throw new IOException("Повтор ID счёта");}
                next.put(new Key(town,project),new Entry(v.getBoolean("active"),number(v,"due"),v.getString("reason"),bill));}}
        entries=Map.copyOf(next);clock=time;writable=true;
    }
    private static long number(org.bukkit.configuration.ConfigurationSection y,String key)throws IOException{try{return new java.math.BigDecimal(String.valueOf(y.get(key))).longValueExact();}catch(Exception ex){throw new IOException("Неверное число: "+key,ex);}}
    public void save(long time,Map<Key,Entry> next)throws IOException{
        if(!writable)throw new IOException("База не загружена; запись запрещена");if(time<clock)throw new IllegalArgumentException("Часы не могут идти назад");next=Map.copyOf(next);if(time==clock&&next.equals(entries))return;
        var y=new YamlConfiguration();y.set("schema",1);y.set("online-seconds",time);y.createSection("towns");
        for(var e:next.entrySet()){String k="towns."+e.getKey().town()+"."+e.getKey().project()+".";var v=e.getValue();y.set(k+"active",v.active());y.set(k+"due",v.due());y.set(k+"reason",v.reason());
            if(v.invoice()!=null){var b=v.invoice();k+="invoice.";y.set(k+"id",b.id().toString());y.set(k+"money-cents",b.cost().money());y.createSection(k+"resources",b.cost().resources());y.set(k+"period",b.period());y.set(k+"phase",b.phase().name());}}
        Path parent=file.toAbsolutePath().getParent();Files.createDirectories(parent);Path tmp=Files.createTempFile(parent,"upkeep-",".tmp");
        try{try(var channel=FileChannel.open(tmp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){var bytes=ByteBuffer.wrap(y.saveToString().getBytes(StandardCharsets.UTF_8));while(bytes.hasRemaining())channel.write(bytes);channel.force(true);}
            try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}entries=next;clock=time;
        }finally{Files.deleteIfExists(tmp);}
    }
}
