package ru.neverland.townymarket;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import static ru.neverland.townymarket.MarketData.*;
public final class MarketService {
    private final JavaPlugin plugin;public final MarketRepository repo;public final MarketGateway bridge;public final MarketCatalog catalog;
    private final Map<UUID,Quote> quotes=new HashMap<>();private final Map<UUID,Long> errors=new HashMap<>();private BukkitTask task;private boolean broken;private int listingCursor,orderCursor;
    private int priceTick=-1;private Map<String,Long> supplyCache=Map.of();
    public record Stats(long offers,long pickup){}
    private volatile Map<UUID,Stats> stats=Map.of();private long statsAt;
    public Stats stats(UUID player){return stats.getOrDefault(player,new Stats(0,0));}
    private void invalidate(){priceTick=-1;}
    private void refreshStats(long now){if(now<statsAt)return;statsAt=now+5000;Map<UUID,Long> offers=new HashMap<>(),pickup=new HashMap<>();
        for(var l:repo.listings())if(l.state()==ListingState.ACTIVE)offers.merge(l.town(),1L,Long::sum);
        for(var o:repo.orders())if(!o.city()&&o.phase()==Phase.COMPLETE&&!o.finalized())pickup.merge(o.buyer(),1L,Long::sum);
        Map<UUID,Stats> next=new HashMap<>();for(var p:Bukkit.getOnlinePlayers()){var t=bridge.town(p);next.put(p.getUniqueId(),new Stats(t==null?0:offers.getOrDefault(t.getUUID(),0L),pickup.getOrDefault(p.getUniqueId(),0L)));}stats=Map.copyOf(next);
    }

