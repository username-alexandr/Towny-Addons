package ru.neverland.mintcontracts.fishing;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.mintcontracts.MintTownyContracts;

/** One authority for both public and municipal fishing progress; ordinary vanilla fishing is opt-in. */
public final class FishingProtection implements Listener,AutoCloseable {
    private static final Set<Material> FISH=Set.of(Material.COD,Material.SALMON,Material.PUFFERFISH,Material.TROPICAL_FISH);
    private static final class Bite {
        final UUID id=UUID.randomUUID(),hook,player;final FishStock.Point point;final long expires;
        final AtomicReference<PlayerFishEvent> catchEvent=new AtomicReference<>();
        volatile FishStock.Decision decision;volatile boolean released;
        Bite(UUID hook,UUID player,FishStock.Point point,long expires){this.hook=hook;this.player=player;this.point=point;this.expires=expires;}
    }
    private final JavaPlugin plugin;private final Predicate<UUID> activeTask;private final FishStock stock;
    private final ConcurrentMap<UUID,Bite> bites=new ConcurrentHashMap<>();private final ConcurrentMap<UUID,Long> notices=new ConcurrentHashMap<>();
    private final Set<UUID> knownProtected=ConcurrentHashMap.newKeySet();
    private volatile FishingSettings settings;private volatile boolean closed;private volatile int retentionDays;private int pulses=3595;
    private static int retention(org.bukkit.configuration.ConfigurationSection config){int days=SafeYaml.intValue(config,"retention-days",3);if(days<2||days>365)throw new IllegalArgumentException("retention-days: от 2 до 365");return days;}
    public FishingProtection(JavaPlugin plugin,Predicate<UUID> activeTask)throws Exception{
        this.plugin=plugin;this.activeTask=activeTask;Path config=plugin.getDataFolder().toPath().resolve("fishing.yml");
        if(!Files.exists(config))plugin.saveResource("fishing.yml",false);var loaded=SafeYaml.load(config);settings=FishingSettings.read(loaded);retentionDays=retention(loaded);
        stock=new FishStock(plugin.getDataFolder().toPath().resolve("fishing-stock.db"));
        stock.ready().whenComplete((v,error)->{if(error!=null)plugin.getLogger().log(java.util.logging.Level.SEVERE,"Задания на рыбалку приостановлены: хранилище рыбы не готово",error);});
        Bukkit.getPluginManager().registerEvents(this,plugin);
        // No Bukkit state is touched by expiry. Unknown interrupted catches conservatively retain their stock debit.
        SuiteScheduler.get().runTaskTimer(plugin,()->{
            long now=System.currentTimeMillis();for(var bite:List.copyOf(bites.values()))if(bite.expires<now&&bites.remove(bite.hook,bite)){
                if(bite.catchEvent.get()==null)release(bite); // We know this live hook never passed the catch gate.
            }
            if(++pulses%3600==0&&stock.available())stock.compact(now-retentionDays*86400000L).whenComplete((v,error)->warn(error));
        },20,20);
    }
    public boolean ready(){return !closed&&(!settings.enabled()||stock.available());}
    public FishingSettings settings(){return settings;}
    public List<String> instructions(){
        if(!settings.enabled())return List.of("Ограничения водоёмов отключены администратором.");
        return List.of(settings.requireOpenWater()?"Ловите в просторном водоёме: от "+settings.minimumSurface()+" блоков воды, не уже "+settings.minimumSpan()+" блоков в обе стороны.":"Выбирайте водоём с рыбой.",
            "Нужна глубина от "+settings.minimumDepth()+" блоков. Большой искусственный пруд подходит.",
            "Запас общий: до "+settings.capacity()+" рыб в радиусе "+settings.radius()+" блоков.",
            "Если рыба закончилась, перейдите в другую часть водоёма.",
            "Запас постепенно восстанавливается за "+(settings.recoveryMillis()/60000)+" мин.",
            "Лодки и другой транспорт разрешены. Запас: /orders fishing");
    }
    public void reload()throws IOException{
        var config=SafeYaml.load(plugin.getDataFolder().toPath().resolve("fishing.yml"));var next=FishingSettings.read(config);int days=retention(config);
        for(var bite:List.copyOf(bites.values()))if(bite.catchEvent.get()==null)release(bite);bites.clear();settings=next;retentionDays=days;
    }
    private boolean protectedTask(UUID player){boolean value=settings.enabled()&&(settings.withoutTask()||activeTask.test(player));if(value)knownProtected.add(player);else knownProtected.remove(player);return value;}
    /** Used by legacy listeners too; the plugin's production instance must have initialized its authority. */
    public static boolean allows(JavaPlugin plugin,PlayerFishEvent event){
        if(!(plugin instanceof MintTownyContracts main))return true; // Existing standalone integrations/test harnesses.
        var service=main.fishing();return service!=null&&service.allows(event);
    }
    public boolean allows(PlayerFishEvent event){
        if(!protectedTask(event.getPlayer().getUniqueId()))return true;
        var bite=bites.get(event.getHook().getUniqueId());
        return !closed&&ready()&&!event.isCancelled()&&bite!=null&&bite.player.equals(event.getPlayer().getUniqueId())
            &&bite.catchEvent.get()==event&&bite.decision!=null&&bite.decision.reserved()&&!bite.released;
    }
    private void notice(Player player,String message){
        long now=System.currentTimeMillis();Long previous=notices.put(player.getUniqueId(),now);
        if(previous!=null&&now-previous<5000)return;
        SuiteScheduler.player(plugin,player,p->p.sendMessage("§6Рыбалка §8» §e"+message));
    }
    private static String waitText(long until){long seconds=Math.max(1,(until-System.currentTimeMillis()+999)/1000);return seconds>=60?((seconds+59)/60)+" мин.":seconds+" сек.";}
    private void release(Bite bite){
        bite.released=true;
        if(bite.decision!=null&&bite.decision.reserved()&&bite.catchEvent.get()==null)stock.finish(bite.id,false).whenComplete((v,error)->warn(error));
    }
    private void warn(Throwable error){if(error!=null&&!closed)plugin.getLogger().log(java.util.logging.Level.WARNING,"Операция запаса рыбы ожидает сверки",error);}
    private void deny(PlayerFishEvent event,String reason){event.setCancelled(true);event.setExpToDrop(0);notice(event.getPlayer(),reason);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void gate(PlayerFishEvent event){
        if(closed||!settings.enabled()||SuiteState.frozen(plugin.getName()))return;
        if(!SuiteState.tryRun("fishing gate",()->gateOwned(event))&&(settings.withoutTask()||knownProtected.contains(event.getPlayer().getUniqueId())))deny(event,"Проверяем задание. Повторите попытку немного позже.");
    }
    private void gateOwned(PlayerFishEvent event){
        UUID player=event.getPlayer().getUniqueId(),hook=event.getHook().getUniqueId();
        if(!protectedTask(player))return;
        switch(event.getState()){
            case BITE -> {
                if(!SuiteScheduler.owns(event.getHook())){deny(event,"Поплавок ещё не готов. Перезабросьте удочку.");return;}
                var location=event.getHook().getLocation();
                if(settings.requireOpenWater()){
                    var habitat=FishingHabitat.inspect(location,settings);
                    if(habitat==WaterSurface.Result.SMALL){deny(event,"Здесь слишком мало воды вокруг поплавка. Перейдите к более просторному водоёму.");return;}
                    if(habitat==WaterSurface.Result.SHALLOW){deny(event,"Здесь слишком мелко. Перейдите в более глубокое место.");return;}
                    if(habitat==WaterSurface.Result.UNKNOWN){deny(event,"Не удалось проверить весь водоём. Забросьте удочку дальше от границы участка.");return;}
                }
                if(!ready()){deny(event,"Задания на рыбалку временно приостановлены. Их время и прогресс сохраняются.");return;}
                var point=new FishStock.Point(location.getWorld().getUID(),location.getBlockX(),location.getBlockZ());
                var old=bites.remove(hook);if(old!=null&&old.catchEvent.get()==null)release(old);
                var bite=new Bite(hook,player,point,System.currentTimeMillis()+settings.leaseMillis());bites.put(hook,bite);
                stock.reserve(bite.id,player,point,settings,System.currentTimeMillis()).whenComplete((decision,error)->{
                    if(error!=null){bite.released=true;warn(error);return;}
                    bite.decision=decision;
                    if(bite.released||closed){if(decision.reserved())stock.finish(bite.id,false);return;}
                    if(!decision.reserved())notice(event.getPlayer(),"Рыба здесь закончилась. Перейдите в другую часть водоёма. Запас начнёт позволять ловлю примерно через "+waitText(decision.retryAt()));
                });
            }
            case CAUGHT_FISH -> {
                var bite=bites.get(hook);
                // No live hook/foreign region access here: a bite captured immutable habitat and location beforehand.
                if(bite==null||!bite.player.equals(player)||bite.released||bite.expires<System.currentTimeMillis()){
                    deny(event,"Для задания перезабросьте удочку в просторный водоём.");return;
                }
                if(!ready()||bite.decision==null){deny(event,"Проверяем запас рыбы. Перезабросьте удочку и повторите попытку.");return;}
                if(!bite.decision.reserved()){
                    deny(event,"Рыба здесь закончилась. Перейдите в другое место; восстановление — примерно через "+waitText(bite.decision.retryAt()));return;
                }
                if(!(event.getCaught() instanceof Item item)||!SuiteScheduler.owns(item)||!FISH.contains(item.getItemStack().getType())){
                    // Junk/treasure is not a fish and never progresses a fishing task or consumes a fish.
                    release(bite);return;
                }
                if(!bite.catchEvent.compareAndSet(null,event))deny(event,"Этот улов уже обработан.");
            }
            case FAILED_ATTEMPT,REEL_IN,IN_GROUND,CAUGHT_ENTITY -> {var bite=bites.get(hook);if(bite!=null&&bite.catchEvent.get()==null){bites.remove(hook,bite);release(bite);}}
            default -> {}
        }
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void observed(PlayerFishEvent event){
        var bite=bites.get(event.getHook().getUniqueId());if(bite==null)return;
        if(event.getState()==PlayerFishEvent.State.BITE&&event.isCancelled()){bites.remove(bite.hook,bite);release(bite);return;}
        if(event.getState()!=PlayerFishEvent.State.CAUGHT_FISH||bite.catchEvent.get()!=event)return;
        SuiteScheduler.entity(plugin,event.getPlayer(),()->{
            boolean caught=!event.isCancelled();
            var actor=AuditTrail.player(bite.player);
            stock.finish(bite.id,caught).whenComplete((result,error)->{
                warn(error);if(error==null&&caught)AuditTrail.record(plugin,"fish",bite.id.toString(),"FISHING_CATCH","COMPLETED",actor,AuditRecord.Party.system(),actor,"",1,"","world="+bite.point.world()+"; x="+bite.point.x()+"; z="+bite.point.z());
            });
            // Keep the event witness briefly so deferred public/municipal progress validates the same catch.
            SuiteScheduler.entityLater(plugin,event.getPlayer(),()->bites.remove(bite.hook,bite),null,5);
        });
    }
    @EventHandler public void quit(PlayerQuitEvent event){
        knownProtected.remove(event.getPlayer().getUniqueId());notices.remove(event.getPlayer().getUniqueId());for(var bite:List.copyOf(bites.values()))if(bite.player.equals(event.getPlayer().getUniqueId())&&bite.catchEvent.get()==null){bites.remove(bite.hook,bite);release(bite);}
    }
    public void status(Player player){
        FishStock.Point point=null;for(var bite:bites.values())if(bite.player.equals(player.getUniqueId())){point=bite.point;break;}
        if(point==null){var location=player.getLocation();point=new FishStock.Point(location.getWorld().getUID(),location.getBlockX(),location.getBlockZ());}
        player.sendMessage("§bДля задания нужен просторный водоём глубиной от "+settings.minimumDepth()+" блоков. Маленький пруд не подходит.");
        player.sendMessage("§7Большой искусственный пруд и ловля с транспорта разрешены. Участок имеет общий запас рыбы.");
        if(!settings.enabled()){player.sendMessage("§7Ограничение водоёмов отключено администратором.");return;}
        stock.inspect(player.getUniqueId(),point,settings,System.currentTimeMillis()).whenComplete((result,error)->SuiteScheduler.player(plugin,player,p->{
            if(error!=null){p.sendMessage("§eЗапас рыбы сейчас не удаётся проверить. Прогресс сохранён.");return;}
            p.sendMessage("§bРыбы поблизости осталось: §f"+result.remaining()+" §7из "+settings.capacity());
            if(result.remaining()==0)p.sendMessage("§eПерейдите в другую часть водоёма. До восстановления улова: примерно "+waitText(result.retryAt()));
        }));
    }
    public CompletionStage<Void> shutdownAsync(){closed=true;bites.clear();notices.clear();knownProtected.clear();stock.close();return stock.closed();}
    @Override public void close(){shutdownAsync();}
}
