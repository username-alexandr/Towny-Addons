package ru.neverland.minttrade.contract;
import java.util.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.io.IOException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import static ru.neverland.minttrade.contract.SupplyContract.*;
/** Separate strict, atomic journal; old trade-data.yml remains compatible. Failed writes freeze this journal. */
public final class SupplyRepository implements SupplyProcessor.Store {
    private final Path path;private volatile Map<UUID,SupplyContract> contracts=Map.of();private boolean writable;
    public SupplyRepository(Path path){this.path=path;}
    public boolean writable(){return writable;}
    public Collection<SupplyContract> all(){return contracts.values();}
    @Override public SupplyContract get(UUID id){return contracts.get(id);}
    public void load()throws IOException {
        writable=false;Map<UUID,SupplyContract> next=new LinkedHashMap<>();
        if(Files.exists(path))try {
            var yaml=new YamlConfiguration();yaml.load(path.toFile());
            if(yaml.getInt("schema")!=1)throw new IOException("Неизвестная схема договоров");
            var root=yaml.getConfigurationSection("contracts");if(root==null)throw new IOException("Отсутствует раздел contracts");
            for(String id:root.getKeys(false)){var c=read(UUID.fromString(id),root.getConfigurationSection(id));next.put(c.terms().id(),c);}
        }catch(Exception ex){throw new IOException("contracts-data.yml повреждён; автопоставки остановлены",ex);}
        contracts=Collections.unmodifiableMap(next);writable=true;
    }
    @Override public void put(SupplyContract contract)throws IOException {
        var next=new LinkedHashMap<>(contracts);next.put(contract.terms().id(),contract);save(next);
    }
    public void prune(int closedLimit)throws IOException {
        var closed=contracts.values().stream().filter(c->!c.open()).sorted(Comparator.comparingLong((SupplyContract c)->c.terms().created()).reversed()).toList();
        if(closed.size()<=closedLimit)return;var next=new LinkedHashMap<>(contracts);closed.subList(closedLimit,closed.size()).forEach(c->next.remove(c.terms().id()));save(next);
    }
    private void save(Map<UUID,SupplyContract> next)throws IOException {
        if(!writable)throw new IOException("Журнал договоров остановлен после ошибки; исправьте файл и выполните reload");
        try {
            var yaml=new YamlConfiguration();yaml.set("schema",1);yaml.createSection("contracts");
            for(var c:next.values())write(yaml.createSection("contracts."+c.terms().id()),c);
            atomic(yaml,path);contracts=Collections.unmodifiableMap(new LinkedHashMap<>(next));
        }catch(IOException|RuntimeException ex){writable=false;throw new IOException("Не удалось сохранить договоры; выполнение остановлено",ex);}
    }
    public static void atomic(YamlConfiguration yaml,Path path)throws IOException {
        var target=path.toAbsolutePath();Files.createDirectories(target.getParent());var tmp=Files.createTempFile(target.getParent(),"contracts-",".tmp");
        try{yaml.save(tmp.toFile());try(var channel=FileChannel.open(tmp,StandardOpenOption.WRITE)){channel.force(true);}
            try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(tmp);}
    }
    private static String string(ConfigurationSection s,String key)throws IOException {if(!s.isString(key))throw new IOException("Нет строки "+key);return s.getString(key);}
    private static long number(ConfigurationSection s,String key)throws IOException {Object n=s.get(key);if(!(n instanceof Number v)||v.doubleValue()!=v.longValue())throw new IOException("Нет целого числа "+key);return v.longValue();}
    private static int integer(ConfigurationSection s,String key)throws IOException{return Math.toIntExact(number(s,key));}
    public static SupplyContract read(UUID id,ConfigurationSection s)throws IOException {
        if(s==null)throw new IOException("Нет записи договора");
        var terms=new Terms(id,UUID.fromString(string(s,"seller")),UUID.fromString(string(s,"buyer")),string(s,"item-data"),string(s,"item-name"),integer(s,"amount"),number(s,"cents"),integer(s,"days"),number(s,"created"),number(s,"expires"));
        if(!s.isList("paused")||!s.isList("history"))throw new IOException("Нет истории/паузы договора");
        Set<UUID> paused=new HashSet<>();for(Object o:s.getList("paused"))paused.add(UUID.fromString((String)o));
        List<Receipt> history=new ArrayList<>();for(Object o:s.getList("history")){if(!(o instanceof Map<?,?> row)||!(row.get("at") instanceof Number n))throw new IOException("Повреждена квитанция");history.add(new Receipt(UUID.fromString((String)row.get("id")),n.longValue()));}
        Attempt attempt=null;if(s.contains("attempt")){var a=s.getConfigurationSection("attempt");if(a==null)throw new IOException("Повреждена попытка");attempt=new Attempt(UUID.fromString(string(a,"id")),Phase.valueOf(string(a,"phase")),number(a,"started"));}
        return new SupplyContract(terms,Status.valueOf(string(s,"status")),paused,number(s,"due"),number(s,"check"),number(s,"deliveries"),history,string(s,"note"),attempt);
    }
    public static void write(ConfigurationSection s,SupplyContract c){
        var t=c.terms();s.set("seller",t.seller().toString());s.set("buyer",t.buyer().toString());s.set("item-data",t.itemData());s.set("item-name",t.itemName());s.set("amount",t.amount());s.set("cents",t.cents());s.set("days",t.days());s.set("created",t.created());s.set("expires",t.expires());
        s.set("status",c.status().name());s.set("paused",c.pausedBy().stream().map(UUID::toString).sorted().toList());s.set("due",c.nextDue());s.set("check",c.nextCheck());s.set("deliveries",c.deliveries());s.set("note",c.note());
        s.set("history",c.history().stream().map(r->Map.of("id",r.id().toString(),"at",r.at())).toList());
        if(c.attempt()!=null){s.set("attempt.id",c.attempt().id().toString());s.set("attempt.phase",c.attempt().phase().name());s.set("attempt.started",c.attempt().started());}
    }
}