    public MarketService(JavaPlugin plugin){this.plugin=plugin;repo=new MarketRepository(plugin.getDataFolder().toPath().resolve("market-data.yml"));bridge=new MarketGateway(plugin,repo);catalog=new MarketCatalog(plugin);}
    public void start(){reload();task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,40,20);}
    public void stop(){if(task!=null)task.cancel();quotes.clear();}
    public void reload(){quotes.clear();invalidate();statsAt=0;try{repo.load();catalog.reload();for(var l:repo.listings()){var i=MarketCatalog.decode(l.item());if(i==null||i.getType().isAir()||i.getAmount()!=1)throw new IllegalStateException("Повреждён товар "+l.id());}broken=false;errors.clear();}catch(Exception ex){broken=true;plugin.getLogger().severe(ex.getMessage());}}
    public boolean available(){return !broken&&repo.writable();}
    private void writable(){if(!Bukkit.isPrimaryThread()||!available())throw new IllegalStateException("Рынок остановлен после ошибки. Обратитесь к администратору");}
    public boolean manage(Player p,Town t){var resident=TownyAPI.getInstance().getResident(p);if(t==null||resident==null||!t.equals(resident.getTownOrNull()))return false;
        return t.isMayor(resident)||p.hasPermission("neverlandtownymarket.manage")&&plugin.getConfig().getStringList("management.allowed-town-ranks").stream().anyMatch(resident::hasTownRank);}
    public void requireManager(Player p,Town t){if(!manage(p,t))throw new IllegalArgumentException("Управлять предложениями и казной может мэр или уполномоченный заместитель");}
    public void requireUse(Player p){if(!p.hasPermission("neverlandtownymarket.use"))throw new IllegalArgumentException("Нет права на просмотр рынка");}
    public int limit(UUID town,Scope scope){return scope==Scope.LOCAL?4*bridge.level(town,"market"):4*bridge.level(town,"merchant_guild");}
    public List<Listing> listings(){return repo.listings().stream().sorted(Comparator.comparingLong(Listing::created).reversed().thenComparing(Listing::id)).toList();}
    public List<Order> orders(Player p){var town=bridge.town(p);return repo.orders().stream().filter(o->!o.city()?(o.buyer().equals(p.getUniqueId())||town!=null&&o.seller().equals(town.getUUID())&&manage(p,town)):town!=null&&(o.buyer().equals(town.getUUID())||o.seller().equals(town.getUUID()))).sorted(Comparator.comparingLong(Order::created).reversed()).toList();}
    public Listing find(String id){var matches=listings().stream().filter(l->id!=null&&id.length()>=8&&l.id().toString().startsWith(id.toLowerCase(Locale.ROOT))).toList();return matches.size()==1?matches.get(0):null;}
    public Order order(String id){var matches=repo.orders().stream().filter(o->id!=null&&id.length()>=8&&o.id().toString().startsWith(id.toLowerCase(Locale.ROOT))).toList();return matches.size()==1?matches.get(0):null;}
    public Listing sell(Player p,String itemKey,int amount,String pricing,Scope scope)throws Exception{
        writable();requireUse(p);var town=bridge.town(p);requireManager(p,town);if(!plugin.getConfig().getBoolean("enabled",true))throw new IllegalArgumentException("Создание предложений отключено");
        var sample=itemKey.equalsIgnoreCase("hand")?p.getInventory().getItemInMainHand():catalog.select(itemKey);if(sample==null||sample.getType().isAir())throw new IllegalArgumentException("Предмет не найден");sample=sample.clone();sample.setAmount(1);
        if(amount<1||amount>Math.min(3456,54*Math.min(64,sample.getMaxStackSize())))throw new IllegalArgumentException("Количество должно помещаться в 54 ячейки склада");
        var product=catalog.match(sample);boolean auto=pricing.equalsIgnoreCase("auto");if(auto&&product==null)throw new IllegalArgumentException("Для автоцены добавьте точный предмет в catalog.yml или укажите фиксированную цену");
        long base=auto?product.base():cents(pricing);if(base>MAX/amount)throw new IllegalArgumentException("Общая стоимость предложения слишком велика");
        if(repo.listings().stream().filter(l->l.town().equals(town.getUUID())&&l.scope()==scope&&l.state()!=ListingState.CLOSED).count()>=limit(town.getUUID(),scope))throw new IllegalArgumentException("Лимит предложений: "+limit(town.getUUID(),scope)+". Улучшите рынок или гильдию");
        if(repo.listings().size()>=5000)throw new IllegalArgumentException("Достигнут общий лимит предложений. Закройте завершённые предложения");
        String data=MarketCatalog.encode(sample);var l=new Listing(UUID.randomUUID(),town.getUUID(),data,catalog.label(sample),product==null?MarketCatalog.key(data):product.id(),scope,auto,base,amount,System.currentTimeMillis(),ListingState.PREPARING,"Резервируется товар со склада");
        String blocked=bridge.sellerReady(l);if(blocked!=null)throw new IllegalArgumentException(blocked);repo.prune();repo.put(l);processListing(l);return repo.listing(l.id());
    }
    public void close(Player p,String id)throws Exception {writable();var l=find(id);var town=bridge.town(p);requireManager(p,town);if(l==null||!l.town().equals(town.getUUID()))throw new IllegalArgumentException("Предложение вашего города не найдено");repo.put(l.state(ListingState.CLOSING,"Возвращается непроданный товар"));processListing(repo.listing(l.id()));}
    public void adminClose(UUID id)throws Exception{writable();var l=repo.listing(id);if(l==null)throw new IllegalArgumentException("Предложение не найдено");repo.put(l.state(ListingState.CLOSING,"Снято администратором"));processListing(repo.listing(id));}
    public long unit(Listing l)throws Exception{
        if(!l.auto())return l.base();
        if(priceTick!=Bukkit.getCurrentTick()){Map<String,Long> next=new HashMap<>();for(var other:repo.listings())if(other.state()==ListingState.ACTIVE&&bridge.sellerReady(other)==null)next.merge(other.scopeKey()+"/"+other.product(),(long)bridge.stock(other),Long::sum);supplyCache=Map.copyOf(next);priceTick=Bukkit.getCurrentTick();}
        long supply=supplyCache.getOrDefault(l.scopeKey()+"/"+l.product(),0L);
        long demand=MarketPrices.demand(repo.demand(),l.scopeKey(),l.product(),System.currentTimeMillis(),Math.max(1,Math.min(3456,plugin.getConfig().getInt("prices.buyer-daily-cap",256))));
        int target=Math.max(1,Math.min(100000,plugin.getConfig().getInt("prices.target-stock",512))),min=Math.max(100,Math.min(10000,plugin.getConfig().getInt("prices.minimum-basis-points",5000))),max=Math.max(10000,Math.min(50000,plugin.getConfig().getInt("prices.maximum-basis-points",20000)));
        return MarketPrices.unit(l.base(),supply,demand,target,min,max);
    }
    private Town buying(Player p,Listing l){requireUse(p);if(!p.hasPermission("neverlandtownymarket.buy"))throw new IllegalArgumentException("Нет права покупки");var town=bridge.town(p);if(town==null)throw new IllegalArgumentException("Вы не состоите в городе");
        if(l.scope()==Scope.GLOBAL){requireManager(p,town);if(l.town().equals(town.getUUID()))throw new IllegalArgumentException("Город не может покупать у самого себя");}
        else{if(!l.town().equals(town.getUUID()))throw new IllegalArgumentException("Городской рынок доступен его жителям");if(p.getGameMode()!=GameMode.SURVIVAL&&p.getGameMode()!=GameMode.ADVENTURE)throw new IllegalArgumentException("Личные покупки доступны в выживании и приключении");}return town;
    }
    public Quote quote(Player p,String id,int amount)throws Exception {writable();var l=find(id);if(l==null||l.state()!=ListingState.ACTIVE)throw new IllegalArgumentException("Предложение недоступно");var town=buying(p,l);
        if(amount<1||amount>3456||amount>bridge.stock(l))throw new IllegalArgumentException("Недостаточно товара или неверное количество");
        if(l.scope()==Scope.LOCAL&&amount>36*Math.min(64,MarketCatalog.decode(l.item()).getMaxStackSize()))throw new IllegalArgumentException("Партия должна помещаться в инвентарь игрока");
        long unit=unit(l);if(unit>MAX/amount)throw new IllegalArgumentException("Слишком большая сумма покупки");long now=System.currentTimeMillis();var q=new Quote(UUID.randomUUID(),p.getUniqueId(),town.getUUID(),l.id(),amount,unit,now+30_000);
        var probe=new Order(q.token(),l.id(),l.town(),l.scope()==Scope.GLOBAL?town.getUUID():p.getUniqueId(),p.getUniqueId(),l.scope()==Scope.GLOBAL,amount,unit,now,Phase.PREPARED,now,"",false);
        String blocked=bridge.ready(probe);if(blocked!=null)throw new IllegalArgumentException(blocked);quotes.put(p.getUniqueId(),q);return q;
    }
    public Order confirm(Player p,Quote q)throws Exception {writable();if(!q.equals(quotes.get(p.getUniqueId()))||!q.actor().equals(p.getUniqueId())||System.currentTimeMillis()>=q.expires())throw new IllegalArgumentException("Цена устарела. Откройте подтверждение заново");
        var l=repo.listing(q.lot());if(l==null||l.state()!=ListingState.ACTIVE)throw new IllegalArgumentException("Предложение закрыто");var town=buying(p,l);if(!town.getUUID().equals(q.town()))throw new IllegalArgumentException("Ваш город изменился");
        if(bridge.stock(l)<q.amount()||unit(l)!=q.unit())throw new IllegalArgumentException("Количество или цена изменились. Откройте новое подтверждение");
        UUID buyer=l.scope()==Scope.GLOBAL?town.getUUID():p.getUniqueId();
        if(repo.orders().stream().filter(o->!o.finalized()&&o.buyer().equals(buyer)).count()>=20)throw new IllegalArgumentException("Завершите или заберите предыдущие покупки (лимит 20)");
        if(repo.orders().stream().filter(o->!o.finalized()).count()>=2000)throw new IllegalArgumentException("Рынок занят: слишком много незавершённых покупок");
        long now=System.currentTimeMillis();var o=new Order(q.token(),l.id(),l.town(),buyer,p.getUniqueId(),l.scope()==Scope.GLOBAL,q.amount(),q.unit(),now,Phase.PREPARED,now,"Покупка подтверждена",false);
        String blocked=bridge.ready(o);if(blocked!=null)throw new IllegalArgumentException(blocked);quotes.remove(p.getUniqueId());repo.prune();repo.put(o);try{MarketPayments.advance(o.id(),now,retry(),repo,bridge);}finally{invalidate();}return repo.order(o.id());
    }
    public String claim(Player p,String id)throws Exception {writable();requireUse(p);var o=order(id);if(o==null||o.city()||!o.buyer().equals(p.getUniqueId())||o.phase()!=Phase.COMPLETE)throw new IllegalArgumentException("Оплаченная личная покупка не найдена");if(o.finalized())return "CLAIMED";
        String result=(String)bridge.call("claim",new Class<?>[]{Player.class,UUID.class,UUID.class,UUID.class},p,o.seller(),o.lot(),o.id());
        if(result.equals("CLAIMED"))MarketPayments.advance(o.id(),System.currentTimeMillis(),retry(),repo,bridge);return result;
    }
    public void resolve(UUID id,String decision)throws Exception {writable();var o=repo.order(id);if(o==null)throw new IllegalArgumentException("Нужен полный UUID существующей покупки");
        if(decision.equals("claim-received")||decision.equals("claim-unreceived")){if(o.city()||o.phase()!=Phase.COMPLETE)throw new IllegalArgumentException("Личная покупка ещё не оплачена");bridge.call("resolveClaim",new Class<?>[]{UUID.class,UUID.class,UUID.class,boolean.class},o.seller(),o.lot(),o.id(),decision.equals("claim-received"));}
        else repo.put(MarketPayments.resolve(o,decision,System.currentTimeMillis()));plugin.getLogger().warning("Сверка покупки "+id+": "+decision);
    }
    private long retry(){return 1000L*Math.max(10,Math.min(3600,plugin.getConfig().getInt("retry-seconds",60)));}
    private void processListing(Listing l)throws Exception {
        invalidate();
        if(l.state()==ListingState.PREPARING){String blocked=bridge.sellerReady(l);if(blocked!=null){repo.put(l.state(ListingState.CLOSING,blocked));return;}
            String s=(String)bridge.call("open",new Class<?>[]{UUID.class,UUID.class,ItemStack.class,int.class},l.town(),l.id(),MarketCatalog.decode(l.item()),l.amount());
            if(s.equals("OPEN"))repo.put(l.state(ListingState.ACTIVE,"Товар выставлен"));else if(s.equals("CLOSED"))repo.put(l.state(ListingState.CLOSED,"Предложение закрыто"));else repo.put(l.state(ListingState.CLOSING,MarketPayments.stockNote(s)));
        }else if(l.state()==ListingState.CLOSING){String s=(String)bridge.call("close",new Class<?>[]{UUID.class,UUID.class},l.town(),l.id());
            if(s.equals("CLOSED")||s.equals("MISSING"))repo.put(l.state(ListingState.CLOSED,"Непроданный товар возвращён"));else throw new IllegalStateException(MarketPayments.stockNote(s));
        }else if(l.state()==ListingState.CLOSED&&repo.orders().stream().noneMatch(o->o.lot().equals(l.id()))){bridge.call("forget",new Class<?>[]{UUID.class,UUID.class},l.town(),l.id());repo.removeListing(l.id());}
    }
    private void tick(){if(!available())return;long now=System.currentTimeMillis();refreshStats(now);quotes.values().removeIf(q->q.expires()<=now);var ls=repo.listings().stream().filter(l->l.state()!=ListingState.ACTIVE).toList();
        for(int i=0;i<Math.min(2,ls.size());i++){listingCursor=Math.floorMod(listingCursor,ls.size());var l=ls.get(listingCursor++);if(errors.getOrDefault(l.id(),0L)>now)continue;try{processListing(l);}catch(Exception ex){error(l.id(),ex,now);}if(!available())return;}
        var os=repo.orders().stream().filter(o->!o.finalized()).toList();int count=Math.max(1,Math.min(30,plugin.getConfig().getInt("purchases-per-second",8)));
        for(int i=0;i<Math.min(count,os.size());i++){orderCursor=Math.floorMod(orderCursor,os.size());var o=os.get(orderCursor++);if(errors.getOrDefault(o.id(),0L)>now)continue;
            try{invalidate();MarketPayments.advance(o.id(),now,retry(),repo,bridge);var after=repo.order(o.id());if(after.phase()==Phase.COMPLETE&&o.phase()!=Phase.COMPLETE){var p=Bukkit.getPlayer(o.actor());if(p!=null)tell(p,after.note()+". ID: "+after.shortId());}}
            catch(Exception ex){error(o.id(),ex,now);}if(!available())return;
        }
    }
    private void error(UUID id,Exception ex,long now){errors.put(id,now+retry());plugin.getLogger().severe("Рынок "+id+": "+ex.getMessage());}
    public static void tell(org.bukkit.command.CommandSender p,String message){p.sendMessage(ChatColor.translateAlternateColorCodes('&',"&8[&aNeverLand &8• &fРынок&8] &r"+message));}
}
