package ru.neverland.townylogistics.config;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;
public record LogisticsSettings(int nodes,int routes,double linkDistance,double routeDistance,int active,int pathBudget,int cargoBudget,int radius,int checkpoint,
        Set<String> hubs,Map<Integer,Level> levels,double roadBonus,double railBonus,int stuck,int retry){
    public record Level(int couriers,int cargo,double speed,int handling){}
    public LogisticsSettings{hubs=Set.copyOf(hubs);levels=Map.copyOf(levels);}
    public Level level(int n){return levels.get(Math.max(1,Math.min(5,n)));}
    public static LogisticsSettings load(YamlConfiguration c){Map<Integer,Level> levels=new HashMap<>();
        for(int i=1;i<=5;i++){String p="levels."+i+".";levels.put(i,new Level(integer(c,p+"couriers",1,16),integer(c,p+"cargo",1,1024),number(c,p+"speed",0.1,2),integer(c,p+"handling-seconds",1,60)));}
        Set<String> hubs=new HashSet<>(c.getStringList("hubs"));if(hubs.isEmpty())throw new IllegalArgumentException("Нет зданий логистики");
        return new LogisticsSettings(integer(c,"limits.nodes-per-town",1,512),integer(c,"limits.routes-per-town",1,128),number(c,"limits.link-distance",4,48),number(c,"limits.route-distance",32,8192),
            integer(c,"limits.active-couriers",1,128),integer(c,"limits.pathfinds-per-tick",1,8),integer(c,"limits.cargo-operations-per-tick",1,4),integer(c,"limits.activation-radius",16,128),integer(c,"limits.checkpoint-seconds",1,60),
            hubs,levels,number(c,"movement.road-speed-bonus",0,0.5),number(c,"movement.rail-speed-bonus",0,0.5),integer(c,"movement.stuck-seconds",10,300),integer(c,"movement.retry-seconds",2,120));
    }
    private static int integer(YamlConfiguration c,String k,int min,int max){double n=number(c,k,min,max);if(n!=Math.rint(n))throw new IllegalArgumentException(k+": целое число");return(int)n;}
    private static double number(YamlConfiguration c,String k,double min,double max){Object raw=c.get(k);if(!(raw instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)throw new IllegalArgumentException(k+": "+min+".."+max);return n.doubleValue();}
}
