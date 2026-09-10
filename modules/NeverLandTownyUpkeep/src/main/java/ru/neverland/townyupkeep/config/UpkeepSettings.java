package ru.neverland.townyupkeep.config;
import org.bukkit.configuration.ConfigurationSection;
import ru.neverland.townyupkeep.model.*;
import java.math.*;
import java.util.*;
public record UpkeepSettings(int period,int grace,int retry,int budget,BigDecimal perPlot,BigDecimal perResident,BigDecimal perLevel,BigDecimal maximum,Map<String,Profile> profiles){
    public UpkeepSettings {
        if(period<60||period>604800||grace<0||grace>604800||retry<10||retry>3600||budget<1||budget>10)throw new IllegalArgumentException("Период: 60..604800 сек.; льгота: 0..604800; повтор: 10..3600; счетов/сек.: 1..10");
        for(var n:List.of(perPlot,perResident,perLevel))if(n.signum()<0||n.compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("Коэффициенты размера: 0..1");
        if(maximum.compareTo(BigDecimal.ONE)<0||maximum.compareTo(BigDecimal.valueOf(20))>0||profiles.isEmpty()||profiles.size()>512)throw new IllegalArgumentException("Множитель: 1..20; профили: 1..512");
        profiles=Collections.unmodifiableMap(new LinkedHashMap<>(profiles));for(var p:profiles.values())p.cost().multiply(5,maximum);
    }
    public BigDecimal factor(int plots,int residents,int levels){if(plots<0||residents<0||levels<0)throw new IllegalArgumentException("Размер города отрицательный");return BigDecimal.ONE.add(perPlot.multiply(BigDecimal.valueOf(plots))).add(perResident.multiply(BigDecimal.valueOf(residents))).add(perLevel.multiply(BigDecimal.valueOf(levels))).min(maximum);}
    public static UpkeepSettings load(ConfigurationSection c,ConfigurationSection b){
        var root=b.getConfigurationSection("buildings");if(root==null)throw new IllegalArgumentException("Отсутствуют buildings");Map<String,Profile> profiles=new LinkedHashMap<>();
        for(String id:root.getKeys(false)){var p=root.getConfigurationSection(id);if(p==null)throw new IllegalArgumentException("Неверный профиль "+id);Object enabled=p.get("enabled",true);if(!(enabled instanceof Boolean))throw new IllegalArgumentException("enabled: true/false");
            Map<String,Long> resources=resources(p,"resources");profiles.put(id,new Profile(id,p.getString("name"),p.getString("icon","BRICKS"),(Boolean)enabled,integer(p,"priority",50),new Cost(Cost.parse(p.get("money",0),2),resources)));}
        return new UpkeepSettings(integer(c,"billing.period-seconds",3600),integer(c,"billing.grace-seconds",3600),integer(c,"billing.retry-seconds",300),integer(c,"billing.invoices-per-second",2),decimal(c,"size.per-plot","0.005"),decimal(c,"size.per-resident","0.002"),decimal(c,"size.per-building-level","0.003"),decimal(c,"size.maximum","5"),profiles);
    }
    public static Map<String,Long> resources(ConfigurationSection p,String key){var section=p.getConfigurationSection(key);if(section==null&&p.contains(key))throw new IllegalArgumentException("Неверный список ресурсов");Map<String,Long> out=new HashMap<>();if(section!=null)for(String id:section.getKeys(false))out.put(id,Cost.parse(section.get(id),3));return out;}
    private static int integer(ConfigurationSection p,String key,int fallback){try{return new BigDecimal(String.valueOf(p.get(key,fallback))).intValueExact();}catch(Exception ex){throw new IllegalArgumentException("Нужно целое число: "+key);}}
    private static BigDecimal decimal(ConfigurationSection p,String key,String fallback){try{return new BigDecimal(String.valueOf(p.get(key,fallback)));}catch(Exception ex){throw new IllegalArgumentException("Неверный коэффициент: "+key);}}
}
