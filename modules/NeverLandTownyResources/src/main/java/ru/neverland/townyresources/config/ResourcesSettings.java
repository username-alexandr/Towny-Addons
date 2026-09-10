package ru.neverland.townyresources.config;
import org.bukkit.configuration.ConfigurationSection;
import ru.neverland.townyresources.model.*;
import java.util.*;
public record ResourcesSettings(int interval,boolean populationLinked,long foodPerPerson,long waterPerPerson,
                                Map<Resource,Long> initial,Map<Resource,Long> baseCapacity,Map<Resource,Long> baseProduction,
                                Map<Resource,Display> display,Map<String,BuildingProfile> buildings) {
    public record Display(String name,String icon) { public Display { if(name==null||name.isBlank()||icon==null||icon.isBlank())throw new IllegalArgumentException("Название или иконка ресурса не заданы"); } }
    public ResourcesSettings {
        if(interval<10||interval>3600)throw new IllegalArgumentException("interval-seconds: 10..3600");
        Amounts.valid(foodPerPerson);Amounts.valid(waterPerPerson);initial=Amounts.copy(initial);baseCapacity=Amounts.copy(baseCapacity);baseProduction=Amounts.copy(baseProduction);
        display=Map.copyOf(display);buildings=Collections.unmodifiableMap(new LinkedHashMap<>(buildings));
        if(display.size()!=8||buildings.isEmpty()||buildings.size()>512)throw new IllegalArgumentException("Нужны восемь ресурсов и 1..512 профилей зданий");
        for(var r:Resource.values())if(initial.get(r)>baseCapacity.get(r))throw new IllegalArgumentException("Начальный запас больше базовой вместимости: "+r.title);
    }
    public static ResourcesSettings load(ConfigurationSection c,ConfigurationSection projects) {
        Map<Resource,Display> display=new EnumMap<>(Resource.class);
        for(var r:Resource.values())display.put(r,new Display(c.getString("resources."+r.id()+".name",r.title),c.getString("resources."+r.id()+".icon",r.icon)));
        Map<String,BuildingProfile> profiles=new LinkedHashMap<>();var root=projects.getConfigurationSection("buildings");if(root==null)throw new IllegalArgumentException("Нет buildings");
        for(String id:root.getKeys(false)) { var p=root.getConfigurationSection(id);if(p==null)throw new IllegalArgumentException("Неверный профиль "+id);
            profiles.put(id,new BuildingProfile(id,p.getString("name"),p.getString("icon","BRICKS"),bool(p,"enabled",true),integer(p,"minimum-level",1),integer(p,"maximum-level",5),integer(p,"priority",50),amounts(p,"produces"),amounts(p,"consumes"),amounts(p,"capacity")));
        }
        return new ResourcesSettings(integer(c,"simulation.interval-seconds",60),bool(c,"population.enabled",true),Amounts.parse(c.get("population.food-per-person",0.05)),Amounts.parse(c.get("population.water-per-person",0.1)),
            amounts(c,"initial"),amounts(c,"capacity"),amounts(c,"base-production"),display,profiles);
    }
    private static boolean bool(ConfigurationSection c,String key,boolean fallback) {
        Object value=c.get(key,fallback);if(!(value instanceof Boolean b))throw new IllegalArgumentException("Требуется true или false: "+key);return b;
    }
    private static int integer(ConfigurationSection c,String key,int fallback) {
        Object raw=c.get(key,fallback);try { return new java.math.BigDecimal(String.valueOf(raw)).intValueExact(); }catch(Exception ex){throw new IllegalArgumentException("Требуется целое число: "+key);}
    }
    public static Map<Resource,Long> amounts(ConfigurationSection c,String key) {
        Object raw=c.get(key);if(raw!=null&&!(raw instanceof ConfigurationSection))throw new IllegalArgumentException("Ожидается список ресурсов: "+key);
        var p=c.getConfigurationSection(key);var out=new EnumMap<Resource,Long>(Resource.class);
        if(p!=null)for(String id:p.getKeys(false)){Resource r=Resource.parse(id);if(out.put(r,Amounts.parse(p.get(id)))!=null)throw new IllegalArgumentException("Повтор ресурса "+id);}
        return Amounts.copy(out);
    }
}
