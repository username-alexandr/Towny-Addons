package ru.neverland.runtime;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.entity.*;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import net.kyori.adventure.text.Component;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.townydiplomacy.*;
import ru.neverland.townydiplomacy.api.TownyDiplomacyApi;
import ru.neverland.townycouncil.*;
import ru.neverland.townycitizens.*;
import ru.neverland.townycitizens.model.*;
import ru.neverland.core.DiplomacyAccess;
import ru.neverland.mintespionage.service.EspionageService;
import ru.neverland.mintespionage.model.*;
import static ru.neverland.townydiplomacy.Treaty.*;

/** Native Towny fixture; explicitly opt-in, loopback-only, never shipped in suite ZIPs. */
public final class DiplomacyRuntimeProbe extends JavaPlugin {
    static UUID id(String s){return UUID.nameUUIDFromBytes(("diplomacy-027-"+s).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static final UUID A=id("A"),B=id("B"),C=id("C"),D=id("D"),MA=id("mayorA"),MB=id("mayorB"),MC=id("mayorC"),MD=id("mayorD"),F=id("foreign"),N=id("next");
    private Path proof;private int checks;private World world;private Town a,b,c,d;private NeverLandTownyDiplomacy owner;private DiplomacyService service;private CouncilService council;private CitizensService citizens;private EspionageService spy;
    private JavaPlugin plugin(String name){return (JavaPlugin)Bukkit.getPluginManager().getPlugin(name);}
    static <T>T serviceField(Object o,Class<T> type)throws Exception{for(var f:o.getClass().getDeclaredFields())if(type.isAssignableFrom(f.getType())){f.setAccessible(true);return type.cast(f.get(o));}throw new IllegalArgumentException(type.getName());}
    void check(boolean ok,String label)throws Exception{if(!ok)throw new AssertionError(label);checks++;getLogger().info("CHECK "+label);Files.writeString(proof.resolve("checks.txt"),label+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    interface Work{void run()throws Exception;}
    void deny(Work work,String label)throws Exception{try{work.run();}catch(IllegalArgumentException expected){check(true,label);return;}throw new AssertionError(label);}
    @Override public void onEnable(){if(!Boolean.getBoolean("neverland.runtimeProbe")||!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RELIABILITY_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){Bukkit.getPluginManager().disablePlugin(this);return;}Bukkit.getScheduler().runTaskLater(this,this::run,40);}
    private void run(){try{
        proof=getDataFolder().toPath();Files.createDirectories(proof);world=Bukkit.getWorlds().getFirst();
        check(Arrays.stream(Bukkit.getPluginManager().getPlugins()).filter(p->p.getName().startsWith("NeverLandTowny")&&p.isEnabled()).count()==31,"all 31 addons enabled");
        owner=(NeverLandTownyDiplomacy)plugin("NeverLandTownyDiplomacy");service=owner.diplomacy();council=((NeverLandTownyCouncil)plugin("NeverLandTownyCouncil")).council();citizens=((NeverLandTownyCitizens)plugin("NeverLandTownyCitizens")).citizens();spy=serviceField(plugin("NeverLandTownyEspionage"),EspionageService.class);
        check(Bukkit.getServicesManager().load(TownyDiplomacyApi.class)==service&&DiplomacyAccess.available(),"versioned healthy Diplomacy API registered");
        if(Files.exists(proof.resolve("first-passed.txt"))){towns();restart();finish(true);}else{setup();scenarios();menuScenario();}
    }catch(Throwable error){fail(error);}}
    private void fail(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"DIPLOMACY_FAIL",error);try{var out=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(out));Files.writeString(proof.resolve("failed.txt"),out.toString());}catch(Exception ignored){}Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);}
    private void finish(boolean restart)throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();world.save();Files.writeString(proof.resolve(restart?"restart-passed.txt":"first-passed.txt"),"PASS "+checks+" assertions\n");Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);}
    private void towns(){a=TownyAPI.getInstance().getTown(A);b=TownyAPI.getInstance().getTown(B);c=TownyAPI.getInstance().getTown(C);d=TownyAPI.getInstance().getTown(D);}
    private void setup()throws Exception{
        var u=TownyUniverse.getInstance();if(TownyAPI.getInstance().getTown(A)!=null)throw new IllegalStateException("Fixture exists; restore disposable snapshot first");
        for(var e:Map.of(A,"DiplomacyA",B,"DiplomacyB",C,"DiplomacyC",D,"DiplomacyD").entrySet())u.newTownInternal(e.getValue(),e.getKey());towns();
        for(var e:Map.of(MA,"DiplomatA",MB,"DiplomatB",MC,"DiplomatC",MD,"DiplomatD",F,"DiplomatForeign",N,"DiplomatNext").entrySet()){
            var r=u.getDataSource().newResident(e.getValue(),e.getKey());r.setTown(e.getKey().equals(MB)?b:e.getKey().equals(MC)?c:e.getKey().equals(MD)?d:a);r.save();
        }
        a.setMayor(TownyAPI.getInstance().getResident(MA));b.setMayor(TownyAPI.getInstance().getResident(MB));c.setMayor(TownyAPI.getInstance().getResident(MC));d.setMayor(TownyAPI.getInstance().getResident(MD));
        int coordinate=126;for(var town:List.of(a,b,c,d)){var claim=new TownBlock(coordinate,coordinate,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);town.setHomeBlock(claim);claim.save();world.getChunkAt(coordinate,coordinate).load();town.setSpawn(new Location(world,coordinate*16+4,92,coordinate*16+4));town.save();coordinate++;}
    }
    private Treaty propose(Town from,Town to,TreatyType type,SanctionScope scope)throws Exception{return service.offer(Bukkit.getConsoleSender(),from,to,type,30,scope,"Disposable diplomatic fixture");}
    private Treaty agree(Town from,Town to,TreatyType type)throws Exception{var t=propose(from,to,type,SanctionScope.NONE);return service.change(Bukkit.getConsoleSender(),to,t.id(),"accept");}
    private Treaty find(TreatyType type,UUID first,UUID second){return service.repository().all().values().stream().filter(t->t.type()==type&&t.first().equals(first)&&t.second().equals(second)&&t.phase()!=Phase.ENDED).findFirst().orElseThrow();}
    private SpyOperation mission(String name,Town from,Town to){long now=System.currentTimeMillis();var def=spy.registry().all().iterator().next();var op=new SpyOperation(id(name),from.getUUID(),from.getName(),to.getUUID(),to.getName(),MA,"DiplomatA",def.id(),now,now+3_600_000,.5,.1,0,OperationStatus.ACTIVE,false);spy.repository().add(op);spy.repository().save();return op;}
    private void scenarios()throws Exception{
        serviceField(plugin("NeverLandTownyPolicies"),ru.neverland.townypolicies.service.PoliciesService.class).refresh();
        var mayor=new Actor(MA);var minister=new Actor(F);var ordinary=new Actor(N);var outsider=new Actor(MB);
        deny(()->service.offer(ordinary.player,a,b,TreatyType.ALLIANCE,30,SanctionScope.NONE,"attempt"),"ordinary resident cannot sign treaties");
        deny(()->service.offer(outsider.player,a,b,TreatyType.ALLIANCE,30,SanctionScope.NONE,"attempt"),"foreign mayor cannot sign for another town");
        council.appoint(mayor.player,a,MinisterRole.FOREIGN,F);council.refresh(minister.player);
        check(service.manages(minister.player,a,TreatyType.ALLIANCE)&&service.manages(minister.player,a,TreatyType.EMBARGO),"foreign minister has both agreement and sanction authority");
        citizens.assign(Bukkit.getConsoleSender(),a,F,CitizenshipStatus.FOREIGNER,0,"fixture");
        check(!service.manages(minister.player,a,TreatyType.TRADE),"citizenship revocation takes effect before attachment refresh");
        citizens.assign(Bukkit.getConsoleSender(),a,F,CitizenshipStatus.CITIZEN,0,"fixture");council.refresh(minister.player);
        var offer=service.offer(minister.player,a,b,TreatyType.ALLIANCE,30,SanctionScope.NONE,"Alliance fixture");
        check(!service.hostileBlocked(A,B),"unsigned alliance grants no protection");
        deny(()->service.change(mayor.player,a,offer.id(),"accept"),"initiator cannot accept own offer");
        var active=service.change(outsider.player,b,offer.id(),"accept");check(service.hostileBlocked(A,B)&&DiplomacyAccess.hostileBlocked(B,A),"accepted alliance protects both sides through public API");
        deny(()->service.change(outsider.player,b,offer.id(),"accept"),"repeated acceptance is rejected");
        var ending=service.change(mayor.player,a,offer.id(),"end");check(ending.phase()==Phase.TERMINATING&&service.hostileBlocked(A,B),"termination notice retains protection");
        agree(a,b,TreatyType.TRADE);check(DiplomacyAccess.tariffMultiplier(A,B,A)==.75&&DiplomacyAccess.tariffMultiplier(A,B,C)==1,"party discount excludes independent tariff collector");
        agree(a,c,TreatyType.NONAGGRESSION);agree(a,c,TreatyType.GUARANTEE);agree(b,d,TreatyType.VASSALAGE);
        check(service.overlord(D).equals(B)&&service.defenders(D).contains(B),"accepted vassalage has one direct suzerain and defense obligation");
        deny(()->propose(d,b,TreatyType.VASSALAGE,SanctionScope.NONE),"native service rejects vassal cycles");
        deny(()->propose(b,c,TreatyType.VASSALAGE,SanctionScope.NONE),"guaranteed independence blocks vassalage");
        var embargo=propose(b,c,TreatyType.EMBARGO,SanctionScope.NONE);
        check(service.tradeBlocked(B,C)&&!service.tradeBlocked(A,C),"unilateral embargo affects only its two towns");
        deny(()->service.change(new Actor(MC).player,c,embargo.id(),"end"),"target cannot lift another town's embargo");
        var restrictions=new ru.neverland.minttrade.integration.TaxesBridge(plugin("NeverLandTownyTrade"));
        var fiscal=serviceField(plugin("NeverLandTownyTaxes"),ru.neverland.townytaxes.service.FiscalService.class);
        var market=new ru.neverland.townymarket.MarketGateway(plugin("NeverLandTownyMarket"),new ru.neverland.townymarket.MarketRepository(proof.resolve("unused-market.yml")));
        check(restrictions.tradeBlocked(B,C)&&restrictions.supplyRestriction(B,C)!=null&&fiscal.isTradeBlocked(B,C)&&market.imports(B,C).contains("дипломат"),"Trade, recurring supply, Taxes and Market enforce embargo");
        check(!restrictions.tradeBlocked(A,C)&&!fiscal.isTradeBlocked(A,C)&&market.imports(A,C)==null,"unrelated partner remains open in all trade integrations");
        propose(c,d,TreatyType.SANCTIONS,SanctionScope.DIPLOMATIC);
        deny(()->propose(d,c,TreatyType.ALLIANCE,SanctionScope.NONE),"diplomatic sanctions block new bilateral offers");
        check(!service.tradeBlocked(C,D),"diplomatic-only sanctions leave trade open");
        var helpers=service.recordAttack(D,C,"world 1 2 3");check(helpers.equals(Set.of(A)),"guarantor receives durable defense obligation");
        check(service.recordAttack(D,C,"world 1 2 3").isEmpty(),"incident cooldown suppresses repeat notifications");
        var def=spy.registry().all().iterator().next();double balance=spy.economy().balance(a);
        check(spy.start(mayor.player,c,def).status()==EspionageService.StartStatus.DIPLOMACY_BLOCKED&&spy.economy().balance(a)==balance,"nonaggression rejects espionage before bank debit");
        var op=mission("protected-spy",a,c);int reports=spy.repository().reports(A).size();
        check(spy.forceComplete(op.id())&&op.status()==OperationStatus.CANCELLED&&spy.repository().reports(A).size()==reports,"new pact cancels unfinished intelligence without a report");
        var paused=mission("paused-spy",d,c);
        Bukkit.getServicesManager().unregister(TownyDiplomacyApi.class,service);
        check(!DiplomacyAccess.available()&&restrictions.tradeBlocked(A,D)&&market.imports(A,D)!=null,"installed provider outage closes new cross-town purchases");
        check(!spy.forceComplete(paused.id())&&paused.status()==OperationStatus.ACTIVE,"provider outage pauses paid espionage without fabricated cancellation");
        Bukkit.getServicesManager().register(TownyDiplomacyApi.class,service,owner,ServicePriority.Normal);
        check(DiplomacyAccess.available()&&!DiplomacyAccess.tradeBlocked(A,D),"API recovery restores unchanged relations");spy.cancel(paused.id());
        combat();
        var pending=propose(a,c,TreatyType.TRADE,SanctionScope.NONE);a.setMayor(TownyAPI.getInstance().getResident(N));a.save();service.maintain(System.currentTimeMillis());
        check(service.repository().all().get(pending.id()).phase()==Phase.ENDED&&service.hostileBlocked(A,B),"mayor change cancels unsigned offer and retains active treaty");
        a.setMayor(TownyAPI.getInstance().getResident(MA));a.save();council.appoint(mayor.player,a,MinisterRole.FOREIGN,F);
        check(TownyCommandAddonAPI.getAddonCommand(TownyCommandAddonAPI.CommandType.TOWN,"diplomacy")!=null,"Towny diplomacy subcommand registered");
    }
    private Player nativePlayer(UUID id){return new ServerPlayer(((CraftServer)Bukkit.getServer()).getServer(),((CraftWorld)world).getHandle(),new GameProfile(id,TownyAPI.getInstance().getResident(id).getName()),ClientInformation.createDefault()).getBukkitEntity();}
    private void combat()throws Exception{
        var listener=new DiplomacyCombatListener(owner,service);var attacker=nativePlayer(MA);var victim=nativePlayer(MC);
        var source=org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withCausingEntity(attacker).withDirectEntity(attacker).build();
        var damage=new EntityDamageByEntityEvent(attacker,victim,EntityDamageEvent.DamageCause.ENTITY_ATTACK,source,5);listener.damage(damage);check(damage.isCancelled(),"native player damage vetoed by nonaggression");
        var townyEvent=new com.palmergames.bukkit.towny.event.damage.TownyPlayerDamagePlayerEvent(victim.getLocation(),victim,EntityDamageEvent.DamageCause.ENTITY_ATTACK,null,false,attacker);listener.towny(townyEvent);check(townyEvent.isCancelled(),"Towny PvP custom event receives treaty veto");
        var arrow=world.spawn(world.getSpawnLocation(),Arrow.class);arrow.setShooter(attacker);
        try{var event=new EntityDamageByEntityEvent(arrow,victim,EntityDamageEvent.DamageCause.PROJECTILE,source,5);listener.damage(event);check(event.isCancelled()&&listener.responsible(arrow).equals(MA),"projectile resolves its shooter and blocks attack");}finally{arrow.remove();}
        var cloud=world.spawn(world.getSpawnLocation(),AreaEffectCloud.class);cloud.setSource(attacker);cloud.addCustomEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.POISON,100,0),true);
        try{var event=new AreaEffectCloudApplyEvent(cloud,new ArrayList<>(List.of(victim)));listener.cloud(event);check(event.getAffectedEntities().isEmpty(),"harmful area cloud excludes treaty-protected player");}finally{cloud.remove();}
        var neutral=nativePlayer(MD);var allowed=new EntityDamageByEntityEvent(neutral,victim,EntityDamageEvent.DamageCause.ENTITY_ATTACK,org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withCausingEntity(neutral).withDirectEntity(neutral).build(),3);listener.damage(allowed);check(!allowed.isCancelled(),"listener leaves unrelated attack to Towny rules");
        allowed.setCancelled(true);listener.damage(allowed);check(allowed.isCancelled(),"existing protection cancellation is never cleared");
    }
    private void menuScenario()throws Exception{
        var offer=propose(d,a,TreatyType.TRADE,SanctionScope.NONE);var minister=new Actor(F);council.refresh(minister.player);var menus=new DiplomacyMenus(owner,service);menus.open(minister.player,a,0,null);
        check(minister.top.getSize()==54&&minister.top.getItem(4)!=null,"readable paginated diplomacy menu opens");
        var holder=minister.top.getHolder();var detail=Arrays.stream(DiplomacyMenus.class.getDeclaredMethods()).filter(m->m.getName().equals("detail")).findFirst().orElseThrow();detail.setAccessible(true);detail.invoke(menus,minister.player,holder,offer.id(),"accept");
        check(minister.top.getItem(31).getType()==Material.LIME_DYE,"acceptance has explicit confirmation page");
        council.dismiss(new Actor(MA).player,a,MinisterRole.FOREIGN);
        var click=new InventoryClickEvent(minister.view,InventoryType.SlotType.CONTAINER,31,ClickType.LEFT,InventoryAction.PICKUP_ALL);menus.click(click);check(click.isCancelled(),"confirmation menu blocks item extraction");
        Bukkit.getScheduler().runTaskLater(this,()->{try{
            check(service.repository().all().get(offer.id()).phase()==Phase.PENDING,"stale GUI confirmation cannot bypass minister dismissal");
            council.appoint(new Actor(MA).player,a,MinisterRole.FOREIGN,F);finish(false);
        }catch(Throwable error){fail(error);}},2);
    }
    private void restart()throws Exception{
        check(a!=null&&b!=null&&c!=null&&d!=null,"Towny diplomatic parties survive restart");
        check(service.hostileBlocked(A,B)&&find(TreatyType.ALLIANCE,A,B).phase()==Phase.TERMINATING,"termination notice survives restart");
        check(service.hostileBlocked(A,C)&&service.overlord(D).equals(B)&&service.defenders(C).contains(A),"pact, vassal and guarantee effects survive restart");
        check(service.tradeBlocked(B,C)&&!service.tradeBlocked(A,C)&&!service.tradeBlocked(C,D),"embargo and sanction scope survive restart");
        check(service.tariffMultiplier(A,B,B)==.75&&service.tariffMultiplier(A,B,D)==1,"tariff consent survives restart");
        check(service.recordAttack(D,C,"world 1 2 3").isEmpty()&&service.repository().incidents().size()==1,"incident and cooldown survive restart");
        check(service.repository().history().stream().anyMatch(x->x.action().equals("MAYOR_CHANGED")),"mayor-change audit persisted");
        var minister=new Actor(F);council.refresh(minister.player);check(service.manages(minister.player,a,TreatyType.TRADE),"foreign minister authority restored from Council");
        var pending=find(TreatyType.TRADE,D,A);service.change(minister.player,a,pending.id(),"accept");check(service.tariffMultiplier(D,A,A)==.75,"persisted offer can be accepted after restart");
        var file=owner.getDataFolder().toPath().resolve("diplomacy.yml");byte[] backup=Files.readAllBytes(file);Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("obstruction"),"keep");
        try{propose(a,d,TreatyType.EMBARGO,SanctionScope.NONE);throw new AssertionError("disk failure ignored");}catch(java.io.IOException expected){}
        check(!service.healthy()&&service.tradeBlocked(A,C)&&service.hostileBlocked(C,D),"save failure closes trade and combat while enabled");
        check(service.repository().all().values().stream().noneMatch(t->t.type()==TreatyType.EMBARGO&&t.first().equals(A)),"failed embargo was not published in memory");
        Files.delete(file.resolve("obstruction"));Files.delete(file);Files.write(file,backup);check(!service.healthy(),"repair alone cannot silently unlock failed registry");service.repository().load();check(service.healthy()&&!service.tradeBlocked(A,C),"explicit validated load restores original treaty state");
        Bukkit.getPluginManager().disablePlugin(owner);check(!DiplomacyAccess.available()&&DiplomacyAccess.tradeBlocked(A,C)&&Bukkit.getServicesManager().load(TownyDiplomacyApi.class)==null,"disabled provider unregisters API and closes integration gates");
    }
    private final class Actor {
        final Player player; PermissibleBase permissions; Inventory top=Bukkit.createInventory(null,9,Component.text("Fixture")); final Inventory bottom=Bukkit.createInventory(null,36);
        InventoryView view; final List<String> messages=new ArrayList<>();
        Actor(UUID id) {
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,m,a)->switch(m.getName()) {
                case "getUniqueId"->id;case "getName"->TownyAPI.getInstance().getResident(id).getName();case "getServer"->Bukkit.getServer();case "getWorld"->world;case "getLocation"->world.getSpawnLocation();
                case "isOnline"->true;case "isOp"->false;
                case "hasPermission","isPermissionSet","addAttachment","removeAttachment","recalculatePermissions","getEffectivePermissions"->m.invoke(permissions,a);
                case "sendMessage"->{messages.add(String.valueOf(a[a.length-1]));yield null;}
                case "getOpenInventory"->view;case "openInventory"->{top=(Inventory)a[0];yield view;}case "closeInventory"->{top=Bukkit.createInventory(null,9);yield null;}
                case "hashCode"->id.hashCode();case "equals"->proxy==a[0];case "toString"->"Diplomacy actor "+id;default->null;
            });
            permissions=new PermissibleBase(player);
            view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(proxy,m,a)->switch(m.getName()) {
                case "getTopInventory"->top;case "getBottomInventory"->bottom;case "getPlayer"->player;case "getType"->InventoryType.CHEST;
                case "getCursor"->new ItemStack(Material.AIR);case "countSlots"->top.getSize()+36;case "getTitle","getOriginalTitle"->"Fixture";case "title"->Component.text("Fixture");
                case "getSlotType"->InventoryType.SlotType.CONTAINER;case "convertSlot"->a[0];case "getInventory"->(Integer)a[0]<top.getSize()?top:bottom;default->null;
            });
        }
    }
}
