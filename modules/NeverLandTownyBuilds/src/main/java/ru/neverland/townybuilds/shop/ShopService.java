package ru.neverland.townybuilds.shop;
import java.util.*;
import java.math.BigDecimal;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.object.economy.*;
import ru.neverland.core.*;
import ru.neverland.townybuilds.data.*;
import ru.neverland.townybuilds.storage.*;
import ru.neverland.townybuilds.api.TownShopTradeEvent;
/** The controller owns ordering. Bukkit inventory handoff and bank calls are bounded by persisted receipts. */
public final class ShopService implements PurchaseSaga.Gateway<ShopOrder> {
    private final JavaPlugin plugin;private final DataStore data;private final ShopJournal journal;private BukkitTask task;
    private final Set<UUID> checkingOut=new HashSet<>();private long warned;
    public ShopService(JavaPlugin plugin,DataStore data){this.plugin=plugin;this.data=data;journal=new ShopJournal(plugin.getDataFolder().toPath().resolve("shop-orders.yml"));try{journal.load();}catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}}
    public ShopJournal journal(){return journal;}
    public void start(){stop();task=Bukkit.getScheduler().runTaskTimer(plugin,this::pulse,20,20);}
    public void stop(){if(task!=null)task.cancel();task=null;}
    private void thread(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Покупки требуют основного потока");if(!journal.writable()||!data.writable())throw new IllegalStateException("Покупки остановлены после ошибки сохранения; обратитесь к администратору");}
    public static long cents(double price){try{long n=BigDecimal.valueOf(price).movePointRight(2).longValueExact();if(n<1||n>100_000_000_000L)throw new ArithmeticException();return n;}catch(RuntimeException ex){throw new IllegalArgumentException("Цена должна быть положительной и содержать не больше двух знаков после запятой");}}
    private boolean allowed(Material material){return plugin.getConfig().getStringList("settings.civic.spawn-shops.allowed-materials").stream().map(ru.neverland.localization.MaterialNameConfig::matchMaterial).anyMatch(m->m==material);}
    public ShopOrder purchase(Player player,UUID seller,Material material,long quotedUnit,boolean stack)throws Exception{
        thread();if(!player.isOnline()||player.getGameMode()==GameMode.CREATIVE||player.getGameMode()==GameMode.SPECTATOR)throw new IllegalArgumentException("Для покупки нужен режим выживания или приключения");
        if(!checkingOut.add(player.getUniqueId()))throw new IllegalArgumentException("Покупка уже обрабатывается");
        try{if(journal.all().stream().filter(o->!o.finalized()&&o.buyer().equals(player.getUniqueId())).count()>=20||journal.all().stream().filter(o->!o.finalized()).count()>=5000)throw new IllegalArgumentException("Сначала завершите ожидающие покупки");
            Town town=TownyAPI.getInstance().getTown(seller);var resident=TownyAPI.getInstance().getResident(player.getUniqueId());var state=data.town(seller);
            if(town==null||resident==null||state.operationalLevel("merchant_guild")==0||state.shopStall().isBlank()||!allowed(material))throw new IllegalArgumentException("Лавка недоступна");
            long unit=cents(state.shopPrice(material.name()));if(unit!=quotedUnit)throw new IllegalArgumentException("Цена изменилась. Откройте лавку заново");if(data.storageBusy(seller,"merchant_guild"))throw new IllegalArgumentException("Склад гильдии открыт другим игроком");
            ItemStack sample=new ItemStack(material);int amount=Math.min(StockMath.count(state.civicInventory("merchant_guild",54),sample),stack?Math.min(64,material.getMaxStackSize()):1);if(amount<1)throw new IllegalArgumentException("Товар закончился");
            UUID id=UUID.randomUUID();long now=System.currentTimeMillis();var order=new ShopOrder(id,seller,player.getUniqueId(),material.name(),amount,unit,now,"PREPARED",now,"Покупка создана",false);
            ItemStack purchased=new ItemStack(material,amount);var view=StockMath.copy(player.getInventory().getStorageContents());if(!StockMath.insert(view,new ItemStack[]{purchased}))throw new IllegalArgumentException("Освободите место в инвентаре");
            if(!resident.getAccount().canPayFromHoldings(order.total()/100.0))throw new IllegalArgumentException("Недостаточно средств");
            var event=new TownShopTradeEvent(player.getUniqueId(),seller,purchased,order.total()/100.0);Bukkit.getPluginManager().callEvent(event);if(event.isCancelled())throw new IllegalArgumentException("Покупка отменена");
            // Recheck price/operation gate after hooks; prepared terms themselves remain immutable.
            if(cents(state.shopPrice(material.name()))!=quotedUnit)throw new IllegalArgumentException("Цена изменилась. Откройте лавку заново");
            journal.put(order);for(int i=0;i<4;i++)advance(id);order=journal.order(id);
            if(order.paymentStep().equals("COMPLETE"))claim(player,id);return journal.order(id);
        }finally{checkingOut.remove(player.getUniqueId());}
    }
    public String claim(Player player,UUID id)throws Exception{
        thread();var o=journal.order(id);if(o==null||!o.buyer().equals(player.getUniqueId()))throw new IllegalArgumentException("Покупка не принадлежит игроку");
        if(!player.isOnline()||player.getGameMode()==GameMode.CREATIVE||player.getGameMode()==GameMode.SPECTATOR)throw new IllegalArgumentException("Выдача доступна в выживании или приключении");
        if(!o.paymentStep().equals("COMPLETE"))return o.title();if(o.finalized())return "CLAIMED";
        String result=MarketTransactions.claim(data.town(o.seller()),access(),o.id(),o.id(),new MarketTransactions.Inventory(){public UUID owner(){return player.getUniqueId();}public ItemStack[] read(){return player.getInventory().getStorageContents();}public void write(ItemStack[] items){player.getInventory().setStorageContents(items);}public void save(){player.saveData();}});
        if(result.equals("CLAIMED"))advance(id);return result;
    }
    public void advance(UUID id)throws Exception{thread();PurchaseSaga.advance(id,System.currentTimeMillis(),5000,journal,this);}
    private void pulse(){if(!journal.writable()||!data.writable())return;for(var o:journal.all().stream().filter(v->!v.finalized()&&v.check()<=System.currentTimeMillis()&&!Set.of("DEBIT_PENDING","CREDIT_PENDING").contains(v.paymentStep())&&!(v.paymentStep().equals("COMPLETE")&&Set.of("PICKUP","CLAIM_PENDING").contains(receipt(v)))).limit(20).toList())try{advance(o.id());}catch(Exception ex){if(System.currentTimeMillis()-warned>60000){warned=System.currentTimeMillis();plugin.getLogger().log(java.util.logging.Level.WARNING,"Лавка ожидает восстановления операции "+o.id(),ex);}}}
    private MarketTransactions.Access access(){Map<UUID,TownData> states=new HashMap<>();return new MarketTransactions.Access(){private TownData state(UUID id){return states.computeIfAbsent(id,data::town);}public ItemStack[] read(UUID town){return state(town).civicInventory("merchant_guild",54);}public void write(UUID town,ItemStack[] items){state(town).setCivicInventory("merchant_guild",items);data.markDirty();}public boolean busy(UUID town){return data.storageBusy(town,"merchant_guild");}public void commit()throws java.io.IOException{data.saveOrThrow();}};}
    @Override public String ready(ShopOrder o){if(TownyAPI.getInstance().getTown(o.seller())==null||TownyAPI.getInstance().getResident(o.buyer())==null)return "Участник покупки недоступен";var t=data.town(o.seller());return t.operationalLevel("merchant_guild")>0&&!t.shopStall().isBlank()?null:"Лавка временно не работает";}
    @Override public String reserve(ShopOrder o)throws Exception{String state=MarketTransactions.open(data.town(o.seller()),access(),o.id(),new ItemStack(Material.valueOf(o.material())),o.amount());if(!state.equals("OPEN"))return state;return MarketTransactions.reserve(data.town(o.seller()),access(),o.id(),o.id(),o.buyer(),false,o.amount());}
    @Override public String deliver(ShopOrder o)throws Exception{return MarketTransactions.deliver(data.town(o.seller()),access(),o.id(),o.id());}
    @Override public String refund(ShopOrder o)throws Exception{var town=data.town(o.seller());String result=MarketTransactions.refund(town,access(),o.id(),o.id());String closed=MarketTransactions.close(town,access(),o.id());return Set.of("CLOSED","MISSING").contains(closed)?result:closed;}
    @Override public String receipt(ShopOrder o){var stock=data.town(o.seller()).marketStock().get(o.id());var hold=stock==null?null:stock.holds().get(o.id());return hold==null?"MISSING":hold.status();}
    @Override public void acknowledge(ShopOrder o)throws Exception{var town=data.town(o.seller());MarketTransactions.acknowledge(town,access(),o.id(),o.id());String status=MarketTransactions.close(town,access(),o.id());if(!Set.of("CLOSED","MISSING").contains(status))throw new IllegalStateException(status);MarketTransactions.forget(town,access(),o.id());}
    @Override public boolean debit(ShopOrder o)throws Exception{return payment(o,false);}
    @Override public boolean credit(ShopOrder o)throws Exception{return payment(o,true);}
    private boolean payment(ShopOrder o,boolean incoming)throws Exception{
        var town=TownyAPI.getInstance().getTown(o.seller());var buyer=TownyAPI.getInstance().getResident(o.buyer());if(incoming?town==null:buyer==null)return false;
        Account account=incoming?town.getAccount():buyer.getAccount();String token="[guild-shop:"+o.id()+"]";var evidence=new PaymentEvidence(account,incoming,token,o.total());
        var observer=new AccountObserver(){public void withdrew(Account source,double amount,String reason){evidence.observe(source,false,amount,reason);}public void deposited(Account source,double amount,String reason){evidence.observe(source,true,amount,reason);}};
        synchronized(account){account.addObserver(observer);try{boolean result;try{result=incoming?ru.neverland.integration.TreasuryAccess.deposit(town,"free","shop",false,o.total()/100.0,token+" Продажа в городской лавке"):account.withdraw(o.total()/100.0,token+" Покупка в городской лавке");}catch(RuntimeException ex){if(evidence.observed())return true;throw ex;}return evidence.result(result);}finally{account.removeObserver(observer);}}
    }
    public void resolve(UUID id,String decision)throws Exception{thread();var o=journal.order(id);if(o==null)throw new IllegalArgumentException("Покупка не найдена");if(Set.of("claim-received","claim-not-received").contains(decision)){if(!o.paymentStep().equals("COMPLETE"))throw new IllegalArgumentException("Оплата ещё не завершена");MarketTransactions.resolveClaim(data.town(o.seller()),access(),id,id,decision.equals("claim-received"));}else journal.put(PurchaseSaga.resolve(o,decision,System.currentTimeMillis()));}
}
