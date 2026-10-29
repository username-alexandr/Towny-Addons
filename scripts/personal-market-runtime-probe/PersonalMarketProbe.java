package ru.neverland.runtime;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import com.mojang.authlib.GameProfile;
import io.papermc.paper.threadedregions.scheduler.*;
import ru.neverland.core.*;
import ru.neverland.townymarket.*;
import ru.neverland.townymarket.personal.*;
import static ru.neverland.townymarket.personal.LimitBook.*;

/** Actual native player inventory/PDC/saveData and exact file proof. Bank, online status, view and scheduler are explicit fixtures. */
public class PersonalMarketProbe extends JavaPlugin {
    static UUID id(String key){return UUID.nameUUIDFromBytes(("personal-market-020-"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static final UUID BUY=id("buyer"),SELL=id("seller"),FAIL=id("failed");
    Path proof;boolean restart;int checks;World world;Location origin;NeverLandTownyMarket owner;LimitService limits;MarketService market;
    final Map<UUID,Inventory> windows=new HashMap<>();final Map<UUID,CraftPlayer> real=new HashMap<>();final List<String> messages=new ArrayList<>();Player buyer,seller,failed;UUID sellOrder,buyOrder,pendingReceipt,unknownBankReceipt;FixtureBank bank=new FixtureBank();
    interface Work{void run()throws Exception;}
    static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    void check(boolean good,String why)throws Exception{if(!good)throw new AssertionError(why);checks++;Files.writeString(proof.resolve((restart?"restart":"first")+"-checks.txt"),"PASS "+why+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+why);}
    void denied(Work work,String why)throws Exception{try{work.run();}catch(Exception expected){check(true,why);return;}throw new AssertionError(why);}
    void later(long ticks,Work work){Bukkit.getRegionScheduler().runDelayed(this,origin,t->{if(!SuiteState.tryRun("Market native fixture",()->{try{work.run();}catch(Throwable error){fail(error);}}))later(1,work);},ticks);}
    void idle(Work next){idle(next,150);}
    void idle(Work next,int remaining){later(1,()->{if((boolean)field(limits,"busy")||limits.repository().busy()){if(remaining<=0)throw new AssertionError("market did not finish");idle(next,remaining-1);}else next.run();});}
    void fail(Throwable error){org.spigotmc.SpigotConfig.disablePlayerDataSaving=false;getLogger().log(java.util.logging.Level.SEVERE,"PERSONAL MARKET PROBE FAILED",error);try{Files.writeString(proof.resolve("failed.txt"),error.toString());}catch(Exception ignored){}Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
    @Override public void onEnable(){if(!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RAID_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){Bukkit.getPluginManager().disablePlugin(this);return;}proof=getDataFolder().toPath();Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{try{Files.createDirectories(proof);restart=Files.exists(proof.resolve("first-passed.txt"));world=Bukkit.getWorlds().getFirst();origin=new Location(world,8.5,120,8.5);world.getChunkAtAsync(0,0).whenComplete((chunk,error)->{if(error!=null)fail(error);else later(3,this::setup);});}catch(Throwable error){fail(error);}},40);}
    @SuppressWarnings("unchecked") Player actor(UUID uuid,String name)throws Exception{
        var server=(CraftServer)Bukkit.getServer();var player=new ServerPlayer(server.getServer(),((CraftWorld)world).getHandle(),new GameProfile(uuid,name),ClientInformation.createDefault());player.setPos(origin.getX(),origin.getY(),origin.getZ());CraftPlayer actual=player.getBukkitEntity();real.put(uuid,actual);
        var scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class<?>[]{EntityScheduler.class},(p,m,a)->switch(m.getName()){
            case "runDelayed"->Bukkit.getRegionScheduler().runDelayed(owner,origin,(Consumer<ScheduledTask>)a[1],(Long)a[3]);case "run"->Bukkit.getRegionScheduler().run(owner,origin,(Consumer<ScheduledTask>)a[1]);default->throw new UnsupportedOperationException(m.getName());});
        return(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
            case "getUniqueId"->uuid;case "getName"->name;case "getServer"->Bukkit.getServer();case "getWorld"->world;case "getLocation"->origin;case "isOnline"->true;case "isOp"->false;case "getScheduler"->scheduler;case "getGameMode"->GameMode.SURVIVAL;
            case "getInventory"->actual.getInventory();case "getPersistentDataContainer"->actual.getPersistentDataContainer();case "saveData"->{actual.saveData();yield null;}
            case "hasPermission"->!String.valueOf(a[0]).endsWith(".admin");case "sendMessage"->{messages.add(name+":"+String.valueOf(a[a.length-1]));yield null;}
            case "openInventory"->{windows.put(uuid,(Inventory)a[0]);yield null;}case "getOpenInventory"->view((Player)p,uuid);case "closeInventory"->{windows.remove(uuid);yield null;}
            case "hashCode"->uuid.hashCode();case "equals"->p==a[0];default->null;});
    }
    InventoryView view(Player p,UUID uuid){return(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(v,m,a)->switch(m.getName()){case "getTopInventory"->windows.computeIfAbsent(uuid,k->Bukkit.createInventory(null,9));case "getPlayer"->p;case "getType"->InventoryType.CHEST;case "getSlotType"->InventoryType.SlotType.CONTAINER;case "convertSlot"->a[0];default->null;});}
    String lore(Player p,int slot){return String.join(" ",windows.get(p.getUniqueId()).getItem(slot).getItemMeta().getLore());}
    int count(Player p){int n=0;for(var i:p.getInventory().getStorageContents())if(i!=null&&i.getType()==Material.STONE)n+=i.getAmount();return n;}
    Receipt receipt(UUID order){return limits.repository().state().receipts().values().stream().filter(r->order.equals(r.order())).findFirst().orElseThrow();}
    void setup()throws Exception{
        owner=(NeverLandTownyMarket)Bukkit.getPluginManager().getPlugin("NeverLandTownyMarket");market=(MarketService)field(owner,"service");limits=market.personal;check(owner.isEnabled()&&owner.getDescription().getVersion().equals("0.2.0")&&limits.repository().readable(),"actual Market 0.2 ledger and old city market enabled");
        buyer=actor(BUY,"MarketBuyer");seller=actor(SELL,"MarketSeller");failed=actor(FAIL,"MarketSaveFail");check(!new LimitBank().ready(BUY),"actual personal bank fails closed when Towny economy adapter is missing");set(limits,"bank",bank);
        if(restart){resume();return;}
        bank.money.put(BUY,10000L);bank.money.put(SELL,0L);seller.getInventory().setItem(0,new ItemStack(Material.STONE,20));failed.getInventory().setItem(0,new ItemStack(Material.STONE,8));real.get(FAIL).saveData();
        check(new PlayerSaveEvidence(owner).prepare(seller,UUID.randomUUID()).file().getParent().equals(((CraftServer)Bukkit.getServer()).getHandle().playerIo.getPlayerDir().toPath().toAbsolutePath().normalize()),"preflight resolves actual native player storage folder");
        var custom=new ItemStack(Material.STONE,2);var meta=custom.getItemMeta();meta.setDisplayName("Unique stone");custom.setItemMeta(meta);seller.getInventory().setItem(1,custom);denied(()->limits.offer(seller,Side.SELL,"STONE",21,100),"custom metadata resources cannot fund ordinary commodity sales");
        limits.command(seller,new String[]{"limit-sell","STONE","10","1.00"});check(windows.get(SELL).getItem(29).getType()==Material.LIME_CONCRETE&&lore(seller,13).contains("Весь товар"),"actual command opens explicit full-resource confirmation");@SuppressWarnings("unchecked")var quotes=(Map<UUID,LimitService.Offer>)field(limits,"offers");sellOrder=quotes.get(SELL).token();var click=new InventoryClickEvent(view(seller,SELL),InventoryType.SlotType.CONTAINER,29,ClickType.LEFT,InventoryAction.PICKUP_ALL);limits.click(click);check(click.isCancelled(),"confirmation menu prevents moving icon items");idle(this::afterSell);
    }
    void afterSell()throws Exception{
        check(limits.repository().state().cash(id("history"))==975&&limits.repository().state().receipts().size()<600,"live first commit compacts seeded schema1 claim history without changing remaining backing");check(limits.repository().state().orders().get(sellOrder).status()==Status.OPEN&&receipt(sellOrder).status()==ReceiptStatus.DONE,"SELL becomes funded only after actual native saved marker proof");check(count(seller)==12&&seller.getInventory().getItem(1).hasItemMeta(),"sale removes exact plain quantity preserving custom original metadata");denied(()->limits.confirm(seller,sellOrder),"repeated confirmation cannot reserve items twice");
        var buy=limits.offer(buyer,Side.BUY,"STONE",6,150);buyOrder=buy.token();limits.confirm(buyer,buy.token());idle(()->waitFill(100));
    }
    void waitFill(int remaining)throws Exception{
        if(limits.repository().state().fills().isEmpty()){if(remaining<1)throw new AssertionError("orders did not match");later(1,()->waitFill(remaining-1));return;}
        var state=limits.repository().state();check(state.item(BUY,"STONE")==6&&state.cash(SELL)==600&&state.cash(BUY)==300&&state.orders().get(sellOrder).remaining()==4,"price/time fill transfers real goods/proceeds and refunds price difference");check(bank.money.get(BUY)==9100,"only confirmed personal BUY debit created the reserved cash");
        org.spigotmc.SpigotConfig.disablePlayerDataSaving=true;limits.claimItems(buyer,"STONE",6);idle(()->{org.spigotmc.SpigotConfig.disablePlayerDataSaving=false;var pending=limits.repository().state().receipts().values().stream().filter(row->row.owner().equals(BUY)&&row.kind()==Kind.ITEM_CLAIM&&row.status()==ReceiptStatus.PENDING).findFirst().orElseThrow();check(count(buyer)==6&&limits.repository().state().item(BUY,"STONE")==0,"real disabled player save leaves item CLAIM pending rather than losing backed goods");real.get(BUY).saveData();var file=new PlayerSaveEvidence(owner).prepare(buyer,pending.id()).file();CompletableFuture.supplyAsync(()->{try{return PlayerDataMarkers.verify(file,BUY,"neverlandtownymarket:personal_market_receipt",pending.id().toString());}catch(Exception error){throw new CompletionException(error);}}).whenComplete((verified,error)->{if(error!=null){fail(error);return;}later(1,()->{check(verified,"administrator verifies exact durable receipt after recovery save");limits.resolve(Bukkit.getConsoleSender(),pending.id(),true);idle(()->{check(count(buyer)==6&&limits.repository().state().receipts().get(pending.id()).status()==ReceiptStatus.DONE,"confirmed recovery never gives claimed goods twice");limits.claimCash(seller);idle(this::cancel);});});});});
    }
    void cancel()throws Exception{
        check(bank.money.get(SELL)==600&&limits.repository().state().cash(SELL)==0,"seller withdraws only earned backed money");limits.cancel(seller,sellOrder);idle(()->{check(limits.repository().state().item(SELL,"STONE")==4,"cancellation returns only unmatched item remainder");owner.getConfig().set("personal-orders.enabled",false);limits.reloadConfig();denied(()->limits.offer(seller,Side.SELL,"STONE",1,100),"module feature pause rejects new reservation");limits.claimItems(seller,"STONE",4);idle(()->{check(count(seller)==16,"feature pause still allows existing item retrieval");owner.getConfig().set("personal-orders.enabled",true);limits.reloadConfig();silentSave();});});
    }
    void silentSave()throws Exception{
        org.spigotmc.SpigotConfig.disablePlayerDataSaving=true;var offer=limits.offer(failed,Side.SELL,"STONE",3,50);limits.confirm(failed,offer.token());idle(()->{org.spigotmc.SpigotConfig.disablePlayerDataSaving=false;var r=receipt(offer.token());pendingReceipt=r.id();check(r.status()==ReceiptStatus.PENDING&&limits.repository().state().orders().get(offer.token()).status()==Status.FUNDING&&count(failed)==5,"real disabled vanilla player save returns silently but never funds SELL or matches it");denied(()->limits.offer(failed,Side.SELL,"STONE",1,50),"unknown item outcome blocks another reservation for same owner");
            // Never autosave this fixture player: restarting must restore original 8, proving why DONE would dupe.
            bank.throwAfter=true;limits.claimCash(buyer);idle(()->{unknownBankReceipt=limits.repository().state().receipts().values().stream().filter(row->row.owner().equals(BUY)&&row.kind()==Kind.CASH_CLAIM&&row.status()==ReceiptStatus.PENDING).findFirst().orElseThrow().id();check(bank.money.get(BUY)==9400&&limits.repository().state().cash(BUY)==0,"bank mutation followed by unknown acknowledgement stays pending and cannot pay twice");Files.writeString(proof.resolve("expected.txt"),sellOrder+"\n"+buyOrder+"\n"+pendingReceipt+"\n"+unknownBankReceipt+"\n");finish();});});
    }
    void resume()throws Exception{
        var ids=Files.readAllLines(proof.resolve("expected.txt"));sellOrder=UUID.fromString(ids.get(0));buyOrder=UUID.fromString(ids.get(1));pendingReceipt=UUID.fromString(ids.get(2));unknownBankReceipt=UUID.fromString(ids.get(3));for(var p:real.values())p.loadData();
        check(count(buyer)==6&&count(seller)==16&&count(failed)==8,"full native restart restores acknowledged inventories and unsaved failed withdrawal remains original");var audit=new StringBuilder();try(var files=Files.walk(owner.getDataFolder().toPath().resolve("audit"))){for(var file:files.filter(path->path.toString().endsWith(".tsv")).toList())audit.append(Files.readString(file));}check(audit.toString().contains(pendingReceipt.toString())&&audit.toString().contains(unknownBankReceipt.toString())&&audit.toString().contains("MARKET_DEAL"),"accepted audit worker entries drained before full shutdown and survive restart");var state=limits.repository().state();check(state.cash(id("history"))==975&&state.receipts().size()<600,"native restart validates archived checkpoint and retains old money ownership");check(state.receipts().get(pendingReceipt).status()==ReceiptStatus.PENDING&&state.receipts().get(unknownBankReceipt).status()==ReceiptStatus.PENDING&&state.cash(BUY)==0,"restart retains unknown receipts without replay or spendable balance");check(bank.calls==0&&state.fills().size()==1,"restart never calls external bank or repeats funded match");
        limits.resolve(Bukkit.getConsoleSender(),pendingReceipt,false);idle(()->{check(limits.repository().state().receipts().get(pendingReceipt).status()==ReceiptStatus.FAILED&&count(failed)==8,"admin verified not-applied item outcome does not change physical inventory");limits.resolve(Bukkit.getConsoleSender(),unknownBankReceipt,true);idle(()->{check(bank.calls==0&&limits.repository().state().receipts().get(unknownBankReceipt).status()==ReceiptStatus.DONE,"admin verified bank outcome settles ledger without making another payment");denied(()->limits.resolve(Bukkit.getConsoleSender(),unknownBankReceipt,true),"same manual receipt cannot be resolved twice");limits.open(buyer,"mine",0);check(lore(buyer,43).contains("с собой исключены"),"native personal menu explains matching and receipt status");finish();});});
    }
    void finish()throws Exception{Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" checks; real Folia Towny Market plugin, native constructed ServerPlayer inventory/PDC/saveData and actual player.dat, full JVM restart, real disabled-save failure; explicit controlled bank and online/entity-scheduler/view proxies, no network client/provider claim\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
    public static class FixtureBank extends LimitBank{final Map<UUID,Long> money=new HashMap<>();boolean throwAfter;int calls;@Override public boolean ready(UUID owner){return true;}@Override public boolean move(UUID owner,UUID receipt,long cents,boolean incoming){if(SuiteState.held())throw new AssertionError("External bank called under SuiteState");calls++;long before=money.getOrDefault(owner,0L);if(!incoming&&before<cents)return false;money.put(owner,before+(incoming?cents:-cents));if(throwAfter){throwAfter=false;throw new IllegalStateException("fixture provider lost acknowledgement after actual balance mutation");}return true;}}
}
