package ru.neverland.townylogistics.integration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.neverland.townylogistics.model.Position;
import java.util.*;
import java.lang.reflect.*;
import java.io.IOException;
public final class BuildsStorage {
    public record Depot(String id,String name,String icon,UUID world,int minX,int minZ,int maxX,int maxZ,int y,int level,int slots){
        public boolean contains(Position p){return world.equals(p.world())&&p.x()>=minX-6&&p.x()<=maxX+7&&p.z()>=minZ-6&&p.z()<=maxZ+7&&Math.abs(p.y()-y)<=16;}
    }
    public record Shipment(UUID id,String route,String source,String target,ItemStack[] cargo,String status){public boolean transit(){return status.equals("TRANSIT");}}
    private final Class<?> api;private final Object provider;
    public BuildsStorage()throws Exception{
        var plugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");if(plugin==null||!plugin.isEnabled())throw new IllegalStateException("Требуется Builds 0.8.0 или новее");
        api=Class.forName("ru.neverland.townybuilds.api.BuildingStorageApi",true,plugin.getClass().getClassLoader());provider=Bukkit.getServicesManager().load(api);
        if(provider==null)throw new IllegalStateException("API складов недоступен");
    }
    private Object call(String name,Class<?>[] types,Object... args)throws IOException{
        if(!Bukkit.getPluginManager().isPluginEnabled("NeverLandTownyBuilds"))throw new IOException("Склады отключены");
        try{return api.getMethod(name,types).invoke(provider,args);}catch(InvocationTargetException ex){if(ex.getCause() instanceof IOException io)throw io;throw new IllegalStateException(ex.getCause());}
        catch(ReflectiveOperationException ex){throw new IllegalStateException("Несовместимый API складов",ex);}
    }
    private static Object field(Object value,String name){try{return value.getClass().getMethod(name).invoke(value);}catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}}
    private static int number(Object value,String name){return ((Number)field(value,name)).intValue();}
    public Map<String,Depot> depots(UUID town)throws IOException{Map<String,Depot> result=new TreeMap<>();for(var entry:((Map<?,?>)call("depots",new Class[]{UUID.class},town)).entrySet()){
        var v=entry.getValue();String id=entry.getKey().toString();result.put(id,new Depot(id,(String)field(v,"name"),(String)field(v,"icon"),(UUID)field(v,"world"),number(v,"minX"),number(v,"minZ"),number(v,"maxX"),number(v,"maxZ"),number(v,"y"),number(v,"level"),number(v,"slots")));}return Map.copyOf(result);}
    private Shipment shipment(Object v){return new Shipment((UUID)field(v,"id"),(String)field(v,"route"),(String)field(v,"source"),(String)field(v,"target"),(ItemStack[])field(v,"cargo"),(String)field(v,"status"));}
    public Map<UUID,Shipment> shipments(UUID town)throws IOException{Map<UUID,Shipment> result=new HashMap<>();for(var v:((Map<?,?>)call("shipments",new Class[]{UUID.class},town)).values()){var s=shipment(v);result.put(s.id(),s);}return Map.copyOf(result);}
    @SuppressWarnings("unchecked")public Set<UUID> shipmentTowns()throws IOException{return (Set<UUID>)call("shipmentTowns",new Class[]{});}
    public Shipment pickup(UUID town,UUID id,String route,String from,String to,String filter,int limit,int keep)throws IOException{
        Object v=call("pickup",new Class[]{UUID.class,UUID.class,String.class,String.class,String.class,ItemStack.class,int.class,int.class},town,id,route,from,to,decode(filter),limit,keep);return v==null?null:shipment(v);}
    public boolean unload(UUID town,UUID id,boolean returned)throws IOException{return (boolean)call("unload",new Class[]{UUID.class,UUID.class,boolean.class},town,id,returned);}
    public void acknowledge(UUID town,UUID id)throws IOException{call("acknowledge",new Class[]{UUID.class,UUID.class},town,id);}
    public ItemStack[] stock(UUID town,String id)throws IOException{return (ItemStack[])call("stock",new Class[]{UUID.class,String.class},town,id);}
    public void open(Player player,String id)throws IOException{call("openStorage",new Class[]{Player.class,String.class},player,id);}
    public static String encode(ItemStack item){var value=item.clone();value.setAmount(1);return Base64.getEncoder().encodeToString(value.serializeAsBytes());}
    public static ItemStack decode(String value){return value.isBlank()?null:ItemStack.deserializeBytes(Base64.getDecoder().decode(value));}
}
