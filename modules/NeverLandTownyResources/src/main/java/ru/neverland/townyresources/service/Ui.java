package ru.neverland.townyresources.service;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import ru.neverland.townyresources.config.ResourcesSettings;
import ru.neverland.townyresources.model.*;
import java.util.*;
public final class Ui {
    private Ui() {}
    public static String color(String s){return ChatColor.translateAlternateColorCodes('&',s);}
    public static void tell(CommandSender sender,String text){sender.sendMessage(color("&8[&aNeverLand &8• &fРесурсы&8] &r"+text));}
    public static Material icon(String id){Material m=Material.matchMaterial(id);return m==null||!m.isItem()||m.isAir()?Material.BRICKS:m;}
    public static String amount(long value){return Amounts.display(value);}
    public static String signed(long value){return (value>0?"+":"")+amount(value);}
    public static List<String> amounts(String prefix,Map<Resource,Long> values,ResourcesSettings settings){var out=new ArrayList<String>();for(var r:Resource.values())if(values.getOrDefault(r,0L)>0)out.add(prefix+settings.display().get(r).name()+": "+amount(values.get(r)));if(out.isEmpty())out.add(prefix+"нет");return out;}
}
