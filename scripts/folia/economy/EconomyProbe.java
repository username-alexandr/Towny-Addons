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
import ru.neverland.mintcontracts.orders.DailyOrderBoard;
import ru.neverland.mintcontracts.orders.OrderCatalog;
import ru.neverland.mintcontracts.orders.OrderRarity;

/** Native Towny/Vault and Folia checks in an isolated server; no production files are used. */
public final class EconomyProbe extends JavaPlugin {
    private final List<String> results=Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger remaining=new AtomicInteger(2);private int failed;
    private void check(String title,boolean value){results.add((value?"PASS ":"FAIL ")+title);if(!value)failed++;}
    @Override public void onEnable(){
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()){
            try{bankChecks();auditWhenDisconnected();presentationChecks();dailyOrdersChecks();regions();}catch(Throwable ex){getLogger().log(java.util.logging.Level.SEVERE,"Probe failed",ex);check(ex.toString(),false);finish();}
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
    private void dailyOrdersChecks()throws Exception{
        var executor=(PublicOrders)Bukkit.getPluginCommand("orders").getExecutor();
        var plugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyContracts");
        var cfg=SafeYaml.load(plugin.getDataFolder().toPath().resolve("orders.yml"));
        var catalog=OrderCatalog.templates(cfg);OrderCatalog.validateItems(catalog);
        check("migrated installed catalog has 80 valid tasks",catalog.size()==80&&cfg.getInt("catalog-version")==3);
        check("legacy config backed up",Files.exists(plugin.getDataFolder().toPath().resolve("orders.pre-catalog-v3.yml")));
        var actual=(DailyOrderBoard)field(executor,"dailyBoard");var snapshot=actual.snapshot();
        int size=snapshot.templates().size();
        check("native board retains existing 12 or generates 18 unique orders and four activities",(size==12||size==18)&&snapshot.templates().stream().map(PublicOrders.Template::id).distinct().count()==size&&snapshot.templates().stream().map(PublicOrders.Template::type).distinct().count()==4);
        var icon=PublicOrders.class.getDeclaredMethod("icon",PublicOrders.Template.class);icon.setAccessible(true);
        boolean readable=true;
        for(var template:catalog){
            var item=(org.bukkit.inventory.ItemStack)icon.invoke(executor,template);
            String lore=String.join("\n",item.getItemMeta().getLore());
            readable&=lore.contains(template.rarity().label)&&!lore.contains(template.target())&&!lore.contains("Неизвестный");
        }
        check("all 80 native icons show translated targets and rarity",readable);
        var time=new AtomicReference<java.time.Instant>(java.time.Instant.parse("2026-10-20T20:59:59Z"));
        var clock=new java.time.Clock(){public java.time.ZoneId getZone(){return java.time.ZoneOffset.UTC;}public java.time.Clock withZone(java.time.ZoneId zone){return java.time.Clock.fixed(time.get(),zone);}public java.time.Instant instant(){return time.get();}};
        var template=new PublicOrders.Template("native_daily","Проверка редкого заказа",PublicOrders.Type.MINING,"IRON_ORE",32,75000,21600000,OrderRarity.EPIC);
        Path isolated=getDataFolder().toPath().resolve("daily-"+UUID.randomUUID()+".yml");
        var daily=new DailyOrderBoard(isolated,List.of(template),1,OrderCatalog.weights(cfg),clock,new Random(17));
        var dailyField=PublicOrders.class.getDeclaredField("dailyBoard");dailyField.setAccessible(true);
        var entries=rows(executor);var original=new LinkedHashMap<>(entries);Path ordersFile=(Path)field(executor,"file");byte[] originalBytes=Files.exists(ordersFile)?Files.readAllBytes(ordersFile):null;
        var save=PublicOrders.class.getDeclaredMethod("save");save.setAccessible(true);var load=PublicOrders.class.getDeclaredMethod("load");load.setAccessible(true);
        var accept=PublicOrders.class.getDeclaredMethod("accept",Player.class,String.class);accept.setAccessible(true);
        var clickAccept=PublicOrders.class.getDeclaredMethod("accept",Player.class,String.class,java.time.LocalDate.class);clickAccept.setAccessible(true);
        UUID owner=UUID.randomUUID();var p=player(owner,true,new ArrayList<>());
        try{
            dailyField.set(executor,daily);entries.clear();accept.invoke(executor,p,template.id());
            var accepted=entries.values().iterator().next();
            check("accepted task snapshots rarity and Moscow board day",accepted.rarity()==OrderRarity.EPIC&&accepted.boardDay().equals("2026-10-20")&&accepted.cents()==75000);
            var progressed=new PublicOrders.Order(accepted.id(),owner,accepted.template(),accepted.title(),accepted.type(),accepted.target(),accepted.goal(),7,accepted.cents(),123000,accepted.cycle(),accepted.state(),accepted.rarity(),accepted.boardDay());
            entries.put(progressed.id(),progressed);save.invoke(executor);entries.clear();load.invoke(executor);
            check("accepted reward goal progress time rarity day survive reload",progressed.equals(entries.get(progressed.id())));
            time.set(java.time.Instant.parse("2026-10-20T21:00:00Z"));boolean stale=false;
            try{clickAccept.invoke(executor,p,template.id(),java.time.LocalDate.of(2026,10,20));}catch(InvocationTargetException ex){stale=ex.getCause() instanceof IllegalArgumentException&&ex.getCause().getMessage().contains("Доска обновилась");}
            check("midnight stale GUI cannot accept even if same ID is on new board",stale&&entries.size()==1);
            accept.invoke(executor,p,template.id());
            check("new daily occurrence accepted without changing old order",entries.size()==2&&entries.get(progressed.id()).equals(progressed));
            var today=entries.values().stream().filter(o->o.boardDay().equals("2026-10-21")).findFirst().orElseThrow();
            executor.onCommand(p,Bukkit.getPluginCommand("orders"),"orders",new String[]{"cancel",today.id().toString()});boolean duplicate=false;
            try{accept.invoke(executor,p,template.id());}catch(InvocationTargetException ex){duplicate=ex.getCause() instanceof IllegalArgumentException&&ex.getCause().getMessage().contains("уже принят сегодня");}
            check("cancelled order cannot be retaken from same daily board",duplicate&&entries.size()==2);
            var legacy=SafeYaml.load(ordersFile);legacy.set("schema",1);
            for(String id:legacy.getConfigurationSection("orders").getKeys(false)){legacy.set("orders."+id+".rarity",null);legacy.set("orders."+id+".board-day",null);}
            AtomicFiles.write(ordersFile,legacy::saveToString);entries.clear();load.invoke(executor);var old=entries.get(progressed.id());
            check("schema 1 accepted tasks retain progress reward deadline",old.progress()==7&&old.cents()==75000&&old.remaining()==123000&&old.goal()==32&&old.rarity()==OrderRarity.COMMON&&old.boardDay().isEmpty());
        }finally{
            dailyField.set(executor,actual);entries.clear();entries.putAll(original);
            if(originalBytes==null)Files.deleteIfExists(ordersFile);else Files.write(ordersFile,originalBytes);
            Files.deleteIfExists(isolated);
        }
    }
    private void regions(){World world=Bukkit.getWorlds().getFirst();for(int x:new int[]{0,10000}){
        Location at=new Location(world,x,80,0);world.getChunkAtAsync(at).thenAccept(chunk->Bukkit.getRegionScheduler().run(this,at,t->{try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()){
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
