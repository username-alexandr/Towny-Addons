package ru.neverland.townysieges;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import static ru.neverland.townysieges.SiegeRules.*;

public record SiegeSettings(Map<Fort, Double> radii, double groundHeight, double towerHeight,
        double wallReduction, double towerReduction, double defenseCap, double moatRate, double moatCap, boolean battleOnly) {
    public SiegeSettings { radii=Map.copyOf(radii); }
    public static SiegeSettings load(ConfigurationSection y) {
        var radii=new EnumMap<Fort,Double>(Fort.class);
        for (Fort f:Fort.values()) radii.put(f,number(y,"radius."+f.project,0,128));
        return new SiegeSettings(radii,number(y,"ground-height",4,64),number(y,"tower-height",16,320),
                number(y,"damage.wall-per-level",0,.2),number(y,"damage.tower-per-level",0,.2),number(y,"damage.maximum-reduction",0,.75),
                number(y,"moat.slow-per-level",0,.5),number(y,"moat.maximum-slow",0,.75),bool(y,"battle-sessions-only"));
    }
    static double number(ConfigurationSection y,String key,double min,double max) {
        Object value=y.get(key); if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue()<min || n.doubleValue()>max) throw new IllegalArgumentException("Неверная настройка "+key);
        return n.doubleValue();
    }
    static boolean bool(ConfigurationSection y,String key) { if (!(y.get(key) instanceof Boolean value)) throw new IllegalArgumentException("Ожидался boolean: "+key); return value; }
}
