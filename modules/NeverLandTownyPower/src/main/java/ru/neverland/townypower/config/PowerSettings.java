package ru.neverland.townypower.config;
import org.bukkit.configuration.ConfigurationSection;
import ru.neverland.townypower.model.PowerProfile;
import java.util.*;
import java.math.BigDecimal;
public record PowerSettings(int interval,Map<String,PowerProfile> profiles) {
    public PowerSettings{if(interval<1||interval>60||profiles.isEmpty()||profiles.size()>512)throw new IllegalArgumentException("Интервал: 1..60 сек.; профили: 1..512");profiles=Collections.unmodifiableMap(new LinkedHashMap<>(profiles));}
    public static PowerSettings load(ConfigurationSection c,ConfigurationSection b){Map<String,PowerProfile> profiles=new LinkedHashMap<>();var root=b.getConfigurationSection("buildings");if(root==null)throw new IllegalArgumentException("Отсутствует buildings");
        for(String id:root.getKeys(false)){var p=root.getConfigurationSection(id);if(p==null)throw new IllegalArgumentException("Неверный профиль "+id);Object raw=p.get("enabled",true);if(!(raw instanceof Boolean enabled))throw new IllegalArgumentException("enabled: true/false");
            profiles.put(id,new PowerProfile(id,p.getString("name"),p.getString("icon","REDSTONE"),enabled,Math.toIntExact(number(p.get("priority",50))),numbers(p,"generation"),numbers(p,"demand")));}
        return new PowerSettings(Math.toIntExact(number(c.get("simulation.refresh-seconds",5))),profiles);
    }
    public static long number(Object v){try{return new BigDecimal(String.valueOf(v)).longValueExact();}catch(Exception ex){throw new IllegalArgumentException("Ожидается целое число: "+v);}}
    private static List<Long> numbers(ConfigurationSection p,String key){Object raw=p.get(key);if(!(raw instanceof List<?> list))throw new IllegalArgumentException("Нужен список значений: "+key);return list.stream().map(PowerSettings::number).toList();}
}
