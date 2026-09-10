package ru.neverland.townylogistics.service;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townylogistics.config.LogisticsSettings;
import ru.neverland.townylogistics.data.LogisticsRepository;
import ru.neverland.townylogistics.integration.BuildsStorage;
import ru.neverland.townylogistics.model.*;
import ru.neverland.townylogistics.model.Network.*;
import ru.neverland.townylogistics.model.CourierJob.Phase;
import java.util.*;
import java.io.IOException;

public final class LogisticsService {
    private final JavaPlugin plugin;private final LogisticsRepository repository;private final BuildsStorage builds;private final CourierNpcs npcs;
    private Map<UUID,Network> networks;private Map<UUID,CourierJob> jobs;private final Map<UUID,String> statuses=new HashMap<>();private final Map<String,Long> cooldown=new HashMap<>();private final Map<String,String> routeStatuses=new HashMap<>();
    private Map<UUID,Map<String,BuildsStorage.Depot>> depots=new HashMap<>();private long refreshed,saved;private int cursor,jobCursor;private boolean fault;private BukkitTask task;
    private LogisticsSettings settings;
    public LogisticsService(JavaPlugin plugin,LogisticsRepository repository,BuildsStorage builds,LogisticsSettings settings,LogisticsRepository.Snapshot state){
        this.plugin=plugin;this.repository=repository;this.builds=builds;this.settings=settings;this.networks=new HashMap<>(state.networks());this.jobs=new LinkedHashMap<>(state.jobs());
        npcs=new CourierNpcs(plugin,settings,e->{var j=jobs.get(e.getKey());if(j!=null&&CourierNpcs.owned(j.town(),e.getValue()))jobs.put(j.id(),j.move(e.getValue(),j.waypoint()));});
    }
    public void start()throws IOException{reconcile();npcs.start();task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,10);}
    public void stop(){if(task!=null)task.cancel();npcs.stop();try{save();}catch(IOException ex){plugin.getLogger().severe("Не удалось записать позиции курьеров: "+ex.getMessage());}}
    public void reload(LogisticsSettings value)throws IOException{save();reconcile();settings=value;npcs.settings(value);fault=false;refreshed=0;}
    public LogisticsSettings settings(){return settings;}public BuildsStorage builds(){return builds;}public boolean paused(){return fault;}
    public Network network(UUID id){return networks.getOrDefault(id,Network.empty(id));}
    public List<CourierJob> jobs(UUID town){return jobs.values().stream().filter(j->j.town().equals(town)).toList();}
    public String routeStatus(UUID town,String route){
        if(fault)return "Приостановлено из-за ошибки записи";if(!network(town).route(route).enabled())return "Приостановлен; текущий груз завершит доставку";
        var active=jobs(town).stream().filter(j->j.route().equals(route)).toList();if(!active.isEmpty())return status(active.get(0))+" • курьеров: "+active.size();
        if(jobs.size()>=settings.active())return "Достигнут общий предел курьеров";
        return routeStatuses.getOrDefault(town+"/"+route,"Ожидает проверки маршрута");
    }
    public String status(CourierJob j){return statuses.getOrDefault(j.id(),j.phase().title);}
    public Town town(Player p){var resident=TownyAPI.getInstance().getResident(p);return resident==null?null:resident.getTownOrNull();}
    public boolean manager(Player p,Town t){var resident=TownyAPI.getInstance().getResident(p);return resident!=null&&t.equals(resident.getTownOrNull())&&(t.isMayor(resident)||resident.hasTownRank("assistant")||p.hasPermission("neverlandtownylogistics.manage"));}
    public Map<String,BuildsStorage.Depot> depots(UUID town)throws IOException{return builds.depots(town);}
    private void save()throws IOException{repository.save(networks,jobs);saved=System.currentTimeMillis();}
    public void change(Network next,boolean topology)throws IOException{
        if(fault)throw new IllegalStateException("Логистика приостановлена из-за ошибки сохранения; обратитесь к администратору");
        if(topology&&!jobs(next.town()).isEmpty())throw new IllegalArgumentException("Сначала приостановите маршруты и дождитесь курьеров; свободных можно снять командой dismiss");
        var copy=new HashMap<>(networks);copy.put(next.town(),next);repository.save(copy,jobs);networks=copy;refreshed=0;
    }
    public void dismiss(UUID town,String route)throws IOException{
        var cargo=builds.shipments(town);if(jobs(town).stream().anyMatch(j->j.route().equals(route)&&cargo.containsKey(j.id())&&cargo.get(j.id()).transit()))throw new IllegalArgumentException("В маршруте есть груз; дождитесь доставки или восстановления администратором");
        var copy=new LinkedHashMap<>(jobs);for(var j:jobs(town))if(j.route().equals(route)){npcs.remove(j.id());copy.remove(j.id());}
        repository.save(networks,copy);jobs=copy;for(var s:cargo.values())if(s.route().equals(route)&&!s.transit())builds.acknowledge(town,s.id());
    }
    public boolean recover(UUID town,UUID id)throws IOException{
        if(!builds.unload(town,id,true))return false;npcs.remove(id);var copy=new LinkedHashMap<>(jobs);copy.remove(id);repository.save(networks,copy);jobs=copy;builds.acknowledge(town,id);return true;
    }
    public void validateNode(UUID town,Node node)throws IOException{
        if(!CourierNpcs.owned(town,node.point())||!CourierNpcs.standable(node.point()))throw new IllegalArgumentException("Встаньте на безопасный проход на территории своего города");
        if(node.kind()==Kind.BUILDING){var depot=depots(town).get(node.project());if(depot==null||!depot.contains(node.point()))throw new IllegalArgumentException("Точка погрузки должна быть у выбранного построенного здания");}
    }
    public void validateLink(UUID town,Node a,Node b){
        double distance=a.point().distance(b.point());if(!Double.isFinite(distance)||distance>settings.linkDistance())throw new IllegalArgumentException("Связь не длиннее "+settings.linkDistance()+" блоков, в одном мире");
        int steps=Math.max(1,(int)Math.ceil(distance));for(int i=0;i<=steps;i++){double f=(double)i/steps;var p=new Position(a.point().world(),a.point().x()+(b.point().x()-a.point().x())*f,a.point().y()+(b.point().y()-a.point().y())*f,a.point().z()+(b.point().z()-a.point().z())*f);
            if(!CourierNpcs.owned(town,p)||!CourierNpcs.loaded(p))throw new IllegalArgumentException("Связь проходит через чужую или незагруженную территорию");}
    }
    public CourierJob job(Network n,Route r)throws IOException{
        var hub=n.node(r.hub());var source=n.node(r.source());var target=n.node(r.target());var stock=depots(n.town());
        for(var node:List.of(hub,source,target)){if(node.kind()!=Kind.BUILDING||!stock.containsKey(node.project()))throw new IllegalArgumentException("Нет построенного здания для узла "+node.id());if(!stock.get(node.project()).contains(node.point())||!CourierNpcs.owned(n.town(),node.point()))throw new IllegalArgumentException("Точка здания потеряла привязку или территорию");}
        if(!settings.hubs().contains(hub.project()))throw new IllegalArgumentException("Узел отправки должен быть складом, терминалом, караван-сараем или портом");
        if(source.project().equals(target.project()))throw new IllegalArgumentException("Отправитель и получатель должны быть разными зданиями");
        BuildsStorage.decode(r.filter());
        var out=n.path(r.hub(),r.source(),settings.routeDistance());var delivery=n.path(r.source(),r.target(),settings.routeDistance());var home=n.path(r.target(),r.hub(),settings.routeDistance());
        double length=0;for(var path:List.of(out,delivery,home))for(int i=1;i<path.size();i++)length+=path.get(i-1).distance(path.get(i));if(length>settings.routeDistance())throw new IllegalArgumentException("Полный рейс слишком длинный");
        return new CourierJob(UUID.randomUUID(),n.town(),r.id(),hub.project(),source.project(),target.project(),Math.min(5,stock.get(hub.project()).level()),out,delivery,home,Phase.TO_SOURCE,0,hub.point(),0);
    }
    private void reconcile()throws IOException{
        boolean changed=false;
        for(UUID town:builds.shipmentTowns())for(var cargo:builds.shipments(town).values()){
            var job=jobs.get(cargo.id());
            if(job==null){if(!cargo.transit()){builds.acknowledge(town,cargo.id());continue;}
                var n=network(town);var route=n.routes().get(cargo.route());if(route==null){plugin.getLogger().warning("Груз "+cargo.id()+" требует административного recover; его маршрут отсутствует");continue;}
                // Cargo is authoritative. A missing movement checkpoint restarts its walk at the pickup point.
                CourierJob planned;try{planned=job(n,route);}catch(IllegalArgumentException ex){plugin.getLogger().warning("Груз "+cargo.id()+" ожидает восстановления маршрута: "+ex.getMessage());continue;}
                if(!planned.source().equals(cargo.source())||!planned.target().equals(cargo.target())){plugin.getLogger().warning("Груз "+cargo.id()+" требует recover: изменены здания маршрута");continue;}
                job=new CourierJob(cargo.id(),town,cargo.route(),planned.hub(),cargo.source(),cargo.target(),planned.level(),planned.outbound(),planned.delivery(),planned.home(),Phase.TO_TARGET,0,planned.delivery().get(0),0);jobs.put(job.id(),job);changed=true;
            }
            // Existing jobs replay their last movement phase; pickup/unload receipts prevent duplicate item changes.
        }
        if(changed)save();
    }
    private void refresh()throws IOException{Map<UUID,Map<String,BuildsStorage.Depot>> next=new HashMap<>();for(UUID town:networks.keySet())next.put(town,builds.depots(town));depots=next;refreshed=System.currentTimeMillis();}
    private void tick(){if(fault)return;try{
        long now=System.currentTimeMillis();if(now-refreshed>=5000)refresh();npcs.beginTick();int operations=settings.cargoBudget();
        List<CourierJob> queue=new ArrayList<>(jobs.values());if(!queue.isEmpty())Collections.rotate(queue,-Math.floorMod(jobCursor++,queue.size()));
        for(var original:queue){var j=jobs.get(original.id());if(j==null)continue;
            var available=depots.getOrDefault(j.town(),Map.of());if(!available.containsKey(j.hub())||!available.containsKey(j.source())||!available.containsKey(j.target())){npcs.remove(j.id());statuses.put(j.id(),"Ожидает восстановления здания");continue;}
            if(j.phase()!=Phase.DONE&&(!working(j.town(),j.hub())||!working(j.town(),j.source())||!working(j.town(),j.target()))){npcs.remove(j.id());statuses.put(j.id(),"НЕАКТИВНО — проверьте содержание и питание зданий маршрута; груз сохранён");continue;}
            int level=Math.max(1,Math.min(j.level(),available.get(j.hub()).level()));var path=j.path();
            boolean moving=j.phase()==Phase.TO_SOURCE||j.phase()==Phase.TO_TARGET||j.phase()==Phase.RETURNING;
            Position target=path.get(moving?Math.min(j.waypoint(),path.size()-1):path.size()-1);
            boolean handling=j.phase()==Phase.LOADING||j.phase()==Phase.UNLOADING;
            var motion=npcs.tick(j,target,moving||(handling&&j.position().distance(target)>1.2),level);j=j.move(motion.position(),j.waypoint());jobs.put(j.id(),j);
            if(!motion.active()){statuses.put(j.id(),motion.status());continue;}statuses.put(j.id(),j.phase().title);
            if(moving){int index=j.waypoint();while(index<path.size()&&j.position().distance(path.get(index))<=1.2)index++;
                j=j.move(j.position(),index);if(index>=path.size()){Phase next=j.phase()==Phase.TO_SOURCE?Phase.LOADING:j.phase()==Phase.TO_TARGET?Phase.UNLOADING:Phase.DONE;j=j.phase(next,next==Phase.DONE?0:settings.level(level).handling()*20);}
                jobs.put(j.id(),j);continue;
            }
            if(handling&&j.position().distance(target)>1.2){statuses.put(j.id(),"Возвращается к точке погрузки или разгрузки");continue;}
            if(j.handlingTicks()>0){jobs.put(j.id(),j.waitTicks(10));continue;}
            if(operations<=0)continue;
            var route=network(j.town()).routes().get(j.route());if(route==null)throw new IllegalStateException("Маршрут курьера отсутствует");
            if(j.phase()==Phase.LOADING){
                var existing=builds.shipments(j.town()).get(j.id());
                if(!route.enabled()&&existing==null){jobs.put(j.id(),j.returnFromSource());continue;}
                operations--;var cargo=builds.pickup(j.town(),j.id(),j.route(),j.source(),j.target(),route.filter(),Math.min(route.limit(),settings.level(level).cargo()),route.keep());
                if(cargo==null){statuses.put(j.id(),"Нет груза, свободного места или склад открыт");cooldown.put(j.town()+"/"+j.route(),now+10000);jobs.put(j.id(),j.returnFromSource());}
                else jobs.put(j.id(),j.phase(cargo.transit()?Phase.TO_TARGET:Phase.RETURNING,0));
            }else if(j.phase()==Phase.UNLOADING){
                operations--;if(builds.unload(j.town(),j.id(),false))jobs.put(j.id(),j.phase(Phase.RETURNING,0));else statuses.put(j.id(),"Ожидает места в складе получателя или его закрытия");
            }else if(j.phase()==Phase.DONE){
                operations--;npcs.remove(j.id());var copy=new LinkedHashMap<>(jobs);copy.remove(j.id());repository.save(networks,copy);jobs=copy;builds.acknowledge(j.town(),j.id());statuses.remove(j.id());
            }
        }
        if(now-saved>=settings.checkpoint()*1000L)save();schedule(now);
    }catch(Exception ex){fault=true;npcs.stop();plugin.getLogger().log(java.util.logging.Level.SEVERE,"Логистика приостановлена, груз остаётся в журнале складов",ex);}}
    private boolean working(UUID town,String project){return ru.neverland.integration.BuildingOperations.active(town,project);}
    private void schedule(long now)throws IOException{
        if(jobs.size()>=settings.active())return;
        record Candidate(Network network,Route route){}List<Candidate> all=new ArrayList<>();for(var n:networks.values())for(var r:n.routes().values())if(r.enabled())all.add(new Candidate(n,r));
        all.sort(Comparator.comparing(c->c.network().town()+"/"+c.route().id()));if(all.isEmpty())return;
        for(int i=0;i<Math.min(8,all.size());i++){
            var c=all.get(Math.floorMod(cursor++,all.size()));var n=c.network();var r=c.route();String key=n.town()+"/"+r.id();
            if(now<cooldown.getOrDefault(key,0L))continue;
            var hub=depots.getOrDefault(n.town(),Map.of()).get(n.node(r.hub()).project());if(hub==null){routeStatuses.put(key,"Здание базы недоступно");continue;}if(!npcs.near(n.node(r.hub()).point())){routeStatuses.put(key,"Ожидает игроков рядом с базой");continue;}
            long count=jobs(n.town()).stream().filter(j->j.hub().equals(hub.id())).count();if(count>=settings.level(hub.level()).couriers()){routeStatuses.put(key,"Все курьеры базы заняты");continue;}
            try{var j=job(n,r);if(!working(j.town(),j.hub())||!working(j.town(),j.source())||!working(j.town(),j.target())){routeStatuses.put(key,"НЕАКТИВНО — проверьте содержание и питание зданий маршрута");continue;}boolean loaded=true;for(var path:List.of(j.outbound(),j.delivery(),j.home()))for(var p:path)if(!CourierNpcs.loaded(p)||!CourierNpcs.owned(n.town(),p)){loaded=false;break;}if(!loaded){routeStatuses.put(key,"Путь не загружен или потеряна территория");continue;}
                var stock=builds.stock(n.town(),j.source());var filter=BuildsStorage.decode(r.filter());int amount=0;for(var item:stock)if(item!=null&&!item.getType().isAir()&&(filter==null||item.isSimilar(filter)))amount+=item.getAmount();if(amount<=r.keep()){routeStatuses.put(key,"Нет подходящего груза сверх резерва");continue;}
                var copy=new LinkedHashMap<>(jobs);copy.put(j.id(),j);repository.save(networks,copy);jobs=copy;routeStatuses.put(key,"Курьер отправлен");return;
            }catch(IllegalArgumentException ex){routeStatuses.put(key,ex.getMessage());cooldown.put(key,now+10000);}
        }
    }
}
