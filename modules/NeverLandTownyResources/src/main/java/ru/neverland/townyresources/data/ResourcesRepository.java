package ru.neverland.townyresources.data;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyresources.model.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.IOException;

public final class ResourcesRepository {
    private final Path file;
    private Map<UUID,TownState> states=Map.of();
    private boolean writable;
    public ResourcesRepository(Path file) { this.file=file; }
    public Map<UUID,TownState> states() { return states; }
    public void load() throws Exception {
        writable=false;
        if(!Files.exists(file)){states=Map.of();writable=true;return;}
        var y=new YamlConfiguration();y.load(file.toFile());
        if(number(y,"schema")!=1||!y.isConfigurationSection("towns"))throw new IOException("Неверная схема resources-data.yml");
        Map<UUID,TownState> next=new HashMap<>();var towns=y.getConfigurationSection("towns");
        for(String id:towns.getKeys(false)){
            var p=towns.getConfigurationSection(id);if(p==null)throw new IOException("Неверная запись города");
            Set<String> paused=new HashSet<>();Object raw=p.get("paused");if(!(raw instanceof List<?> list))throw new IOException("Неверный список остановленных зданий");
            for(Object value:list)if(!(value instanceof String name)||!paused.add(name))throw new IOException("Неверное или повторное здание");
            var order=p.getConfigurationSection("priorities");if(order==null)throw new IOException("Отсутствуют приоритеты");Map<String,Integer> priorities=new HashMap<>();
            for(String key:order.getKeys(false))priorities.put(key,Math.toIntExact(number(order,key)));
            TownState state=new TownState(amounts(p,"balances"),amounts(p,"reserves"),paused,priorities,number(p,"cycles"),number(p,"last-cycle"),amounts(p,"income"),amounts(p,"expense"),fraction(p,"food-coverage"),fraction(p,"water-coverage"));
            next.put(UUID.fromString(id),state);
        }
        states=Map.copyOf(next);writable=true;
    }
    private static long number(ConfigurationSection p,String key)throws IOException{
        try{return new java.math.BigDecimal(String.valueOf(p.get(key))).longValueExact();}catch(Exception ex){throw new IOException("Некорректное целое число: "+key,ex);}
    }
    private static double fraction(ConfigurationSection p,String key)throws IOException{
        Object value=p.get(key);if(!(value instanceof Number n))throw new IOException("Некорректное покрытие: "+key);return n.doubleValue();
    }
    private static Map<Resource,Long> amounts(ConfigurationSection p,String key)throws IOException{
        var section=p.getConfigurationSection(key);if(section==null||section.getKeys(false).size()!=8)throw new IOException("Неполный список ресурсов: "+key);
        Map<Resource,Long> result=new EnumMap<>(Resource.class);for(var r:Resource.values())result.put(r,number(section,r.id()));return key.equals("income")||key.equals("expense")?Amounts.flows(result):Amounts.copy(result);
    }
    public void replace(Map<UUID,TownState> next)throws IOException {
        if(!writable)throw new IOException("Запись запрещена до успешной загрузки базы");
        next=Map.copyOf(next);if(next.equals(states))return;
        var y=new YamlConfiguration();y.set("schema",1);y.createSection("towns");
        for(var entry:next.entrySet()){
            String k="towns."+entry.getKey()+".";var s=entry.getValue();
            put(y,k+"balances",s.balances());put(y,k+"reserves",s.reserves());put(y,k+"income",s.income());put(y,k+"expense",s.expense());
            y.set(k+"paused",s.paused().stream().sorted().toList());y.createSection(k+"priorities",s.priorities());y.set(k+"cycles",s.cycles());y.set(k+"last-cycle",s.lastCycle());y.set(k+"food-coverage",s.foodCoverage());y.set(k+"water-coverage",s.waterCoverage());
        }
        Path parent=file.toAbsolutePath().getParent();Files.createDirectories(parent);Path tmp=Files.createTempFile(parent,"resources-",".tmp");
        try{
            byte[] bytes=y.saveToString().getBytes(StandardCharsets.UTF_8);
            try(var channel=FileChannel.open(tmp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
            try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
            states=next;
        }finally{Files.deleteIfExists(tmp);}
    }
    private static void put(YamlConfiguration y,String key,Map<Resource,Long> values){y.createSection(key);values.forEach((r,n)->y.set(key+"."+r.id(),n));}
}
