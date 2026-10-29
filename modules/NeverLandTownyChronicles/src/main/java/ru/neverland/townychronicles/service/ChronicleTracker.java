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
    private final JavaPlugin plugin;private final TownyHook towny;private final BuildBridge builds;private final ChronicleRepository repository;private final ChronicleService chronicles;private final DecimalFormat money=new DecimalFormat("#,##0.##");private BukkitTask task;private boolean initialSyncPending=true;
    public ChronicleTracker(JavaPlugin plugin,TownyHook towny,BuildBridge builds,ChronicleRepository repository,ChronicleService chronicles){this.plugin=plugin;this.towny=towny;this.builds=builds;this.repository=repository;this.chronicles=chronicles;}
    private BukkitTask scanTask;
    private final java.util.ArrayDeque<UUID> pendingTowns = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<UUID> pendingMissing = new java.util.ArrayDeque<>();
    private int scanTotal;
    private boolean scanInitial;
    public void start() { try (var guard = ru.neverland.core.SuiteState.guard()) {
        stop(); initialSyncPending=true;
        long delay=Math.max(1,plugin.getConfig().getLong("tracking.first-scan-delay-seconds",3))*20L;
        long period=Math.max(10,plugin.getConfig().getLong("tracking.scan-interval-seconds",60))*20L;
        task=ru.neverland.core.SuiteScheduler.get().runTaskTimer(plugin,()->scanAll(false),delay,period);
    } }
    public void stop() { try (var guard = ru.neverland.core.SuiteState.guard()) {
        if(task!=null){task.cancel();task=null;}
        if(scanTask!=null){scanTask.cancel();scanTask=null;}
        pendingTowns.clear(); pendingMissing.clear();
    } }
    /** Enqueues a bounded scan; the returned count is scheduled, not already processed. */
    public int scanAll(boolean manual) { try (var guard = ru.neverland.core.SuiteState.guard()) {
        if(scanTask!=null)return scanTotal;
        pendingTowns.clear();pendingMissing.clear();
        for(Town town:towny.towns())pendingTowns.add(town.getUUID());
        for(TownChronicleState state:repository.states())if(state.active())pendingMissing.add(state.townId());
        scanTotal=pendingTowns.size();scanInitial=initialSyncPending;
        scanTask=ru.neverland.core.SuiteScheduler.get().runTask(plugin,this::scanBatch);
        return scanTotal;
    } }
    private void scanBatch() {
        // A failed town/API call must not leave a completed task registered forever.
        // The next periodic/manual scan can rebuild the queue after an exception.
        scanTask=null;
        long started=System.nanoTime();
        int limit=Math.max(1,Math.min(25,plugin.getConfig().getInt("tracking.towns-per-tick",2)));
        long budget=Math.max(1,Math.min(20,plugin.getConfig().getLong("tracking.scan-budget-millis",3)))*1_000_000L;
        int processed=0;
        while(processed<limit&&(processed==0||System.nanoTime()-started<budget)) {
            UUID id=pendingTowns.pollFirst();
            if(id!=null){Town town=towny.town(id);if(town!=null)process(town,false,scanInitial);}
            else {
                id=pendingMissing.pollFirst();if(id==null)break;
                // Recheck live Towny: a city created during this scan must never be marked dissolved.
                if(towny.town(id)==null){TownChronicleState state=repository.state(id);if(state.active())dissolve(id,state.name(),"Город отсутствует в базе Towny");}
            }
            processed++;
        }
        if(pendingTowns.isEmpty()&&pendingMissing.isEmpty()) {
            initialSyncPending=false;scanTask=null;repository.requestSave();
        } else scanTask=ru.neverland.core.SuiteScheduler.get().runTask(plugin,this::scanBatch);
    }
    public void process(Town town,boolean newEvent){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {process(town,newEvent,false);} }
    private void process(Town town,boolean newEvent,boolean initialSync){
        TownChronicleState state=repository.state(town.getUUID());boolean fresh=state.foundedAt()<=0;long founded=TimeUtil.normalizedEpoch(town.getRegistered());if(!fresh&&!state.active()&&!town.isRuined()){chronicles.record(town.getUUID(),ChronicleCategory.FOUNDING,"Город вернулся в историю",List.of("Город снова обнаружен в базе Towny."),"","NeverLandTownyChronicles");state.active(true);}
        if(fresh){state.foundedAt(founded);state.name(town.getName());boolean record=newEvent||plugin.getConfig().getBoolean("tracking.record-existing-towns",true);if(record)chronicles.record(town.getUUID(),town.getName(),ChronicleCategory.FOUNDING,founded,"Основан город "+town.getName(),List.of("Основатель: "+safe(town.getFounder()),"Первый мэр: "+mayor(town)),safe(town.getFounder()),"Towny",newEvent);}
        else if(!state.name().equals(town.getName())&&plugin.getConfig().getBoolean("tracking.record-town-renames",true))renamed(town,state.name());
        Resident mayor=town.getMayor();UUID mayorId=mayor==null?null:mayor.getUUID();String mayorName=mayor==null?"":mayor.getName();if(!fresh&&!same(state.mayorId(),mayorId))mayorChanged(town,state.mayorName(),mayorName,mayorId);else state.mayor(mayorName,mayorId);
        Nation nation=town.getNationOrNull();UUID nationId=nation==null?null:nation.getUUID();String nationName=nation==null?"":nation.getName();if(!fresh&&!same(state.nationId(),nationId)&&plugin.getConfig().getBoolean("tracking.record-nation-changes",true))nationChanged(town,state.nationName(),nationName,nationId);else state.nation(nationName,nationId);
        state.name(town.getName());state.residents(town.getResidents().size());state.blocks(town.getTownBlocks().size());var balance=towny.availableBalance(town);if(balance.isPresent())state.balance(balance.getAsDouble());state.lastSeen(System.currentTimeMillis());state.active(!town.isRuined());achievements(town,state,fresh,initialSync,balance);wonders(town,state,fresh,initialSync);repository.markDirty();
    }
    public void renamed(Town town,String oldName){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {TownChronicleState state=repository.state(town.getUUID());if(oldName!=null&&!oldName.isBlank()&&!oldName.equals(town.getName()))chronicles.record(town.getUUID(),ChronicleCategory.CUSTOM,"Город получил новое имя",List.of("Прежнее название: "+oldName,"Новое название: "+town.getName()),"","Towny");state.name(town.getName());repository.markDirty();} }
    public void mayorChanged(Town town,String oldMayor,String newMayor,UUID newId){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {TownChronicleState state=repository.state(town.getUUID());if(newMayor!=null&&!newMayor.isBlank()&&!newMayor.equals(oldMayor))chronicles.record(town.getUUID(),ChronicleCategory.MAYOR,"Новый мэр — "+newMayor,List.of("Предыдущий мэр: "+safe(oldMayor),"Город: "+town.getName()),newMayor,"Towny");state.mayor(newMayor,newId);repository.markDirty();} }
    public void nationChanged(Town town,String oldNation,String newNation,UUID newId){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {TownChronicleState state=repository.state(town.getUUID());String title;if(newNation==null||newNation.isBlank())title="Город покинул нацию "+safe(oldNation);else if(oldNation==null||oldNation.isBlank())title="Город вступил в нацию "+newNation;else title="Город сменил нацию";chronicles.record(town.getUUID(),ChronicleCategory.NATION,title,List.of("Прежняя нация: "+safe(oldNation),"Новая нация: "+safe(newNation)),"","Towny");state.nation(newNation,newId);repository.markDirty();} }
    public void dissolve(UUID townId,String name,String reason){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {TownChronicleState state=repository.state(townId);if(!state.active())return;chronicles.record(townId,name,ChronicleCategory.DISSOLUTION,System.currentTimeMillis(),"Город "+name+" исчез",List.of(reason),"","Towny",true);state.active(false);state.lastSeen(System.currentTimeMillis());repository.markDirty();repository.requestSave();} }
    public void conquered(Town town){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {if(!plugin.getConfig().getBoolean("tracking.record-conquests",true))return;chronicles.record(town.getUUID(),ChronicleCategory.WAR,"Город был завоёван",List.of("Город перешёл под власть победителя."),"","Towny");} }
    public void ruined(Town town,String mayor){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {chronicles.record(town.getUUID(),ChronicleCategory.DISSOLUTION,"Город оказался в руинах",List.of("Последний мэр: "+safe(mayor)),mayor,"Towny");repository.state(town.getUUID()).active(false);repository.markDirty();} }
    public void reclaimed(Town town,String resident){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {chronicles.record(town.getUUID(),ChronicleCategory.RECOVERY,"Город восстановлен из руин",List.of("Возродил город: "+resident),resident,"Towny");repository.state(town.getUUID()).active(true);process(town,false);} }
    private void achievements(Town town,TownChronicleState state,boolean fresh,boolean initialSync,java.util.OptionalDouble balance){for(int value:plugin.getConfig().getIntegerList("achievements.population"))if(town.getResidents().size()>=value)award(town,state,"population-"+value,"Население достигло "+value+" жителей",List.of("Город продолжает расти."),System.currentTimeMillis(),fresh,initialSync);for(int value:plugin.getConfig().getIntegerList("achievements.territory"))if(town.getTownBlocks().size()>=value)award(town,state,"territory-"+value,"Освоено "+value+" городских участков",List.of("Территория города расширилась."),System.currentTimeMillis(),fresh,initialSync);for(double value:plugin.getConfig().getDoubleList("achievements.treasury"))if(balance.isPresent()&&balance.getAsDouble()>=value)award(town,state,"treasury-"+(long)value,"Казна достигла "+money.format(value)+" монет",List.of("Финансовое достижение города."),System.currentTimeMillis(),fresh,initialSync);long age=TimeUtil.days(state.foundedAt());for(int days:plugin.getConfig().getIntegerList("achievements.age-days"))if(age>=days)award(town,state,"age-"+days,"Городу исполнилось "+days+" дней",List.of("Историческая веха города."),state.foundedAt()+days*86_400_000L,fresh,initialSync);}
    private void award(Town town,TownChronicleState state,String id,String title,List<String>details,long timestamp,boolean fresh,boolean initialSync){if(!state.achievements().add(id))return;chronicles.record(town.getUUID(),town.getName(),ChronicleCategory.ACHIEVEMENT,timestamp,title,details,"","NeverLandTownyChronicles",StartupAnnouncementPolicy.shouldAnnounce(fresh,initialSync));}
    private void wonders(Town town,TownChronicleState state,boolean fresh,boolean initialSync){for(Map.Entry<String,String>entry:builds.projects().entrySet()){int level=builds.level(town.getUUID(),entry.getKey()),old=state.wonders().getOrDefault(entry.getKey(),0);boolean discovered=level>0&&old<=0;if(StartupAnnouncementPolicy.shouldRecordWonder(discovered,initialSync,plugin.getConfig().getBoolean("tracking.record-existing-wonders",true)))chronicles.record(town.getUUID(),town.getName(),ChronicleCategory.WONDER,System.currentTimeMillis(),"Возведено Чудо Света — "+entry.getValue(),List.of("Отныне "+entry.getValue()+" является частью истории города."),"","NeverLandTownyBuilds",StartupAnnouncementPolicy.shouldAnnounce(fresh,initialSync));state.wonders().put(entry.getKey(),level);}}
    private boolean same(UUID first,UUID second){return first==null?second==null:first.equals(second);}private String mayor(Town town){return town.getMayor()==null?"неизвестен":town.getMayor().getName();}private String safe(String value){return value==null||value.isBlank()?"нет":value;}
}
