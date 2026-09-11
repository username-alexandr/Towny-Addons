package ru.neverland.townyjobs;
import java.util.*;
import java.util.function.LongSupplier;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.townyjobs.api.TownyJobsApi;
import static ru.neverland.townyjobs.WorkPolicy.*;

public final class JobsService implements TownyJobsApi {
    public interface PresenceSource {Presence get(UUID actor);}
    private final JavaPlugin plugin;private final JobsRepository repository;private final CityBridge bridge;
    private JobsSettings settings;private BukkitTask task;private boolean fault;private long lastPulse;
    private LongSupplier clock=System::currentTimeMillis;private PresenceSource source=this::nativePresence;
    private final Map<UUID,Shift> shifts=new HashMap<>();private Map<UUID,Map<String,Site>> sites=Map.of();
    private Map<UUID,Map<String,List<UUID>>> rosters=Map.of();private Set<UUID> admitted=Set.of();
    public JobsService(JavaPlugin plugin,JobsRepository repository,JobsSettings settings,CityBridge bridge){this.plugin=plugin;this.repository=repository;this.settings=settings;this.bridge=bridge;}
    public JobsSettings settings(){return settings;}public JobsRepository repository(){return repository;}public boolean fault(){return fault||!repository.writable();}
    private void primary(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Нужен основной поток сервера");}
    private void ready(){primary();if(fault())throw new IllegalStateException("Профессии временно недоступны; проверьте консоль");}
    public void start()throws Exception{primary();bridge.verify();refresh(false);lastPulse=clock.getAsLong();task=Bukkit.getScheduler().runTaskTimer(plugin,()->refresh(true),20L*settings.interval(),20L*settings.interval());}
    public void stop(){if(task!=null)task.cancel();task=null;shifts.clear();sites=Map.of();rosters=Map.of();admitted=Set.of();}
    public void reload(JobsSettings next)throws Exception{primary();bridge.verify();stop();settings=next;fault=false;start();}
    public Town town(Player p){return town(p.getUniqueId());}
    public Town town(UUID actor){var resident=TownyAPI.getInstance().getResident(actor);return resident==null?null:resident.getTownOrNull();}
    public boolean manager(Player p,Town t){var r=TownyAPI.getInstance().getResident(p.getUniqueId());return t!=null&&r!=null&&t.equals(r.getTownOrNull())&&(t.isMayor(r)||r.hasTownRank("assistant")||p.hasPermission("neverlandtownyjobs.manage"));}
    private Town user(Player p){ready();if(!p.hasPermission("neverlandtownyjobs.use"))throw new IllegalArgumentException("Нет прав на профессии");Town t=town(p);if(t==null)throw new IllegalArgumentException("Нужно состоять в городе");return t;}
    public Career career(UUID actor){return repository.get(actor);}
    public void choose(Player p,Profession profession,long revision,JobsSettings quoted)throws Exception{
        user(p);Career current=career(p.getUniqueId());if(settings!=quoted||current.revision()!=revision)throw new IllegalArgumentException("Условия изменились. Откройте меню заново");
        if(!settings.profiles().get(profession).enabled())throw new IllegalArgumentException("Профессия отключена");if(current.profession()==profession)throw new IllegalArgumentException("Эта профессия уже выбрана");long now=clock.getAsLong();
        if(current.profession()!=null&&now-current.changedAt()<settings.cooldown()*3600000L)throw new IllegalArgumentException("Смена профессии пока недоступна; оставшееся время указано в меню");
        repository.put(p.getUniqueId(),current.choose(profession,now));shifts.remove(p.getUniqueId());refresh(false);
    }
    public long cooldown(UUID actor){var c=career(actor);return c.profession()==null?0:Math.max(0,(c.changedAt()+settings.cooldown()*3600000L-clock.getAsLong()+999)/1000);}
    public Map<String,Site> workplaces(UUID town)throws Exception{ready();return bridge.sites(town);}
    public void work(Player p,String building)throws Exception{
        Town t=user(p);if(!p.hasPermission("neverlandtownyjobs.work"))throw new IllegalArgumentException("Нет прав на работу");Career c=career(p.getUniqueId());var profile=c.profession()==null?null:settings.profiles().get(c.profession());
        if(profile==null||!profile.enabled()||!profile.buildings().contains(building))throw new IllegalArgumentException("Выберите подходящую профессию");Site site=workplaces(t.getUUID()).get(building);if(site==null||site.level()<1||!site.owned())throw new IllegalArgumentException("Нужно завершённое здание своего города");if(!site.active())throw new IllegalArgumentException("Здание не работает; проверьте содержание и энергию");
        if(t.getUUID().equals(c.town())&&building.equals(c.building()))throw new IllegalArgumentException("Это рабочее место уже выбрано");long occupied=repository.all().entrySet().stream().filter(e->!e.getKey().equals(p.getUniqueId())&&t.getUUID().equals(e.getValue().town())&&building.equals(e.getValue().building())).count();if(occupied>=seats(site,settings))throw new IllegalArgumentException("В здании нет свободных рабочих мест");
        repository.put(p.getUniqueId(),c.work(t.getUUID(),building,clock.getAsLong()));shifts.remove(p.getUniqueId());refresh(false);
    }
    public void leave(Player p)throws Exception{user(p);if(career(p.getUniqueId()).town()==null)throw new IllegalArgumentException("Рабочее место не выбрано");repository.put(p.getUniqueId(),career(p.getUniqueId()).leave());shifts.remove(p.getUniqueId());refresh(false);}
    public void release(Player p,UUID actor,long revision)throws Exception{Town t=user(p);Career c=career(actor);if(!manager(p,t)||!t.getUUID().equals(c.town())||c.revision()!=revision)throw new IllegalArgumentException("Нужны права управляющего и актуальное назначение жителя своего города");repository.put(actor,c.leave());shifts.remove(actor);refresh(false);}
    public void adminRelease(UUID actor)throws Exception{ready();Career c=career(actor);if(c.town()==null)throw new IllegalArgumentException("Нет рабочего места");repository.put(actor,c.leave());shifts.remove(actor);refresh(false);}
    public void duty(Player p)throws Exception{user(p);if(shifts.containsKey(p.getUniqueId())){shifts.remove(p.getUniqueId());return;}beginShift(p.getUniqueId());}
    public void beginShift(UUID actor)throws Exception{ready();refresh(false);Career c=career(actor);Site s=site(c);Presence presence=source.get(actor);long now=clock.getAsLong();
        String status=WorkPolicy.status(c,s,presence,new Shift(now-settings.warmup()*1000L,now),admitted.contains(actor),settings,now);if(!status.equals("На смене"))throw new IllegalArgumentException(status);shifts.put(actor,new Shift(now,0));}
    public boolean onDuty(UUID actor){return shifts.containsKey(actor);}
    public void endShift(UUID actor){shifts.remove(actor);}
    public void removeResident(UUID actor){primary();shifts.remove(actor);if(career(actor).town()!=null)try{repository.put(actor,career(actor).leave());refresh(false);}catch(Exception ex){fail(ex);}}
    public void signal(UUID actor,Profession activity){if(!Bukkit.isPrimaryThread()||fault())return;Career c=career(actor);Shift shift=shifts.get(actor);if(shift==null||c.profession()!=activity)return;Site s=site(c);long now=clock.getAsLong();
        if(WorkPolicy.status(c,s,source.get(actor),new Shift(now-settings.warmup()*1000L,now),admitted.contains(actor),settings,now).equals("На смене"))shifts.put(actor,shift.touch(now));
    }
    public void signalAt(org.bukkit.block.Block block,Profession activity){for(UUID id:List.copyOf(shifts.keySet())){Presence p=source.get(id);if(p!=null&&block.getWorld().getUID().equals(p.world())&&Math.abs(p.x()-block.getX())<=6&&Math.abs(p.y()-block.getY())<=6&&Math.abs(p.z()-block.getZ())<=6)signal(id,activity);}}
    private Site site(Career c){return c.town()==null?null:sites.getOrDefault(c.town(),Map.of()).get(c.building());}
    public String status(UUID actor){if(fault())return "Профессии приостановлены";Career c=career(actor);Site site=site(c);if(site!=null&&c.town()!=null&&!ru.neverland.integration.BuildingOperations.active(c.town(),c.building()))return "Здание не работает: проверьте содержание и энергию";return WorkPolicy.status(c,site,source.get(actor),shifts.get(actor),admitted.contains(actor),settings,clock.getAsLong());}
    public Site workplace(UUID actor){return site(career(actor));}
    public void refresh(boolean accrue){primary();if(!repository.writable()){fail(new IllegalStateException("База недоступна"));return;}
        try{long now=clock.getAsLong();int seconds=accrue?(int)Math.max(0,Math.min(settings.interval(),(now-lastPulse)/1000)):0;if(accrue)lastPulse=now;var rows=new HashMap<>(repository.all());
            for(var e:List.copyOf(rows.entrySet())){Career c=e.getValue();Town current=town(e.getKey());if(c.town()!=null&&(current==null||!c.town().equals(current.getUUID()))){rows.put(e.getKey(),c.leave());shifts.remove(e.getKey());}}
            repository.replace(rows);Map<UUID,Map<String,Site>> nextSites=new HashMap<>();for(Career c:rows.values())if(c.town()!=null&&!nextSites.containsKey(c.town()))nextSites.put(c.town(),bridge.sites(c.town()));sites=Map.copyOf(nextSites);
            Map<UUID,Map<String,List<UUID>>> nextRosters=new HashMap<>();for(var e:rows.entrySet())if(e.getValue().town()!=null)nextRosters.computeIfAbsent(e.getValue().town(),k->new HashMap<>()).computeIfAbsent(e.getValue().building(),k->new ArrayList<>()).add(e.getKey());rosters=nextRosters;Set<UUID> nextAdmitted=new HashSet<>();for(var city:rosters.entrySet())for(var building:city.getValue().entrySet()){
                Site site=sites.getOrDefault(city.getKey(),Map.of()).get(building.getKey());building.getValue().stream().sorted(Comparator.<UUID>comparingLong(id->repository.get(id).assignedAt()).thenComparing(UUID::toString)).limit(seats(site,settings)).forEach(nextAdmitted::add);
            }admitted=Set.copyOf(nextAdmitted);fault=false;
            for(UUID actor:List.copyOf(shifts.keySet())){Presence presence=source.get(actor);if(presence==null||!presence.online()||!presence.allowed()||!Objects.equals(career(actor).town(),presence.town())){shifts.remove(actor);continue;}if(seconds>0&&status(actor).equals("На смене"))rows.put(actor,career(actor).earn(seconds));}
            repository.replace(rows);
        }catch(Exception ex){fail(ex);}
    }
    private void fail(Exception ex){if(!fault)plugin.getLogger().log(java.util.logging.Level.SEVERE,"Профессии приостановлены; назначения сохранены",ex);fault=true;shifts.clear();sites=Map.of();rosters=Map.of();admitted=Set.of();}
    private Presence nativePresence(UUID actor){Player p=Bukkit.getPlayer(actor);if(p==null)return null;Town t=town(actor);Location l=p.getLocation();boolean allowed=p.hasPermission("neverlandtownyjobs.use")&&p.hasPermission("neverlandtownyjobs.work")&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE)&&!p.isFlying()&&!p.isGliding()&&!p.isInsideVehicle();return new Presence(t==null?null:t.getUUID(),l.getWorld().getUID(),l.getX(),l.getY(),l.getZ(),p.isOnline(),allowed);}
    private List<UUID> roster(UUID town,String project){return rosters.getOrDefault(town,Map.of()).getOrDefault(project,List.of());}
    @Override public boolean supports(String project){return settings.profiles().values().stream().anyMatch(p->p.enabled()&&p.buildings().contains(project));}
    @Override public double bonus(UUID town,String project){if(!Bukkit.isPrimaryThread()||fault()||town==null||project==null)return 0;List<Double> values=new ArrayList<>();for(UUID actor:roster(town,project))if(status(actor).equals("На смене")){Career c=career(actor);values.add(settings.profiles().get(c.profession()).bonus().get(settings.level(c)-1));}return sum(values,settings.cap());}
    @Override public int workers(UUID town,String project){if(!Bukkit.isPrimaryThread()||fault()||town==null||project==null)return 0;return (int)roster(town,project).stream().filter(id->status(id).equals("На смене")).count();}
    @Override public double townBonus(UUID town,String effect){if(!Bukkit.isPrimaryThread()||fault()||town==null||effect==null)return 0;Profession profession=switch(effect){case "research_speed"->Profession.RESEARCHER;case "guard_defence"->Profession.GUARD;default->null;};if(profession==null)return 0;double best=0;for(String project:settings.profiles().get(profession).buildings()){List<Double> values=new ArrayList<>();for(UUID actor:roster(town,project))if(career(actor).profession()==profession&&status(actor).equals("На смене"))values.add(settings.profiles().get(profession).bonus().get(settings.level(career(actor))-1));best=Math.max(best,sum(values,settings.cap()));}return Math.min(effect.equals("guard_defence")?0.2:settings.cap(),best);}
}
