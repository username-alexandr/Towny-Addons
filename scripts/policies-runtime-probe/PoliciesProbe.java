package ru.neverland.runtime;
import java.nio.file.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import io.papermc.paper.threadedregions.scheduler.*;
import ru.neverland.core.*;
import ru.neverland.townypolicies.api.*;
import ru.neverland.townypolicies.service.*;
import ru.neverland.townypolicies.gui.PoliciesMenu;

/** Real commands, inventory cards, disk acknowledgement and Control lifecycle. No network client. */
public final class PoliciesProbe extends TntRuntimeProbe {
    JavaPlugin owner;PoliciesService service;Player mayor,member;Inventory top;boolean admin;
    final List<String> messages=new ArrayList<>();
    @SuppressWarnings("unchecked") Player actor(UUID id,String name){
        var scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class<?>[]{EntityScheduler.class},(p,m,a)->switch(m.getName()){
            case "runDelayed"->Bukkit.getRegionScheduler().runDelayed(this,origin,(Consumer<ScheduledTask>)a[1],(Long)a[3]);
            case "run"->Bukkit.getRegionScheduler().run(this,origin,(Consumer<ScheduledTask>)a[1]);default->throw new UnsupportedOperationException(m.getName());});
        return(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "getName"->name;case "getServer"->Bukkit.getServer();case "getWorld"->world;case "getLocation"->origin;case "isOnline"->true;case "isOp"->false;case "getScheduler"->scheduler;
            case "hasPermission"->a[0].equals("neverlandtownypolicies.use")||(admin&&id.equals(MAYOR)&&a[0].equals("neverlandtownypolicies.admin"));
            case "sendMessage"->{if(a[0] instanceof String s)messages.add(s);yield null;}
            case "openInventory"->{top=(Inventory)a[0];yield null;}
            case "getOpenInventory"->Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(v,n,b)->n.getName().equals("getTopInventory")?top:null);
            case "hashCode"->id.hashCode();case "equals"->p==a[0];default->null;});
    }
    void waitFor(BooleanSupplier ready,Work next,int left)throws Exception{if(ready.getAsBoolean()){next.run();return;}if(left<=0)throw new AssertionError("policy wait timed out");later(1,()->waitFor(ready,next,left-1));}
    void command(Player p,String...args){owner.getCommand("townypolicies").execute(p,"townypolicies",args);}
    void menu(Player p,String group,String mode){new PoliciesMenu(owner,service).open(p,DEF,group,mode);}
    String lore(int slot){return String.join(" ",top.getItem(slot).getItemMeta().getLore());}
    @Override void setup()throws Exception{
        owner=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyPolicies");service=(PoliciesService)Bukkit.getServicesManager().load(TownyPoliciesApi.class);
        check(owner.isEnabled()&&service!=null&&owner.getDescription().getVersion().equals("0.3.0"),"actual Policies 0.3 service and dependency graph enabled");
        if(!restart){var u=TownyUniverse.getInstance();u.newTownInternal("PolicyCity",DEF);var town=TownyAPI.getInstance().getTown(DEF);for(var e:Map.of(MAYOR,"PolicyMayor",ACTOR,"PolicyMember").entrySet()){var r=u.getDataSource().newResident(e.getValue(),e.getKey());r.setTown(town);if(e.getKey().equals(MAYOR))town.setMayor(r);r.save();}var c=new TownBlock(0,0,u.getWorld(world.getName()));u.addTownBlock(c);c.setTown(town);town.setHomeBlock(c);c.save();town.setSpawn(origin);town.save();}
        mayor=actor(MAYOR,"PolicyMayor");member=actor(ACTOR,"PolicyMember");service.refresh();
        if(restart){resume();return;}
        check(service.presetPlans(DEF).size()==3,"three ordered city presets exposed through actual API");
        menu(mayor,null,null);check(top.getItem(30).getType()==Material.COMPASS,"existing main menu exposes comparison button");
        menu(member,"preset/development",null);check(top.getItem(31).getType()==Material.BARRIER,"ordinary resident can compare but cannot confirm");
        command(member,"preset","development");check(service.state(DEF).revision()==0&&!service.saving(),"ordinary command cannot grant policies");
        SuiteState.freeze("NeverLandTownyUpkeep");menu(mayor,"preset/development",null);check(top.getItem(31).getType()==Material.BARRIER&&lore(30).contains("Содержание зданий"),"paused dependency explains whole-set refusal in native menu");
        command(mayor,"preset","development");check(service.state(DEF).revision()==0&&!service.saving(),"unavailable single category prevents the complete mutation");SuiteState.thaw("NeverLandTownyUpkeep");
        menu(mayor,"preset/development",null);check(top.getItem(31).getType()==Material.LIME_DYE&&lore(36).contains("115%")&&lore(36).contains("125%"),"actual comparison shows production and upkeep costs before consent");
        holdWriter();
    }
    @SuppressWarnings("unchecked") void holdWriter()throws Exception{
        var clazz=Class.forName("ru.neverland.core.AtomicFiles",true,owner.getClass().getClassLoader());var field=clazz.getDeclaredField("writers");field.setAccessible(true);var locks=(Map<Path,ReentrantLock>)field.get(null);var path=owner.getDataFolder().toPath().resolve("policies-data.yml").toAbsolutePath().normalize();var lock=locks.computeIfAbsent(path,p->new ReentrantLock());var held=new CountDownLatch(1);gate=new CountDownLatch(1);
        var holder=new Thread(()->{lock.lock();try{held.countDown();if(!gate.await(15,TimeUnit.SECONDS))fail(new AssertionError("writer gate timeout"));}catch(InterruptedException e){Thread.currentThread().interrupt();fail(e);}finally{lock.unlock();}},"native-policy-write-holder");holder.setDaemon(true);holder.start();
        waitFor(()->held.getCount()==0,()->{command(mayor,"preset","development");command(mayor,"confirm");later(3,()->{check(service.saving()&&service.state(DEF).revision()==0,"real blocked file writer leaves confirmed state unchanged while region continues");gate.countDown();waitFor(()->service.state(DEF).revision()==2&&!service.saving(),()->committed(),150);});},100);
    }
    void committed()throws Exception{
        var state=service.state(DEF);check(state.mode(service.settings().groups().get("farmers")).equals("targeted")&&state.mode(service.settings().groups().get("industry")).equals("focused")&&state.history().size()==2,"one acknowledged snapshot applies both changes with their history");
        check(Math.abs(service.productionMultiplier(DEF,"foundry")-1.15)<1e-9&&Math.abs(service.upkeepMultiplier(DEF,"foundry")-1.25)<1e-9,"live policy API matches displayed production and cost");
        boolean stale=false;try{service.presets().choose(mayor,DEF,"recovery",0,service.settings(),service.presets().settings(),false);}catch(IllegalArgumentException expected){stale=true;}check(stale,"stale menu revision cannot replace the committed selection");
        menu(mayor,"preset/recovery",null);check(top.getItem(31).getType()==Material.BARRIER,"whole-set selection cannot bypass ordinary cooldown");
        Files.writeString(proof.resolve("cooldown.txt"),Long.toString(state.next("farmers")));
        var control=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyControl");control.getCommand("nltmodules").execute(Bukkit.getConsoleSender(),"nltmodules",new String[]{"disable","Policies"});
        waitFor(()->!owner.isEnabled(),()->{check(Files.readString(owner.getDataFolder().toPath().resolve("policies-data.yml")).contains("revision: 2"),"controlled unload drains confirmed policy image before classloader closes");control.getCommand("nltmodules").execute(Bukkit.getConsoleSender(),"nltmodules",new String[]{"enable","Policies"});check(!owner.isEnabled(),"Folia enable waits for complete server restart");finish();},150);
    }
    void resume()throws Exception{
        var state=service.state(DEF);check(state.revision()==2&&state.history().size()==2&&state.next("farmers")==Long.parseLong(Files.readString(proof.resolve("cooldown.txt"))),"full restart preserves both policy history and exact cooldown");
        check(Math.abs(service.productionMultiplier(DEF,"foundry")-1.15)<1e-9,"new conflict configuration does not silently erase existing selected policies");
        admin=true;boolean blocked=false;try{service.choose(mayor,DEF,"mobilization","reserve",2,service.settings(),true);}catch(IllegalArgumentException expected){blocked=expected.getMessage().contains("проверки");}check(blocked&&service.state(DEF).revision()==2,"manual admin selection cannot bypass configured whole-state conflict");
        command(mayor,"bundle","PolicyCity","recovery");check(top.getItem(31).getType()==Material.LIME_DYE&&lore(31).contains("Администратор"),"explicit admin recovery explains cooldown exception before confirmation");command(mayor,"confirm");waitFor(()->service.state(DEF).revision()==4&&!service.saving(),()->recovered(),150);
    }
    void recovered()throws Exception{
        var state=service.state(DEF);check(state.mode(service.settings().groups().get("farmers")).equals("none")&&state.mode(service.settings().groups().get("industry")).equals("none")&&state.history().size()==4,"explicit recovery resolves both conflicting categories in one commit");
        check(service.productionMultiplier(DEF,"foundry")==1&&service.upkeepMultiplier(DEF,"foundry")==1,"recovery restores neutral actual effects without inventing money");
        menu(mayor,"history",null);check(top.getItem(0).getItemMeta().getLore().stream().anyMatch(s->s.contains("PolicyMayor")),"native history retains the verified acting mayor");finish();
    }
    @Override void finish()throws Exception{var rows=new ArrayList<AuditRecord>();AuditJournal.scan(owner.getDataFolder().toPath().resolve("audit"),rows::add);check(rows.stream().filter(r->r.kind().equals("POLICY_SELECTION")).count()==(restart?2:1),"selection audit has one unique operation per acknowledged whole-set change");TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; real Folia/Towny policy service, commands, native inventories, actual delayed atomic writer, controlled unload and full restart; explicit offline player/scheduler and InventoryView proxies, no network client or bank payout claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
