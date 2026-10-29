package ru.neverland.townyspecialization.production;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.*;
import ru.neverland.townyspecialization.api.ProfileGoodsApi;
import static ru.neverland.townyspecialization.production.ProfileGoodsBook.*;

/** The single production owner authorizes both mayors and acknowledges its own atomic stock ledger. */
public final class ProfileGoodsProvider implements ProfileGoodsApi {
    private record Actor(UUID city,UUID player,String name){}
    private final JavaPlugin plugin;private final ProductionService production;private final ProductionRepository repo;
    private final Set<CompletableFuture<?>> requests=ConcurrentHashMap.newKeySet(),waitingOwners=ConcurrentHashMap.newKeySet();
    private final org.bukkit.scheduler.BukkitTask maintenance;private final CompletableFuture<Void> boot;
    private final ProfileGoodsCache cache=new ProfileGoodsCache();private volatile boolean loaded,closing;private CompletableFuture<Void> closed;
    public ProfileGoodsProvider(JavaPlugin plugin,ProductionService production){
        this.plugin=plugin;this.production=production;repo=production.repository();boot=repo.ready().thenCompose(v->refresh()).toCompletableFuture();
        boot.whenComplete((v,e)->{if(e!=null)plugin.getLogger().warning("Сезонные запасы и обмены недоступны; прежняя специализация сохранена: "+e.getMessage());});
        maintenance=SuiteScheduler.get().runTaskTimer(plugin,this::pulse,100,100);
    }
    private CompletionStage<Void> refresh(){return repo.goodsSnapshot().thenAccept(snapshot->{cache.publish(snapshot);loaded=true;});}
    private CompletionStage<Map<String,Object>> saved(Entry row){return refresh().thenApply(ignored->row.view());}
    @Override public boolean ready(){return !closing&&loaded&&repo.available()&&plugin.isEnabled()&&!SuiteState.frozen(plugin.getName());}
    @Override public List<Map<String,Object>> active(){return cache.active();}
    private void gate(boolean creating){if(!ready())throw new IllegalStateException("Каталог продукции временно недоступен; сохранённые партии не изменены");if(creating&&!production.catalog().enabled())throw new IllegalArgumentException("Новые операции с продукцией приостановлены; резервы можно отменить");}
    private Actor mayor(Player player){
        if(!player.isOnline()||!player.hasPermission("neverlandtownyseasons.use")||!player.hasPermission("neverlandtownyseasons.manage"))throw new IllegalArgumentException("Нет права распоряжаться сезонными запасами");
        var resident=TownyAPI.getInstance().getResident(player);var city=resident==null?null:resident.getTownOrNull();if(city==null||!city.isMayor(resident))throw new IllegalArgumentException("Распоряжаться запасами и обменами может мэр своего города");return new Actor(city.getUUID(),player.getUniqueId(),player.getName());
    }
    private void world(UUID city,UUID expected){var town=TownyAPI.getInstance().getTown(city);var home=town==null?null:town.getHomeBlockOrNull();var actual=home==null?null:Bukkit.getWorld(home.getWorld().getName());if(actual==null||!actual.getUID().equals(expected))throw new IllegalArgumentException("Города должны находиться в одном мире общего календаря");}
    @SuppressWarnings("unchecked")private Map<String,Object> calendar(UUID world){try{var link=ApiServices.connect("NeverLandTownySeasons","ru.neverland.townyseasons.api.TownySeasonsApi",1,"seasonalMarket");if(!link.ready())throw new IllegalStateException("Сезонный календарь ярмарки недоступен");return (Map<String,Object>)link.invoke("seasonalMarket",new Class<?>[]{UUID.class},world);}catch(ReflectiveOperationException e){throw new IllegalStateException("Сезонный календарь ярмарки недоступен",e);}}
    private void window(UUID world,String period,boolean offer){var view=calendar(world);if(!period.equals(view.get("period")))throw new IllegalArgumentException("Сезон изменился; откройте новое подтверждение");if(!Boolean.TRUE.equals(view.get("enabled"))||offer&&!Boolean.TRUE.equals(view.get("open")))throw new IllegalArgumentException(String.valueOf(view.getOrDefault("reason","Условия ярмарки изменились; откройте меню заново")));}
    private <T>CompletionStage<T> track(Supplier<CompletionStage<T>> work){try(var guard=SuiteState.guard()){
        if(closing)return CompletableFuture.failedFuture(new IllegalStateException("Каталог останавливается; квитанции сохранены"));var result=new CompletableFuture<T>();requests.add(result);result.whenComplete((v,e)->requests.remove(result));try{work.get().whenComplete((v,e)->{if(e==null)result.complete(v);else result.completeExceptionally(e);});}catch(Throwable e){result.completeExceptionally(e);}return result;
    }}
    private <T>CompletionStage<T> owner(Player player,Supplier<CompletionStage<T>> work){try(var guard=SuiteState.guard()){
        var result=new CompletableFuture<T>();if(closing||!plugin.isEnabled())return CompletableFuture.failedFuture(new IllegalStateException("Каталог останавливается"));waitingOwners.add(result);
        try{var task=SuiteScheduler.entityLater(plugin,player,()->{waitingOwners.remove(result);if(result.isDone())return;try{if(closing||!player.isOnline())throw new IllegalStateException("Игрок вышел; предложение сохранено");work.get().whenComplete((v,e)->{if(e==null)result.complete(v);else result.completeExceptionally(e);});}catch(Throwable e){result.completeExceptionally(e);}},()->{waitingOwners.remove(result);result.completeExceptionally(new IllegalStateException("Игрок вышел; предложение сохранено"));},1);if(task.isCancelled()){waitingOwners.remove(result);result.completeExceptionally(new IllegalStateException("Каталог останавливается"));}}catch(Throwable e){waitingOwners.remove(result);result.completeExceptionally(e);}return result;
    }}
    @Override public CompletionStage<List<Map<String,Object>>> stock(UUID town,int page){return track(()->{gate(false);return repo.stock(town,page).thenApply(rows->rows.stream().map(row->Map.<String,Object>of("product",row.product(),"profile",row.profile(),"title",row.title(),"available",row.available(),"reserved",row.reserved(),"produced",row.produced(),"received",row.received(),"sent",row.sent())).toList());});}
    @Override public CompletionStage<List<Map<String,Object>>> entries(UUID town,int page){return track(()->{gate(false);return repo.goodsEntries(town,page).thenApply(rows->rows.stream().map(Entry::view).toList());});}
    @Override public CompletionStage<Map<String,Object>> entry(UUID id){return track(()->{gate(false);return repo.goodsEntry(id).thenApply(row->{if(row==null)throw new IllegalArgumentException("Сезонная запись не найдена");return row.view();});});}
    @Override public CompletionStage<Map<String,Object>> quote(Player player,UUID id,UUID other,UUID world,String period,String title,Map<String,Long> give,Map<String,Long> receive,long expires){return track(()->{
        gate(true);var actor=mayor(player);world(actor.city(),world);if(other!=null)world(other,world);window(world,period,other!=null);return repo.goodsQuote(id,actor.city(),other,world,period,title,give,receive,actor.player(),actor.name(),expires,TownyAPI.getInstance().getTown(actor.city()).getName(),other==null?"":TownyAPI.getInstance().getTown(other).getName()).thenApply(Terms::view);
    });}
    @Override public CompletionStage<Map<String,Object>> confirm(Player player,Map<String,Object> quote){return track(()->{
        gate(true);var terms=read(quote);return owner(player,()->{gate(true);var actor=mayor(player);if(!actor.city().equals(terms.town())||!actor.player().equals(terms.actor()))throw new IllegalArgumentException("Мэр или город изменился; смета не принята");world(actor.city(),terms.world());if(terms.other()!=null)world(terms.other(),terms.world());window(terms.world(),terms.period(),terms.other()!=null);return repo.goodsConfirm(terms,production.catalog().limits()).thenCompose(this::saved);});
    });}
    @Override public CompletionStage<Map<String,Object>> accept(Player player,UUID id,UUID receipt,String hash){return track(()->{
        gate(true);return repo.goodsEntry(id).thenCompose(row->owner(player,()->{gate(true);if(row==null)throw new IllegalArgumentException("Предложение не найдено");var actor=mayor(player);if(!actor.city().equals(row.terms().other()))throw new IllegalArgumentException("Это предложение для другого города");world(actor.city(),row.terms().world());world(row.terms().town(),row.terms().world());if(row.state().equals("HELD"))window(row.terms().world(),row.terms().period(),true);return repo.goodsAccept(id,receipt,actor.city(),actor.player(),actor.name(),hash,production.catalog().limits()).thenCompose(this::saved);}));
    });}
    @Override public CompletionStage<Map<String,Object>> cancel(Player player,UUID id,String reason){return track(()->{
        gate(false);return owner(player,()->{gate(false);var actor=mayor(player);return repo.goodsCancel(id,actor.city(),actor.player(),actor.name(),reason,false).thenCompose(this::saved);});
    });}
    @Override public CompletionStage<Map<String,Object>> adminCancel(UUID id,UUID actor,String actorName,String reason){return track(()->{gate(false);return repo.goodsCancel(id,null,actor,actorName,reason,true).thenCompose(this::saved);});}
    private void pulse(){if(!ready())return;var periods=new HashMap<String,String>();for(var world:Bukkit.getWorlds())try{var view=calendar(world.getUID());Object value=view.get("period");if(value instanceof String key&&!key.isBlank())periods.put(world.getUID().toString(),key);}catch(RuntimeException ignored){}
        track(()->repo.goodsExpire(periods).thenCompose(count->refresh())).whenComplete((v,e)->{if(e!=null&&ready())plugin.getLogger().warning("Сезонные резервы ожидают сверки срока: "+e.getMessage());});
    }
    public CompletionStage<Void> closeAsync(){try(var guard=SuiteState.guard()){
        if(closed!=null)return closed;closing=true;maintenance.cancel();waitingOwners.forEach(f->f.completeExceptionally(new IllegalStateException("Каталог останавливается; резервы сохранены")));waitingOwners.clear();var pending=new ArrayList<CompletableFuture<?>>(requests);pending.add(boot);closed=CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).handle((v,e)->(Void)null);return closed;
    }}
}
