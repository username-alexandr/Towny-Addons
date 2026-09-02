package ru.neverland.townychronicles.service;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townychronicles.integration.BuildBridge;
import ru.neverland.townychronicles.integration.TownyHook;
import ru.neverland.townychronicles.model.ChronicleCategory;
import ru.neverland.townychronicles.model.TownChronicleState;
import ru.neverland.townychronicles.util.TimeUtil;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ChronicleTracker {
    private final JavaPlugin plugin;private final TownyHook towny;private final BuildBridge builds;private final ChronicleRepository repository;private final ChronicleService chronicles;private final DecimalFormat money=new DecimalFormat("#,##0.##");private BukkitTask task;
    public ChronicleTracker(JavaPlugin plugin,TownyHook towny,BuildBridge builds,ChronicleRepository repository,ChronicleService chronicles){this.plugin=plugin;this.towny=towny;this.builds=builds;this.repository=repository;this.chronicles=chronicles;}
    public void start(){stop();long delay=Math.max(1,plugin.getConfig().getLong("tracking.first-scan-delay-seconds",3))*20L,period=Math.max(10,plugin.getConfig().getLong("tracking.scan-interval-seconds",60))*20L;task=Bukkit.getScheduler().runTaskTimer(plugin,()->scanAll(false),delay,period);}
    public void stop(){if(task!=null){task.cancel();task=null;}}
    public int scanAll(boolean manual){Set<UUID>seen=new HashSet<>();for(Town town:towny.towns()){seen.add(town.getUUID());process(town,false);}for(TownChronicleState state:repository.states())if(state.active()&&!seen.contains(state.townId()))dissolve(state.townId(),state.name(),"Город отсутствует в базе Towny");repository.saveIfDirty();return seen.size();}
    public void process(Town town,boolean newEvent){
        TownChronicleState state=repository.state(town.getUUID());boolean fresh=state.foundedAt()<=0;long founded=TimeUtil.normalizedEpoch(town.getRegistered());if(!fresh&&!state.active()&&!town.isRuined()){chronicles.record(town.getUUID(),ChronicleCategory.FOUNDING,"Город вернулся в историю",List.of("Город снова обнаружен в базе Towny."),"","NeverLandTownyChronicles");state.active(true);}
        if(fresh){state.foundedAt(founded);state.name(town.getName());boolean record=newEvent||plugin.getConfig().getBoolean("tracking.record-existing-towns",true);if(record)chronicles.record(town.getUUID(),town.getName(),ChronicleCategory.FOUNDING,founded,"Основан город "+town.getName(),List.of("Основатель: "+safe(town.getFounder()),"Первый мэр: "+mayor(town)),safe(town.getFounder()),"Towny",newEvent);}
        else if(!state.name().equals(town.getName())&&plugin.getConfig().getBoolean("tracking.record-town-renames",true))renamed(town,state.name());
        Resident mayor=town.getMayor();UUID mayorId=mayor==null?null:mayor.getUUID();String mayorName=mayor==null?"":mayor.getName();if(!fresh&&!same(state.mayorId(),mayorId))mayorChanged(town,state.mayorName(),mayorName,mayorId);else state.mayor(mayorName,mayorId);
        Nation nation=town.getNationOrNull();UUID nationId=nation==null?null:nation.getUUID();String nationName=nation==null?"":nation.getName();if(!fresh&&!same(state.nationId(),nationId)&&plugin.getConfig().getBoolean("tracking.record-nation-changes",true))nationChanged(town,state.nationName(),nationName,nationId);else state.nation(nationName,nationId);
        state.name(town.getName());state.residents(town.getResidents().size());state.blocks(town.getTownBlocks().size());state.balance(towny.balance(town));state.lastSeen(System.currentTimeMillis());state.active(!town.isRuined());achievements(town,state,fresh);wonders(town,state,fresh);repository.markDirty();
    }
    public void renamed(Town town,String oldName){TownChronicleState state=repository.state(town.getUUID());if(oldName!=null&&!oldName.isBlank()&&!oldName.equals(town.getName()))chronicles.record(town.getUUID(),ChronicleCategory.CUSTOM,"Город получил новое имя",List.of("Прежнее название: "+oldName,"Новое название: "+town.getName()),"","Towny");state.name(town.getName());repository.markDirty();}
    public void mayorChanged(Town town,String oldMayor,String newMayor,UUID newId){TownChronicleState state=repository.state(town.getUUID());if(newMayor!=null&&!newMayor.isBlank()&&!newMayor.equals(oldMayor))chronicles.record(town.getUUID(),ChronicleCategory.MAYOR,"Новый мэр — "+newMayor,List.of("Предыдущий мэр: "+safe(oldMayor),"Город: "+town.getName()),newMayor,"Towny");state.mayor(newMayor,newId);repository.markDirty();}
    public void nationChanged(Town town,String oldNation,String newNation,UUID newId){TownChronicleState state=repository.state(town.getUUID());String title;if(newNation==null||newNation.isBlank())title="Город покинул нацию "+safe(oldNation);else if(oldNation==null||oldNation.isBlank())title="Город вступил в нацию "+newNation;else title="Город сменил нацию";chronicles.record(town.getUUID(),ChronicleCategory.NATION,title,List.of("Прежняя нация: "+safe(oldNation),"Новая нация: "+safe(newNation)),"","Towny");state.nation(newNation,newId);repository.markDirty();}
    public void dissolve(UUID townId,String name,String reason){TownChronicleState state=repository.state(townId);if(!state.active())return;chronicles.record(townId,name,ChronicleCategory.DISSOLUTION,System.currentTimeMillis(),"Город "+name+" исчез",List.of(reason),"","Towny",true);state.active(false);state.lastSeen(System.currentTimeMillis());repository.markDirty();repository.save();}
    public void conquered(Town town){if(!plugin.getConfig().getBoolean("tracking.record-conquests",true))return;chronicles.record(town.getUUID(),ChronicleCategory.WAR,"Город был завоёван",List.of("Город перешёл под власть победителя."),"","Towny");}
    public void ruined(Town town,String mayor){chronicles.record(town.getUUID(),ChronicleCategory.DISSOLUTION,"Город оказался в руинах",List.of("Последний мэр: "+safe(mayor)),mayor,"Towny");repository.state(town.getUUID()).active(false);repository.markDirty();}
    public void reclaimed(Town town,String resident){chronicles.record(town.getUUID(),ChronicleCategory.FOUNDING,"Город восстановлен из руин",List.of("Возродил город: "+resident),resident,"Towny");repository.state(town.getUUID()).active(true);process(town,false);}
    private void achievements(Town town,TownChronicleState state,boolean fresh){for(int value:plugin.getConfig().getIntegerList("achievements.population"))if(town.getResidents().size()>=value)award(town,state,"population-"+value,"Население достигло "+value+" жителей",List.of("Город продолжает расти."),System.currentTimeMillis(),fresh);for(int value:plugin.getConfig().getIntegerList("achievements.territory"))if(town.getTownBlocks().size()>=value)award(town,state,"territory-"+value,"Освоено "+value+" городских участков",List.of("Территория города расширилась."),System.currentTimeMillis(),fresh);for(double value:plugin.getConfig().getDoubleList("achievements.treasury"))if(towny.balance(town)>=value)award(town,state,"treasury-"+(long)value,"Казна достигла "+money.format(value)+" монет",List.of("Финансовое достижение города."),System.currentTimeMillis(),fresh);long age=TimeUtil.days(state.foundedAt());for(int days:plugin.getConfig().getIntegerList("achievements.age-days"))if(age>=days)award(town,state,"age-"+days,"Городу исполнилось "+days+" дней",List.of("Историческая веха города."),state.foundedAt()+days*86_400_000L,fresh);}
    private void award(Town town,TownChronicleState state,String id,String title,List<String>details,long timestamp,boolean fresh){if(!state.achievements().add(id))return;chronicles.record(town.getUUID(),town.getName(),ChronicleCategory.ACHIEVEMENT,timestamp,title,details,"","NeverLandTownyChronicles",!fresh);}
    private void wonders(Town town,TownChronicleState state,boolean fresh){for(Map.Entry<String,String>entry:builds.projects().entrySet()){int level=builds.level(town.getUUID(),entry.getKey()),old=state.wonders().getOrDefault(entry.getKey(),0);if(level>0&&old<=0&&(plugin.getConfig().getBoolean("tracking.record-existing-wonders",true)||!fresh))chronicles.record(town.getUUID(),town.getName(),ChronicleCategory.WONDER,System.currentTimeMillis(),"Возведено Чудо Света — "+entry.getValue(),List.of("Отныне "+entry.getValue()+" является частью истории города."),"","NeverLandTownyBuilds",!fresh);state.wonders().put(entry.getKey(),level);}}
    private boolean same(UUID first,UUID second){return first==null?second==null:first.equals(second);}private String mayor(Town town){return town.getMayor()==null?"неизвестен":town.getMayor().getName();}private String safe(String value){return value==null||value.isBlank()?"нет":value;}
}
