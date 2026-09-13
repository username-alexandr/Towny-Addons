package ru.neverland.townycrime;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
public record CrimeSettings(long cycle, double change, double unhappiness, double unguarded,
        double levelStrength, double workerStrength, double guardReduction, int shopLoss,
        double incidentMinimum, double chance, long cooldown, long duration, int extortionLoss,
        int theftShare, long theftCap, List<String> resources) {
    public static final Set<String> RESOURCE_IDS=Set.of("wood","stone","metal","food","water","materials","knowledge","influence");
    public CrimeSettings {
        resources=List.copyOf(resources);
        if(cycle<1000||cycle>86400000||cooldown<cycle||cooldown>2592000000L||duration<1000||duration>cooldown
            ||!finite(change,0.01,100)||!finite(unhappiness,0,2)||!finite(unguarded,0,100)
            ||!finite(levelStrength,0,100)||!finite(workerStrength,0,100)||!finite(guardReduction,0,2)
            ||shopLoss<0||shopLoss>5000||extortionLoss<0||shopLoss+extortionLoss>5000
            ||!finite(incidentMinimum,0,100)||!finite(chance,0,1)||theftShare<1||theftShare>1000
            ||theftCap<1||theftCap>1000000000||resources.isEmpty()||!RESOURCE_IDS.containsAll(resources)
            ||new HashSet<>(resources).size()!=resources.size())throw new IllegalArgumentException("Неверные настройки преступности");
    }
    static boolean finite(double n,double min,double max){return Double.isFinite(n)&&n>=min&&n<=max;}
    static double n(ConfigurationSection c,String key){Object v=c.get(key);if(!(v instanceof Number a)||!Double.isFinite(a.doubleValue()))throw new IllegalArgumentException("Нужно число: "+key);return a.doubleValue();}
    static long integer(ConfigurationSection c,String key){Object v=c.get(key);if(!(v instanceof Integer||v instanceof Long))throw new IllegalArgumentException("Нужно целое: "+key);return ((Number)v).longValue();}
    public static CrimeSettings load(ConfigurationSection c){
        Object raw=c.get("incidents.resources");if(!(raw instanceof List<?> list)||list.stream().anyMatch(x->!(x instanceof String)))throw new IllegalArgumentException("Неверный список ресурсов");
        return new CrimeSettings(Math.multiplyExact(integer(c,"cycle-seconds"),1000),n(c,"maximum-change"),n(c,"unhappiness-weight"),n(c,"unguarded-pressure"),n(c,"guard-level-strength"),n(c,"guard-worker-strength"),n(c,"guard-reduction"),Math.toIntExact(integer(c,"maximum-shop-loss-bps")),n(c,"incidents.minimum-crime"),n(c,"incidents.chance-per-cycle"),Math.multiplyExact(integer(c,"incidents.cooldown-seconds"),1000),Math.multiplyExact(integer(c,"incidents.duration-seconds"),1000),Math.toIntExact(integer(c,"incidents.extortion-loss-bps")),Math.toIntExact(integer(c,"incidents.theft-share-bps")),integer(c,"incidents.theft-cap"),list.stream().map(String.class::cast).toList());
    }
}
