package ru.neverland.townymarket;
import java.util.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import static ru.neverland.townymarket.MarketData.*;
public final class MarketRepository implements MarketPayments.Store {
    private final Path file;private volatile Map<UUID,Listing> listings=Map.of();private volatile Map<UUID,Order> orders=Map.of();private volatile List<Demand> demand=List.of();private boolean writable;
    public MarketRepository(Path file){this.file=file;}
    public boolean writable(){return writable;}public Collection<Listing> listings(){return listings.values();}public Collection<Order> orders(){return orders.values();}public List<Demand> demand(){return demand;}
    public Listing listing(UUID id){return listings.get(id);}@Override public Order order(UUID id){return orders.get(id);}
    public void load()throws IOException {
        writable=false;Map<UUID,Listing> ls=new LinkedHashMap<>();Map<UUID,Order> os=new LinkedHashMap<>();List<Demand> ds=new ArrayList<>();
        if(Files.exists(file))try{var y=new YamlConfiguration();y.load(file.toFile());if(y.getInt("schema")!=1)throw new IOException("Неизвестная схема рынка");
            var l=section(y,"listings");for(String id:l.getKeys(false)){UUID uuid=UUID.fromString(id);var s=section(l,id);ls.put(uuid,new Listing(uuid,uuid(s,"town"),text(s,"item"),text(s,"label"),text(s,"product"),Scope.valueOf(text(s,"scope")),bool(s,"auto"),num(s,"base"),integer(s,"amount"),num(s,"created"),ListingState.valueOf(text(s,"state")),text(s,"note")));}
            var o=section(y,"orders");for(String id:o.getKeys(false)){UUID uuid=UUID.fromString(id);var s=section(o,id);var order=new Order(uuid,uuid(s,"lot"),uuid(s,"seller"),uuid(s,"buyer"),uuid(s,"actor"),bool(s,"city"),integer(s,"amount"),num(s,"unit"),num(s,"created"),Phase.valueOf(text(s,"phase")),num(s,"check"),text(s,"note"),bool(s,"finalized"));os.put(uuid,order);}
            var d=section(y,"demand");for(String id:d.getKeys(false)){var s=section(d,id);ds.add(new Demand(UUID.fromString(id),text(s,"scope"),text(s,"product"),uuid(s,"buyer"),num(s,"at"),integer(s,"amount")));}
            validate(ls,os);
        }catch(Exception ex){throw new IOException("market-data.yml повреждён; операции рынка остановлены",ex);}
        listings=Collections.unmodifiableMap(ls);orders=Collections.unmodifiableMap(os);demand=List.copyOf(ds);writable=true;
    }
    private static void validate(Map<UUID,Listing> ls,Map<UUID,Order> os)throws IOException {for(var o:os.values()){var l=ls.get(o.lot());if(l==null||!l.town().equals(o.seller())||o.city()!=(l.scope()==Scope.GLOBAL))throw new IOException("Покупка не соответствует предложению");}}
    public void put(Listing l)throws IOException{var ls=new LinkedHashMap<>(listings);ls.put(l.id(),l);save(ls,orders,demand);}
    @Override public void put(Order o)throws IOException{put(o,System.currentTimeMillis());}
    public void put(Order o,long now)throws IOException {
        var os=new LinkedHashMap<>(orders);Order old=os.put(o.id(),o);List<Demand> ds=new ArrayList<>(demand.stream().filter(d->d.at()>now-DAY).toList());
        if(o.phase()==Phase.COMPLETE&&(old==null||old.phase()!=Phase.COMPLETE)&&ds.stream().noneMatch(d->d.order().equals(o.id()))){var l=listings.get(o.lot());ds.add(new Demand(o.id(),l.scopeKey(),l.product(),o.buyer(),now,o.amount()));}
        ds.sort(Comparator.comparingLong(Demand::at));if(ds.size()>5000)ds=new ArrayList<>(ds.subList(ds.size()-5000,ds.size()));save(listings,os,ds);
    }
    public void prune()throws IOException {
        var done=orders.values().stream().filter(Order::finalized).sorted(Comparator.comparingLong(Order::created).reversed()).toList();if(done.size()<=2000)return;
        var os=new LinkedHashMap<>(orders);done.subList(2000,done.size()).forEach(o->os.remove(o.id()));save(listings,os,demand);
    }
    public void removeListing(UUID id)throws IOException {if(orders.values().stream().anyMatch(o->o.lot().equals(id)))return;var ls=new LinkedHashMap<>(listings);ls.remove(id);save(ls,orders,demand);}
    private void save(Map<UUID,Listing> ls,Map<UUID,Order> os,List<Demand> ds)throws IOException {
        if(!writable)throw new IOException("Рынок остановлен после ошибки записи. Исправьте файл и выполните reload");
        try{validate(ls,os);var y=new YamlConfiguration();y.set("schema",1);y.createSection("listings");y.createSection("orders");y.createSection("demand");
            for(var l:ls.values()){var s=y.createSection("listings."+l.id());s.set("town",l.town().toString());s.set("item",l.item());s.set("label",l.label());s.set("product",l.product());s.set("scope",l.scope().name());s.set("auto",l.auto());s.set("base",l.base());s.set("amount",l.amount());s.set("created",l.created());s.set("state",l.state().name());s.set("note",l.note());}
            for(var o:os.values()){var s=y.createSection("orders."+o.id());s.set("lot",o.lot().toString());s.set("seller",o.seller().toString());s.set("buyer",o.buyer().toString());s.set("actor",o.actor().toString());s.set("city",o.city());s.set("amount",o.amount());s.set("unit",o.unit());s.set("created",o.created());s.set("phase",o.phase().name());s.set("check",o.check());s.set("note",o.note());s.set("finalized",o.finalized());}
            for(var d:ds){var s=y.createSection("demand."+d.order());s.set("scope",d.scope());s.set("product",d.product());s.set("buyer",d.buyer().toString());s.set("at",d.at());s.set("amount",d.amount());}
            atomic(y,file);listings=Collections.unmodifiableMap(new LinkedHashMap<>(ls));orders=Collections.unmodifiableMap(new LinkedHashMap<>(os));demand=List.copyOf(ds);
        }catch(IOException|RuntimeException ex){writable=false;throw new IOException("Не удалось сохранить рынок; операции остановлены",ex);}
    }
    public static void atomic(YamlConfiguration y,Path path)throws IOException {Path target=path.toAbsolutePath();Files.createDirectories(target.getParent());var tmp=Files.createTempFile(target.getParent(),"market-",".tmp");try{y.save(tmp.toFile());try(var ch=FileChannel.open(tmp,StandardOpenOption.WRITE)){ch.force(true);}try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}
    private static ConfigurationSection section(ConfigurationSection s,String key)throws IOException{var v=s.getConfigurationSection(key);if(v==null)throw new IOException("Нет раздела "+key);return v;}
    private static String text(ConfigurationSection s,String key)throws IOException{if(!s.isString(key))throw new IOException("Нет строки "+key);return s.getString(key);}
    private static UUID uuid(ConfigurationSection s,String key)throws IOException{return UUID.fromString(text(s,key));}
    private static long num(ConfigurationSection s,String key)throws IOException{Object v=s.get(key);if(!(v instanceof Number n)||n.doubleValue()!=n.longValue())throw new IOException("Нет целого "+key);return n.longValue();}
    private static int integer(ConfigurationSection s,String key)throws IOException{return Math.toIntExact(num(s,key));}
    private static boolean bool(ConfigurationSection s,String key)throws IOException{if(!s.isBoolean(key))throw new IOException("Нет флага "+key);return s.getBoolean(key);}
}
