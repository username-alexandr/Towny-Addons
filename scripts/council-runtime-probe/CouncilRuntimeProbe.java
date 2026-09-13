package ru.neverland.runtime;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.*;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.townycouncil.*;
import ru.neverland.townycouncil.api.TownyCouncilApi;
import ru.neverland.core.CouncilAccess;
import ru.neverland.townycitizens.*;
import ru.neverland.townycitizens.model.*;
import ru.neverland.governance.api.TownyGovernanceApi;
import ru.neverland.governance.service.GovernanceService;
import ru.neverland.governance.model.LawCategory;
import ru.neverland.townytreasury.service.*;

/** Actual Towny services and Bukkit permissions; no network clients or production data. */
public final class CouncilRuntimeProbe extends JavaPlugin {
    static UUID id(String s) { return UUID.nameUUIDFromBytes(("council-026-" + s).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    static final UUID TOWN=id("town"), OTHER=id("other"), M=id("mayor"), N=id("next"), E=id("economy"), D=id("defense"), C=id("construction"), F=id("foreign"), TEMP=id("temporary"), G=id("guest");
    private Path proof; private int checks; private Town town, other; private World world; private CouncilService council; private CitizensService citizens;
    private NeverLandTownyCouncil owner; private TownyGovernanceApi gov; private GovernanceService governance;
    private JavaPlugin plugin(String name) { return (JavaPlugin) Bukkit.getPluginManager().getPlugin(name); }
    static Object field(Object o,String n)throws Exception { var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o); }
    void check(boolean value,String label)throws Exception { if(!value)throw new AssertionError(label);checks++;getLogger().info("CHECK "+label);Files.writeString(proof.resolve("checks.txt"),label+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND); }
    interface Work { void run()throws Exception; }
    void deny(Work work,String label)throws Exception { try{work.run();}catch(IllegalArgumentException expected){check(true,label);return;}throw new AssertionError(label); }
    @Override public void onEnable() {
        if(!Boolean.getBoolean("neverland.runtimeProbe") || !Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RELIABILITY_PROBE")) || !"127.0.0.1".equals(getServer().getIp())) { getServer().getPluginManager().disablePlugin(this);return; }
        Bukkit.getScheduler().runTaskLater(this,this::run,40);
    }
    private void run() { try {
        proof=getDataFolder().toPath();Files.createDirectories(proof);world=Bukkit.getWorlds().getFirst();
        check(Arrays.stream(Bukkit.getPluginManager().getPlugins()).filter(p->p.getName().startsWith("NeverLandTowny")&&p.isEnabled()).count()==30,"all 30 addons enabled");
        owner=(NeverLandTownyCouncil)plugin("NeverLandTownyCouncil");council=owner.council();citizens=((NeverLandTownyCitizens)plugin("NeverLandTownyCitizens")).citizens();
        gov=Bukkit.getServicesManager().load(TownyGovernanceApi.class);governance=(GovernanceService)field(plugin("NeverLandTownyGovernance"),"governance");
        check(Bukkit.getServicesManager().load(TownyCouncilApi.class)==council,"Council versioned API registered");
        boolean restart=Files.exists(proof.resolve("first-passed.txt"));
        if(restart)restart();else{setup();scenarios();}
        TownyUniverse.getInstance().getDataSource().saveAll();world.save();
        Files.writeString(proof.resolve(restart?"restart-passed.txt":"first-passed.txt"),"PASS "+checks+" assertions\n");
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);
    } catch(Throwable error) {
        getLogger().log(java.util.logging.Level.SEVERE,"COUNCIL_FAIL",error);
        try{var out=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(out));Files.writeString(proof.resolve("failed.txt"),out.toString());}catch(Exception ignored){}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,1);
    } }
    private void setup()throws Exception {
        var u=TownyUniverse.getInstance();
        if(TownyAPI.getInstance().getTown(TOWN)!=null)throw new IllegalStateException("Council fixture already exists; use a fresh disposable snapshot");
        u.newTownInternal("CouncilTown",TOWN);u.newTownInternal("CouncilOther",OTHER);town=TownyAPI.getInstance().getTown(TOWN);other=TownyAPI.getInstance().getTown(OTHER);
        for(var entry:Map.of(M,"CouncilMayor",N,"CouncilNext",E,"CouncilEconomy",D,"CouncilDefense",C,"CouncilBuilder",F,"CouncilForeign",TEMP,"CouncilTemporary",G,"CouncilGuest").entrySet()) {
            var r=u.getDataSource().newResident(entry.getValue(),entry.getKey());r.setTown(entry.getKey().equals(G)?other:town);r.save();
        }
        town.setMayor(TownyAPI.getInstance().getResident(M));other.setMayor(TownyAPI.getInstance().getResident(G));
        int coordinate=124;
        for(var t:List.of(town,other)) {
            var claim=new TownBlock(coordinate,coordinate,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(t);t.setHomeBlock(claim);claim.save();
            world.getChunkAt(coordinate,coordinate).load();t.setSpawn(new Location(world,coordinate*16+4,92,coordinate*16+4));t.save();coordinate++;
        }
    }
    private void status(UUID resident,CitizenshipStatus status,int days)throws Exception { citizens.assign(Bukkit.getConsoleSender(),town,resident,status,days,"Disposable Council fixture"); }
    private Actor appoint(Actor mayor,UUID resident,MinisterRole role)throws Exception { council.appoint(mayor.player,town,role,resident);var actor=new Actor(resident);council.refresh(actor.player);return actor; }
    private void scenarios()throws Exception {
        var mayor=new Actor(M);var outsider=new Actor(G);var ordinary=new Actor(N);
        deny(()->council.appoint(ordinary.player,town,MinisterRole.ECONOMY,E),"ordinary resident cannot appoint ministers");
        deny(()->council.appoint(outsider.player,town,MinisterRole.ECONOMY,E),"another town mayor cannot appoint ministers here");
        deny(()->council.appoint(mayor.player,town,MinisterRole.ECONOMY,G),"nonmember cannot be appointed");
        deny(()->council.appoint(mayor.player,town,MinisterRole.ECONOMY,M),"mayor cannot occupy a ministry");
        status(TEMP,CitizenshipStatus.TEMPORARY,30);
        deny(()->council.appoint(mayor.player,town,MinisterRole.ECONOMY,TEMP),"temporary resident lacks HOLD_OFFICE");
        status(E,CitizenshipStatus.HONORARY,0);
        var econ=appoint(mayor,E,MinisterRole.ECONOMY);var defense=appoint(mayor,D,MinisterRole.DEFENSE);var construction=appoint(mayor,C,MinisterRole.CONSTRUCTION);var foreign=appoint(mayor,F,MinisterRole.FOREIGN);
        check(council.holders(TOWN).size()==4,"four appointments committed");
        for(var entry:Map.of(MinisterRole.ECONOMY,econ,MinisterRole.DEFENSE,defense,MinisterRole.CONSTRUCTION,construction,MinisterRole.FOREIGN,foreign).entrySet())
            check(entry.getValue().player.hasPermission(entry.getKey().permission()),"Bukkit permission granted for "+entry.getKey().id());
        check(!econ.player.hasPermission("neverlandtownycouncil.action.army")&&!defense.player.hasPermission("neverlandtownycouncil.action.budget"),"ministries have separate effective permissions");
        deny(()->council.appoint(mayor.player,town,MinisterRole.ECONOMY,N),"occupied ministry cannot be silently replaced");
        council.dismiss(mayor.player,town,MinisterRole.FOREIGN);
        deny(()->council.appoint(mayor.player,town,MinisterRole.FOREIGN,E),"one ministry per resident");
        council.appoint(mayor.player,town,MinisterRole.FOREIGN,F);council.refresh(foreign.player);
        check(CouncilAccess.allows(econ.player,TOWN,"budget")&&!CouncilAccess.allows(econ.player,OTHER,"budget"),"budget permission is restricted to appointed town");
        var treasury=(TreasuryService)field(plugin("NeverLandTownyTreasuryPlus"),"service");
        check(treasury.manager(econ.player,town)&&!treasury.manager(foreign.player,town)&&!treasury.manager(econ.player,other),"Treasury manager accepts only economy minister in own town");
        check(TreasuryAccess.canExport(econ.player)&&!TreasuryAccess.canExport(ordinary.player),"CSV visible and allowed only with export authority");
        var taxes=new ru.neverland.townytaxes.integration.TownyHook(plugin("NeverLandTownyTaxes"));
        check(taxes.isTownManager(econ.player,town,"tax")&&!taxes.isTownManager(foreign.player,town,"tax"),"Taxes distinguishes tax ministry");
        check(taxes.isTownManager(foreign.player,town,"agreements")&&taxes.isTownManager(foreign.player,town,"sanctions")&&!taxes.isTownManager(econ.player,town,"agreements"),"foreign policy has agreements and sanctions only");
        var trade=new ru.neverland.minttrade.integration.TownyHook(plugin("NeverLandTownyTrade"));
        check(trade.isManager(foreign.player,town)&&!trade.isManager(econ.player,town)&&!trade.isManager(foreign.player,other),"Trade uses live foreign ministry authority");
        var espionage=new ru.neverland.mintespionage.integration.TownyHook(plugin("NeverLandTownyEspionage"));
        check(espionage.isManager(defense.player,town)&&!espionage.isManager(foreign.player,town),"Espionage uses defense ministry authority");
        var army=((ru.neverland.townybuilds.NeverLandTownyBuilds)plugin("NeverLandTownyBuilds")).army();
        var manages=army.getClass().getDeclaredMethod("manager",Player.class,Town.class);manages.setAccessible(true);
        check((Boolean)manages.invoke(army,defense.player,town)&&!(Boolean)manages.invoke(army,econ.player,town),"Army manager accepts defense minister");
        var buildsOwner=plugin("NeverLandTownyBuilds");
        var builds=new ru.neverland.townybuilds.service.BuildService(buildsOwner,new ru.neverland.townybuilds.integration.TownyHook(),
                (ru.neverland.townybuilds.data.DataStore)field(buildsOwner,"dataStore"),(ru.neverland.townybuilds.service.MessageService)field(buildsOwner,"messages"),
                new ru.neverland.townybuilds.integration.ArchaeologyBridge(buildsOwner),(ru.neverland.townybuilds.service.RussianItemNames)field(buildsOwner,"itemNames"),
                (ru.neverland.townybuilds.service.DefinitionRegistry)field(buildsOwner,"definitions"));
        var definitions=(ru.neverland.townybuilds.service.DefinitionRegistry)field(buildsOwner,"definitions");
        var project=definitions.project("warehouse");
        check(project!=null,"building catalog fixture exists");
        check(builds.contributeFromStorage(construction.player,project).status()!=ru.neverland.townybuilds.service.ContributionResult.Status.NOT_MAYOR
                && builds.contributeFromStorage(econ.player,project).status()==ru.neverland.townybuilds.service.ContributionResult.Status.NOT_MAYOR,"Builds delegates project warehouse resources to construction minister");
        check(governance.mayPropose(econ.player,town,LawCategory.TAX)&&!governance.mayPropose(econ.player,town,LawCategory.CONSTRUCTION)
                &&governance.mayPropose(construction.player,town,LawCategory.CONSTRUCTION),"Governance proposal rights respect ministry category");
        gov.applyElection(TOWN,id("elected-council"),Map.of("councillor",List.of(C)));
        check(governance.council(town).equals(Set.of(M,C)),"appointed ministers do not replace elected council electorate");
        check(governance.dismiss(mayor.player,"councillor","CouncilBuilder")==GovernanceService.Result.CHANGE_DISABLED,"elected councillor remains protected from manual dismissal");
        var command=TownyCommandAddonAPI.getAddonCommand(TownyCommandAddonAPI.CommandType.TOWN,"ministers");
        check(command!=null&&TownyCommandAddonAPI.getAddonCommand(TownyCommandAddonAPI.CommandType.TOWN,"council")!=null,"ministers command coexists with Governance council");
        command.getCommandExecutor().onCommand(ordinary.player,command,"t",new String[0]);
        check(ordinary.top.getSize()==45&&ordinary.top.getItem(19).getType()!=ordinary.top.getItem(21).getType(),"Council menu opens with distinct ministry icons");
        var click=new InventoryClickEvent(ordinary.view,InventoryType.SlotType.CONTAINER,19,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY);
        Bukkit.getPluginManager().callEvent(click);check(click.isCancelled(),"Council menu blocks item extraction");
        command.getCommandExecutor().onCommand(ordinary.player,command,"t",new String[]{"dismiss","economy"});
        check(council.holders(TOWN).get("economy").equals(E),"command cannot bypass mayor authority");
        var external=econ.player.addAttachment(this);external.setPermission("external.keep",true);
        council.dismiss(mayor.player,town,MinisterRole.ECONOMY);
        check(!econ.player.hasPermission("neverlandtownycouncil.action.budget")&&econ.player.hasPermission("external.keep"),"dismissal removes only Council permission attachment");
        external.setPermission("neverlandtownycouncil.action.budget",true);
        check(!CouncilAccess.allows(econ.player,TOWN,"budget"),"external action permission alone cannot forge an appointment");
        external.unsetPermission("neverlandtownycouncil.action.budget");
        council.appoint(mayor.player,town,MinisterRole.ECONOMY,E);council.refresh(econ.player);
        status(E,CitizenshipStatus.FOREIGNER,0);
        check(!CouncilAccess.allows(econ.player,TOWN,"budget")&&!TreasuryAccess.canExport(econ.player),"citizenship revocation blocks actions before attachment refresh");
        council.refresh(econ.player);check(!econ.player.hasPermission("neverlandtownycouncil.action.budget"),"citizenship refresh revokes Bukkit grant");
        status(E,CitizenshipStatus.HONORARY,0);council.refresh(econ.player);
        check(CouncilAccess.allows(econ.player,TOWN,"budget"),"eligible honorary resident regains suspended duties");
        var config=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(owner.getDataFolder().toPath().resolve("config.yml").toFile());
        var original=CouncilSettings.load(config);config.set("roles.economy.permissions",List.of("external.statement"));council.settings(CouncilSettings.load(config));
        check(econ.player.hasPermission("external.statement")&&!CouncilAccess.allows(econ.player,TOWN,"budget"),"configuration reload replaces rights of current ministers");
        council.settings(original);check(!econ.player.hasPermission("external.statement")&&econ.player.hasPermission("external.keep"),"reload removes obsolete rights and preserves external grants");
        council.release(E);check(!econ.player.hasPermission("neverlandtownycouncil.action.budget"),"quit releases attachment");
        council.refresh(econ.player);check(econ.player.hasPermission("neverlandtownycouncil.action.budget"),"join restores committed appointment rights");
        var dr=TownyAPI.getInstance().getResident(D);dr.setTown(null);Bukkit.getPluginManager().callEvent(new com.palmergames.bukkit.towny.event.TownRemoveResidentEvent(dr,town));
        check(council.appointment(TOWN,MinisterRole.DEFENSE)==null&&!defense.player.hasPermission("neverlandtownycouncil.action.army"),"leaving town revokes appointment and permissions");
        dr.setTown(town);dr.save();check(council.role(TOWN,D).isEmpty(),"rejoining town does not resurrect appointment");
        town.setMayor(TownyAPI.getInstance().getResident(N));town.save();
        check(council.holders(TOWN).isEmpty()&&!econ.player.hasPermission("neverlandtownycouncil.action.budget"),"mayor change clears cabinet and grants immediately");
        town.setMayor(TownyAPI.getInstance().getResident(M));town.save();
        check(council.repository().all().isEmpty(),"returning old mayor does not resurrect old ministers");
        for(var entry:Map.of(MinisterRole.ECONOMY,E,MinisterRole.DEFENSE,D,MinisterRole.CONSTRUCTION,C,MinisterRole.FOREIGN,F).entrySet())council.appoint(mayor.player,town,entry.getKey(),entry.getValue());
        check(council.holders(TOWN).size()==4&&!council.repository().history().isEmpty(),"new appointments and audit persisted for restart");
    }
    private void restart()throws Exception {
        town=TownyAPI.getInstance().getTown(TOWN);other=TownyAPI.getInstance().getTown(OTHER);
        check(town!=null&&town.getMayor().getUUID().equals(M),"Towny town and mayor survived restart");
        check(council.holders(TOWN).equals(Map.of("economy",E,"defense",D,"construction",C,"foreign",F)),"all appointments survived restart");
        var econ=new Actor(E);council.refresh(econ.player);
        check(econ.player.hasPermission("neverlandtownycouncil.action.budget")&&CouncilAccess.allows(econ.player,TOWN,"budget"),"Bukkit and live action grants restored after restart");
        check(!CouncilAccess.allows(econ.player,OTHER,"budget"),"town isolation survives restart");
        check(council.repository().history().stream().anyMatch(a->a.detail().startsWith("MAYOR_CHANGED")),"appointment audit survives restart");
        check(gov.officeHolders(TOWN).get("councillor").equals(List.of(C)),"elected council remains separate after restart");
        var file=owner.getDataFolder().toPath().resolve("council.yml");byte[] backup=Files.readAllBytes(file);Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("obstruction"),"keep");
        try { council.dismiss(new Actor(M).player,town,MinisterRole.ECONOMY);throw new AssertionError("disk error ignored"); } catch(java.io.IOException expected) { }
        check(!council.healthy()&&!econ.player.hasPermission("neverlandtownycouncil.action.budget")&&!CouncilAccess.allows(econ.player,TOWN,"budget"),"save failure locks API and revokes all permission grants");
        check(council.appointment(TOWN,MinisterRole.ECONOMY).resident().equals(E),"failed dismissal retains previous committed in-memory record");
        Files.delete(file.resolve("obstruction"));Files.delete(file);Files.write(file,backup);
        check(!council.healthy(),"file repair alone does not silently unlock registry");
        council.repository().load();council.refresh(econ.player);
        var external=econ.player.addAttachment(this);external.setPermission("external.keep",true);
        Bukkit.getPluginManager().disablePlugin(owner);
        check(!econ.player.hasPermission("neverlandtownycouncil.action.budget")&&econ.player.hasPermission("external.keep"),"plugin disable removes only Council grants");
        check(!CouncilAccess.allows(econ.player,TOWN,"budget")&&Bukkit.getServicesManager().load(TownyCouncilApi.class)==null,"disabled provider grants no authority and unregisters API");
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
                case "hashCode"->id.hashCode();case "equals"->proxy==a[0];case "toString"->"Council actor "+id;default->null;
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
