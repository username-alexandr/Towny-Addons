package ru.neverland.runtime;
import java.nio.file.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import io.papermc.paper.threadedregions.scheduler.*;
import ru.neverland.core.*;
import ru.neverland.reputation.api.*;
import ru.neverland.reputation.service.*;
import ru.neverland.reputation.model.*;
import ru.neverland.reputation.gui.*;
import static ru.neverland.reputation.model.ReputationLedger.*;

/** Genuine profile service/API, native inventory cards and persisted receipt index; seeded historical ledger. */
public final class ReputationExplainProbe extends TntRuntimeProbe {
    JavaPlugin owner;ReputationService service;ProfileRepository repository;ReputationMenuManager menus;TownyReputationApi api;Player mayor;Inventory top;
    @SuppressWarnings("unchecked") Player actor(){
        var scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class<?>[]{EntityScheduler.class},(p,m,a)->switch(m.getName()){
            case "runDelayed"->Bukkit.getRegionScheduler().runDelayed(this,origin,(Consumer<ScheduledTask>)a[1],(Long)a[3]);case "run"->Bukkit.getRegionScheduler().run(this,origin,(Consumer<ScheduledTask>)a[1]);default->throw new UnsupportedOperationException(m.getName());});
        return(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
            case "getUniqueId"->MAYOR;case "getName"->"ReputationMayor";case "getServer"->Bukkit.getServer();case "getWorld"->world;case "getLocation"->origin;case "isOnline"->true;case "isOp"->false;case "getScheduler"->scheduler;
            case "hasPermission"->!Set.of("townyreputation.admin","townyreputation.bypass").contains(a[0]);case "sendMessage"->null;
            case "openInventory"->{top=(Inventory)a[0];yield null;}case "getOpenInventory"->Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(v,n,b)->n.getName().equals("getTopInventory")?top:null);
            case "hashCode"->MAYOR.hashCode();case "equals"->p==a[0];default->null;});
    }
    String lore(int slot){return String.join(" ",top.getItem(slot).getItemMeta().getLore());}
    Map<String,Object> report(int page){return api.explainProfile("TOWN",DEF,"TRADE",page);}
    @Override void setup()throws Exception{
        owner=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyReputation");service=(ReputationService)field(owner,"reputation");repository=(ProfileRepository)field(service.profiles(),"repository");menus=(ReputationMenuManager)field(owner.getCommand("reputation").getExecutor(),"menus");api=Bukkit.getServicesManager().load(TownyReputationApi.class);mayor=actor();check(owner.isEnabled()&&owner.getDescription().getVersion().equals("0.3.0")&&api.healthy(),"actual Reputation 0.3 profile and explanation API enabled");
        if(restart){resume();return;}var u=TownyUniverse.getInstance();u.newTownInternal("ExplanationCity",DEF);var town=TownyAPI.getInstance().getTown(DEF);var r=u.getDataSource().newResident("ReputationMayor",MAYOR);r.setTown(town);town.setMayor(r);r.save();var block=new TownBlock(0,0,u.getWorld(world.getName()));u.addTownBlock(block);block.setTown(town);town.setHomeBlock(block);block.save();town.setSpawn(origin);town.save();
        var subject=new Subject(ReputationScope.TOWN,DEF);var state=new State(Map.of(subject,new Profile(17,0,0,List.of())),Map.of(),Map.of());long now=System.currentTimeMillis();for(int i=0;i<80;i++){var type=Class.forName("ru.neverland.core.ReputationOutcome",true,owner.getClass().getClassLoader());var receipt=type.getConstructor(String.class,String.class,UUID.class,String.class,long.class,String.class).newInstance("supply:native:"+i,"TOWN",DEF,"SUPPLY_COMPLETED",now-(80-i)*ReputationExplanation.DAY,"Поставка по городскому обязательству "+i);state=((ReputationLedger.Result)ReputationLedger.class.getMethod("apply",State.class,type,ReputationAspect.class,int.class,int.class).invoke(null,state,receipt,ReputationAspect.TRADE,3,30)).state();}repository.commit(state);
        check(service.profiles().get(ReputationScope.TOWN,DEF).history().size()==60,"existing short history keeps its sixty-entry bound");var result=report(0);check(result.get("count").equals(80)&&result.get("evidence").equals(240L)&&result.get("initial").equals(0L),"actual API explains all eighty durable receipts beyond short history");
        var pages=report(Integer.MAX_VALUE);check(pages.get("page").equals(2)&&((List<?>)pages.get("rows")).size()==24,"native API safely clamps extreme requested page to earliest evidence");
        menus.openProfile(mayor,ReputationScope.TOWN,DEF,"ExplanationCity");check(top.getItem(20).getType()==Material.COMPARATOR&&top.getItem(22).getType()==Material.COMPARATOR&&top.getItem(24).getType()==Material.COMPARATOR,"native existing profile exposes explanation for all three aspects");
        byte[] before=Files.readAllBytes(owner.getDataFolder().toPath().resolve("profiles.yml"));menus.openExplanation(mayor,ReputationScope.TOWN,DEF,ReputationAspect.TRADE,0);check(lore(4).contains("Результатов: 80")&&top.getItem(50).getType()==Material.ARROW,"native explanation card shows full count and paginated evidence");
        check(lore(10).contains("Квитанция:")&&lore(10).contains("МСК")&&lore(10).contains("Давность:"),"actual evidence card retains source ID, Moscow date and age");
        menus.openExplanation(mayor,ReputationScope.TOWN,DEF,ReputationAspect.DIPLOMATIC,0);check(lore(4).contains("перенесённая часть: +17")&&lore(4).contains("Результатов: 0"),"legacy diplomatic baseline is not presented as a new verified obligation");
        owner.getCommand("reputation").execute(mayor,"rep",new String[]{"explain","town","ExplanationCity","trade"});check(((ReputationMenuHolder)top.getHolder()).type()==ReputationMenuHolder.Type.EXPLANATION,"actual explain command opens targeted receipt view");check(Arrays.equals(before,Files.readAllBytes(owner.getDataFolder().toPath().resolve("profiles.yml"))),"repeated explanation views and API reads never rewrite the profile ledger");
        String status=api.recordOutcome("TOWN",DEF,"MUNICIPAL_SUCCESS","municipal:native-new",now,"Новый выполненный городской договор");check(status.equals("APPLIED")&&report(0).get("count").equals(81)&&report(0).get("score").equals(243),"real durable outcome API invalidates cached explanation only after confirmed new receipt");
        check(api.recordOutcome("TOWN",DEF,"MUNICIPAL_SUCCESS","municipal:native-new",now,"Новый выполненный городской договор").equals("DUPLICATE")&&report(0).get("count").equals(81),"duplicate source receipt neither changes score nor duplicates explanation evidence");
        disable();finish();
    }
    void disable()throws Exception{var current=service.profiles().get(ReputationScope.TOWN,DEF);double fee=api.tradeFeeMultiplier(DEF);byte[] before=Files.readAllBytes(owner.getDataFolder().toPath().resolve("profiles.yml"));owner.getConfig().set("profiles.explanations.enabled",false);check(Boolean.FALSE.equals(report(0).get("available"))&&service.profiles().get(ReputationScope.TOWN,DEF).equals(current)&&api.tradeFeeMultiplier(DEF)==fee&&Arrays.equals(before,Files.readAllBytes(owner.getDataFolder().toPath().resolve("profiles.yml"))),"neutral explanation toggle preserves scores, trade fees, receipts and original profile services");owner.getConfig().set("profiles.explanations.enabled",true);}
    void resume()throws Exception{
        var p=service.profiles().get(ReputationScope.TOWN,DEF);check(p.trade()==243&&p.diplomatic()==17&&p.history().size()==60,"scores and original short history survive full native restart");var result=report(0);check(result.get("count").equals(81)&&result.get("evidence").equals(243L)&&result.get("consistent").equals(true),"full receipt index reconstructs identical exact rating after restart");
        @SuppressWarnings("unchecked")var rows=(List<Map<String,Object>>)result.get("rows");check(rows.getFirst().get("receipt").equals("municipal:native-new"),"new actual outcome retains source identity and chronological position after restart");
        menus.openExplanation(mayor,ReputationScope.TOWN,DEF,ReputationAspect.TRADE,2);check(top.getItem(49).getItemMeta().getDisplayName().contains("3 / 3")&&lore(4).contains("Результатов: 81"),"earliest receipt page remains reachable in restored native menu");
        disable();finish();
    }
    @Override void finish()throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; genuine Folia/Towny/Reputation services, native inventory cards and commands, receipt index, actual new durable outcome and duplicate checks, neutral read-only toggle and full restart; explicit offline player/scheduler/view and historical receipt fixtures; no GUI client, bank operation or time-driven score decay claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
