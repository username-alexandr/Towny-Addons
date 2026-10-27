package ru.neverland.townysieges;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public record TntSettings(boolean enabled, double radius, double contact, double damage,
        double falloff, int warmupSeconds, int durationSeconds, int cooldownSeconds,
        int defendersOnline, int repairSeconds, double defaultHp, double doorHp,
        double ironDoorHp, double glassHp, double woodHp, double obsidianHp,
        Map<String,Double> materials) {
    public TntSettings { materials=Map.copyOf(materials); }
    public static TntSettings load(ConfigurationSection y) {
        var overrides=new HashMap<String,Double>();
        var section=y.getConfigurationSection("tnt.hitpoints.materials");
        if(section!=null)for(String key:section.getKeys(false)) {
            Material m=Material.matchMaterial(key);
            if(m==null||!m.isBlock()||m.isAir())throw new IllegalArgumentException("Неизвестный блок tnt.hitpoints.materials."+key);
            overrides.put(m.name(),number(section,key,1,1_000_000));
        }
        double radius=number(y,"tnt.radius",1,8),contact=number(y,"tnt.contact-radius",0,2);
        if(contact>=radius)throw new IllegalArgumentException("tnt.contact-radius должен быть меньше tnt.radius");
        return new TntSettings(SiegeSettings.bool(y,"tnt.enabled"),radius,contact,number(y,"tnt.contact-damage",1,100000),
                number(y,"tnt.falloff-power",.1,8),integer(y,"tnt.raid.warmup-seconds",0,3600),
                integer(y,"tnt.raid.duration-seconds",30,7200),integer(y,"tnt.raid.cooldown-seconds",0,604800),
                integer(y,"tnt.raid.minimum-defenders-online",0,1000),integer(y,"tnt.repair-after-seconds",60,604800),
                number(y,"tnt.hitpoints.default",1,1_000_000),number(y,"tnt.hitpoints.wooden-door",1,1_000_000),
                number(y,"tnt.hitpoints.iron-door",1,1_000_000),number(y,"tnt.hitpoints.glass",1,1_000_000),
                number(y,"tnt.hitpoints.wood",1,1_000_000),number(y,"tnt.hitpoints.obsidian",1,1_000_000),overrides);
    }
    private static double number(ConfigurationSection c,String key,double min,double max) {
        Object raw=c.get(key);if(!(raw instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)
            throw new IllegalArgumentException("Некорректная настройка "+key+": "+min+".."+max);
        return n.doubleValue();
    }
    private static int integer(ConfigurationSection c,String key,int min,int max) {
        double value=number(c,key,min,max);if(value!=Math.rint(value))throw new IllegalArgumentException("Нужно целое число "+key);
        return (int)value;
    }
    public double hp(String material) {
        Double custom=materials.get(material);if(custom!=null)return custom;
        if(material.equals("IRON_DOOR")||material.equals("IRON_TRAPDOOR"))return ironDoorHp;
        if(material.endsWith("_DOOR")||material.endsWith("_TRAPDOOR")||material.endsWith("_FENCE_GATE"))return doorHp;
        if(material.contains("GLASS"))return glassHp;
        if(material.equals("OBSIDIAN")||material.equals("CRYING_OBSIDIAN"))return obsidianHp;
        if(material.endsWith("_PLANKS")||material.endsWith("_LOG")||material.endsWith("_WOOD")||material.endsWith("_STEM")||material.endsWith("_HYPHAE"))return woodHp;
        return defaultHp;
    }
}
