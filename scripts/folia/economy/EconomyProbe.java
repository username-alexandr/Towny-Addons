import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.lang.reflect.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import ru.neverland.earnings.*;
import ru.neverland.core.*;
import ru.neverland.mintcontracts.orders.PublicOrders;

/** Native Towny/Vault and Folia checks in an isolated server; no production files are used. */
public final class EconomyProbe extends JavaPlugin {
    private final List<String> results=Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger remaining=new AtomicInteger(2);private int failed;
    private void check(String title,boolean value){results.add((value?"PASS ":"FAIL ")+title);if(!value)failed++;}
    @Override public void onEnable(){
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{synchronized(SuiteState.lock()){
            try{bankChecks();auditWhenDisconnected();presentationChecks();regions();}catch(Throwable ex){getLogger().log(java.util.logging.Level.SEVERE,"Probe failed",ex);check(ex.toString(),false);finish();}
        }},60);
    }
    private void bankChecks()throws Exception{
        check("Towny economy active with Vault provider",TownyEconomyHandler.isActive());
        for(String module:List.of("Builds","Contracts","Jobs","Companies","Taxes","Control")){var p=Bukkit.getPluginManager().getPlugin("NeverLandTowny"+module);check("module enabled: "+module,p!=null&&p.isEnabled());}
        check("orders executor initialized",Bukkit.getPluginCommand("orders").getExecutor().getClass().getName().endsWith("PublicOrders"));
        check("salary executor initialized",Bukkit.getPluginCommand("jobpay").getExecutor().getClass().getName().endsWith("WageService"));
        var universe=TownyUniverse.getInstance();UUID id=UUID.randomUUID();var resident=universe.getDataSource().newResident("Probe"+id.toString().substring(0,8),id);
        check("public-order recipient has no town",!resident.hasTown());
        var ledger=new RewardLedger(getDataFolder().toPath().resolve("native-payments"),150000,200000);ledger.load();var bank=new TownyRewardBank(this);for(String line:bank.diagnostics())results.add("DIAGNOSTIC "+line);
        double before=resident.getAccount().getHoldingBalance();UUID payment=UUID.randomUUID();ledger.reserve(payment,id,12000,"orders:native-townless",false);ledger.submit(payment,bank);
        check("Towny/Vault personal balance increased by 120",Math.abs(resident.getAccount().getHoldingBalance()-before-120)<0.0001);
        ledger.submit(payment,bank);check("duplicate submission does not mint twice",Math.abs(resident.getAccount().getHoldingBalance()-before-120)<0.0001);
        var reload=new RewardLedger(getDataFolder().toPath().resolve("native-payments"),150000,200000);reload.load();reload.submit(payment,bank);check("reloaded PAID does not mint twice",Math.abs(resident.getAccount().getHoldingBalance()-before-120)<0.0001);
        universe.newTown("ProbeCity"+id.toString().substring(0,6));var town=universe.getTown("ProbeCity"+id.toString().substring(0,6));resident.setTown(town);town.setMayor(resident);
        double treasury=town.getAccount().getHoldingBalance();check("resident transfers income to town treasury",resident.getAccount().payTo(100,town,"native earned-income contribution"));
        check("town received 100, resident retained 20",Math.abs(town.getAccount().getHoldingBalance()-treasury-100)<0.0001&&Math.abs(resident.getAccount().getHoldingBalance()-before-20)<0.0001);
        // Existing city escrow must move existing money, not invoke the server reward ledger.
        check("city contract reservation spends treasury",town.getAccount().withdraw(30,"native city escrow"));
        check("city escrow amount conserved",Math.abs(town.getAccount().getHoldingBalance()-treasury-70)<0.0001);
        var fixture=Bukkit.getPluginManager().getPlugin("EconomyFixture");var health=fixture.getClass().getField("healthy");
        UUID pending=UUID.randomUUID();ledger.reserve(pending,id,100,"jobs:provider-outage",false);double saved=resident.getAccount().getHoldingBalance();
        health.setBoolean(fixture,false);check("disabled provider pauses new bank calls",!bank.ready(id));ledger.submit(pending,bank);check("outage retains pending obligation",ledger.get(pending).state()==RewardLedger.State.PENDING);
        deliveryPreflight(id);
        health.setBoolean(fixture,true);ledger.submit(pending,bank);check("provider recovery pays pending once",ledger.get(pending).state()==RewardLedger.State.PAID&&Math.abs(resident.getAccount().getHoldingBalance()-saved-1)<0.0001);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"orders admin status");Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"jobpay admin status");
    }
    private Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void auditWhenDisconnected()throws Exception{
        var control=Bukkit.getPluginManager().getPlugin("NeverLandTownyControl");
        var listener=org.bukkit.event.HandlerList.getRegisteredListeners(control).stream().map(org.bukkit.plugin.RegisteredListener::getListener)
                .filter(l->l.getClass().getSimpleName().equals("BankAudit")).findFirst().orElseThrow();
        var resident=new com.palmergames.bukkit.towny.object.Resident("NoEconomyProbe",UUID.randomUUID());
        var adapter=TownyEconomyHandler.class.getDeclaredField("economy");adapter.setAccessible(true);Object saved=adapter.get(null);
        try{
            adapter.set(null,null);check("missing Towny adapter pauses economy",!TownyEconomyHandler.isActive());
            var event=new com.palmergames.bukkit.towny.event.resident.NewResidentEvent(resident);
            listener.getClass().getMethod("resident",event.getClass()).invoke(listener,event);
            check("audit does not initialize resident account without provider",resident.getAccountOrNull()==null);
        }finally{adapter.set(null,saved);}
    }
    @SuppressWarnings("unchecked") private Map<UUID,PublicOrders.Order> rows(PublicOrders orders)throws Exception{return (Map<UUID,PublicOrders.Order>)field(orders,"orders");}
    private Player player(UUID id,boolean allowed,List<Component> messages){
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()){
            case "getUniqueId"->id;case "hasPermission"->allowed;case "isOp"->false;case "getName"->"UIProbe";
            case "sendMessage"->{if(args!=null)for(Object arg:args)if(arg instanceof Component c)messages.add(c);yield null;}
            case "getInventory"->throw new AssertionError("Disabled provider must not touch player inventory");
            default->throw new UnsupportedOperationException(method.getName());
        });
    }
    private void deliveryPreflight(UUID player)throws Exception{
        var executor=(PublicOrders)Bukkit.getPluginCommand("orders").getExecutor();var entries=rows(executor);UUID id=UUID.randomUUID();
        entries.put(id,new PublicOrders.Order(id,player,"wheat_delivery","Поставка пшеницы",PublicOrders.Type.DELIVERY,"WHEAT",64,0,12000,7200000,0,"ACTIVE"));
        try{
            var method=PublicOrders.class.getDeclaredMethod("claim",Player.class,UUID.class);method.setAccessible(true);
            boolean rejected=false;try{method.invoke(executor,player(player,true,new ArrayList<>()),id);}catch(InvocationTargetException ex){rejected=ex.getCause() instanceof IllegalArgumentException&&ex.getCause().getMessage().contains("Ресурсы остаются");}
            check("delivery rejects inactive Vault before reading or removing items",rejected);
            var runtime=(IncomeRuntime)field(executor,"income");check("failed preflight leaves order without HELD reservation",runtime.ledger().get(id)==null);
        }finally{entries.remove(id);}
    }
    private void presentationChecks()throws Exception{
        var executor=(PublicOrders)Bukkit.getPluginCommand("orders").getExecutor();var entries=rows(executor);
        var icon=PublicOrders.class.getDeclaredMethod("icon",PublicOrders.Template.class);icon.setAccessible(true);
        var sword=(org.bukkit.inventory.ItemStack)icon.invoke(executor,new PublicOrders.Template("zombie_hunt","Безопасность дорог",PublicOrders.Type.HUNTING,"ZOMBIE",20,16000,7200000));
        var display=sword.getData(DataComponentTypes.TOOLTIP_DISPLAY);
        check("Folia item hides native attack attributes",display!=null&&!display.hideTooltip()&&display.hiddenComponents().contains(DataComponentTypes.ATTRIBUTE_MODIFIERS));
        String lore=String.join("\n",sword.getItemMeta().getLore());check("Russian target and readable hours in native item",lore.contains("Зомби")&&lore.contains("2 ч.")&&!lore.contains("ZOMBIE"));
        UUID owner=UUID.randomUUID(),id=UUID.randomUUID();entries.put(id,new PublicOrders.Order(id,owner,"iron_mining","Разведка железной жилы",PublicOrders.Type.MINING,"IRON_ORE",24,0,18000,14400000,0,"ACTIVE"));
        try{
            var messages=new ArrayList<Component>();var p=player(owner,true,messages);
            executor.onCommand(p,Bukkit.getPluginCommand("orders"),"orders",new String[]{"mine"});
            String text=messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).collect(java.util.stream.Collectors.joining("\n"));
            check("player status shows readable goal/progress without UUID",text.contains("Железная руда")&&text.contains("0 из 24")&&!text.contains(id.toString()));
            var accept=PublicOrders.class.getDeclaredMethod("accept",Player.class,String.class);accept.setAccessible(true);boolean denied=false;
            try{accept.invoke(executor,player(owner,false,messages),"wheat_delivery");}catch(InvocationTargetException ex){denied=ex.getCause() instanceof IllegalArgumentException&&ex.getCause().getMessage().contains("Нет прав");}
            check("stale board cannot accept after permission revoked",denied&&entries.values().stream().filter(o->o.player().equals(owner)).count()==1);
        }finally{entries.remove(id);}
    }
    private void regions(){World world=Bukkit.getWorlds().getFirst();for(int x:new int[]{0,10000}){
        Location at=new Location(world,x,80,0);world.getChunkAtAsync(at).thenAccept(chunk->Bukkit.getRegionScheduler().run(this,at,t->{synchronized(SuiteState.lock()){
            try{check("region owns location "+x,Bukkit.isOwnedByCurrentRegion(at));var original=at.getBlock().getBlockData();at.getBlock().setType(Material.IRON_ORE,false);
                var guard=new ActionGuard(this,"native-blocks.log");check("fresh ore eligible "+x,guard.untouched(at.getBlock()));guard.deny(at.getBlock());check("placed/mined coordinate rejected "+x,!guard.untouched(at.getBlock()));var reload=new ActionGuard(this,"native-blocks.log");check("block provenance survives reload "+x,!reload.untouched(at.getBlock()));at.getBlock().setBlockData(original,false);
                if(x==0){
                    Zombie mob=world.spawn(at,Zombie.class);
                    try{
                        var death=new org.bukkit.event.entity.EntityDeathEvent(mob,org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),new ArrayList<>()){
                            @Override public LivingEntity getEntity(){throw new AssertionError("Cancelled death must not inspect/credit its victim");}
                        };
                        death.setCancelled(true);check("cancelled death rejected before victim or player access",!guard.hunted(death));
                        for(String command:List.of("orders","jobpay")){
                            var handler=Bukkit.getPluginCommand(command).getExecutor().getClass().getMethod("kill",org.bukkit.event.entity.EntityDeathEvent.class);
                            check(command+" kill listener skips cancelled events",handler.getAnnotation(org.bukkit.event.EventHandler.class).ignoreCancelled());
                        }
                    }finally{mob.remove();}
                }
                ArmorStand entity=world.spawn(at,ArmorStand.class);SuiteScheduler.entity(this,entity,()->{check("entity scheduler owns entity "+x,Bukkit.isOwnedByCurrentRegion(entity));entity.remove();if(remaining.decrementAndGet()==0)finish();});
            }catch(Throwable ex){check(ex.toString(),false);if(remaining.decrementAndGet()==0)finish();}
        }}));}}
    private void finish(){Bukkit.getGlobalRegionScheduler().run(this,t->{try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("result.txt"),String.join("\n",results)+"\nfailed="+failed+"\n");}catch(Exception ex){throw new RuntimeException(ex);}});}
}
