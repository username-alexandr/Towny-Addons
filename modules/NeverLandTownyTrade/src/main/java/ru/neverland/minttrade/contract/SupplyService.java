package ru.neverland.minttrade.contract;
import java.util.*;
import java.io.IOException;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.minttrade.integration.TownyHook;
import ru.neverland.minttrade.service.*;
import static ru.neverland.minttrade.contract.SupplyContract.*;
public final class SupplyService {
    private final JavaPlugin plugin;private final TownyHook towny;private final TradeService trade;private final SupplyRepository repo;private final SupplyGateway gateway;private final MessageService messages;
    private final Map<UUID,Long> errors=new HashMap<>();private BukkitTask task;private boolean broken;private int cursor;
    public SupplyService(JavaPlugin plugin,TownyHook towny,TradeService trade,SupplyGateway gateway,MessageService messages){this.plugin=plugin;this.towny=towny;this.trade=trade;this.gateway=gateway;this.messages=messages;repo=new SupplyRepository(plugin.getDataFolder().toPath().resolve("contracts-data.yml"));}
    public void start(){reload();task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,40,20);}
    public void stop(){if(task!=null)task.cancel();}
    public void reload(){try{repo.load();for(var c:repo.all()){var item=SupplyGateway.decode(c.terms().itemData());if(item==null||item.getType().isAir()||item.getAmount()!=1)throw new IOException("Повреждён образец товара "+c.terms().shortId());}broken=false;errors.clear();}
        catch(Exception ex){broken=true;plugin.getLogger().severe("Регулярные договоры остановлены: "+ex.getMessage());}}
    public boolean available(){return !broken&&repo.writable();}
    private void writable(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Требуется основной поток");if(!available())throw new IllegalStateException("Регулярные договоры остановлены после ошибки записи. Обратитесь к администратору");}
    public List<SupplyContract> all(){return repo.all().stream().sorted(Comparator.comparingLong((SupplyContract c)->c.terms().created()).reversed().thenComparing(c->c.terms().id())).toList();}
    public List<SupplyContract> town(UUID id){return all().stream().filter(c->c.terms().party(id)).sorted(Comparator.comparing((SupplyContract c)->!c.open())).toList();}
    public SupplyContract find(String prefix){if(prefix==null||prefix.length()<8)return null;var matches=all().stream().filter(c->c.terms().id().toString().startsWith(prefix.toLowerCase(Locale.ROOT))).toList();return matches.size()==1?matches.get(0):null;}
    private SupplyContract require(UUID town,String id){var c=find(id);if(c==null||!c.terms().party(town))throw new IllegalArgumentException("Договор не найден в вашем городе");return c;}
    public int defaultDays(){return Math.max(1,Math.min(30,plugin.getConfig().getInt("contracts.default-days",7)));}
    public SupplyContract propose(Town seller,Town buyer,ItemStack item,int amount,long cents,int days)throws IOException {
        writable();if(!plugin.getConfig().getBoolean("contracts.enabled",true))throw new IllegalArgumentException("Новые автопоставки отключены администратором");
        if(seller==null||buyer==null||seller.equals(buyer))throw new IllegalArgumentException("Нужен другой существующий город");
        if(!gateway.available())throw new IllegalArgumentException("Обновите NeverLandTownyBuilds до 0.8.7 или новее");
        int limit=Math.max(1,Math.min(50,plugin.getConfig().getInt("contracts.maximum-per-town",10)));
        if(town(seller.getUUID()).stream().filter(SupplyContract::open).count()>=limit||town(buyer.getUUID()).stream().filter(SupplyContract::open).count()>=limit)throw new IllegalArgumentException("У одного из городов достигнут лимит договоров: "+limit);
        if(all().stream().filter(SupplyContract::open).count()>=1000)throw new IllegalArgumentException("Достигнут общий лимит 1000 незавершённых договоров");
        if(item==null||item.getType().isAir()||!item.getType().isItem())throw new IllegalArgumentException("Предмет не найден");
        if(amount>Math.min(3456,Math.min(64,item.getMaxStackSize())*54))throw new IllegalArgumentException("Партия больше вместимости 54 ячеек склада");
        var sample=item.clone();sample.setAmount(1);long now=System.currentTimeMillis();
        long expiration=now+Math.max(1,Math.min(168,plugin.getConfig().getInt("contracts.proposal-hours",24)))*3_600_000L;
        var c=SupplyContract.proposal(new Terms(UUID.randomUUID(),seller.getUUID(),buyer.getUUID(),Base64.getEncoder().encodeToString(sample.serializeAsBytes()),
            ru.neverland.minttrade.util.ColorUtil.strip(trade.registry().itemName(sample)),amount,cents,days,now,expiration));
        String blocked=gateway.termsReady(c);if(blocked!=null)throw new IllegalArgumentException(blocked);
        repo.prune(1000);repo.put(c);announce(c,"contract-proposed");return c;
    }
    public SupplyContract action(UUID town,String id,String action,Terms expected)throws IOException {
        writable();var c=require(town,id);if(!c.terms().equals(expected))throw new IllegalArgumentException("Условия изменились; заново откройте договор и проверьте их");long now=System.currentTimeMillis();
        var next=switch(action){
            case "accept" -> {if(!plugin.getConfig().getBoolean("contracts.enabled",true)||!gateway.available())throw new IllegalArgumentException("Новые автопоставки отключены или склад недоступен");String blocked=gateway.termsReady(c);if(blocked!=null)throw new IllegalArgumentException(blocked);yield c.accept(town,now);}
            case "cancel", "reject" -> c.cancel(town);
            case "pause" -> c.pause(town,true);
            case "resume" -> c.pause(town,false);
            default -> throw new IllegalArgumentException("Неизвестное действие");};
        repo.put(next);announce(next,"contract-changed");return next;
    }
    public void resolve(String id,UUID attempt,String decision)throws IOException {
        writable();var c=SupplyProcessor.resolve(find(id),attempt,decision,System.currentTimeMillis());repo.put(c);
        plugin.getLogger().warning("Сверка договора "+c.terms().id()+", поставка "+attempt+": "+decision);announce(c,"contract-changed");
    }
    private void tick(){if(!available())return;long now=System.currentTimeMillis();long retry=Math.max(30,Math.min(3600,plugin.getConfig().getInt("contracts.retry-seconds",300)))*1000L;
        var pending=repo.all().stream().filter(SupplyContract::open).toList();
        int budget=Math.max(1,Math.min(50,plugin.getConfig().getInt("contracts.process-per-second",8)));
        for(int i=0;i<Math.min(budget,pending.size());i++){
            cursor=Math.floorMod(cursor,pending.size());var before=pending.get(cursor++);
            if(errors.getOrDefault(before.terms().id(),0L)>now)continue;
            try {
                if(before.status()==Status.PROPOSED&&now>=before.terms().expires()){repo.put(before.expire());continue;}
                if(before.attempt()==null&&!plugin.getConfig().getBoolean("contracts.enabled",true))continue;
                SupplyProcessor.advance(before.terms().id(),now,retry,repo,gateway);
                var after=repo.get(before.terms().id());if(after.deliveries()>before.deliveries())announce(after,"contract-delivered");
                if(after.attempt()!=null&&(after.attempt().phase()==Phase.DEBIT_PENDING||after.attempt().phase()==Phase.CREDIT_PENDING)
                    &&(before.attempt()==null||before.attempt().phase()!=after.attempt().phase()))announce(after,"contract-reconcile");
            }catch(Exception ex){errors.put(before.terms().id(),now+retry);plugin.getLogger().severe("Договор "+before.terms().id()+": "+ex.getMessage());
                var after=repo.get(before.terms().id());if(after!=null&&after.attempt()!=null&&(after.attempt().phase()==Phase.DEBIT_PENDING||after.attempt().phase()==Phase.CREDIT_PENDING))announce(after,"contract-reconcile");
                if(!repo.writable())break;
            }
        }
    }
    private void announce(SupplyContract c,String key){for(var player:Bukkit.getOnlinePlayers()){var t=towny.town(player);if(t!=null&&c.terms().party(t.getUUID())&&towny.isManager(player,t))messages.send(player,key,Map.of("id",c.terms().shortId(),"item",c.terms().itemName(),"amount",c.terms().amount(),"price",SupplyContract.money(c.terms().cents()),"days",c.terms().days()));}}
}
