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
    private Map<UUID,Reservation> reservations=Map.of();
    private boolean writable;
    public Map<UUID,Reservation> reservations(){return reservations;}
    public ResourcesRepository(Path file) { this.file=file; }
    public Map<UUID,TownState> states() { return states; }
    public Map<Resource,Long> held(UUID town){
        var out=Amounts.mutable(Map.of());for(var v:reservations.values())if(v.town().equals(town)&&v.status()==Reservation.Status.HELD)for(var r:Resource.values())out.put(r,Math.addExact(out.get(r),v.amounts().get(r)));return Amounts.copy(out);
    }
    public void load() throws Exception {
        writable=false;
        if(!Files.exists(file)){states=Map.of();writable=true;return;}
        var y=new YamlConfiguration();y.load(file.toFile());
        long schema=number(y,"schema");
        if((schema!=1&&schema!=2)||!y.isConfigurationSection("towns"))throw new IOException("Неверная схема resources-data.yml");
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
        Map<UUID,Reservation> held=new HashMap<>();
        if(schema==2){var root=y.getConfigurationSection("reservations");if(root==null)throw new IOException("Отсутствует журнал резервирования");
            for(String id:root.getKeys(false)){var v=root.getConfigurationSection(id);if(v==null)throw new IOException("Неверный резерв");
                var receipt=new Reservation(UUID.fromString(v.getString("town")),amounts(v,"amounts"),Reservation.Status.valueOf(v.getString("status")));
                if(receipt.status()==Reservation.Status.HELD&&!next.containsKey(receipt.town()))throw new IOException("Резерв без города");held.put(UUID.fromString(id),receipt);}}
        validateHeadroom(next,held);states=Map.copyOf(next);reservations=Map.copyOf(held);writable=true;
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
        var copy=new HashMap<>(next);
        for(var receipt:reservations.values())if(receipt.status()==Reservation.Status.HELD)copy.putIfAbsent(receipt.town(),states.get(receipt.town()));
        write(copy,reservations);
    }
    /** Balance and receipt are one atomic file replacement. Retries do not debit twice. */
    public boolean reserve(UUID id,UUID town,Map<Resource,Long> amounts,Map<Resource,Long> keep)throws IOException{
        Objects.requireNonNull(id);var cost=Amounts.copy(amounts);var old=reservations.get(id);
        if(old!=null){if(!old.town().equals(town)||!old.amounts().equals(cost))throw new IllegalArgumentException("ID резерва уже занят другим счётом");return old.status()!=Reservation.Status.RELEASED;}
        var state=states.get(town);if(state==null)throw new IllegalStateException("Город ещё не рассчитан");
        for(var resource:Resource.values())if(cost.get(resource)>Math.max(0,state.balances().get(resource)-keep.getOrDefault(resource,0L)))return false;
        for(var resource:Resource.values())state=state.balance(resource,state.balances().get(resource)-cost.get(resource));
        var next=new HashMap<>(states);next.put(town,state);var journal=new HashMap<>(reservations);journal.put(id,new Reservation(town,cost,Reservation.Status.HELD));write(next,journal);return true;
    }
    public void settle(UUID id,boolean consume)throws IOException{
        var receipt=reservations.get(id);if(receipt==null)throw new IllegalArgumentException("Резерв не найден");
        var expected=consume?Reservation.Status.CONSUMED:Reservation.Status.RELEASED;
        if(receipt.status()==expected)return;if(receipt.status()!=Reservation.Status.HELD)throw new IllegalStateException("Резерв уже завершён иначе");
        var next=new HashMap<>(states);
        if(!consume){var state=next.get(receipt.town());if(state==null)throw new IllegalStateException("Нет города для возврата");
            for(var resource:Resource.values())state=state.balance(resource,Math.addExact(state.balances().get(resource),receipt.amounts().get(resource)));next.put(receipt.town(),state);}
        var journal=new HashMap<>(reservations);journal.put(id,receipt.finish(consume));write(next,journal);
    }
    public void forget(UUID id)throws IOException{
        var receipt=reservations.get(id);if(receipt==null)return;if(receipt.status()==Reservation.Status.HELD)throw new IllegalStateException("Нельзя удалить незавершённый резерв");
        var journal=new HashMap<>(reservations);journal.remove(id);write(states,journal);
    }
    private static void validateHeadroom(Map<UUID,TownState> states,Map<UUID,Reservation> receipts)throws IOException{
        Map<UUID,Map<Resource,Long>> total=new HashMap<>();
        for(var receipt:receipts.values())if(receipt.status()==Reservation.Status.HELD){var sum=total.computeIfAbsent(receipt.town(),id->Amounts.mutable(Map.of()));for(var r:Resource.values())sum.put(r,Math.addExact(sum.get(r),receipt.amounts().get(r)));}
        for(var e:total.entrySet()){var state=states.get(e.getKey());if(state==null)throw new IOException("Резерв без города");for(var r:Resource.values())if(e.getValue().get(r)>Amounts.MAX-state.balances().get(r))throw new IOException("Запас вместе с резервом превышает числовой предел");}
    }
    private void write(Map<UUID,TownState> next,Map<UUID,Reservation> receipts)throws IOException{
        if(!writable)throw new IOException("Запись запрещена до успешной загрузки базы");
        validateHeadroom(next,receipts);next=Map.copyOf(next);receipts=Map.copyOf(receipts);if(next.equals(states)&&receipts.equals(reservations))return;
        var y=new YamlConfiguration();y.set("schema",2);y.createSection("towns");y.createSection("reservations");
        for(var entry:receipts.entrySet()){String k="reservations."+entry.getKey()+".";var receipt=entry.getValue();y.set(k+"town",receipt.town().toString());y.set(k+"status",receipt.status().name());put(y,k+"amounts",receipt.amounts());}
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
            states=next;reservations=receipts;
        }finally{Files.deleteIfExists(tmp);}
    }
    private static void put(YamlConfiguration y,String key,Map<Resource,Long> values){y.createSection(key);values.forEach((r,n)->y.set(key+"."+r.id(),n));}
}
