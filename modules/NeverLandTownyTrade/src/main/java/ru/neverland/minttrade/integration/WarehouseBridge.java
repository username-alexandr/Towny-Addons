package ru.neverland.minttrade.integration;
import java.util.*;import org.bukkit.inventory.ItemStack;import org.bukkit.plugin.java.JavaPlugin;import ru.neverland.core.ApiServices;
public final class WarehouseBridge {
    public enum Status { SUCCESS, UNAVAILABLE, BUSY, INSUFFICIENT, FULL }
    public record Result(Status status,int amount){}
    public WarehouseBridge(JavaPlugin plugin){}
    private ApiServices.Connection api(){return ApiServices.connect("NeverLandTownyBuilds","ru.neverland.townybuilds.api.WarehouseApi",1,"take","deposit");}
    public boolean available(){return api().ready();}
    public Result take(UUID town,ItemStack sample,int amount){return change("take",town,sample,amount);}
    public Result deposit(UUID town,ItemStack sample,int amount){return change("deposit",town,sample,amount);}
    private Result change(String method,UUID town,ItemStack sample,int amount){var connection=api();if(!connection.ready())return new Result(Status.UNAVAILABLE,0);try{var result=(Map<?,?>)connection.invoke(method,new Class<?>[]{UUID.class,ItemStack.class,int.class},town,sample,amount);return new Result(Status.valueOf((String)result.get("status")),((Number)result.get("amount")).intValue());}catch(ReflectiveOperationException|RuntimeException ex){return new Result(Status.UNAVAILABLE,0);}}
}
