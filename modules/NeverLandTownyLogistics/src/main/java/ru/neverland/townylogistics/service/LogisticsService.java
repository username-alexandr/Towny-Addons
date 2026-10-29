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
import java.util.concurrent.*;
import ru.neverland.core.*;

public final class LogisticsService {
    private final JavaPlugin plugin;private final LogisticsRepository repository;private final BuildsStorage builds;private final CourierNpcs npcs;
    private Map<UUID,Network> networks;private Map<UUID,CourierJob> jobs;private final Map<UUID,String> statuses=new HashMap<>();private final Map<String,Long> cooldown=new HashMap<>();private final Map<String,String> routeStatuses=new HashMap<>();
    private Map<UUID,Map<String,BuildsStorage.Depot>> depots=new HashMap<>();private long saved;private int cursor,jobCursor;private boolean fault;private BukkitTask task;
    private LogisticsSettings settings;private boolean loaded,persisting,closing;private CompletionStage<LogisticsRepository.Snapshot> writing=CompletableFuture.completedFuture(null);private CompletionStage<Void> closed;
    private final RouteCache paths=new RouteCache();private RouteIndex index=new RouteIndex();
    private record RouteKey(UUID town,String route){}
    private record Loaded(LogisticsRepository.Snapshot snapshot,RouteIndex index){}
    private final Map<RouteKey,Map<Link,Long>> blocked=new HashMap<>();private final Map<UUID,Long> detourRetry=new HashMap<>();private final Set<UUID> rerouting=new HashSet<>();
    private final ArrayDeque<UUID> depotQueue=new ArrayDeque<>(),recoveryQueue=new ArrayDeque<>();private final Map<UUID,Long> depotAt=new HashMap<>();private final Map<UUID,UUID> acknowledgements=new LinkedHashMap<>();
    public LogisticsService(JavaPlugin plugin,LogisticsRepository repository,BuildsStorage builds,LogisticsSettings settings,LogisticsRepository.Snapshot state){
        this.plugin=plugin;this.repository=repository;this.builds=builds;this.settings=settings;this.networks=new HashMap<>(state.networks());this.jobs=new LinkedHashMap<>(state.jobs());
        npcs=new CourierNpcs(plugin,settings,e->{var j=jobs.get(e.getKey());if(j!=null&&!persisting&&CourierNpcs.owned(j.town(),e.getValue()))jobs.put(j.id(),j.move(e.getValue(),j.waypoint()));});
    }
    public void start(){try(var guard=SuiteState.guard()){
        npcs.start();plugin.getServer().getPluginManager().registerEvents(new LogisticsChanges(this),plugin);task=SuiteScheduler.get().runTaskTimer(plugin,this::tick,20,10);
        repository.loadAsync().thenCompose(snapshot->paths.index(snapshot.networks()).thenApply(indexed->new Loaded(snapshot,indexed))).whenComplete((loadedState,error)->{if(error!=null){if(!closing)fail(error);return;}if(closing||!plugin.isEnabled())return;SuiteScheduler.get().runTask(plugin,()->{if(closing)return;var snapshot=loadedState.snapshot();index=loadedState.index();networks=new HashMap<>(snapshot.networks());jobs=new LinkedHashMap<>(snapshot.jobs());depotQueue.addAll(new TreeSet<>(networks.keySet()));try{recoveryQueue.addAll(builds.shipmentTowns());loaded=true;}catch(Exception ex){fail(ex);}});});
    }}
    public CompletionStage<Void> prepareClose(){try(var guard=SuiteState.guard()){
        if(closed!=null)return closed;closing=true;if(task!=null)task.cancel();npcs.stop();rerouting.clear();var computed=paths.prepareClose();
        var finalSave=writing.handle((v,e)->null).thenCompose(ignored->{try(var locked=SuiteState.guard()){if(!loaded||fault||!repository.writable())return CompletableFuture.completedFuture(repository.state());return repository.commit(repository.state(),new LogisticsRepository.Snapshot(networks,jobs));}});
        closed=finalSave.handle((v,error)->error).thenCompose(error->repository.prepareClose().thenCombine(computed,(a,b)->error)).thenCompose(error->error==null?CompletableFuture.completedFuture(null):CompletableFuture.failedFuture(error));return closed;
    }}
    public void stop(){prepareClose();}
    public void awaitClosed(){var future=prepareClose().toCompletableFuture();if(SuiteState.held()&&!future.isDone())throw new IllegalStateException("Ожидайте запись логистики вне общей блокировки");try{future.get(10,TimeUnit.SECONDS);}catch(Exception error){throw new IllegalStateException("Логистика ещё завершает запись",error);}}
    public void reload(LogisticsSettings value){try(var guard=SuiteState.guard()){requireReady();settings=value;npcs.settings(value);for(UUID town:networks.keySet()){paths.forget(town);depotAt.remove(town);}blocked.clear();}}
    private void requireReady(){if(closing||!loaded||fault||!repository.writable())throw new IllegalStateException(!loaded?"Логистика ещё загружает сохранённые маршруты":"Логистика приостановлена; груз сохранён");if(persisting||repository.busy())throw new IllegalStateException("Предыдущее действие сохраняется; повторите через секунду");}
    private void fail(Throwable error){try(var guard=SuiteState.guard()){fault=true;npcs.stop();plugin.getLogger().log(java.util.logging.Level.SEVERE,"Логистика приостановлена; пути и груз сохранены",error);}}
    private CompletionStage<LogisticsRepository.Snapshot> persist(Map<UUID,Network> nextNetworks,Map<UUID,CourierJob> nextJobs){
        requireReady();persisting=true;var next=new LogisticsRepository.Snapshot(nextNetworks,nextJobs);
        writing=repository.commit(repository.state(),next).whenComplete((snapshot,error)->{try(var guard=SuiteState.guard()){persisting=false;if(error!=null){fail(error);return;}networks=new HashMap<>(snapshot.networks());jobs=new LinkedHashMap<>(snapshot.jobs());saved=System.currentTimeMillis();}});return writing;
    }
    public LogisticsSettings settings(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return settings;} }public BuildsStorage builds(){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {return builds;} }public boolean paused(){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {return fault||!loaded;} }
    public Network network(UUID id){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {return networks.getOrDefault(id,Network.empty(id));} }
    public List<CourierJob> jobs(UUID town){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {return jobs.values().stream().filter(j->j.town().equals(town)).toList();} }
    public String routeStatus(UUID town,String route){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {
        if(!loaded)return "Загружаем сохранённые маршруты";if(fault)return "Приостановлено из-за ошибки записи; груз сохранён";if(persisting)return "Сохраняем изменение маршрутов; груз сохранён";if(!network(town).route(route).enabled())return "Приостановлен; текущий груз завершит доставку";
        var active=jobs(town).stream().filter(j->j.route().equals(route)).toList();if(!active.isEmpty())return status(active.get(0))+" • курьеров: "+active.size();
        if(jobs.size()>=settings.active())return "Достигнут общий предел курьеров";
        return routeStatuses.getOrDefault(town+"/"+route,"Ожидает проверки маршрута");
    } }
    public String status(CourierJob j){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {return statuses.getOrDefault(j.id(),j.phase().title)+(j.detours()>0?" • обходов: "+j.detours():"");} }
    public String readiness(){try(var guard=SuiteState.guard()){return !loaded?"Загружаем сохранённые маршруты":fault?"Запись недоступна; груз сохранён":persisting?"Сохраняем изменения":"Логистика работает";}}
    public Town town(Player p){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {var resident=TownyAPI.getInstance().getResident(p);return resident==null?null:resident.getTownOrNull();} }
    public boolean manager(Player p,Town t){ try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {var resident=TownyAPI.getInstance().getResident(p);return resident!=null&&t.equals(resident.getTownOrNull())&&(t.isMayor(resident)||resident.hasTownRank("assistant")||p.hasPermission("neverlandtownylogistics.manage"));} }
    public Map<String,BuildsStorage.Depot> depots(UUID town)throws IOException{ try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {return builds.depots(town);} }
    private CompletionStage<LogisticsRepository.Snapshot> save(){return persist(networks,jobs);}
    public CompletionStage<LogisticsRepository.Snapshot> change(Network next,boolean topology){try(var guard=SuiteState.guard()){
        requireReady();if(topology&&!jobs(next.town()).isEmpty())throw new IllegalArgumentException("Сначала приостановите маршруты и дождитесь курьеров; свободных можно снять командой dismiss");
        var before=network(next.town());var copy=new HashMap<>(networks);copy.put(next.town(),next);return persist(copy,jobs).thenApply(saved->{try(var locked=SuiteState.guard()){paths.changed(before,next);index.update(next);for(String oldRoute:before.routes().keySet())if(!next.routes().containsKey(oldRoute)){blocked.remove(new RouteKey(next.town(),oldRoute));cooldown.remove(next.town()+"/"+oldRoute);routeStatuses.remove(next.town()+"/"+oldRoute);}depotAt.remove(next.town());if(!depotQueue.contains(next.town()))depotQueue.add(next.town());return saved;}});
    }}
    public CompletionStage<LogisticsRepository.Snapshot> dismiss(UUID town,String route)throws IOException{try(var guard=SuiteState.guard()){
        requireReady();var cargo=builds.shipments(town);if(jobs(town).stream().anyMatch(j->j.route().equals(route)&&cargo.containsKey(j.id())&&cargo.get(j.id()).transit()))throw new IllegalArgumentException("В маршруте есть груз; дождитесь доставки или восстановления администратором");
        var removed=jobs(town).stream().filter(j->j.route().equals(route)).toList();var copy=new LinkedHashMap<>(jobs);removed.forEach(j->copy.remove(j.id()));return persist(networks,copy).thenApply(saved->{try(var locked=SuiteState.guard()){removed.forEach(j->forgetCourier(j.id()));for(var shipment:cargo.values())if(shipment.route().equals(route)&&!shipment.transit())acknowledgements.put(shipment.id(),town);return saved;}});
    }}
    public CompletionStage<Boolean> recover(UUID town,UUID id)throws IOException{try(var guard=SuiteState.guard()){
        requireReady();if(!builds.unload(town,id,true))return CompletableFuture.completedFuture(false);var copy=new LinkedHashMap<>(jobs);copy.remove(id);return persist(networks,copy).thenApply(saved->{try(var locked=SuiteState.guard()){forgetCourier(id);acknowledgements.put(id,town);return true;}});
    }}
    public void validateNode(UUID town,Node node)throws IOException{ try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {
        if(!CourierNpcs.owned(town,node.point())||!CourierNpcs.standable(node.point()))throw new IllegalArgumentException("Встаньте на безопасный проход своего города или у своего осадного лагеря");
        if(node.kind()==Kind.BUILDING){var depot=depots(town).get(node.project());if(depot==null||!depot.contains(node.point()))throw new IllegalArgumentException("Точка погрузки должна быть у выбранного построенного здания");}
    } }
    public void validateLink(UUID town,Node a,Node b){ try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {
        double distance=a.point().distance(b.point());if(!Double.isFinite(distance)||distance>settings.linkDistance())throw new IllegalArgumentException("Связь не длиннее "+settings.linkDistance()+" блоков, в одном мире");
        int steps=Math.max(1,(int)Math.ceil(distance));for(int i=0;i<=steps;i++){double f=(double)i/steps;var p=new Position(a.point().world(),a.point().x()+(b.point().x()-a.point().x())*f,a.point().y()+(b.point().y()-a.point().y())*f,a.point().z()+(b.point().z()-a.point().z())*f);
            if(!CourierNpcs.owned(town,p)||!CourierNpcs.loaded(p))throw new IllegalArgumentException("Связь проходит через чужую или незагруженную территорию");}
    } }
    public CourierJob job(Network n,Route r)throws IOException{ try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {
        var hub=n.node(r.hub());var source=n.node(r.source());var target=n.node(r.target());var stock=depots(n.town());
        for(var node:List.of(hub,source,target)){if(node.kind()!=Kind.BUILDING||!stock.containsKey(node.project()))throw new IllegalArgumentException("Нет построенного здания для узла "+node.id());if(!stock.get(node.project()).contains(node.point())||!CourierNpcs.owned(n.town(),node.point()))throw new IllegalArgumentException("Точка здания потеряла привязку или территорию");}
        if(!settings.hubs().contains(hub.project()))throw new IllegalArgumentException("Узел отправки должен быть складом, терминалом, караван-сараем или портом");
        if(source.project().startsWith("raid_camp:"))throw new IllegalArgumentException("Лагерь может быть только получателем снабжения");
        if(source.project().equals(target.project()))throw new IllegalArgumentException("Отправитель и получатель должны быть разными зданиями");
        BuildsStorage.decode(r.filter());
        var excluded=excluded(n.town(),r.id());var out=planned(n,r.hub(),r.source(),excluded,settings.routeDistance());var delivery=planned(n,r.source(),r.target(),excluded,settings.routeDistance());var home=planned(n,r.target(),r.hub(),excluded,settings.routeDistance());
        double length=0;for(var path:List.of(out,delivery,home))for(int i=1;i<path.size();i++)length+=path.get(i-1).distance(path.get(i));if(length>settings.routeDistance())throw new IllegalArgumentException("Полный рейс слишком длинный");
        return new CourierJob(UUID.randomUUID(),n.town(),r.id(),hub.project(),source.project(),target.project(),Math.min(5,stock.get(hub.project()).level()),out,delivery,home,Phase.TO_SOURCE,0,hub.point(),0);
    } }
    private List<Position> planned(Network network,String from,String to,Set<Link> excluded,double maximum){var reading=paths.read(network,from,to,excluded,maximum);if(!reading.status().equals("READY"))throw new IllegalArgumentException(reading.explanation());if(!reading.result().ready())throw new IllegalArgumentException(reading.result().explanation());return reading.result().paths().get(0).points();}
    private Set<Link> excluded(UUID town,String route){var value=blocked.get(new RouteKey(town,route));if(value==null)return Set.of();long now=System.currentTimeMillis();value.values().removeIf(until->until<=now);return Set.copyOf(value.keySet());}
    private void reconcileOne()throws IOException{
        UUID town=recoveryQueue.poll();if(town==null)return;boolean retry=false;
        for(var cargo:builds.shipments(town).values())if(!jobs.containsKey(cargo.id())){
            if(!cargo.transit()){acknowledgements.put(cargo.id(),town);continue;}var network=network(town);var route=network.routes().get(cargo.route());if(route==null){routeStatuses.put(town+"/"+cargo.route(),"Груз сохранён, но маршрут удалён; нужен возврат администратора");continue;}
            CourierJob planned;try{planned=job(network,route);}catch(IllegalArgumentException error){routeStatuses.put(town+"/"+cargo.route(),error.getMessage());retry=true;continue;}
            if(!planned.source().equals(cargo.source())||!planned.target().equals(cargo.target())){routeStatuses.put(town+"/"+cargo.route(),"Склад отправителя или получателя изменён; нужен возврат администратора");continue;}
            var job=new CourierJob(cargo.id(),town,cargo.route(),planned.hub(),cargo.source(),cargo.target(),planned.level(),planned.outbound(),planned.delivery(),planned.home(),Phase.TO_TARGET,0,planned.delivery().get(0),0);var copy=new LinkedHashMap<>(jobs);copy.put(job.id(),job);persist(networks,copy);retry=true;break;
        }if(retry)recoveryQueue.add(town);
    }
    private void refreshOne()throws IOException{UUID town=depotQueue.poll();if(town==null)return;depotQueue.add(town);long now=System.currentTimeMillis();if(now-depotAt.getOrDefault(town,0L)<5000)return;depots.put(town,builds.depots(town));depotAt.put(town,now);}
    private void tick(){if(fault||!loaded||closing||persisting||repository.busy())return;try{
        long now=System.currentTimeMillis();refreshOne();npcs.beginTick();int operations=settings.cargoBudget();if(!acknowledgements.isEmpty()){var ack=acknowledgements.entrySet().iterator().next();builds.acknowledge(ack.getValue(),ack.getKey());acknowledgements.remove(ack.getKey());operations--;}reconcileOne();if(persisting)return;
        List<CourierJob> queue=new ArrayList<>(jobs.values());if(!queue.isEmpty())Collections.rotate(queue,-Math.floorMod(jobCursor++,queue.size()));
        for(var original:queue){var j=jobs.get(original.id());if(j==null||rerouting.contains(j.id()))continue;
            var available=depots.getOrDefault(j.town(),Map.of());if(!available.containsKey(j.hub())||!available.containsKey(j.source())||!available.containsKey(j.target())){npcs.remove(j.id());statuses.put(j.id(),"Ожидает восстановления здания");continue;}
            if(j.phase()!=Phase.DONE&&(!working(j.town(),j.hub())||!working(j.town(),j.source())||!working(j.town(),j.target()))){npcs.remove(j.id());statuses.put(j.id(),"НЕАКТИВНО — проверьте содержание и питание зданий маршрута; груз сохранён");continue;}
            int level=Math.max(1,Math.min(j.level(),available.get(j.hub()).level()));var path=j.path();
            boolean moving=j.phase()==Phase.TO_SOURCE||j.phase()==Phase.TO_TARGET||j.phase()==Phase.RETURNING;
            Position target=path.get(moving?Math.min(j.waypoint(),path.size()-1):path.size()-1);
            boolean handling=j.phase()==Phase.LOADING||j.phase()==Phase.UNLOADING;
            var motion=npcs.tick(j,target,moving||(handling&&j.position().distance(target)>1.2),level);j=j.move(motion.position(),j.waypoint());jobs.put(j.id(),j);
            if(!motion.active()){statuses.put(j.id(),motion.status());if(moving&&motion.reason().reroute)tryReroute(j,motion.reason(),now);if(persisting)return;continue;}statuses.put(j.id(),j.phase().title);
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
                operations--;var finished=j;var copy=new LinkedHashMap<>(jobs);copy.remove(j.id());persist(networks,copy).thenRun(()->{try(var guard=SuiteState.guard()){forgetCourier(finished.id());acknowledgements.put(finished.id(),finished.town());}});return;
            }
        }
        if(now-saved>=settings.checkpoint()*1000L){save();return;}schedule(now);
    }catch(Exception ex){fail(ex);}}
    private void forgetCourier(UUID id){npcs.remove(id);statuses.remove(id);detourRetry.remove(id);rerouting.remove(id);}
    private boolean working(UUID town,String project){if(project.startsWith("raid_camp:"))return depots.getOrDefault(town,Map.of()).containsKey(project);return ru.neverland.integration.BuildingOperations.active(town,project);}
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
                var copy=new LinkedHashMap<>(jobs);copy.put(j.id(),j);persist(networks,copy).thenRun(()->{try(var guard=SuiteState.guard()){routeStatuses.put(key,"Курьер отправлен");}});return;
            }catch(IllegalArgumentException ex){routeStatuses.put(key,ex.getMessage());cooldown.put(key,now+10000);}
        }
    }
    private static String atNode(Network network,Position position){String found=null;for(var node:network.nodes().values())if(node.point().equals(position)){if(found!=null)return null;found=node.id();}return found;}
    private static double length(List<Position> points){double value=0;for(int i=1;i<points.size();i++)value+=points.get(i-1).distance(points.get(i));return value;}
    private void tryReroute(CourierJob job,StopReason cause,long now){
        if(now<detourRetry.getOrDefault(job.id(),0L)||rerouting.contains(job.id()))return;
        if(job.detours()>=settings.maximumDetours()){statuses.put(job.id(),"Достигнут предел обходов; исправьте дорогу и нажмите «Перепроверить путь». Груз сохранён");return;}
        var oldPath=job.path();int step=Math.min(job.waypoint(),oldPath.size()-1);if(step<1)return;var graph=network(job.town());Position anchor=oldPath.get(step-1);String from=atNode(graph,anchor),blockedNode=atNode(graph,oldPath.get(step)),to=atNode(graph,oldPath.get(oldPath.size()-1));
        if(from==null||to==null||blockedNode==null||from.equals(blockedNode)){statuses.put(job.id(),"Точки пути удалены или совпадают; проверьте узлы сети. Груз сохранён");return;}
        Link failed=new Link(from,blockedNode);var routeKey=new RouteKey(job.town(),job.route());blocked.computeIfAbsent(routeKey,key->new HashMap<>()).put(failed,now+settings.blockedSeconds()*1000L);
        double other=job.phase()==Phase.TO_SOURCE?length(job.delivery())+length(job.home()):job.phase()==Phase.TO_TARGET?length(job.outbound())+length(job.home()):length(job.outbound())+length(job.delivery());double maximum=settings.routeDistance()-other-job.position().distance(anchor);
        if(maximum<=0){statuses.put(job.id(),"Обход превышает длину рейса; сократите дорожную сеть");return;}
        var reading=paths.read(graph,from,to,excluded(job.town(),job.route()),maximum);if(!reading.status().equals("READY")){statuses.put(job.id(),reading.explanation()+"; груз сохранён");return;}
        if(!reading.result().ready()){statuses.put(job.id(),cause.text+". "+reading.result().explanation()+"; груз сохранён");detourRetry.put(job.id(),now+settings.retry()*1000L);return;}
        var selected=reading.result().paths().get(0);if(selected.links().contains(failed)||selected.points().equals(oldPath))return;
        for(Position point:selected.points())if(!CourierNpcs.loaded(point)||!CourierNpcs.owned(job.town(),point)){statuses.put(job.id(),"Запасная дорога проходит по незагруженной или чужой территории; груз сохранён");detourRetry.put(job.id(),now+settings.retry()*1000L);return;}
        rerouting.add(job.id());npcs.halt(job.id());statuses.put(job.id(),"Проверяем обратный проход до узла "+from+"; груз сохранён");
        npcs.canReturn(job,anchor).whenComplete((proof,error)->{if(closing||!plugin.isEnabled())return;SuiteScheduler.get().runTask(plugin,()->{
            rerouting.remove(job.id());detourRetry.put(job.id(),System.currentTimeMillis()+settings.retry()*1000L);var current=jobs.get(job.id());if(closing||current==null||current.phase()!=job.phase()||current.waypoint()!=job.waypoint()||!current.path().equals(oldPath))return;
            if(error!=null||proof==null||!proof.reachable()){statuses.put(job.id(),error!=null?"Проверка обратного пути не завершилась; груз сохранён":proof==null?"Обратный путь не подтверждён":proof.explanation()+"; груз сохранён");return;}
            var currentGraph=network(job.town());if(!currentGraph.nodes().equals(graph.nodes())||!currentGraph.links().equals(graph.links())||persisting||repository.busy()){statuses.put(job.id(),"Сеть изменилась; перепроверяем обход");return;}
            String note="Обход "+(current.detours()+1)+": "+String.join(" → ",selected.nodes());if(note.length()>300)note=note.substring(0,297)+"...";
            var replacement=current.reroute(selected.points(),proof.position(),proof.reachable(),note);var copy=new LinkedHashMap<>(jobs);copy.put(job.id(),replacement);statuses.put(job.id(),"Сохраняем обход перед продолжением движения");
            persist(networks,copy).whenComplete((saved,error2)->{if(error2==null)try(var guard=SuiteState.guard()){npcs.halt(job.id());statuses.put(job.id(),replacement.recoveryNote());}});
        });});
    }
    public void changedChunk(UUID world,int x,int z){try(var guard=SuiteState.guard()){if(closing||!loaded)return;for(var change:index.affected(world,x,z).entrySet()){
        paths.invalidateLinks(change.getKey(),change.getValue());for(var row:blocked.entrySet())if(row.getKey().town().equals(change.getKey()))row.getValue().keySet().removeAll(change.getValue());
        for(var job:jobs(change.getKey()))if(job.path().stream().anyMatch(point->point.world().equals(world)&&(int)Math.floor(point.x()/16)==x&&(int)Math.floor(point.z()/16)==z)){detourRetry.remove(job.id());npcs.halt(job.id());}
    }}}
    public CompletionStage<LogisticsRepository.Snapshot> recalculate(UUID town,String route){try(var guard=SuiteState.guard()){
        requireReady();var network=network(town);var setting=network.route(route);var copy=new LinkedHashMap<>(jobs);for(var job:jobs(town))if(job.route().equals(route)){if(rerouting.contains(job.id()))throw new IllegalArgumentException("Обход уже проверяется; дождитесь результата");copy.put(job.id(),new CourierJob(job.id(),job.town(),job.route(),job.hub(),job.source(),job.target(),job.level(),job.outbound(),job.delivery(),job.home(),job.phase(),job.waypoint(),job.position(),job.handlingTicks(),0,"Путь перепроверяется по запросу города"));}
        return persist(networks,copy).thenApply(saved->{try(var locked=SuiteState.guard()){blocked.remove(new RouteKey(town,route));paths.invalidateDestinations(town,new HashSet<>(List.of(setting.hub(),setting.source(),setting.target())));cooldown.remove(town+"/"+route);for(var job:jobs(town))if(job.route().equals(route)){detourRetry.remove(job.id());npcs.halt(job.id());statuses.remove(job.id());}return saved;}});
    }}
    public List<String> routeDetails(UUID town,String route){try(var guard=SuiteState.guard()){
        var network=network(town);var setting=network.route(route);var result=new ArrayList<String>();result.add("Курьер использует только связанные точки вашей сети.");int alternatives=0;boolean waiting=false;for(var leg:List.of(List.of(setting.hub(),setting.source()),List.of(setting.source(),setting.target()),List.of(setting.target(),setting.hub()))){var read=paths.read(network,leg.get(0),leg.get(1),excluded(town,route),settings.routeDistance());if(read.status().equals("READY")){if(read.result().ready())alternatives+=Math.max(0,read.result().paths().size()-1);else result.add(read.result().explanation());}else waiting=true;}
        result.add(waiting?"Дополнительные дороги ещё проверяются":"Запасных вариантов по связям сети: "+alternatives);result.add("Свободный проход проверяется самим курьером.");result.add("При препятствии он сначала возвращается к последней точке.");for(var job:jobs(town))if(job.route().equals(route)&&!job.recoveryNote().isBlank()){result.add(job.recoveryNote());break;}return List.copyOf(result);
    }}
    public RouteCache pathCache(){return paths;}
    public List<ru.neverland.core.ActivityAdmin.Target> adminTargets() { try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) {
        ru.neverland.core.ApiServices.primaryThread();
        var result=new ArrayList<ru.neverland.core.ActivityAdmin.Target>();
        for(var network:networks.values())for(var route:network.routes().values()) {
            UUID town=network.town();String id=route.id();
            result.add(new ru.neverland.core.ActivityAdmin.Target(town+"/"+id,"Маршрут "+id,Set.of("status","pause","resume"),(action,minutes)->{
                if(action.equals("status"))return routeStatus(town,id);
                var current=network(town);var routes=new HashMap<>(current.routes());boolean enabled=action.equals("resume");
                if(current.route(id).enabled()==enabled)throw new IllegalArgumentException(enabled?"Маршрут уже работает":"Маршрут уже приостановлен");
                routes.put(id,current.route(id).enabled(enabled));change(new Network(town,current.nodes(),current.links(),routes),false);
                return "Изменение сохраняется; проверьте состояние маршрута через секунду";
            }));
        }
        return List.copyOf(result);
    } }
}
