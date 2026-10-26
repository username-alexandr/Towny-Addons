package ru.neverland.probe;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.townycouncil.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Native Folia/Towny services and real inventories. Offline actor identity is a strict proxy. */
public final class AccessProbe extends JavaPlugin {
    static UUID id(String key){return UUID.nameUUIDFromBytes(("access-20261024-"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static final UUID T=id("town"),O=id("other"),M=id("mayor"),R=id("resident"),N=id("next"),X=id("outsider");
    List<String> results=new ArrayList<>();int checks;Inventory opened;Town town,other;CouncilService service;NeverLandTownyCouncil owner;
    void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);results.add("PASS "+name);}
    interface Work{void run()throws Exception;}
    void deny(Work work,String name)throws Exception{try{work.run();}catch(IllegalArgumentException e){check(true,name);return;}throw new AssertionError(name);}
    @Override public void onEnable(){
        if(!Files.exists(Path.of("ALLOW_DISPOSABLE_MOB_FARM_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){getServer().getPluginManager().disablePlugin(this);return;}
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()){try{run();finish(null);}catch(Throwable e){finish(e);}}},40);
    }
    Player actor(UUID id){return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,m,a)->switch(m.getName()){
        case "getUniqueId"->id;case "getName"->TownyAPI.getInstance().getResident(id).getName();
        case "hasPermission","isOp","isOnline"->true; // Explicit wildcard/OP must still NOT grant edit access.
        case "openInventory"->{opened=(Inventory)a[0];yield null;}case "sendMessage"->null;
        case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==a[0];case "toString"->"AccessProbe("+id+")";
        default->throw new UnsupportedOperationException("Unexpected Player call: "+m);
    });}
    void set(Player mayor,String role,UUID resident,String action,String mode)throws Exception{
        var state=service.rules(town);service.changeAccess(mayor,town,state.mayor(),state.revision(),role,resident,action,mode);
    }
    void run()throws Exception{
        owner=(NeverLandTownyCouncil)Bukkit.getPluginManager().getPlugin("NeverLandTownyCouncil");
        check(owner!=null&&owner.isEnabled(),"Council enabled on native Folia");service=owner.council();check(service.healthy(),"live registry healthy");
        var u=TownyUniverse.getInstance();town=u.getTown(T);other=u.getTown(O);
        if(Files.exists(getDataFolder().toPath().resolve("phase1"))){
            check(town!=null&&service.decision(T,R,"budget").equals("DENY"),"explicit deny survives server restart");
            check(service.decision(T,R,"build").equals("ALLOW"),"personal allow survives server restart");
            check(service.accessHistory().stream().anyMatch(s->s.contains(R.toString())),"access audit survives restart");
            check(service.holders(T).get("economy").equals(R),"existing appointments survive restart");return;
        }
        check(town==null,"fresh disposable fixture");u.newTownInternal("AccessTown",T);u.newTownInternal("AccessOther",O);town=u.getTown(T);other=u.getTown(O);
        for(var entry:Map.of(M,"AccessMayor",R,"AccessResident",N,"AccessNext",X,"AccessOutsider").entrySet()){
            var resident=u.getDataSource().newResident(entry.getValue(),entry.getKey());resident.setTown(entry.getKey().equals(X)?other:town);resident.save();
        }
        town.setMayor(u.getResident(M));other.setMayor(u.getResident(X));town.save();other.save();
        var mayor=actor(M);var resident=actor(R);var outsider=actor(X);
        check(service.mayEditAccess(mayor,town),"actual mayor may edit");
        check(!service.mayEditAccess(resident,town)&&!service.mayEditAccess(outsider,town)&&!service.mayEditAccess(Bukkit.getConsoleSender(),town),"OP, outsider mayor and console cannot edit");
        deny(()->set(resident,"",R,"budget","allow"),"OP resident denied mutation");
        deny(()->set(mayor,"",X,"budget","allow"),"cannot grant outsider");
        deny(()->set(mayor,"",M,"budget","deny"),"cannot restrict mayor");
        deny(()->set(mayor,"",R,"minecraft.command.op","allow"),"cannot manufacture server permission");
        check(service.decision(T,R,"budget").equals("DEFAULT"),"unconfigured resident uses legacy rules");
        service.appoint(mayor,town,MinisterRole.ECONOMY,R);
        check(service.decision(T,R,"budget").equals("ALLOW"),"existing economy minister defaults retained");
        set(mayor,"economy",null,"budget","deny");
        check(!CouncilAccess.check(resident,T,"budget",true),"city role denial beats legacy rank or permission");
        set(mayor,"",R,"budget","allow");check(CouncilAccess.check(resident,T,"budget",false),"personal allow beats role denial");
        set(mayor,"economy",null,"budget","allow");set(mayor,"",R,"budget","deny");
        check(!CouncilAccess.check(resident,T,"budget",true),"personal denial beats role grant");
        check(!CouncilAccess.check(resident,O,"budget",true),"rights cannot cross city boundary");
        set(mayor,"",R,"budget","default");check(CouncilAccess.check(resident,T,"budget",false),"reset inherits role grant");
        var old=service.rules(town);set(mayor,"",R,"build","allow");
        deny(()->service.changeAccess(mayor,town,old.mayor(),old.revision(),"",R,"budget","deny"),"stale menu revision rejected");
        var menus=new AccessMenus(owner,service,new CouncilMenus(owner,service));menus.open(mayor,town,true,0);
        check(opened.getItem(19)!=null&&opened.getItem(25)!=null,"real inventory has four role buttons");
        deny(()->menus.open(resident,town,true,0),"OP non-mayor cannot open editor");
        menus.actions(mayor,town,"",R,"Экономика",0);
        check(opened.getItem(10).getType()==Material.GRAY_DYE,"personal default is displayed distinctly");
        set(mayor,"",R,"budget","deny");menus.actions(mayor,town,"",R,"Экономика",0);
        check(opened.getItem(10).getType()==Material.RED_DYE,"explicit deny is displayed red");
        for(int i=0;i<30;i++){var r=u.getDataSource().newResident("AccessPerson"+i,id("person"+i));r.setTown(town);r.save();}
        menus.open(mayor,town,false,0);check(opened.getItem(50)!=null,"resident list paginates");menus.open(mayor,town,false,1);check(opened.getItem(48)!=null,"second resident page can go back");
        var beforeMayor=service.rules(town);town.setMayor(u.getResident(N));
        deny(()->service.changeAccess(mayor,town,beforeMayor.mayor(),beforeMayor.revision(),"",R,"army","allow"),"old mayor loses authority immediately");
        check(service.decision(T,R,"build").equals("DEFAULT"),"old term overrides immediately inactive");service.prune();
        check(service.holders(T).isEmpty()&&service.rules(town).residents().isEmpty(),"new mayor receives clean appointments and overrides");
        var next=actor(N);set(next,"",R,"build","allow");
        var r=u.getResident(R);r.removeTown();r.setTown(other);check(service.decision(T,R,"build").equals("DENY"),"left resident loses rights immediately");service.prune();r.removeTown();r.setTown(town);
        check(service.decision(T,R,"build").equals("DEFAULT"),"returning resident does not regain removed grant");
        service.appoint(next,town,MinisterRole.ECONOMY,R);set(next,"",R,"build","allow");set(next,"",R,"budget","deny");
        check(service.actions().size()==TownAction.values().length,"client API exposes complete action catalogue");
        check(service.residentActions(T,R).get("budget").equals("DENY"),"client API reports personal policy");
        town.save();other.save();u.getDataSource().saveAll();u.getDataSource().saveQueues();
        getDataFolder().mkdirs();Files.writeString(getDataFolder().toPath().resolve("phase1"),"ready");
    }
    void finish(Throwable e){try{getDataFolder().mkdirs();if(e!=null){getLogger().log(java.util.logging.Level.SEVERE,"Access probe failed",e);results.add("ERROR "+e);}results.add("checks="+checks);results.add("failed="+(e==null?0:1));Files.writeString(getDataFolder().toPath().resolve("result.txt"),String.join("\n",results)+"\n");}catch(Exception ex){getLogger().severe(ex.toString());}}
}
