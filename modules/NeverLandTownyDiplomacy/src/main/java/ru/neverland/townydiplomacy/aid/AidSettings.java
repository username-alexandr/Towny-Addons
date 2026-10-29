package ru.neverland.townydiplomacy.aid;
import org.bukkit.configuration.ConfigurationSection;
public record AidSettings(boolean enabled,int days,int minutes,int damage){
    public AidSettings{if(days<1||days>365||minutes<1||minutes>120||damage<1||damage>100)throw new IllegalArgumentException("Взаимопомощь: дни 1–365, минуты 1–120, урон 1–100");}
    public static AidSettings load(ConfigurationSection c){Object enabled=c.get("mutual-aid.enabled",true);if(!(enabled instanceof Boolean))throw new IllegalArgumentException("mutual-aid.enabled: true или false");return new AidSettings((Boolean)enabled,integer(c,"default-days",30),integer(c,"response-minutes",30),integer(c,"required-damage",20));}
    private static int integer(ConfigurationSection c,String name,int fallback){Object v=c.get("mutual-aid."+name,fallback);if(!(v instanceof Integer n))throw new IllegalArgumentException("mutual-aid."+name+": нужно целое число");return n;}
}
