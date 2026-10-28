package ru.neverland.runtime;

import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import com.palmergames.bukkit.towny.*;
import ru.neverland.townybuilds.*;
import ru.neverland.townybuilds.api.*;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.construction.*;
import ru.neverland.townybuilds.service.DefinitionRegistry;

/** Genuine server BlockData, stored town sites, upgrade cancellation and cross-addon APIs. No player stand-in. */
public final class SchematicsProbe extends TntRuntimeProbe {
    NeverLandTownyBuilds builds;DataStore data;String pin;BuildingBlueprintGenerator generator=new BuildingBlueprintGenerator();
    @Override void setup()throws Exception{
        builds=(NeverLandTownyBuilds)Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");check(builds.isEnabled(),"Builds enabled with genuine Sponge v3 models");data=(DataStore)field(builds,"dataStore");
        String current=BlueprintCatalog.currentPin("town_hall",BuildingStyle.MEDIEVAL);check(current.matches("[a-f0-9]{64}"),"current imported family has content hash");
        if(!restart){TownyUniverse.getInstance().newTownInternal("SchematicCity",DEF);pin=current;Files.writeString(proof.resolve("pin.txt"),pin);
            var site=site(pin);data.town(DEF).setLevel("town_hall",1);data.town(DEF).setConstructionSite(site);data.town(DEF).placement("town_hall",PlacementDraft.create(2,pin));
        }else{pin=Files.readString(proof.resolve("pin.txt"));check(!pin.equals(current),"source replacement creates a new version after full restart");check(data.town(DEF).constructionSite("town_hall").blueprintSet().equals(pin),"existing site retains archived version through restart");check(data.town(DEF).placements().get("town_hall").blueprintSet().equals(pin),"issued draft retains archived version through restart");}
        var site=data.town(DEF).constructionSite("town_hall");var plan=BlueprintCatalog.sitePlan(site,1,generator);
        check(plan.blocks().size()==4&&plan.blocks().get(new BlockOffset(-1,0,-1)).material()==Material.STONE_BRICKS,"old site uses four original blocks and ignores arbitrary world offset");
        check(site.architectureVersion()==10006&&BuildingStyle.ofArchitecture(site.architectureVersion())==BuildingStyle.MEDIEVAL,"import marker preserves style and rejects old procedural interpretation");
        var latest=BlueprintCatalog.preview("town_hall",1,null,BuildingStyle.MEDIEVAL,null,generator);
        check(latest.blocks().size()==(restart?5:4)&&latest.blocks().get(new BlockOffset(-1,0,-1)).material()==(restart?Material.GOLD_BLOCK:Material.STONE_BRICKS),"new preview uses currently imported source");
        var old=new ConstructionSite("town_hall",world.getUID(),15,160,8,BlockFace.NORTH,1,1,2,false,6);
        check(BlueprintCatalog.sitePlan(old,1,generator).blocks().size()>4,"unversioned legacy site remains procedural after importing new family");
        var fp=new BuildingFootprints().footprint(site,1).orElseThrow();check(fp.minX()==16&&fp.maxX()==16&&fp.minZ()==7&&fp.maxZ()==9,"footprint uses pinned maximum-stage geometry and saved rotation");
        var core=Bukkit.getServicesManager().load(RaidStorageApi.class).core(DEF).orElseThrow();check(core.x()==16&&core.y()==160&&core.z()==8,"raid lectern core follows pinned model and rotation");
        var registered=Bukkit.getServicesManager().load(TownyBuildsApi.class).registeredBuildingBlocks(DEF);check(registered.size()==4&&registered.stream().anyMatch(b->b.material().equals("STONE_BRICKS")),"public building protection API uses imported site geometry");
        var repair=Bukkit.getServicesManager().load(BuildingRepairPlansApi.class).plan(DEF,"town_hall");var cells=(List<Map<String,Object>>)repair.get("cells");
        check(cells.size()==4&&cells.stream().anyMatch(c->c.get("data").toString().contains("axis=z")),"raid repair plan uses imported states and rotated log axis");
        check(cells.stream().anyMatch(c->c.get("data").toString().contains("facing=south")&&c.get("data").toString().contains("half=top")&&c.get("data").toString().contains("waterlogged=true")),"stairs retain half, waterlogging and rotated facing");
        var def=(DefinitionRegistry)field(builds,"definitions");check(((Number)repair.get("price")).longValue()==Math.round(def.project("town_hall").level(1).money()*100),"schematic geometry does not alter configured money price");
        var selected=BlueprintCatalog.preview("town_hall",2,null,BuildingStyle.MEDIEVAL,pin,generator);check(selected.blocks().size()==4,"issued placement preview stays on selected archived version");
        Path file=builds.getDataFolder().toPath().resolve("blueprints/versions/"+pin+".nltbp"),hidden=file.resolveSibling(file.getFileName()+".hidden");
        Files.move(file,hidden);boolean denied=false;try{BlueprintCatalog.initialize(builds,data);}catch(java.io.IOException expected){denied=true;}finally{Files.move(hidden,file);}
        check(denied&&BlueprintCatalog.sitePlan(site,1,generator).blocks().size()==4,"missing historical archive fails closed without replacing active catalog");
        Path config=builds.getDataFolder().toPath().resolve("blueprints.yml");String original=Files.readString(config);
        Files.writeString(config,original.replace("hall-2.schem","invalid-level.schem"));boolean invalid=false;
        try{BlueprintCatalog.initialize(builds,data);try{BlueprintCatalog.currentPin("town_hall",BuildingStyle.MEDIEVAL);}catch(IllegalStateException expected){invalid=true;}}
        finally{Files.writeString(config,original);BlueprintCatalog.initialize(builds,data);}
        check(invalid&&BlueprintCatalog.sitePlan(site,1,generator).blocks().size()==4,"incompatible upgrade blocks new drafts while preserving existing archived sites");
        if(!restart){
            site.activate(2,2);var receipt=new ConstructionReceipt(UUID.randomUUID(),2,0,List.of(),Map.of(),"REFUND_PAID",0,List.of(),0,"native-test",false,0);data.town(DEF).receipt("town_hall",receipt);
            call(builds.constructionRefunds(),"drain",new Class<?>[]{ru.neverland.townybuilds.data.TownData.class,String.class,ConstructionReceipt.class},data.town(DEF),"town_hall",receipt);
            check(data.town(DEF).constructionSite("town_hall").blueprintSet().equals(pin)&&!data.town(DEF).constructionSite("town_hall").active(),"cancelled upgrade restores completed site without dropping archived identity");
        }
        data.markDirty();data.saveOrThrow();finish();
    }
    ConstructionSite site(String pin){var s=new ConstructionSite("town_hall",world.getUID(),15,160,8,BlockFace.EAST,1,1,2,false,6);s.blueprintSet(pin);return s;}
    @Override void finish()throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; native Folia/Towny/Sponge v3 imports, BlockData, immutable archives, site/draft persistence, cancellation and public protection/repair APIs; no player client claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
