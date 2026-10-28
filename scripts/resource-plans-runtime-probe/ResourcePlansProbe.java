package ru.neverland.runtime;

import java.nio.file.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import ru.neverland.townyresources.service.ResourcesService;
import ru.neverland.townyresources.data.ResourcesRepository;
import ru.neverland.townyresources.api.TownyResourcesApi;
import ru.neverland.townyresources.model.*;
import ru.neverland.townyresources.gui.ResourcesMenu;
import ru.neverland.core.*;

/** Real Folia services, registered command, town permissions, inventory rendering and journal. Explicit player proxy. */
public final class ResourcePlansProbe extends TntRuntimeProbe {
    JavaPlugin resources;ResourcesService service;ResourcesRepository repository;Inventory top;Player mayor,resident;List<String> messages=new ArrayList<>();
    Player actor(UUID uuid,String name){return(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
        case "getUniqueId"->uuid;case "getName"->name;case "getServer"->Bukkit.getServer();case "getWorld"->world;case "getLocation"->origin;case "isOnline"->true;case "isOp"->false;
        case "hasPermission"->a[0].equals("neverlandtownyresources.use");case "openInventory"->{top=(Inventory)a[0];yield null;}case "sendMessage"->{if(a[0] instanceof String s)messages.add(s);yield null;}
        case "hashCode"->uuid.hashCode();case "equals"->p==a[0];default->null;});}
    void command(Player who,String...args){resources.getCommand("townyresources").execute(who,"townyresources",args);}
    long wood(){return service.resources(DEF).orElseThrow().state().balances().get(Resource.WOOD);}
    @Override void setup()throws Exception{
        resources=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyResources");service=(ResourcesService)Bukkit.getServicesManager().load(TownyResourcesApi.class);repository=(ResourcesRepository)field(service,"repository");
        check(resources.isEnabled()&&service!=null,"real Resources 0.2 service enabled");
        if(!restart){var u=TownyUniverse.getInstance();u.newTownInternal("ResourcePlans",DEF);var town=TownyAPI.getInstance().getTown(DEF);var owner=u.getDataSource().newResident("ResourceMayor",MAYOR);owner.setTown(town);town.setMayor(owner);owner.save();var member=u.getDataSource().newResident("ResourceResident",ACTOR);member.setTown(town);member.save();town.save();service.reload(service.settings());}
        mayor=actor(MAYOR,"ResourceMayor");resident=actor(ACTOR,"ResourceResident");
        if(restart){
            check(wood()==30000&&repository.held(DEF).get(Resource.WOOD)==70000,"funded plan balance and escrow survive full Folia restart");
            check(repository.reservations().get(ResourceRequests.id(DEF,"baths")).status()==Reservation.Status.REQUESTED,"unfunded demand survives full restart");
            command(mayor,"fund","aqueduct");check(wood()==30000,"real command cannot debit funded plan twice after restart");
            command(mayor,"release","aqueduct");command(mayor,"release","aqueduct");check(wood()==100000,"real cancellation command refunds held stock exactly once");
            command(mayor,"release","baths");check(wood()==100000,"cancelling unprovided request never creates stock");finish();return;
        }
        service.adjust(DEF,Resource.WOOD,100000,"set");command(resident,"plan","unauthorized","wood=1");check(service.resourceRequests(DEF).isEmpty(),"ordinary resident cannot create a resource plan");
        command(mayor,"keep","wood","10");command(mayor,"plan","aqueduct","wood=70");command(mayor,"plan","baths","wood=70");
        check(service.resourceRequests(DEF).size()==2&&wood()==100000,"actual mayor command creates two requests without spending");
        check(service.resourceDeficits(DEF).get("wood")==50000,"registered API aggregates deficits and protects mayor minimum");
        command(mayor,"fund","aqueduct");check(wood()==30000&&repository.held(DEF).get(Resource.WOOD)==70000,"fund command fully escrows available resources");
        command(mayor,"fund","baths");check(wood()==30000&&repository.reservations().get(ResourceRequests.id(DEF,"baths")).status()==Reservation.Status.REQUESTED,"short second plan cannot consume minimum or partly debit");
        command(mayor,"consume","aqueduct","no");check(repository.reservations().get(ResourceRequests.id(DEF,"aqueduct")).status()==Reservation.Status.HELD,"final consumption requires literal confirmation");
        var menu=new ResourcesMenu(resources,service);menu.open(mayor,DEF,"menu",null,0);check(top.getItem(46).getType()==Material.WRITABLE_BOOK&&top.getItem(47).getType()==Material.BARREL,"native menu separates deficit and purpose plans");
        menu.open(mayor,DEF,"request","aqueduct",0);check(top.getItem(24).getType()==Material.RED_DYE&&top.getItem(31).getType()==Material.FURNACE&&top.getItem(20)==null,"funded plan menu offers refund and explains confirmed consumption");
        var audit=new ArrayList<AuditRecord>();AuditJournal.scan(resources.getDataFolder().toPath().resolve("audit"),audit::add);
        check(audit.stream().anyMatch(r->r.kind().equals("PURPOSE_RESOURCES")&&r.outcome().equals("SUPPLY_HELD")&&r.actor().id().equals(MAYOR.toString())),"administrator journal records actual funding actor and stable plan identity");finish();
    }
    @Override void finish()throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; genuine Folia resources service, native inventories, mayor permissions, registered command, resource escrow and audit; explicit player proxy, no GUI client claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
