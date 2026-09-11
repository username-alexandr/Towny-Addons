package ru.neverland.mintcontracts.integration;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Builds 0.8.9+ keeps the receipt and items in one atomic warehouse save. */
public final class WarehouseBridge {
    public enum Status { SUCCESS, UNAVAILABLE, BUSY, FULL }
    public record Deposit(Status status,int accepted){}
    private final JavaPlugin plugin;
    public WarehouseBridge(JavaPlugin plugin){this.plugin=plugin;}
    private Object api(){
        var source=Bukkit.getPluginManager().getPlugin(plugin.getConfig().getString("warehouse.plugin","NeverLandTownyBuilds"));
        if(source==null||!source.isEnabled())return null;
        try{return Bukkit.getServicesManager().load(Class.forName("ru.neverland.townybuilds.api.MunicipalStorageApi",false,source.getClass().getClassLoader()));}
        catch(ReflectiveOperationException ex){return null;}
    }
    public boolean available(){return api()!=null;}
    private Object call(String name,Class<?>[] types,Object... args)throws ReflectiveOperationException{Object api=api();if(api==null)throw new IllegalStateException("Требуется Builds 0.8.9 или новее");return api.getClass().getMethod(name,types).invoke(api,args);}
    public int capacity(UUID town,ItemStack item){try{return ((Number)call("capacity",new Class<?>[]{UUID.class,ItemStack.class},town,item)).intValue();}catch(Exception ex){return -1;}}
    public String deposit(UUID town,UUID id,ItemStack item,int amount)throws Exception{return (String)call("deposit",new Class<?>[]{UUID.class,UUID.class,ItemStack.class,int.class},town,id,item,amount);}
    public void acknowledge(UUID town,UUID id)throws Exception{call("acknowledge",new Class<?>[]{UUID.class,UUID.class},town,id);}
}
