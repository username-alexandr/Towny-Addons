package ru.neverland.townyseasons.markets;

import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import ru.neverland.core.SafeYaml;
import ru.neverland.townyseasons.Season;

/** Only new proposals use the current catalog. Accepted quantities and deadlines belong to the goods provider. */
public record SeasonMarketsCatalog(boolean enabled,int fairDays,boolean undated,int reserveHours,int offerHours,
                                   Map<Season,Plan> plans,Map<String,Exchange> exchanges) {
    public record Plan(String title,Map<String,Long> goods){public Plan{text(title,100);goods=quantities(goods);}}
    public record Exchange(String id,String title,Map<String,Long> give,Map<String,Long> receive){
        public Exchange{identifier(id);text(title,100);give=quantities(give);receive=quantities(receive);if(!Collections.disjoint(give.keySet(),receive.keySet()))throw new IllegalArgumentException("Одну продукцию нельзя одновременно отдавать и получать");}
    }
    public SeasonMarketsCatalog{
        plans=Map.copyOf(plans);exchanges=Map.copyOf(exchanges);
        if(fairDays<1||fairDays>365||reserveHours<1||reserveHours>168||offerHours<1||offerHours>24||plans.size()!=4||exchanges.isEmpty()||exchanges.size()>16)throw new IllegalArgumentException("Неверные пределы сезонных запасов или ярмарки");
        for(var row:exchanges.entrySet())if(!row.getKey().equals(row.getValue().id()))throw new IllegalArgumentException("Неверный код предложения");
    }
    public static SeasonMarketsCatalog load(Path file)throws java.io.IOException{
        if(Files.size(file)>1_048_576)throw new java.io.IOException("Настройки ярмарки больше 1 МиБ");var y=SafeYaml.load(file);SafeYaml.keys(y,"enabled","fair","reserve-hours","offer-hours","plans","exchanges");
        var plans=new EnumMap<Season,Plan>(Season.class);var root=section(y,"plans");for(Season season:Season.values()){var p=section(root,season.name().toLowerCase(Locale.ROOT));plans.put(season,new Plan(SafeYaml.text(p,"title"),readQuantities(section(p,"goods"))));}
        var exchanges=new LinkedHashMap<String,Exchange>();root=section(y,"exchanges");for(String id:root.getKeys(false)){var p=section(root,id);exchanges.put(id,new Exchange(id,SafeYaml.text(p,"title"),readQuantities(section(p,"give")),readQuantities(section(p,"receive"))));}
        return new SeasonMarketsCatalog(SafeYaml.booleanValue(y,"enabled",true),SafeYaml.intValue(y,"fair.last-days",2),SafeYaml.booleanValue(y,"fair.allow-undated-source",true),SafeYaml.intValue(y,"reserve-hours",24),SafeYaml.intValue(y,"offer-hours",2),plans,exchanges);
    }
    private static ConfigurationSection section(ConfigurationSection s,String key){var result=SafeYaml.section(s,key);if(result==null)throw new IllegalArgumentException("Нет раздела "+key);return result;}
    private static Map<String,Long> readQuantities(ConfigurationSection s){var result=new LinkedHashMap<String,Long>();for(String key:s.getKeys(false))result.put(key,SafeYaml.integer(s,key));return result;}
    private static Map<String,Long> quantities(Map<String,Long> raw){var values=Map.copyOf(raw);if(values.isEmpty()||values.size()>7)throw new IllegalArgumentException("В смете нужно 1–7 видов продукции");long total=0;for(var entry:values.entrySet()){identifier(entry.getKey());long n=entry.getValue();if(n<1||n>64)throw new IllegalArgumentException("В строке сметы нужно 1–64 партии");total=Math.addExact(total,n);}if(total>128)throw new IllegalArgumentException("Смета превышает 128 партий");return values;}
    private static void identifier(String id){if(id==null||!id.matches("[a-z][a-z0-9_]{0,63}"))throw new IllegalArgumentException("Неверный код продукции");}
    private static void text(String value,int max){if(value==null||value.isBlank()||value.length()>max||value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Неверное название сметы");}
}
