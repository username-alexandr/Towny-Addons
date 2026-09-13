package ru.neverland.runtime;

import com.mojang.authlib.GameProfile;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townycitizens.*;
import ru.neverland.townycitizens.model.*;
import ru.neverland.townycitizens.api.TownyCitizensApi;
import ru.neverland.governance.service.GovernanceService;
import ru.neverland.governance.model.VoteChoice;
import ru.neverland.townytaxes.service.FiscalService;
import ru.neverland.townytaxes.model.*;
import ru.neverland.passport.model.Passport;
import ru.neverland.passport.integration.CitizensBridge;

/** Opt-in real-server assertions; the player's absent network connection is replaced by a view proxy. */
public final class CitizensRuntimeProbe extends JavaPlugin {
    private static UUID id(String value) { return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private static final UUID TOWN=id("citizens-town"), ACTOR=id("citizens-player"), MAYOR=id("citizens-mayor"), TEMP=id("citizens-expiry"), GUEST=id("citizens-guest");
    private Path proof; private int checks; private World world; private Town town;
    private Player player, nativePlayer; private Inventory top; private InventoryView view;
    private final List<String> messages=new ArrayList<>();
    private CitizensService citizens;
    private JavaPlugin plugin(String name) { return (JavaPlugin)Bukkit.getPluginManager().getPlugin(name); }
    private static Object field(Object value,String name)throws Exception { var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value); }
    private void check(boolean good,String text)throws Exception { if(!good)throw new AssertionError(text);checks++;Files.writeString(proof.resolve("checks.txt"),text+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+text); }
    private interface Work { void run()throws Exception; }
    private void denies(Work work,String text)throws Exception { try { work.run(); } catch(Exception expected) { check(true,text);return; } throw new AssertionError(text); }
    @Override public void onEnable() {
        if(!Boolean.getBoolean("neverland.runtimeProbe")||!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RELIABILITY_PROBE"))||!"127.0.0.1".equals(getServer().getIp())) { getServer().getPluginManager().disablePlugin(this);return; }
        Bukkit.getScheduler().runTaskLater(this,this::run,100L);
    }
    private void run() { try {
        proof=getDataFolder().toPath();Files.createDirectories(proof);world=Bukkit.getWorlds().getFirst();
        check(Arrays.stream(Bukkit.getPluginManager().getPlugins()).filter(p->p.getName().startsWith("NeverLandTowny")&&p.isEnabled()).count()==28,"all 28 addons enabled");
        check(plugin("NeverLandPassport").isEnabled(),"Passport 0.4.0 enabled");
        citizens=((NeverLandTownyCitizens)plugin("NeverLandTownyCitizens")).citizens();
        check(Bukkit.getServicesManager().load(TownyCitizensApi.class)==citizens,"Citizens public API registered");
        actor();
        if(Files.exists(proof.resolve("first-passed.txt"))) {
            town=TownyAPI.getInstance().getTown(TOWN);
            check(town!=null,"town survives restart");
            check(citizens.status(TOWN,ACTOR).equals("HONORARY"),"honorary status survives restart");
            check(citizens.status(TOWN,TEMP).equals("FOREIGNER"),"temporary status expires while offline");
            check(!citizens.allows(TOWN,TEMP,"VOTE"),"expired resident cannot vote after restart");
            check(citizens.taxMultiplier(TOWN,TEMP)==1.5,"expired resident receives foreigner tax policy");
            check(CitizensBridge.citizenship(new Passport(ACTOR,"CitizensPlayer")).equals("Почётный гражданин"),"passport reads persisted live status");
            check(!citizens.repository().get(TOWN,ACTOR).actor().isBlank(),"assignment author persists");
            Files.writeString(proof.resolve("restart-passed.txt"),"PASS "+checks+" assertions\n");
        } else {
            setup();scenarios();TownyUniverse.getInstance().getDataSource().saveAll();world.save();nativePlayer.saveData();
            Files.writeString(proof.resolve("first-passed.txt"),"PASS "+checks+" assertions\n");
        }
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,20L);
    } catch(Throwable error) {
        getLogger().log(java.util.logging.Level.SEVERE,"CITIZENS_FAIL",error);
        try { var writer=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(writer));Files.writeString(proof.resolve("failed.txt"),writer.toString()); } catch(Exception ignored) { }
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,20L);
    } }
    private void actor() {
        var handle=new ServerPlayer(((CraftServer)Bukkit.getServer()).getServer(),((CraftWorld)world).getHandle(),new GameProfile(ACTOR,"CitizensPlayer"),ClientInformation.createDefault());
        handle.absSnapTo(1940,92,1940,0,0);nativePlayer=handle.getBukkitEntity();nativePlayer.loadData();top=Bukkit.createInventory(null,9,Component.text("Base"));
        player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)-> {
            switch(m.getName()) {
                case "isOnline":return true; case "isOp":return false;
                case "hasPermission":return !Set.of("neverlandtownycitizens.admin","neverlandtownycitizens.bypass").contains(String.valueOf(a[0]));
                case "sendMessage":messages.add(String.valueOf(a[a.length-1]));return null;
                case "getOpenInventory":return view;case "openInventory":top=(Inventory)a[0];return view;
                case "closeInventory":top=Bukkit.createInventory(null,9,Component.text("Base"));return null;
                case "spawnParticle":return null;
            }
            try { return m.invoke(nativePlayer,a); } catch(InvocationTargetException ex) { throw ex.getCause(); }
        });
        view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(p,m,a)->switch(m.getName()) {
            case "getTopInventory"->top;case "getBottomInventory"->nativePlayer.getInventory();case "getPlayer"->player;
            case "getType"->InventoryType.CHEST;case "getCursor"->new ItemStack(Material.AIR);case "countSlots"->top.getSize()+36;
            case "getTitle","getOriginalTitle"->"Fixture";case "title"->Component.text("Fixture");
            case "getSlotType"->InventoryType.SlotType.CONTAINER;case "convertSlot"->a[0];
            case "getInventory"->((Integer)a[0])<top.getSize()?top:nativePlayer.getInventory();default->null;
        });
    }
    private void setup()throws Exception {
        var u=TownyUniverse.getInstance();if(TownyAPI.getInstance().getTown(TOWN)==null)u.newTownInternal("CitizensTown",TOWN);town=TownyAPI.getInstance().getTown(TOWN);
        for(var entry:Map.of(ACTOR,"CitizensPlayer",MAYOR,"CitizensMayor",TEMP,"CitizensExpiry",GUEST,"CitizensGuest").entrySet()) {
            Resident r=TownyAPI.getInstance().getResident(entry.getKey());if(r==null)r=u.getDataSource().newResident(entry.getValue(),entry.getKey());if(!entry.getKey().equals(GUEST)&&!town.equals(r.getTownOrNull()))r.setTown(town);r.save();
        }
        town.setMayor(TownyAPI.getInstance().getResident(MAYOR));
        if(town.getTownBlocks().isEmpty()){var claim=new TownBlock(121,121,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);town.setHomeBlock(claim);claim.save();}world.getChunkAt(121,121).load();
        town.setSpawn(new Location(world,1940,92,1940));town.save();
    }
    private void assign(UUID player,CitizenshipStatus status,int days)throws Exception { citizens.assign(Bukkit.getConsoleSender(),town,player,status,days,"Disposable server test"); }
    private void scenarios()throws Exception {
        check(citizens.status(TOWN,ACTOR).equals("CITIZEN"),"existing Towny member defaults to citizen");
        check(citizens.status(TOWN,GUEST).equals("FOREIGNER"),"guest defaults to foreigner");
        check(citizens.allows(TOWN,ACTOR,"VOTE"),"citizen franchise");
        check(!citizens.allows(TOWN,ACTOR,"unknown"),"unknown right denied");
        denies(()->citizens.assign(player,town,ACTOR,CitizenshipStatus.HONORARY,0,"self"),"ordinary resident cannot self-promote");
        denies(()->assign(MAYOR,CitizenshipStatus.TEMPORARY,30),"mayor cannot lose citizenship");
        denies(()->assign(GUEST,CitizenshipStatus.CITIZEN,0),"citizenship cannot invent Towny membership");
        assign(ACTOR,CitizenshipStatus.TEMPORARY,30);
        check(!citizens.allows(TOWN,ACTOR,"VOTE")&&!citizens.allows(TOWN,ACTOR,"HOLD_OFFICE"),"temporary resident lacks electoral rights");
        check(!citizens.allows(TOWN,ACTOR,"STORAGE")&&!citizens.allows(TOWN,ACTOR,"INSURANCE"),"temporary municipal restrictions");
        check(citizens.allows(TOWN,ACTOR,"BUILD")&&citizens.allows(TOWN,ACTOR,"SHOP"),"temporary basic rights");
        var passport=new Passport(ACTOR,"CitizensPlayer");passport.setCitizenship("NeverLand");
        check(CitizensBridge.citizenship(passport).equals("Временный житель"),"passport shows temporary status");
        check(passport.getCitizenship().equals("NeverLand"),"passport identity data remains unchanged");
        check(CitizensBridge.value(passport,"citizenship_town_name").equals("CitizensTown"),"passport resolves town through public API");
        check(me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player,"%townycitizens_status%").equals("Временный житель"),"Citizens PlaceholderAPI integration");
        var command=TownyCommandAddonAPI.getAddonCommand(TownyCommandAddonAPI.CommandType.TOWN,"citizens");
        command.getCommandExecutor().onCommand(player,command,"t",new String[0]);
        check(top.getSize()==45&&top.getItem(4)!=null,"citizenship menu opens through Towny command");
        var govPlugin=plugin("NeverLandTownyGovernance");var governance=(GovernanceService)field(govPlugin,"governance");
        govPlugin.getConfig().set("voting.electorate","ALL_RESIDENTS");
        check(!governance.electorate(town).contains(ACTOR),"Governance excludes temporary voter");
        String office=governance.offices().iterator().next().id();
        check(governance.appoint(player,office,"CitizensExpiry")==GovernanceService.Result.NO_PERMISSION,"temporary manager cannot appoint officials");
        check(governance.dismiss(player,office,"CitizensExpiry")==GovernanceService.Result.NO_PERMISSION,"temporary manager cannot dismiss officials");
        assign(ACTOR,CitizenshipStatus.HONORARY,0);
        check(governance.electorate(town).contains(ACTOR),"Governance includes honorary resident");
        check(CitizensBridge.citizenship(passport).equals("Почётный гражданин"),"passport follows a status change without reissue");
        assign(GUEST,CitizenshipStatus.HONORARY,0);
        check(!citizens.allows(TOWN,GUEST,"VOTE")&&!citizens.allows(TOWN,GUEST,"STORAGE"),"honorary guest receives no membership-only rights");
        taxChecks();
        var storage=((ru.neverland.townybuilds.NeverLandTownyBuilds)plugin("NeverLandTownyBuilds")).storage();
        assign(ACTOR,CitizenshipStatus.CITIZEN,0);storage.openStorage(player,"warehouse");
        check(top.getHolder()!=null&&top.getHolder().getClass().getName().contains("BuildingStorageService"),"citizen opens municipal warehouse");
        assign(ACTOR,CitizenshipStatus.FOREIGNER,0);
        var click=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(click);check(click.isCancelled(),"open warehouse rechecks changed citizenship on click");
        var block=world.getBlockAt(1940,91,1940);block.setType(Material.STONE,false);
        var event=new BlockBreakEvent(block,player);new CitizensListener(citizens).destroy(event);
        check(event.isCancelled(),"foreigner cannot destroy town blocks");
        event.setCancelled(true);new CitizensListener(citizens).destroy(event);check(event.isCancelled(),"citizenship never uncancels protection");
        check(!governance.electorate(town).contains(ACTOR),"Governance recalculates electorate after demotion");
        assign(ACTOR,CitizenshipStatus.HONORARY,0);
        long now=System.currentTimeMillis();citizens.repository().put(new CitizenshipRecord(TOWN,TEMP,CitizenshipStatus.TEMPORARY,now+1000,now,"probe","Offline expiry"));
        check(citizens.repository().get(TOWN,TEMP).expiresAt()>0,"temporary expiry written before shutdown");
    }
    private void taxChecks()throws Exception {
        FiscalService fiscal=null;for(Class<?> contract:Bukkit.getServicesManager().getKnownServices()) {
            Object provider=Bukkit.getServicesManager().load(contract);if(provider instanceof FiscalService f)fiscal=f;
        }
        if(fiscal==null)throw new AssertionError("FiscalService missing");
        var r=TownyAPI.getInstance().getResident(ACTOR);r.getAccount().deposit(1000,"Disposable tax fixture");
        var subject=new ru.neverland.townytaxes.integration.TownyHook.Party(Domain.Scope.PLAYER,ACTOR,r.getName(),r.getAccount());
        var destination=new ru.neverland.townytaxes.integration.TownyHook.Party(Domain.Scope.TOWN,TOWN,town.getName(),town.getAccount());
        var policy=fiscal.createPolicy(destination,Domain.TaxType.FIXED,20,destination,86_400_000,"probe");
        var charge=FiscalService.class.getDeclaredMethod("charge",subject.getClass(),TaxPolicy.class,double.class);charge.setAccessible(true);
        double before=r.getAccount().getHoldingBalance();charge.invoke(fiscal,subject,policy,20D);
        check(Math.abs(before-r.getAccount().getHoldingBalance()-10)<.001,"honorary municipal tax is half and charged once");
        assign(ACTOR,CitizenshipStatus.TEMPORARY,30);before=r.getAccount().getHoldingBalance();charge.invoke(fiscal,subject,policy,20D);
        check(Math.abs(before-r.getAccount().getHoldingBalance()-25)<.001,"temporary municipal tax multiplier is applied");
        fiscal.removePolicy(policy.id().toString());
    }
}
