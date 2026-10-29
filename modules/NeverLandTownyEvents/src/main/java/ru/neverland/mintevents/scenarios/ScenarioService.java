package ru.neverland.mintevents.scenarios;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.*;
import static ru.neverland.mintevents.scenarios.ScenarioBook.*;

/** Optional overlay: failures close new choices, while already acknowledged terms stay readable. */
public final class ScenarioService {
    public record Active(UUID id,UUID town,String townName,String eventId,long started,long endsAt,int progress,int goal,boolean paused,String mode){}
    public interface Sources {Active active(UUID town);Source observe(Scenario scenario);}
    private final JavaPlugin plugin;private final Sources sources;private final ScenarioRepository repository;private final Path config;
    private final ThreadPoolExecutor io;private final CompletableFuture<Void> ioClosed=new CompletableFuture<>();private final CompletableFuture<Void> loaded;
    private final Map<UUID,Active> waiting=new LinkedHashMap<>();private ScenarioCatalog catalog=new ScenarioCatalog(true,Map.of());
    private boolean configured,closing,runtimeBroken;private String problem="Сценарии загружаются";private BukkitTask task;private long pulseAt=System.nanoTime();private CompletionStage<Void> closed;
    public ScenarioService(JavaPlugin plugin,Sources sources){
        this.plugin=plugin;this.sources=sources;config=plugin.getDataFolder().toPath().resolve("scenarios.yml");repository=new ScenarioRepository(plugin.getDataFolder().toPath().resolve("scenarios-data.yml"));
        io=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),r->{var t=new Thread(r,"NeverLand-event-scenarios-io");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){ioClosed.complete(null);}};
        loaded=CompletableFuture.runAsync(()->{
            try{repository.load();}catch(Exception error){failed("Не удалось прочитать дополнительный журнал сценариев",error);}
            try{if(!Files.exists(config)){Files.createDirectories(config.getParent());try(var stream=plugin.getResource("scenarios.yml")){if(stream==null)throw new java.io.IOException("Нет встроенных сценариев");try{Files.copy(stream,config);}catch(FileAlreadyExistsException ignored){}}}publishCatalog(ScenarioCatalog.load(config));}
            catch(Exception error){failed("Не удалось прочитать настройки дополнительных сценариев",error);}
        },io);
    }
    public void start(){try(var guard=SuiteState.guard()){if(task!=null)task.cancel();pulseAt=System.nanoTime();task=SuiteScheduler.get().runTaskTimer(plugin,this::pulse,20,20);}}
    private void publishCatalog(ScenarioCatalog next){try(var guard=SuiteState.guard()){if(closing)return;catalog=next;configured=true;if(repository.ready()&&!runtimeBroken)problem="";}}
    private void failed(String message,Throwable error){try(var guard=SuiteState.guard()){problem=message;configured=false;}plugin.getLogger().log(java.util.logging.Level.WARNING,message+"; обычные события продолжают работать, новые решения приостановлены",error);}
    public void reload(){try(var guard=SuiteState.guard()){if(closing)return;configured=false;problem="Перечитываем настройки сценариев";try{io.execute(()->{try{publishCatalog(ScenarioCatalog.load(config));}catch(Exception error){failed("Неверные настройки сценариев",error);}});}catch(RejectedExecutionException error){failed("Очередь настроек сценариев занята",error);}}}
    public void register(Active source){try(var guard=SuiteState.guard()){
        if(closing||source==null||!Set.of("DROUGHT","FLOOD").contains(source.mode()))return;
        if(waiting.size()>=MAX_ACTIVE&&!waiting.containsKey(source.id())){plugin.getLogger().warning("Сценарий не добавлен: очередь новых событий заполнена; обычное событие продолжается");return;}
        waiting.putIfAbsent(source.id(),source);
    }}
    private void pulse(){try{pulseChecked();}catch(RuntimeException error){runtimeBroken=true;failed("Обработка дополнительного сценария приостановлена",error);}}
    private void pulseChecked(){
        long now=System.nanoTime(),elapsed=Math.min(10000,Math.max(0,(now-pulseAt)/1_000_000));pulseAt=now;
        if(closing||runtimeBroken||SuiteState.frozen(plugin.getName())||!repository.ready()||!repository.writable()||repository.busy())return;var before=repository.state();var next=before;
        var observations=new HashMap<UUID,Source>();for(var row:before.active()){
            Source observation;try{observation=sources.observe(row);}catch(RuntimeException error){continue;}
            if(!catalog.enabled()&&observation.state()==SourceState.RUNNING)observation=new Source(SourceState.PAUSED,observation.progress(),observation.goal());observations.put(row.id(),observation);
        }
        next=ScenarioBook.advance(next,elapsed,observations,System.currentTimeMillis());
        if(configured){for(var entry:new ArrayList<>(waiting.entrySet())){
            var requested=entry.getValue();var current=sources.active(requested.town());
            if(current==null||!current.id().equals(requested.id())||!catalog.enabled()){waiting.remove(entry.getKey());continue;}
            var terms=catalog.definitions().get(requested.mode());if(terms==null){waiting.remove(entry.getKey());continue;}
            if(next.entries().containsKey(requested.id())){waiting.remove(entry.getKey());continue;}
            if(next.entries().size()>=MAX_HISTORY||next.active().size()>=MAX_ACTIVE){waiting.remove(entry.getKey());plugin.getLogger().warning("Журнал сценариев заполнен; обычное событие "+requested.id()+" работает без нового плана");continue;}
            next=ScenarioBook.begin(next,ScenarioBook.fresh(requested.id(),requested.town(),requested.townName(),requested.eventId(),requested.started(),terms));
        }}
        if(!before.equals(next)){var committed=next;repository.commit(before,next).whenComplete((saved,error)->{
            if(error!=null){failed("Запись сценариев не подтверждена",error);return;}
            for(var row:committed.entries().values()){var old=before.entries().get(row.id());if(old==null||old.phase()!=row.phase())audit(row,row.phase().name(),old==null?"Дополнительный план начат":"Этап сценария сохранён");}
        });}
    }
    public boolean ready(){try(var guard=SuiteState.guard()){return repository.ready();}}
    public Optional<Scenario> find(UUID instance){try(var guard=SuiteState.guard()){return Optional.ofNullable(repository.state().entries().get(instance));}}
    public Optional<Scenario> current(UUID town){try(var guard=SuiteState.guard()){
        var source=sources.active(town);if(source!=null){var row=repository.state().entries().get(source.id());return Optional.ofNullable(row);}
        return repository.state().entries().values().stream().filter(r->r.town().equals(town)).max(Comparator.comparingLong(Scenario::started).thenComparing(Scenario::id));
    }}
    public Mix effects(UUID instance){try(var guard=SuiteState.guard()){
        if(!catalog.enabled()||SuiteState.frozen(plugin.getName()))return Mix.neutral();var row=repository.state().entries().get(instance);return row==null?Mix.neutral():row.effects();
    }}
    public String problem(){try(var guard=SuiteState.guard()){
        if(!problem.isBlank())return problem;if(!catalog.enabled())return "Дополнительные планы приостановлены администратором";if(!repository.writable())return "Запись недоступна: новые решения приостановлены";if(repository.state().entries().size()>=MAX_HISTORY)return "Журнал заполнен: новые решения приостановлены";return "";
    }}
    public boolean canChoose(Player player,Scenario row){try(var guard=SuiteState.guard()){
        if(closing||SuiteState.frozen(plugin.getName())||!problem().isBlank()||row.phase()!=Phase.DECISION||repository.busy())return false;try{mayor(player,row.town());var source=sources.active(row.town());return source!=null&&source.id().equals(row.id())&&!source.paused()&&source.endsAt()>System.currentTimeMillis();}catch(RuntimeException denied){return false;}
    }}
    private void mayor(Player player,UUID town){
        if(!player.isOnline()||!player.hasPermission("mintevents.use")||!player.hasPermission("mintevents.plan"))throw new IllegalArgumentException("Нет доступа к решениям при событиях");var resident=TownyAPI.getInstance().getResident(player);var city=resident==null?null:resident.getTownOrNull();if(city==null||!city.getUUID().equals(town)||!city.isMayor(resident))throw new IllegalArgumentException("План действий выбирает мэр своего города");
    }
    public CompletionStage<Scenario> choose(Player player,UUID instance,String plan){try(var guard=SuiteState.guard()){
        if(closing||SuiteState.frozen(plugin.getName())||!plugin.isEnabled())throw new IllegalArgumentException("Сценарии остановлены");var before=repository.state();var row=ScenarioBook.require(before,instance);mayor(player,row.town());if(!problem().isBlank())throw new IllegalArgumentException(problem());if(repository.busy())throw new IllegalArgumentException("Предыдущее изменение сохраняется");var source=sources.active(row.town());if(source==null||!source.id().equals(instance)||source.paused()||source.endsAt()<=System.currentTimeMillis())throw new IllegalArgumentException("Событие изменилось или приостановлено");
        var next=ScenarioBook.choose(before,instance,plan,player.getUniqueId(),player.getName(),System.currentTimeMillis());return repository.commit(before,next).thenApply(saved->{var chosen=saved.entries().get(instance);audit(chosen,"CHOSEN","Мэр выбрал "+chosen.terms().plan(plan).name()+"; дополнительных списаний нет");return chosen;});
    }}
    private void audit(Scenario row,String outcome,String note){
        AuditRecord.Party actor=row.actor()==null?AuditRecord.Party.system():new AuditRecord.Party("PLAYER",row.actor().toString(),row.actorName(),row.town().toString());var town=new AuditRecord.Party("TOWN",row.town().toString(),row.townName(),row.town().toString());String step=outcome+":"+row.chosen()+":"+row.ended();var record=new AuditRecord(AuditRecord.id(plugin.getName(),row.id().toString(),step),System.currentTimeMillis(),plugin.getName(),"CITY_EVENT_PLAN",outcome,row.id().toString(),actor,town,town,row.choice(),0,"",note+"; "+ScenarioMenus.stage(row)+"; source="+row.eventId()+"; result="+row.outcome());
        try{io.execute(()->AuditTrail.record(plugin,record));}catch(RejectedExecutionException full){plugin.getLogger().severe("AUDIT GAP: очередь сценариев заполнена; решение "+row.id()+" сохранено в основном журнале сценариев");}
    }
    public CompletionStage<Void> prepareClose(){try(var guard=SuiteState.guard()){
        if(closed!=null)return closed;closing=true;waiting.clear();if(task!=null)task.cancel();closed=loaded.handle((ok,error)->null).thenCompose(ok->repository.prepareClose()).handle((ok,error)->{if(error!=null)plugin.getLogger().warning("Сценарии: запись при остановке не подтверждена: "+error.getMessage());io.shutdown();return null;}).thenCompose(ok->ioClosed);return closed;
    }}
    public void awaitClosed(){var future=prepareClose().toCompletableFuture();if(SuiteState.held()&&!future.isDone())throw new IllegalStateException("Ожидание сценариев должно выполняться вне общей блокировки");try{future.get(10,TimeUnit.SECONDS);}catch(Exception error){plugin.getLogger().warning("Дополнительные сценарии ещё завершают запись: "+error.getMessage());}}
}
