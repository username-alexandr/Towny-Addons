package ru.neverland.mintcontracts.service;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import ru.neverland.mintcontracts.model.*;

public final class ContractCodec {
    private ContractCodec(){}
    public static String item(ItemStack item){ItemStack sample=item.clone();sample.setAmount(1);return Base64.getEncoder().encodeToString(sample.serializeAsBytes());}
    public static ItemStack item(String value){ItemStack item=ItemStack.deserializeBytes(Base64.getDecoder().decode(value));if(item.getType().isAir()||item.getAmount()!=1)throw new IllegalArgumentException("Некорректный предмет");return item;}
    public static void write(ConfigurationSection s,ContractDefinition d){s.set("id",d.id());s.set("name",d.name());s.set("type",d.type().name());s.set("icon",d.icon().name());s.set("description",d.description());s.set("target",d.target());s.set("goal",d.goal());s.set("reward",d.reward());s.set("duration",d.durationSeconds());if(d.deliveryItem()!=null)s.set("item",item(d.deliveryItem()));}
    public static ContractDefinition read(ConfigurationSection s){
        if(s==null||!s.isString("name")||!s.isString("id")||!s.isString("target")||!(s.get("reward") instanceof Number)||!s.isInt("goal")||!(s.get("duration") instanceof Number))throw new IllegalArgumentException("Повреждены условия заказа");
        var type=ContractType.valueOf(s.getString("type",""));return new ContractDefinition(s.getString("id"),s.getString("name"),type,Material.valueOf(s.getString("icon","")),0,s.getStringList("description"),s.getString("target"),type==ContractType.DELIVERY?item(s.getString("item","")):null,s.getInt("goal"),s.getDouble("reward"),s.getLong("duration"));
    }
    public static void write(ConfigurationSection s,WorkArea area){s.set("world",area.world().toString());s.set("min-x",area.minX());s.set("y",area.y());s.set("min-z",area.minZ());s.set("max-x",area.maxX());s.set("max-z",area.maxZ());s.set("required",new ArrayList<>(area.required()));}
    public static WorkArea area(ConfigurationSection s){for(String k:List.of("min-x","min-z","max-x","max-z","y"))if(!s.isInt(k))throw new IllegalArgumentException("Повреждены координаты");if(!s.isList("required"))throw new IllegalArgumentException("Нет точек задания");var keys=s.getStringList("required");if(new HashSet<>(keys).size()!=keys.size())throw new IllegalArgumentException("Точки повторяются");return new WorkArea(UUID.fromString(s.getString("world","")),s.getInt("min-x"),s.getInt("y"),s.getInt("min-z"),s.getInt("max-x"),s.getInt("max-z"),Set.copyOf(keys));}
}
