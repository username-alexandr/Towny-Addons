package ru.neverland.townyjobs;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
public record JobsSettings(int interval,int cooldown,int warmup,int activity,int radius,int height,int seats,double cap,List<Integer> levels,Map<Profession,Profile> profiles,Map<String,String> names){
    public record Profile(String name,Material icon,boolean enabled,Set<String> buildings,List<Double> bonus,List<String> description){public Profile{buildings=Set.copyOf(buildings);bonus=List.copyOf(bonus);description=List.copyOf(description);}}
    public JobsSettings{levels=List.copyOf(levels);profiles=Map.copyOf(profiles);names=Map.copyOf(names);}
    public int level(Career c){long minutes=c.profession()==null?0:c.seconds().getOrDefault(c.profession(),0L)/60;int level=1;for(int i=1;i<levels.size();i++)if(minutes>=levels.get(i))level=i+1;return level;}
    public String building(String id){return names.getOrDefault(id,"Городское здание");}
    public static long integer(Object v){if(!(v instanceof Integer||v instanceof Long))throw new IllegalArgumentException("Нужно целое число");return ((Number)v).longValue();}
    private static int number(ConfigurationSection s,String key,int min,int max){long n=integer(s.get(key));if(n<min||n>max)throw new IllegalArgumentException(key+": допустимо "+min+".."+max);return (int)n;}
    public static JobsSettings load(ConfigurationSection y,Set<String> known){
        List<Integer> levels=new ArrayList<>();var raw=y.getList("level-minutes");if(raw==null||raw.size()!=5)throw new IllegalArgumentException("Нужны пять уровней опыта");for(Object v:raw){long n=integer(v);if(n<0||n>1000000||!levels.isEmpty()&&n<=levels.get(levels.size()-1))throw new IllegalArgumentException("Пороги опыта должны возрастать");levels.add((int)n);}if(levels.get(0)!=0)throw new IllegalArgumentException("Первый уровень начинается с нуля");
        Map<Profession,Profile> profiles=new EnumMap<>(Profession.class);Map<String,String> names=new HashMap<>();
        for(Profession p:Profession.values()){var s=y.getConfigurationSection("profiles."+p.id());if(s==null)throw new IllegalArgumentException("Нет профессии "+p.id());var ids=s.getStringList("buildings");if(ids.isEmpty()||new HashSet<>(ids).size()!=ids.size()||!known.containsAll(ids))throw new IllegalArgumentException("Неизвестные или повторяющиеся здания профессии "+p.id());
            String name=s.getString("name","");Material icon=ru.neverland.localization.MaterialNameConfig.matchMaterial(s.getString("icon",""));if(name.isBlank()||name.length()>80||icon==null||!icon.isItem()||icon.isAir()||!s.isBoolean("enabled"))throw new IllegalArgumentException("Неверные поля профессии "+p.id());
            List<Double> bonus=new ArrayList<>();var values=s.getList("bonus-per-level");if(values==null||values.size()!=5)throw new IllegalArgumentException("Нужны пять бонусов");for(Object v:values){if(!(v instanceof Number))throw new IllegalArgumentException("Бонус должен быть числом");double n=((Number)v).doubleValue();if(!Double.isFinite(n)||n<0||n>0.5||!bonus.isEmpty()&&n<bonus.get(bonus.size()-1))throw new IllegalArgumentException("Бонусы: возрастающие числа 0..0.5");bonus.add(n);}
            profiles.put(p,new Profile(name,icon,s.getBoolean("enabled"),Set.copyOf(ids),bonus,s.getStringList("description")));for(String id:ids){String title=y.getString("building-names."+id,"");if(title.isBlank()||title.length()>120)throw new IllegalArgumentException("Укажите русское название здания "+id);names.put(id,title);}
        }
        if(!(y.get("maximum-building-bonus") instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<0||n.doubleValue()>0.5)throw new IllegalArgumentException("Предел бонуса: 0..0.5");
        return new JobsSettings(number(y,"refresh-seconds",1,30),number(y,"change-cooldown-hours",0,168),number(y,"warmup-seconds",5,300),number(y,"activity-seconds",15,300),number(y,"work-radius",0,32),number(y,"height-radius",2,32),number(y,"seats-per-building-level",1,4),n.doubleValue(),levels,profiles,names);
    }
}
