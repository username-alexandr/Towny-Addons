package ru.neverland.runtime;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.concurrent.locks.ReentrantLock;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import io.papermc.paper.threadedregions.scheduler.*;
import ru.neverland.core.*;
import ru.neverland.townycitizens.*;
import ru.neverland.townycitizens.model.*;
import static ru.neverland.townycitizens.model.Admissions.*;

/** Real Folia, Towny, Citizens repository/commands/menus. Player, view and scheduler are explicit offline substitutes. */
public final class AdmissionsProbe extends JavaPlugin {
    static UUID id(String key){return UUID.nameUUIDFromBytes(("admissions-052-"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static final UUID T=id("town"),P=id("applicant"),M=id("mayor"),S=id("sponsor"),L=id("legacy");
    Path proof;boolean restart;int checks;World world;Location origin;Town town;NeverLandTownyCitizens owner;CitizensService citizens;AdmissionsService admissions;AdmissionsController controller;
    final Map<UUID,Inventory> windows=new HashMap<>();final List<String> messages=new ArrayList<>();Player applicant,mayor,sponsor;UUID application,confirmation;CountDownLatch release;
    interface Work{void run()throws Exception;}
    static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    void check(boolean good,String why)throws Exception{if(!good)throw new AssertionError(why);checks++;Files.writeString(proof.resolve((restart?"restart":"first")+"-checks.txt"),"PASS "+why+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+why);}
    void denied(Work work,String why)throws Exception{try{work.run();}catch(Exception expected){check(true,why);return;}throw new AssertionError(why);}
    void later(long ticks,Work work){Bukkit.getRegionScheduler().runDelayed(this,origin,t->{if(!SuiteState.tryRun("Citizens native admission check",()->{try{work.run();}catch(Throwable error){fail(error);}}))later(1,work);},ticks);}
    <V>void await(CompletionStage<V> future,Consumer<V> next){future.whenComplete((value,error)->{if(error!=null)fail(error);else later(1,()->next.accept(value));});}
    void fail(Throwable error){if(release!=null)release.countDown();getLogger().log(java.util.logging.Level.SEVERE,"ADMISSIONS PROBE FAILED",error);try{Files.writeString(proof.resolve("failed.txt"),error.toString());}catch(Exception ignored){}Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
    @Override public void onEnable(){if(!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RAID_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){Bukkit.getPluginManager().disablePlugin(this);return;}proof=getDataFolder().toPath();Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{try{Files.createDirectories(proof);restart=Files.exists(proof.resolve("first-passed.txt"));world=Bukkit.getWorlds().getFirst();origin=new Location(world,8.5,120,8.5);world.getChunkAtAsync(0,0).whenComplete((chunk,error)->{if(error!=null)fail(error);else later(3,this::setup);});}catch(Throwable error){fail(error);}},40);}
    @SuppressWarnings("unchecked") Player actor(UUID uuid,String name){
        var scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class<?>[]{EntityScheduler.class},(p,m,a)->switch(m.getName()){
            case "runDelayed"->Bukkit.getRegionScheduler().runDelayed(this,origin,(Consumer<ScheduledTask>)a[1],(Long)a[3]);case "run"->Bukkit.getRegionScheduler().run(this,origin,(Consumer<ScheduledTask>)a[1]);default->throw new UnsupportedOperationException(m.getName());});
        return(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
            case "getUniqueId"->uuid;case "getName"->name;case "getServer"->Bukkit.getServer();case "getWorld"->world;case "getLocation"->origin;case "isOnline"->true;case "isOp"->false;case "getScheduler"->scheduler;
            case "hasPermission"->!Set.of("neverlandtownycitizens.admin","neverlandtownycitizens.bypass").contains(a[0]);case "sendMessage"->{messages.add(name+":"+String.valueOf(a[a.length-1]));yield null;}
            case "openInventory"->{windows.put(uuid,(Inventory)a[0]);yield null;}case "getOpenInventory"->view((Player)p,uuid);
            case "closeInventory"->{windows.remove(uuid);yield null;}case "hashCode"->uuid.hashCode();case "equals"->p==a[0];default->null;});
    }
    InventoryView view(Player p,UUID uuid){return(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(v,m,a)->switch(m.getName()){case "getTopInventory"->windows.computeIfAbsent(uuid,k->Bukkit.createInventory(null,9));case "getPlayer"->p;case "getType"->InventoryType.CHEST;case "getSlotType"->InventoryType.SlotType.CONTAINER;case "convertSlot"->a[0];default->null;});}
    String lore(Player p,int slot){return String.join(" ",windows.get(p.getUniqueId()).getItem(slot).getItemMeta().getLore());}
    void setup()throws Exception{
        owner=(NeverLandTownyCitizens)Bukkit.getPluginManager().getPlugin("NeverLandTownyCitizens");citizens=owner.citizens();controller=(AdmissionsController)field(owner,"admissions");admissions=new AdmissionsService(owner,citizens);applicant=actor(P,"AdmissionApplicant");mayor=actor(M,"AdmissionMayor");sponsor=actor(S,"AdmissionSponsor");
        check(owner.isEnabled()&&owner.getDescription().getVersion().equals("0.2.0")&&citizens.healthy(),"actual Citizens 0.2 service and validated status repository enabled");
        if(restart){resume();return;}
        var u=TownyUniverse.getInstance();u.newTownInternal("AdmissionsCity",T);town=TownyAPI.getInstance().getTown(T);
        for(var e:Map.of(P,"AdmissionApplicant",M,"AdmissionMayor",S,"AdmissionSponsor",L,"AdmissionLegacy").entrySet()){var r=TownyAPI.getInstance().getResident(e.getKey());if(r==null)r=u.getDataSource().newResident(e.getValue(),e.getKey());r.setTown(town);r.setRegistered(System.currentTimeMillis()-10*DAY);r.save();}town.setMayor(TownyAPI.getInstance().getResident(M));var claim=new TownBlock(0,0,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);town.setHomeBlock(claim);claim.save();town.setSpawn(origin);town.save();
        check(citizens.status(T,P).equals("CITIZEN")&&citizens.allows(T,P,"VOTE"),"old Towny resident remains citizen without mandatory application");check(citizens.repository().get(T,L).reason().equals("Legacy preserved"),"authentic schema1 citizenship loaded without rewriting history");
        denied(()->admissions.policy(applicant,T,new Terms(1,2,"Соблюдать правила",1),0),"ordinary resident cannot change mayor terms");
        await(admissions.policy(mayor,T,new Terms(1,2,"Помогать городу",1),0),state->{try{var a=admissions.proposal(applicant,town,CitizenshipStatus.TEMPORARY,7,"Хочу помогать городу");application=a.id();await(admissions.submit(applicant,a),saved->afterSubmit());}catch(Exception error){fail(error);}});
    }
    void afterSubmit(){try{
        check(citizens.status(T,P).equals("CITIZEN")&&admissions.state().board().applications().get(application).phase()==Phase.OPEN,"submission alone never changes rights");
        denied(()->admissions.support(applicant,application,1,false),"actual service prevents self sponsorship");
        await(admissions.support(sponsor,application,1,false),saved->{try{
            var a=admissions.state().board().applications().get(application);var r=TownyAPI.getInstance().getResident(S);r.setTown(null);check(admissions.eligible(a).isEmpty()&&!admissions.blockers(a).isEmpty(),"left-town sponsor is rechecked and no longer satisfies requirements");controller.openApplication(mayor,application);check(windows.get(M).getItem(32).getType()==Material.GRAY_DYE&&lore(mayor,22).contains("поручители"),"native mayor menu explains unavailable approval");r.setTown(town);r.save();
            await(admissions.policy(mayor,T,new Terms(5,30,"Правила для будущих заявлений",2),1),later->{try{review();}catch(Exception error){fail(error);}});
        }catch(Exception error){fail(error);}});
    }catch(Exception error){fail(error);}}
    void review()throws Exception{
        var a=admissions.state().board().applications().get(application);check(a.terms().sponsors()==1&&admissions.terms(T).sponsors()==5&&admissions.blockers(a).isEmpty(),"future policy update never tightens accepted application terms");
        denied(()->admissions.decide(applicant,application,a.revision(),Phase.APPROVED,"self"),"actual applicant cannot approve own application");
        controller.command(mayor,new String[]{"approve",application.toString(),"Проверено мэром"});var quote=windows.get(M);check(quote.getItem(29).getType()==Material.LIME_DYE&&lore(mayor,13).contains("Проверено мэром"),"actual approve command shows separate exact decision confirmation");
        var offers=(Map<?,?>)field(controller,"offers");var offer=offers.get(M);confirmation=(UUID)field(offer,"id");
        var atomic=Class.forName("ru.neverland.core.AtomicFiles",true,owner.getClass().getClassLoader());var locksField=atomic.getDeclaredField("writers");locksField.setAccessible(true);@SuppressWarnings("unchecked")var locks=(Map<Path,ReentrantLock>)locksField.get(null);var path=owner.getDataFolder().toPath().resolve("citizens.yml").toAbsolutePath().normalize();var lock=locks.computeIfAbsent(path,k->new ReentrantLock());release=new CountDownLatch(1);var locked=new CountDownLatch(1);var thread=new Thread(()->{lock.lock();locked.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}finally{lock.unlock();}},"native-delayed-citizens-file");thread.setDaemon(true);thread.start();if(!locked.await(1,TimeUnit.SECONDS))throw new AssertionError("file lock fixture unavailable");
        var event=new InventoryClickEvent(view(mayor,M),InventoryType.SlotType.CONTAINER,29,ClickType.LEFT,InventoryAction.PICKUP_ALL);controller.click(event);check(event.isCancelled(),"native confirmation inventory cancels item movement");
        later(3,()->{check(citizens.repository().busy()&&citizens.status(T,P).equals("CITIZEN")&&admissions.state().board().applications().get(application).phase()==Phase.OPEN,"real delayed file exposes neither unconfirmed status nor approval");check(SuiteState.tryRun("admissions live ledger while file blocked",()->{}),"region continues while actual citizenship file is blocked");release.countDown();poll(60);});
    }
    void poll(int remaining)throws Exception{if(citizens.repository().busy()){if(remaining<=0)throw new AssertionError("write did not finish");later(1,()->poll(remaining-1));return;}check(citizens.status(T,P).equals("TEMPORARY")&&!citizens.allows(T,P,"VOTE")&&admissions.state().board().applications().get(application).phase()==Phase.APPROVED,"confirmed approval atomically changes actual status and voting rights");long expiry=citizens.expiresAt(T,P);denied(()->controller.command(mayor,new String[]{"confirm",confirmation.toString()}),"single-use confirmation cannot repeat status assignment");check(citizens.expiresAt(T,P)==expiry&&admissions.state().board().history().size()==1,"repeat never extends temporary expiry or duplicates status history");
        controller.openHistory(applicant,T,P,0);check(windows.get(P).getItem(0).getItemMeta().getDisplayName().contains("МСК")&&lore(applicant,0).contains("Проверено мэром"),"native personal history shows Moscow timestamp and recorded reason");
        owner.getConfig().set("applications.enabled",false);denied(()->admissions.proposal(applicant,town,CitizenshipStatus.CITIZEN,0,"test"),"feature disable blocks new petitions");check(citizens.status(T,P).equals("TEMPORARY")&&citizens.expiresAt(T,P)==expiry&&admissions.state().board().applications().size()==1,"feature disable preserves statuses, expiry, sponsors and decision");owner.getConfig().set("applications.enabled",true);
        Files.writeString(proof.resolve("expected.txt"),expiry+"\n"+application+"\n"+admissions.state().board().applications().get(application).created()+"\n");
        // Simulate an administrator's elapsed pause; ModuleLifecycle must shift only live expiry on restart.
        var control=ModulePauseStore.file(owner.getDataFolder().toPath().getParent());ModulePauseStore.save(control,new ModulePauseStore.State(Set.of(),Map.of(owner.getName(),new ModulePauseStore.Entry(false,System.currentTimeMillis()-120_000))));finish();
    }
    void resume()throws Exception{
        town=TownyAPI.getInstance().getTown(T);var expected=Files.readAllLines(proof.resolve("expected.txt"));application=UUID.fromString(expected.get(1));var a=admissions.state().board().applications().get(application);check(town!=null&&citizens.status(T,P).equals("TEMPORARY")&&!citizens.allows(T,P,"VOTE"),"native full restart preserves approved effective status and rights");check(citizens.expiresAt(T,P)>=Long.parseLong(expected.get(0))+120_000,"actual module resume shifts temporary expiry across administrative pause");check(a.phase()==Phase.APPROVED&&a.created()==Long.parseLong(expected.get(2))&&a.sponsors().containsKey(S)&&a.terms().sponsors()==1,"native restart preserves frozen conditions, sponsor and unchanged historical time");check(admissions.state().board().history().size()==1&&admissions.state().board().history().getFirst().reason().equals("Проверено мэром"),"single atomic status history survives restart without duplicate");check(citizens.repository().get(T,L).reason().equals("Legacy preserved"),"unrelated legacy record survives schema migration and resume");controller.openApplication(mayor,application);check(windows.get(M).getItem(32)==null&&lore(mayor,4).contains("Одобрено"),"restored closed petition has no active approval action");
        var rows=Files.walk(owner.getDataFolder().toPath().getParent()).filter(p->p.getFileName().toString().endsWith(".tsv")).toList();boolean audit=false;for(var path:rows)if(Files.readString(path).contains("CITIZEN_APPLICATION"))audit=true;check(audit,"administrative audit records citizenship decisions in durable journal");finish();
    }
    void finish()throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; real Folia/Towny/Citizens service, native inventories and command, physical file delay, atomic status/decision, permissions, neutral feature disable and full restart/pause; explicit offline Player/InventoryView/EntityScheduler substitutes, no network client or bank payment claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
