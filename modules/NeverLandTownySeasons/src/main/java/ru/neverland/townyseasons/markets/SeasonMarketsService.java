package ru.neverland.townyseasons.markets;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.*;
import ru.neverland.townyseasons.SeasonService;

/** Calendar/UI adapter only. All accepted terms, holds, swaps and receipts belong to the single goods database. */
public final class SeasonMarketsService {
    public record City(UUID id,String name){}
    private final JavaPlugin plugin;private final SeasonService seasons;private final ThreadPoolExecutor io;private final CompletableFuture<Void> stopped=new CompletableFuture<>();
    private volatile SeasonMarketsCatalog catalog;private volatile String problem="Сезонные запасы загружаются";private volatile boolean closing;
    public SeasonMarketsService(JavaPlugin plugin,SeasonService seasons){
        this.plugin=plugin;this.seasons=seasons;io=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(16),r->{var t=new Thread(r,"NeverLand-season-markets-config");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){stopped.complete(null);}};reload();
    }
    public void reload(){if(closing)return;try{io.execute(()->{try{Path file=plugin.getDataFolder().toPath().resolve("season-markets.yml");if(!Files.exists(file)){Files.createDirectories(file.getParent());try(var input=plugin.getResource("season-markets.yml")){if(input==null)throw new java.io.IOException("Нет встроенного каталога");try{Files.copy(input,file);}catch(FileAlreadyExistsException ignored){}}}var next=SeasonMarketsCatalog.load(file);if(!closing){catalog=next;problem="";}}catch(Exception e){catalog=null;problem="Настройки сезонных запасов требуют проверки";plugin.getLogger().warning(problem+"; обычный календарь сохраняется: "+e.getMessage());}});}catch(RejectedExecutionException full){problem="Настройки ещё перечитываются; повторите позже";}}
    public String problem(){if(!problem.isBlank())return problem;var c=catalog;if(c==null)return "Сезонные запасы недоступны";if(!c.enabled())return "Новые запасы и предложения приостановлены администратором";if(!backendReady())return "Каталог готовой продукции временно недоступен";return "";}
    public SeasonMarketsCatalog catalog(){var c=catalog;if(c==null)throw new IllegalArgumentException(problem);return c;}
    public City city(Player player){if(!player.isOnline()||!player.hasPermission("neverlandtownyseasons.use"))throw new IllegalArgumentException("Нет доступа к сезонным запасам");var resident=TownyAPI.getInstance().getResident(player);var town=resident==null?null:resident.getTownOrNull();if(town==null)throw new IllegalArgumentException("Сезонный запас принадлежит городу; сначала вступите в город");return new City(town.getUUID(),town.getName());}
    public boolean mayor(Player player){try{var own=city(player);var town=TownyAPI.getInstance().getTown(own.id());return player.hasPermission("neverlandtownyseasons.manage")&&town.isMayor(TownyAPI.getInstance().getResident(player));}catch(RuntimeException e){return false;}}
    private City manage(Player player){var own=city(player);if(!mayor(player))throw new IllegalArgumentException("Смету и обмен подтверждает мэр своего города");return own;}
    public CalendarWindow calendar(UUID town){return CalendarWindow.read(seasons.townCalendar(town));}
    public Map<String,Object> window(UUID world){try(var guard=SuiteState.guard()){
        var date=CalendarWindow.read(seasons.calendar(world));var c=catalog;String reason=problem;boolean enabled=!closing&&plugin.isEnabled()&&!SuiteState.frozen(plugin.getName())&&c!=null&&c.enabled()&&date.enabled();boolean open=enabled&&date.fairOpen(c.fairDays(),c.undated());
        if(reason.isBlank())reason=!enabled?"Новые сезонные запасы и предложения приостановлены":date.closedReason(c.fairDays(),c.undated());
        return Map.of("enabled",enabled,"open",open,"period",date.enabled()?date.key():"DISABLED","reason",reason);
    }}
    private void newWork(){if(closing||!plugin.isEnabled()||SuiteState.frozen(plugin.getName()))throw new IllegalArgumentException("Сезонные запасы приостановлены");if(!problem().isBlank())throw new IllegalArgumentException(problem());}
    private ApiServices.Connection link(String method)throws ReflectiveOperationException{return ApiServices.connect("NeverLandTownySpecialization","ru.neverland.townyspecialization.api.ProfileGoodsApi",1,"ready",method);}
    public boolean backendReady(){try{var link=link("ready");return link.ready()&&Boolean.TRUE.equals(link.invoke("ready",new Class<?>[0]));}catch(ReflectiveOperationException|RuntimeException|LinkageError e){return false;}}
    @SuppressWarnings("unchecked")private <T>CompletionStage<T> call(String method,Class<?>[] types,Object...args){try{var link=link(method);if(!link.ready()||!Boolean.TRUE.equals(link.invoke("ready",new Class<?>[0])))throw new IllegalArgumentException("Каталог готовой продукции недоступен; запасы сохранены");Object result=link.invoke(method,types,args);if(!(result instanceof CompletionStage<?> stage))throw new IllegalStateException("Несовместимый ответ каталога");return(CompletionStage<T>)stage;}catch(Exception|LinkageError e){return CompletableFuture.failedFuture(e);}}
    public CompletionStage<List<Map<String,Object>>> stock(Player player,int page){return call("stock",new Class<?>[]{UUID.class,int.class},city(player).id(),page);}
    public CompletionStage<List<Map<String,Object>>> entries(Player player,int page){return call("entries",new Class<?>[]{UUID.class,int.class},city(player).id(),page);}
    public CompletionStage<Map<String,Object>> entry(UUID id){return call("entry",new Class<?>[]{UUID.class},id);}
    public CompletionStage<Map<String,Object>> preparation(Player player){try(var guard=SuiteState.guard()){
        newWork();var own=manage(player);var date=calendar(own.id());if(!date.enabled())throw new IllegalArgumentException("В мире города сезоны отключены");var c=catalog();var plan=c.plans().get(date.next());
        return quote(player,UUID.randomUUID(),null,date,plan.title(),plan.goods(),Map.of(),deadline(own.id(),c.reserveHours(),false));
    }}
    public CompletionStage<Map<String,Object>> exchange(Player player,UUID other,String template){try(var guard=SuiteState.guard()){
        newWork();var own=manage(player);var date=calendar(own.id());var c=catalog();if(!date.fairOpen(c.fairDays(),c.undated()))throw new IllegalArgumentException(date.closedReason(c.fairDays(),c.undated()));if(own.id().equals(other)||!seasons.townWorld(other).equals(date.world()))throw new IllegalArgumentException("Выберите другой город в том же мире");var offer=c.exchanges().get(template);if(offer==null)throw new IllegalArgumentException("Условия ярмарки изменились; выберите предложение заново");
        return quote(player,UUID.randomUUID(),other,date,offer.title(),offer.give(),offer.receive(),deadline(own.id(),c.offerHours(),true));
    }}
    private CompletionStage<Map<String,Object>> quote(Player p,UUID id,UUID other,CalendarWindow date,String title,Map<String,Long> give,Map<String,Long> receive,long until){return call("quote",new Class<?>[]{Player.class,UUID.class,UUID.class,UUID.class,String.class,String.class,Map.class,Map.class,long.class},p,id,other,date.world(),date.key(),title,give,receive,until);}
    private long deadline(UUID town,int hours,boolean offer){var view=seasons.townCalendar(town);long ttl=hours*3_600_000L;if("CUSTOM".equals(view.get("source"))){long remaining=((Number)view.get("remaining")).longValue();long limit=offer?remaining:Math.addExact(remaining,Math.multiplyExact(seasons.settings().dayMillis(),seasons.settings().days()));if(limit>0)ttl=Math.min(ttl,limit);}return Math.addExact(System.currentTimeMillis(),ttl);}
    public CompletionStage<Map<String,Object>> confirm(Player player,Map<String,Object> quote){try(var guard=SuiteState.guard()){newWork();manage(player);return call("confirm",new Class<?>[]{Player.class,Map.class},player,quote);}}
    public CompletionStage<Map<String,Object>> accept(Player player,Map<String,Object> row,UUID receipt){try(var guard=SuiteState.guard()){newWork();var own=manage(player);if(!own.id().toString().equals(row.get("other")))throw new IllegalArgumentException("Предложение адресовано другому городу");return call("accept",new Class<?>[]{Player.class,UUID.class,UUID.class,String.class},player,UUID.fromString((String)row.get("id")),receipt,(String)row.get("hash"));}}
    public CompletionStage<Map<String,Object>> cancel(Player player,UUID id){try(var guard=SuiteState.guard()){manage(player);return call("cancel",new Class<?>[]{Player.class,UUID.class,String.class},player,id,"Мэр освободил сезонный резерв без потерь");}}
    public List<City> cities(Player player,int page){var own=city(player);if(page<1||page>10000)throw new IllegalArgumentException("Неверная страница");var world=seasons.townWorld(own.id());return TownyAPI.getInstance().getTowns().stream().filter(t->!t.getUUID().equals(own.id())).filter(t->{try{return world.equals(seasons.townWorld(t.getUUID()));}catch(RuntimeException noHome){return false;}}).map(t->new City(t.getUUID(),t.getName())).sorted(Comparator.comparing(City::name,String.CASE_INSENSITIVE_ORDER).thenComparing(City::id)).skip((page-1L)*21).limit(21).toList();}
    @SuppressWarnings("unchecked")public List<Map<String,Object>> active(){try{var link=link("active");if(!link.ready()||!Boolean.TRUE.equals(link.invoke("ready",new Class<?>[0])))return List.of();return(List<Map<String,Object>>)link.invoke("active",new Class<?>[0]);}catch(Exception|LinkageError e){return List.of();}}
    public CompletionStage<Map<String,Object>> adminCancel(CommandSender sender,UUID id){if(!sender.isOp()&&!sender.hasPermission("neverlandtownyseasons.admin"))throw new IllegalArgumentException("Нет права администратора сезонов");return call("adminCancel",new Class<?>[]{UUID.class,UUID.class,String.class,String.class},id,sender instanceof Player p?p.getUniqueId():new UUID(0,0),sender.getName(),"Администратор отменил сезонный резерв без потерь");}
    public CompletionStage<Void> closeAsync(){closing=true;io.shutdown();return stopped;}
    public void awaitClosed(){var done=closeAsync().toCompletableFuture();if(SuiteState.held()&&!done.isDone())throw new IllegalStateException("Ожидание настроек должно быть вне общей блокировки");try{done.get(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception e){plugin.getLogger().warning("Настройки сезонных запасов ещё завершают запись: "+e.getMessage());}}
}
