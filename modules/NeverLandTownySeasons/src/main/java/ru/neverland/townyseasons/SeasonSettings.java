package ru.neverland.townyseasons;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import ru.neverland.core.SafeYaml;

public record SeasonSettings(Mode mode, int days, long dayMillis, Season first,
                             Set<String> worlds, boolean announce, Map<Season, Effects> effects) {
    public enum Mode { CUSTOM, MINECRAFT, REALISTIC_SEASONS }
    public record Effects(Map<String, Double> production, Map<String, Double> events) {
        public Effects { production = Map.copyOf(production); events = Map.copyOf(events); }
    }
    public SeasonSettings { worlds = Set.copyOf(worlds); effects = Map.copyOf(effects); }
    public static SeasonSettings load(ConfigurationSection y) {
        Mode mode = Mode.valueOf(SafeYaml.stringValue(y,"calendar.mode").toUpperCase(Locale.ROOT));
        int days = SafeYaml.intValue(y,"calendar.days-per-season");
        long minutes = SafeYaml.longValue(y,"calendar.day-minutes");
        if (days < 1 || days > 365 || minutes < 1 || minutes > 10080)
            throw new IllegalArgumentException("Дней в сезоне: 1..365; минут в сутках: 1..10080");
        var effects = new EnumMap<Season, Effects>(Season.class);
        for (Season season : Season.values()) {
            var s = SafeYaml.section(y,"seasons."+season.name().toLowerCase(Locale.ROOT));
            if (s == null) throw new IllegalArgumentException("Нет настроек сезона "+season);
            effects.put(season,new Effects(numbers(s,"production",.1,2),numbers(s,"event-weights",0,10)));
        }
        return new SeasonSettings(mode,days,minutes*60000,Season.parse(SafeYaml.stringValue(y,"calendar.first-season")),
                new HashSet<>(SafeYaml.strings(y,"worlds")),SafeYaml.booleanValue(y,"announce"),effects);
    }
    private static Map<String,Double> numbers(ConfigurationSection y,String key,double min,double max) {
        var section=SafeYaml.section(y,key);var out=new HashMap<String,Double>();
        if(section!=null)for(String id:section.getKeys(false)) {
            if(!id.matches("[a-z][a-z0-9_]{0,63}"))throw new IllegalArgumentException("Неверный ключ "+id);
            double n=SafeYaml.doubleValue(section,id);
            if(n<min||n>max)throw new IllegalArgumentException(key+"."+id+": "+min+".."+max);
            if(key.equals("event-weights")&&!Set.of("epidemic","drought","flood","fire","raid","festival").contains(id))
                throw new IllegalArgumentException("Неизвестный тип события "+id);
            out.put(id,n);
        }
        return Map.copyOf(out);
    }
}
