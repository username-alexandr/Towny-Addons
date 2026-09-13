package ru.neverland.runtime;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.townyelections.*;
import ru.neverland.townyelections.api.TownyElectionsApi;
import ru.neverland.townycitizens.*;
import ru.neverland.townycitizens.model.*;
import ru.neverland.governance.api.TownyGovernanceApi;
import ru.neverland.governance.service.GovernanceService;
import ru.neverland.townyideologies.NeverLandTownyIdeologies;
import ru.neverland.townyideologies.api.TownyIdeologiesApi;

/** Opt-in disposable-server fixture. No client session; Bukkit menus use a Player/view proxy. */
public final class ElectionsRuntimeProbe extends JavaPlugin {
    static UUID id(String s){return UUID.nameUUIDFromBytes(("elections-"+s).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static final UUID TOWN=id("town"),M=id("mayor"),A=id("candidate"),B=id("council1"),C=id("council2"),D=id("treasurer"),V=id("voter"),T=id("temporary"),G=id("guest");
    private Path proof;private int checks;private Town town;private World world;private ElectionsService elections;private ElectionsRepository repository;
    private CitizensService citizens;private TownyGovernanceApi gov;private GovernanceService governance;private NeverLandTownyElections owner;
    private JavaPlugin plugin(String n){return (JavaPlugin)Bukkit.getPluginManager().getPlugin(n);}
    static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static void set(Object object,String name,Object value)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    void check(boolean good,String label)throws Exception{if(!good)throw new AssertionError(label);checks++;getLogger().info("CHECK "+label);Files.writeString(proof.resolve("checks.txt"),label+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    interface Work{void run()throws Exception;}
    void deny(Work work,String label)throws Exception{try{work.run();}catch(Exception expected){check(true,label);return;}throw new AssertionError(label);}
    @Override public void onEnable(){
        if(!Boolean.getBoolean("neverland.runtimeProbe")||!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RELIABILITY_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){getServer().getPluginManager().disablePlugin(this);return;}
        Bukkit.getScheduler().runTaskLater(this,this::run,100);
    }
    private void run(){try{
        proof=getDataFolder().toPath();Files.createDirectories(proof);world=Bukkit.getWorlds().getFirst();
        check(Arrays.stream(Bukkit.getPluginManager().getPlugins()).filter(p->p.getName().startsWith("NeverLandTowny")&&p.isEnabled()).count()==29,"all 29 addons enabled");
        check(plugin("NeverLandPassport").isEnabled(),"Passport remains enabled");
        owner=(NeverLandTownyElections)plugin("NeverLandTownyElections");elections=owner.elections();repository=(ElectionsRepository)field(elections,"repository");
        citizens=((NeverLandTownyCitizens)plugin("NeverLandTownyCitizens")).citizens();gov=Bukkit.getServicesManager().load(TownyGovernanceApi.class);
        governance=(GovernanceService)field(plugin("NeverLandTownyGovernance"),"governance");
        check(Bukkit.getServicesManager().load(TownyElectionsApi.class)==elections,"Elections public API registered");
        check(Bukkit.getServicesManager().load(TownyIdeologiesApi.class)!=null,"Ideologies public API registered");
        if(Files.exists(proof.resolve("first-passed.txt")))restart();else{setup();scenarios();}
        TownyUniverse.getInstance().getDataSource().saveAll();world.save();
        Files.writeString(proof.resolve(Files.exists(proof.resolve("first-passed.txt"))?"restart-passed.txt":"first-passed.txt"),"PASS "+checks+" assertions\n");
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,20);
    }catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"ELECTIONS_FAIL",error);try{var w=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(w));Files.writeString(proof.resolve("failed.txt"),w.toString());}catch(Exception ignored){}Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,20);}}
    private void setup()throws Exception{
        var u=TownyUniverse.getInstance();if(TownyAPI.getInstance().getTown(TOWN)==null)u.newTownInternal("ElectionsTown",TOWN);town=TownyAPI.getInstance().getTown(TOWN);
        for(var entry:Map.of(M,"ElectionMayor",A,"ElectionCandidate",B,"ElectionCouncilB",C,"ElectionCouncilC",D,"ElectionTreasurer",V,"ElectionVoter",T,"ElectionTemporary",G,"ElectionGuest").entrySet()){
            Resident r=TownyAPI.getInstance().getResident(entry.getKey());if(r==null)r=u.getDataSource().newResident(entry.getValue(),entry.getKey());if(!entry.getKey().equals(G)&&!town.equals(r.getTownOrNull()))r.setTown(town);r.save();
        }
        town.setMayor(TownyAPI.getInstance().getResident(M));
        if(town.getTownBlocks().isEmpty()){var claim=new TownBlock(122,122,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);town.setHomeBlock(claim);claim.save();}
        world.getChunkAt(122,122).load();town.setSpawn(new Location(world,1956,92,1956));town.save();
    }
    private void status(UUID id,CitizenshipStatus s,int days)throws Exception{citizens.assign(Bukkit.getConsoleSender(),town,id,s,days,"Disposable election fixture");}
    private UUID campaignId()throws Exception{return (UUID)field(elections.state(town),"id");}
    private void voting()throws Exception{var e=elections.state(town);set(e,"start",System.currentTimeMillis()-2000);set(e,"nominationEnd",System.currentTimeMillis()-1000);repository.put(e);elections.advance(town,System.currentTimeMillis());}
    private void close()throws Exception{var e=elections.state(town);set(e,"votingEnd",System.currentTimeMillis()-1);repository.put(e);elections.advance(town,System.currentTimeMillis());}
    @SuppressWarnings("unchecked") private void scenarios()throws Exception{
        check(elections.form(town).equals("DEMOCRACY"),"default economic ideology permits elections");
        check(ElectionIntegrations.catalog().get("councillor")==5,"Governance publishes office capacity");
        var ideologies=(NeverLandTownyIdeologies)plugin("NeverLandTownyIdeologies");
        elections.start(town,System.currentTimeMillis());
        ideologies.getConfig().set("government.ideology-forms.agriculture","MONARCHY");ideologies.ideologyService().adminSet(town,"agriculture",1);
        elections.advance(town,System.currentTimeMillis());
        check(field(elections.state(town),"phase")==Election.Phase.CANCELLED,"monarchy cancels open campaign durably");
        deny(()->elections.start(town,System.currentTimeMillis()),"monarchy blocks manual election start");
        check(town.getMayor().getUUID().equals(M),"monarchy preserves ruler");
        ideologies.getConfig().set("government.ideology-forms.agriculture","TYPO");deny(()->elections.form(town),"unknown government form blocks election API");
        ideologies.getConfig().set("government.ideology-forms.agriculture","DEMOCRACY");
        status(T,CitizenshipStatus.TEMPORARY,30);status(A,CitizenshipStatus.HONORARY,0);
        gov.applyElection(TOWN,id("old-offices"),Map.of("clerk",List.of(M)));
        elections.start(town,System.currentTimeMillis());deny(()->elections.start(town,System.currentTimeMillis()),"overlapping campaigns rejected");
        Actor a=new Actor(A),v=new Actor(V),t=new Actor(T),guest=new Actor(G);
        deny(()->elections.nominate(t.player,"mayor"),"temporary resident cannot stand for office");
        elections.nominate(a.player,"mayor");elections.nominate(new Actor(B).player,"councillor");elections.nominate(new Actor(C).player,"councillor");elections.nominate(new Actor(D).player,"treasurer");
        check(((Map<?,?>)field(elections.state(town),"candidates")).containsKey(A),"honorary member can stand for mayor");
        deny(()->elections.nominate(a.player,"clerk"),"one candidacy per resident");
        var command=TownyCommandAddonAPI.getAddonCommand(TownyCommandAddonAPI.CommandType.TOWN,"elections");
        check(command!=null,"Towny elections subcommand registered");command.getCommandExecutor().onCommand(v.player,command,"t",new String[0]);
        check(v.top.getSize()==54&&v.top.getItem(4)!=null,"readable election menu opens");
        var click=new InventoryClickEvent(v.view,InventoryType.SlotType.CONTAINER,0,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY);
        Bukkit.getPluginManager().callEvent(click);check(click.isCancelled(),"election menu blocks item extraction");
        command.getCommandExecutor().onCommand(v.player,command,"t",new String[]{"start","ElectionsTown"});
        check(v.messages.stream().anyMatch(m->m.contains("администратору")),"ordinary voter cannot administer elections");
        voting();check(field(elections.state(town),"phase")==Election.Phase.VOTING,"scheduler opens voting after nomination");
        check((Long)field(elections.state(town),"votingEnd")>System.currentTimeMillis()+3_500_000,"downtime grants a full voting window");
        deny(()->elections.vote(t.player,"mayor",List.of(A)),"temporary resident cannot vote");
        deny(()->elections.vote(guest.player,"mayor",List.of(A)),"guest cannot vote in another town");
        for(var actor:List.of(a,v)){
            elections.vote(actor.player,"mayor",List.of(A));elections.vote(actor.player,"councillor",List.of(B,C));elections.vote(actor.player,"treasurer",List.of(D));
        }
        elections.vote(v.player,"mayor",List.of(A));check(((Map<UUID,List<UUID>>)((Map<?,?>)field(elections.state(town),"ballots")).get("mayor")).size()==2,"repeated vote never duplicates turnout");
        var selected=new ElectionsMenus(owner,elections);selected.open(v.player,"councillor",0,null,null);
        check(v.top.getItem(49).getItemMeta().getLore().stream().anyMatch(s->s.contains("2 / 3")),"multi-seat menu restores saved ballot");
        status(D,CitizenshipStatus.FOREIGNER,0);close();
        check(field(elections.state(town),"phase")==Election.Phase.COMPLETE,"results completed durably");
        check(town.getMayor().getUUID().equals(A),"election changes actual Towny mayor");
        check(new HashSet<>(gov.officeHolders(TOWN).get("councillor")).equals(Set.of(B,C)),"election installs both council members");
        check(gov.officeHolders(TOWN).getOrDefault("treasurer",List.of()).isEmpty(),"revoked citizenship disqualifies elected office candidate");
        check(gov.officeHolders(TOWN).get("clerk").equals(List.of(M)),"no quorum preserves existing office");
        check(governance.council(town).equals(Set.of(A,B,C)),"Governance council uses elected seats and mayor");
        check(governance.dismiss(a.player,"councillor","ElectionCouncilB")==GovernanceService.Result.CHANGE_DISABLED,"mayor cannot remove elected councillor manually");
        UUID completed=campaignId();gov.applyElection(TOWN,completed,Map.of("councillor",List.of(B,C)));
        check(gov.electionReceipt(TOWN).equals(completed.toString())&&gov.officeHolders(TOWN).get("councillor").size()==2,"replaying same election result is idempotent");
        long next=(Long)field(elections.state(town),"next"),start=(Long)field(elections.state(town),"start");
        check(next>start+13*86_400_000L&&next<start+15*86_400_000L,"next campaign keeps configured N-day cadence");
        // Persist an applying checkpoint with effects already applied. Startup must require review, not silently repeat them.
        var checkpoint=elections.state(town);set(checkpoint,"phase",Election.Phase.APPLYING);repository.put(checkpoint);
        Files.writeString(proof.resolve("election-id.txt"),completed.toString());
        check(!elections.snapshot(TOWN).containsKey("ballots"),"public API never exposes individual ballots");
    }
    private void restart()throws Exception{
        town=TownyAPI.getInstance().getTown(TOWN);check(town!=null,"Towny town persists across restart");
        check(town.getMayor().getUUID().equals(A),"elected Towny mayor persists across restart");
        check(new HashSet<>(gov.officeHolders(TOWN).get("councillor")).equals(Set.of(B,C)),"elected council persists across restart");
        check(field(elections.state(town),"phase")==Election.Phase.REVIEW,"interrupted application requires review on startup");
        UUID election=UUID.fromString(Files.readString(proof.resolve("election-id.txt")));
        deny(()->elections.resume(town,UUID.randomUUID()),"recovery requires exact campaign identity");
        elections.resume(town,election);check(field(elections.state(town),"phase")==Election.Phase.COMPLETE,"reviewed application resumes idempotently");
        check(gov.electionReceipt(TOWN).equals(election.toString()),"Governance result receipt survives restart");
        check(governance.council(town).equals(Set.of(A,B,C)),"elected council retains legislative powers");
        check(((Map<?,?>)field(elections.state(town),"ballots")).size()==3,"saved ballots survive restart");
        check(!((List<?>)field(elections.state(town),"history")).isEmpty(),"campaign audit history survives restart");
    }
    private final class Actor{
        final Player player;Inventory top=Bukkit.createInventory(null,9,Component.text("Fixture"));final Inventory bottom=Bukkit.createInventory(null,36);
        InventoryView view;final List<String> messages=new ArrayList<>();
        Actor(UUID id){
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,m,a)->switch(m.getName()){
                case "getUniqueId"->id;case "getName"->TownyAPI.getInstance().getResident(id).getName();case "getServer"->Bukkit.getServer();case "getWorld"->world;case "getLocation"->world.getSpawnLocation();
                case "isOnline"->true;case "isOp"->false;case "hasPermission"->!String.valueOf(a[0]).endsWith("admin")&&!String.valueOf(a[0]).endsWith("bypass");
                case "sendMessage"->{messages.add(String.valueOf(a[a.length-1]));yield null;}
                case "getOpenInventory"->view;case "openInventory"->{top=(Inventory)a[0];yield view;}case "closeInventory"->{top=Bukkit.createInventory(null,9);yield null;}
                case "hashCode"->id.hashCode();case "equals"->proxy==a[0];case "toString"->"Election actor "+id;default->null;
            });
            view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(proxy,m,a)->switch(m.getName()){
                case "getTopInventory"->top;case "getBottomInventory"->bottom;case "getPlayer"->player;case "getType"->InventoryType.CHEST;
                case "getCursor"->new ItemStack(Material.AIR);case "countSlots"->top.getSize()+36;case "getTitle","getOriginalTitle"->"Fixture";case "title"->Component.text("Fixture");
                case "getSlotType"->InventoryType.SlotType.CONTAINER;case "convertSlot"->a[0];case "getInventory"->(Integer)a[0]<top.getSize()?top:bottom;default->null;
            });
        }
    }
}
