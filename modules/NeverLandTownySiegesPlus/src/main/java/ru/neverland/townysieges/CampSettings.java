package ru.neverland.townysieges;
import org.bukkit.configuration.ConfigurationSection;
public record CampSettings(boolean enabled,int borderDistance,int musterRadius,int minimumArmy,int lifetimeSeconds) {
    public static CampSettings load(ConfigurationSection c){return new CampSettings(SiegeSettings.bool(c,"camp.enabled"),number(c,"camp.border-distance",1,32),number(c,"camp.muster-radius",4,32),number(c,"camp.minimum-army",1,100),number(c,"camp.lifetime-seconds",1800,604800));}
    private static int number(ConfigurationSection c,String key,int min,int max){Object value=c.get(key);if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=Math.rint(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)throw new IllegalArgumentException("Неверная настройка лагеря "+key);return n.intValue();}
}
