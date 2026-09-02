package ru.neverland.townychronicles.integration;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townychronicles.model.ChronicleCategory;
import ru.neverland.townychronicles.service.ChronicleService;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DynamicWarBridge {
    private static final List<String>METHODS=List.of("getTown","getAttackingTown","getDefendingTown","getAttackerTown","getDefenderTown","getSiege","getAttacker","getDefender","getAttackers","getDefenders");
    private final JavaPlugin plugin;private final ChronicleService chronicles;private final List<Listener>listeners=new java.util.ArrayList<>();private final Map<String,Long>dedupe=new HashMap<>();
    public DynamicWarBridge(JavaPlugin plugin,ChronicleService chronicles){this.plugin=plugin;this.chronicles=chronicles;}
    @SuppressWarnings("unchecked")public int register(){if(!plugin.getConfig().getBoolean("wars.dynamic-integration",true))return 0;Plugin source=Bukkit.getPluginManager().getPlugin("SiegeWar");if(source==null)source=Bukkit.getPluginManager().getPlugin("FlagWar");if(source==null)return 0;int count=0;for(String path:List.of("wars.start-event-classes","wars.end-event-classes")){boolean start=path.contains("start");for(String className:plugin.getConfig().getStringList(path))try{Class<?>raw=Class.forName(className,false,source.getClass().getClassLoader());if(!Event.class.isAssignableFrom(raw))continue;Listener listener=new Listener(){};EventExecutor executor=(ignored,event)->handle(event,start);Bukkit.getPluginManager().registerEvent((Class<? extends Event>)raw,listener,EventPriority.MONITOR,executor,plugin,true);listeners.add(listener);count++;}catch(ClassNotFoundException ignored){}}return count;}
    private void handle(Event event,boolean start){Set<Town>towns=new LinkedHashSet<>();collect(event,towns,0);long now=System.currentTimeMillis();for(Town town:towns){String key=town.getUUID()+":"+start;if(now-dedupe.getOrDefault(key,0L)<5000)continue;dedupe.put(key,now);String title=start?"Началась война":"Война завершена";boolean announce=plugin.getConfig().getBoolean(start?"wars.announce-start":"wars.announce-end",true);chronicles.record(town.getUUID(),town.getName(),ChronicleCategory.WAR,now,title,List.of("Военное событие: "+event.getEventName()),"",event.getClass().getSimpleName(),announce);}}
    private void collect(Object value,Set<Town>towns,int depth){if(value==null||depth>2)return;if(value instanceof Town town){towns.add(town);return;}if(value instanceof Resident resident){Town town=resident.getTownOrNull();if(town!=null)towns.add(town);return;}if(value instanceof Collection<?>values){for(Object item:values)collect(item,towns,depth+1);return;}for(String name:METHODS)try{Method method=value.getClass().getMethod(name);if(method.getParameterCount()==0)collect(method.invoke(value),towns,depth+1);}catch(ReflectiveOperationException|RuntimeException ignored){}}
}
