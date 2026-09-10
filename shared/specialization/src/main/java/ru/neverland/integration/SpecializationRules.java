package ru.neverland.integration;
import java.util.*;
import java.math.BigDecimal;
/** Known unique projects remain locked when the specialization plugin is absent or unavailable. */
public final class SpecializationRules {
    public static final Map<String,String> PROJECTS=Map.of("trade_exchange","trade","citadel","fortress","seed_vault","agricultural","industrial_works","industrial","academy_of_sciences","scientific","admiralty","port","pilgrimage_center","religious");
    public static final Set<String> EFFECTS=Set.of("production","trade_speed","mob_defense","research_speed","courier_speed","trade_delay","happiness");
    private SpecializationRules(){}
    public static String required(String project){return project==null?"":PROJECTS.getOrDefault(project,"");}
    public static double bound(String effect,double value){return Double.isFinite(value)?Math.max(0,Math.min(effect.equals("happiness")?20:.5,value)):0;}
    public static double production(double current,double bonus){double base=Double.isFinite(current)?Math.max(1,Math.min(3,current)):1;return BigDecimal.valueOf(base).multiply(BigDecimal.ONE.add(BigDecimal.valueOf(bound("production",bonus)))).min(BigDecimal.valueOf(3)).doubleValue();}
    public static double hostileDamage(double damage,double bonus,boolean residentAtHome,boolean hostile){return damage*(1-(residentAtHome&&hostile?bound("mob_defense",bonus):0));}
}
