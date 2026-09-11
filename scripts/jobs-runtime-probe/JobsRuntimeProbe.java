package ru.neverland.runtime;
import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.AtomicLong;import java.lang.reflect.*;
import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;import com.palmergames.bukkit.towny.object.*;
import ru.neverland.townyjobs.*;import ru.neverland.townyjobs.api.*;
import ru.neverland.townybuilds.NeverLandTownyBuilds;import ru.neverland.townybuilds.data.*;import ru.neverland.townybuilds.api.*;import ru.neverland.townybuilds.construction.ConstructionSite;
import ru.neverland.townyupkeep.api.*;import ru.neverland.townyupkeep.service.*;import ru.neverland.townyupkeep.model.*;
import ru.neverland.townypower.api.*;import ru.neverland.townypower.service.*;
import ru.neverland.townypolicies.api.*;import ru.neverland.townypolicies.service.*;
/** Real server and plugin services, controlled actor/time fixture. This does not simulate a connected Minecraft client. */
public final class JobsRuntimeProbe extends JavaPlugin {
    private final UUID townId=id("town");private JobsService jobs;private DataStore data;private World world;private Path folder;private int checks;
    private final AtomicLong now=new AtomicLong(System.currentTimeMillis());private boolean present=true,allowed=true,far=false;
    private static final Map<Profession,String> BUILDINGS=Map.of(Profession.BLACKSMITH,"forge",Profession.FARMER,"bakery",Profession.ENGINEER,"water_tower",Profession.GUARD,"guard",Profession.MERCHANT,"merchant_guild",Profession.RESEARCHER,"great_library",Profession.ALCHEMIST,"alchemy");
    private static UUID id(String s){return UUID.nameUUIDFromBytes(("neverland-jobs-0210:"+s).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static UUID actor(Profession p){return id(p.id());}
    private void check(boolean yes,String msg){checks++;if(!yes)throw new AssertionError(msg);}
    private void near(double want,double got,String msg){check(Math.abs(want-got)<1e-8,msg+": expected "+want+", got "+got);}
    private interface Attempt{void run()throws Exception;}
    private void reject(Attempt f,String msg)throws Exception{try{f.run();}catch(IllegalArgumentException|IllegalStateException expected){checks++;return;}throw new AssertionError(msg);}
    private static Field field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    @Override public void onEnable(){if(!Boolean.getBoolean("neverland.runtimeProbe")||!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_JOBS_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){getServer().getPluginManager().disablePlugin(this);return;}getServer().getScheduler().runTaskLater(this,this::run,100);}
    private void run(){String phase="initialization";try{
        folder=getDataFolder().toPath();Files.createDirectories(folder);check(Arrays.stream(getServer().getPluginManager().getPlugins()).filter(p->p.getName().startsWith("NeverLandTowny")&&p.isEnabled()).count()==27,"all 27 addons enabled");
        jobs=(JobsService)getServer().getServicesManager().load(TownyJobsApi.class);check(jobs!=null&&!jobs.fault(),"Jobs API ready");
        data=(DataStore)field(NeverLandTownyBuilds.class,"dataStore").get(getServer().getPluginManager().getPlugin("NeverLandTownyBuilds"));world=getServer().getWorlds().getFirst();
        phase=Files.exists(folder.resolve("restart-ready"))?"restart":"first";if(phase.equals("first"))first();else restart();Files.writeString(folder.resolve(phase+"-passed.txt"),"PASS "+checks+" assertions\n");getLogger().info("JOBS_RUNTIME_"+phase.toUpperCase(Locale.ROOT)+"_PASS: "+checks+" assertions");
    }catch(Throwable ex){getLogger().log(java.util.logging.Level.SEVERE,"JOBS_RUNTIME_FAIL at "+phase,ex);try{Files.writeString(folder.resolve("failed.txt"),ex.toString());}catch(Exception ignored){}}finally{getServer().getScheduler().runTaskLater(this,()->getServer().shutdown(),20);}}
    private void inject()throws Exception{field(JobsService.class,"clock").set(jobs,(java.util.function.LongSupplier)now::get);field(JobsService.class,"lastPulse").setLong(jobs,now.get());field(JobsService.class,"source").set(jobs,(JobsService.PresenceSource)uuid->{var c=jobs.career(uuid);var s=jobs.workplace(uuid);if(s==null)return null;return new WorkPolicy.Presence(c.town(),s.world(),far?s.maxX()+100:s.minX()+1,s.y()+1,s.minZ()+1,present,allowed);});}
    private Player player(UUID uuid,boolean permission){return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,m,args)->switch(m.getName()){case "getUniqueId"->uuid;case "hasPermission"->permission;case "getName"->"JobsProbe";case "hashCode"->uuid.hashCode();case "equals"->proxy==args[0];case "toString"->"JobsProbeActor";default->throw new UnsupportedOperationException(m.getName());});}
    private void createTown()throws Exception{var u=TownyUniverse.getInstance();u.newTownInternal("JobsProbe",townId);var town=TownyAPI.getInstance().getTown(townId);
        for(Profession p:Profession.values()){var r=u.getDataSource().newResident("Jobs"+p.id(),actor(p));r.setTown(town);if(p==Profession.MERCHANT)town.setMayor(r);r.save();}
        for(int x=8;x<24;x++)for(int z=8;z<24;z++){var b=new TownBlock(x,z,u.getWorld(world.getName()));u.addTownBlock(b);b.setTown(town);if(x==8&&z==8)town.setHomeBlock(b);b.save();}
        town.setSpawn(new Location(world,140,64,140));town.save();int i=0;
        for(Profession p:Profession.values()){String b=BUILDINGS.get(p);int x=160+(i%4)*48,z=160+(i/4)*64;i++;data.town(townId).setLevel(b,1);data.town(townId).setConstructionSite(new ConstructionSite(b,world.getUID(),x,64,z,org.bukkit.block.BlockFace.NORTH,1,1,1,false));}
        data.town(townId).setLevel("mill",5);data.town(townId).setConstructionSite(new ConstructionSite("mill",world.getUID(),160,64,300,org.bukkit.block.BlockFace.NORTH,5,5,1,false));data.saveOrThrow();
        ((PoliciesService)getServer().getServicesManager().load(TownyPoliciesApi.class)).refresh();var upkeep=(UpkeepService)getServer().getServicesManager().load(TownyUpkeepApi.class);upkeep.reload(upkeep.settings());((PowerService)getServer().getServicesManager().load(TownyPowerApi.class)).refresh();
    }
    private void first()throws Exception{
        createTown();inject();var settings=jobs.settings();check(settings.profiles().size()==7,"all seven native material icons and profiles parsed");
        var bridge=new CityBridge();var sites=bridge.sites(townId);check(sites.keySet().containsAll(BUILDINGS.values()),"public workplace geometry includes all profession fixtures");
        for(Profession p:Profession.values()){
            UUID uuid=actor(p);Player player=player(uuid,true);reject(()->jobs.choose(player(uuid,false),p,0,settings),"use permission required");
            jobs.choose(player,p,0,settings);reject(()->jobs.work(player(uuid,false),BUILDINGS.get(p)),"work permission required");jobs.work(player,BUILDINGS.get(p));
            check(jobs.career(uuid).town().equals(townId),"own town workplace bound "+p);check(jobs.workplace(uuid).owned()&&jobs.workplace(uuid).active(),"owned active finished workplace "+p);
            reject(()->jobs.choose(player,Profession.values()[(p.ordinal()+1)%7],jobs.career(uuid).revision(),settings),"profession cooldown enforced");
            jobs.beginShift(uuid);near(0,jobs.bonus(townId,BUILDINGS.get(p)),"warmup denies bonus "+p);jobs.signal(uuid,p);
        }
        now.addAndGet(30000);jobs.refresh(true);
        for(Profession p:Profession.values()){near(.02,jobs.bonus(townId,BUILDINGS.get(p)),"active profession contributes "+p);check(jobs.workers(townId,BUILDINGS.get(p))==1,"active headcount "+p);check(jobs.career(actor(p)).seconds().get(p)==10,"bounded work time, not 30-second catchup "+p);}
        var resourceBridge=new ru.neverland.townyresources.integration.CityBridge();for(Profession p:Profession.values())near(1.02,resourceBridge.buildings(townId).get(BUILDINGS.get(p)).bonus(),"Resources consumes actual Jobs API "+p);
        var builds=getServer().getServicesManager().load(TownyBuildsApi.class);near(.051,builds.benefit(townId,CivicBenefit.TRADE_CAPACITY),"merchant increases actual civic trade capacity");
        near(.02,jobs.townBonus(townId,"research_speed"),"researcher town effect");near(.02,jobs.townBonus(townId,"guard_defence"),"guard resident monster defence");near(0,jobs.townBonus(townId,"unknown"),"unknown effect neutral");
        // Native recipe stock arithmetic: Jobs grants extra complete operations, not buckets from nothing.
        var stock=new org.bukkit.inventory.ItemStack[9];stock[0]=new org.bukkit.inventory.ItemStack(Material.BUCKET,2);for(int i=0;i<3;i++){var next=ru.neverland.townybuilds.storage.StockMath.recipe(stock,List.of(new org.bukkit.inventory.ItemStack(Material.BUCKET)),new org.bukkit.inventory.ItemStack[]{new org.bukkit.inventory.ItemStack(Material.WATER_BUCKET)});if(next.isPresent())stock=next.get();}
        check(Arrays.stream(stock).filter(Objects::nonNull).filter(s->s.getType()==Material.WATER_BUCKET).mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum()==2,"extra operations conserve empty buckets");
        // Immediate activity and operating state checks between periodic refreshes.
        far=true;near(0,jobs.bonus(townId,"merchant_guild"),"out of workplace bonus zero");far=false;allowed=false;near(0,jobs.bonus(townId,"merchant_guild"),"permission or creative mode blocks bonus");allowed=true;present=false;near(0,jobs.bonus(townId,"merchant_guild"),"offline bonus zero");present=true;
        now.addAndGet(90001);near(0,jobs.bonus(townId,"merchant_guild"),"idle activity expires");for(Profession p:Profession.values())jobs.signal(actor(p),p);
        var upkeep=(UpkeepService)getServer().getServicesManager().load(TownyUpkeepApi.class);var key=new Entry.Key(townId,"merchant_guild");var previous=upkeep.get(key);upkeep.put(key,new Entry(false,0,"probe unpaid",null));near(0,jobs.bonus(townId,"merchant_guild"),"unpaid upkeep removes bonus immediately");upkeep.put(key,previous);near(.02,jobs.bonus(townId,"merchant_guild"),"paid building resumes");
        // Native command permission path for stale confirmations and reassignment.
        UUID merchant=actor(Profession.MERCHANT);reject(()->jobs.choose(player(merchant,true),Profession.FARMER,0,settings),"stale revision refused");
        var c=jobs.career(actor(Profession.FARMER));jobs.repository().put(actor(Profession.FARMER),new Career(c.profession(),Map.of(c.profession(),1790L),c.town(),c.building(),c.assignedAt(),c.changedAt(),c.revision()));now.addAndGet(10000);jobs.refresh(true);check(jobs.settings().level(jobs.career(actor(Profession.FARMER)))==2,"level from active experience");near(.035,jobs.bonus(townId,"bakery"),"new level bonus applied");
        // Reserve a third merchant fixture: after capacity reduction only oldest seats can contribute.
        for(int i=0;i<2;i++){UUID uuid=id("extra"+i);var r=TownyUniverse.getInstance().getDataSource().newResident("JobsExtra"+i,uuid);r.setTown(TownyAPI.getInstance().getTown(townId));r.save();jobs.repository().put(uuid,Career.empty().choose(Profession.MERCHANT,1).work(townId,"merchant_guild",now.get()+i));}
        jobs.refresh(false);reject(()->jobs.beginShift(id("extra1")),"over-capacity assignment cannot begin duty");
        // Departure listener preserves profession and XP while releasing seat.
        var resident=TownyAPI.getInstance().getResident(actor(Profession.ALCHEMIST));getServer().getPluginManager().callEvent(new com.palmergames.bukkit.towny.event.TownRemoveResidentEvent(resident,TownyAPI.getInstance().getTown(townId)));check(jobs.career(actor(Profession.ALCHEMIST)).town()==null&&jobs.career(actor(Profession.ALCHEMIST)).profession()==Profession.ALCHEMIST,"departure clears only assignment");
        jobs.adminRelease(id("extra0"));jobs.adminRelease(id("extra1"));
        getConfig().set("farmer-xp",jobs.career(actor(Profession.FARMER)).seconds().get(Profession.FARMER));saveConfig();Files.writeString(folder.resolve("restart-ready"),"restart\n");
    }
    private void restart()throws Exception{
        check(jobs.career(actor(Profession.MERCHANT)).building().equals("merchant_guild"),"assignment persisted");check(jobs.career(actor(Profession.FARMER)).seconds().get(Profession.FARMER)==getConfig().getLong("farmer-xp"),"experience persists with no offline catchup");check(jobs.career(actor(Profession.ALCHEMIST)).town()==null,"released workplace persists");
        for(Profession p:Profession.values()){check(!jobs.onDuty(actor(p)),"no restart shifts "+p);near(0,jobs.bonus(townId,BUILDINGS.get(p)),"no restart/offline bonus "+p);}
        inject();jobs.beginShift(actor(Profession.MERCHANT));jobs.signal(actor(Profession.MERCHANT),Profession.MERCHANT);now.addAndGet(30000);jobs.refresh(true);near(.02,jobs.bonus(townId,"merchant_guild"),"new shift after restart resumes");
        UUID researcher=actor(Profession.RESEARCHER);jobs.beginShift(researcher);jobs.signal(researcher,Profession.RESEARCHER);now.addAndGet(30000);jobs.refresh(true);
        var resources=(ru.neverland.townyresources.service.ResourcesService)getServer().getServicesManager().load(ru.neverland.townyresources.api.TownyResourcesApi.class);resources.reload(resources.settings());resources.adjust(townId,ru.neverland.townyresources.model.Resource.KNOWLEDGE,10000,"set");
        UUID invoice=id("research-invoice");check(resources.reserveResources(invoice,townId,Map.of("knowledge",1000L)),"native knowledge reservation");
        var research=(ru.neverland.townyresearch.service.ResearchService)getServer().getServicesManager().load(ru.neverland.townyresearch.api.TownyResearchApi.class);var researchRepo=(ru.neverland.townyresearch.data.ResearchRepository)field(research.getClass(),"repository").get(research);
        var study=new ru.neverland.townyresearch.model.CityStudy.Study(invoice,"irrigation",1,1000,100,100,Map.of("great_library",1),ru.neverland.townyresearch.model.CityStudy.Phase.RUNNING);
        researchRepo.put(townId,ru.neverland.townyresearch.model.CityStudy.empty().study(study));research.refresh(10);check(researchRepo.get(townId).active().remaining()==90&&researchRepo.get(townId).active().fraction()==200,"ResearchService applies actual 2% Jobs speed with fractional time");
        jobs.endShift(researcher);research.refresh(10);check(researchRepo.get(townId).active().remaining()==80&&researchRepo.get(townId).active().fraction()==200,"research returns to normal speed after duty");resources.settleResources(invoice,false);resources.forgetReservation(invoice);researchRepo.put(townId,ru.neverland.townyresearch.model.CityStudy.empty());
        getServer().getPluginManager().disablePlugin(getServer().getPluginManager().getPlugin("NeverLandTownyPower"));near(0,jobs.bonus(townId,"merchant_guild"),"unavailable Power fails closed immediately");
        jobs.reload(jobs.settings());check(!jobs.onDuty(actor(Profession.MERCHANT)),"reload resets shift");near(0,jobs.bonus(townId,"merchant_guild"),"reload removes bonus");
    }
}
