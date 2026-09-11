package ru.neverland.townybuilds.shop;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;
public final class ShopJournal implements PurchaseSaga.Store<ShopOrder> {
    private final Path file;private Map<UUID,ShopOrder> orders=Map.of();private boolean writable;
    public ShopJournal(Path file){this.file=file;}
    public boolean writable(){return writable&&AtomicFiles.writable(file);}
    public Collection<ShopOrder> all(){return orders.values();}
    public ShopOrder order(UUID id){return orders.get(id);}
    public void load()throws IOException{
        writable=false;Map<UUID,ShopOrder> next=new LinkedHashMap<>();
        if(Files.exists(file))try{var y=new YamlConfiguration();y.load(file.toFile());if(number(y,"schema")!=1||!y.isConfigurationSection("orders"))throw new IOException("Неверная схема покупок");var root=y.getConfigurationSection("orders");
            for(String key:root.getKeys(false)){var s=root.getConfigurationSection(key);if(s==null||!s.isBoolean("finalized"))throw new IOException("Повреждена запись покупки");UUID id=UUID.fromString(key);next.put(id,new ShopOrder(id,UUID.fromString(text(s,"seller")),UUID.fromString(text(s,"buyer")),text(s,"material"),Math.toIntExact(number(s,"amount")),number(s,"unit"),number(s,"created"),text(s,"phase"),number(s,"check"),text(s,"note"),s.getBoolean("finalized")));}
        }catch(Exception ex){throw new IOException("shop-orders.yml повреждён; покупки остановлены",ex);}
        orders=Map.copyOf(next);AtomicFiles.loaded(file);writable=true;
    }
    public void put(ShopOrder order)throws IOException{
        if(!writable())throw new IOException("Покупки остановлены после ошибки записи");var next=new LinkedHashMap<>(orders);next.put(order.id(),order);
        var completed=next.values().stream().filter(ShopOrder::finalized).sorted(Comparator.comparingLong(ShopOrder::created).reversed()).toList();if(completed.size()>2000)completed.subList(2000,completed.size()).forEach(o->next.remove(o.id()));
        try{AtomicFiles.write(file,()->{var y=new YamlConfiguration();y.set("schema",1);y.createSection("orders");for(var o:next.values()){var s=y.createSection("orders."+o.id());s.set("seller",o.seller().toString());s.set("buyer",o.buyer().toString());s.set("material",o.material());s.set("amount",o.amount());s.set("unit",o.unit());s.set("created",o.created());s.set("phase",o.paymentStep());s.set("check",o.check());s.set("note",o.note());s.set("finalized",o.finalized());}return y.saveToString();});orders=Map.copyOf(next);}catch(IOException ex){writable=false;throw ex;}
    }
    private static String text(ConfigurationSection s,String key)throws IOException{if(!s.isString(key))throw new IOException("Нет строки "+key);return s.getString(key);}
    private static long number(ConfigurationSection s,String key)throws IOException{Object v=s.get(key);if(!(v instanceof Integer||v instanceof Long))throw new IOException("Нужно целое "+key);return ((Number)v).longValue();}
}
