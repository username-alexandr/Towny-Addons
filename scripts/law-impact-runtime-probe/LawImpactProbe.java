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
import ru.neverland.governance.api.*;
import ru.neverland.governance.service.*;
import ru.neverland.governance.model.*;
import ru.neverland.governance.gui.*;

/** Native Towny laws, actual inventories and their server-owned actions; explicit offline actor. */
public final class LawImpactProbe extends TntRuntimeProbe {
    JavaPlugin owner;GovernanceService service;GovernanceRepository repository;GovernanceMenuManager menu;Town town;Player mayor;Inventory top;final List<String> messages=new ArrayList<>();
    @SuppressWarnings("unchecked") Player actor(UUID id,String name){
        var scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class<?>[]{EntityScheduler.class},(p,m,a)->switch(m.getName()){
            case "runDelayed"->Bukkit.getRegionScheduler().runDelayed(this,origin,(Consumer<ScheduledTask>)a[1],(Long)a[3]);
            case "run"->Bukkit.getRegionScheduler().run(this,origin,(Consumer<ScheduledTask>)a[1]);default->throw new UnsupportedOperationException(m.getName());});
        return(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "getName"->name;case "getServer"->Bukkit.getServer();case "getWorld"->world;case "getLocation"->origin;case "isOnline"->true;case "isOp"->false;case "getScheduler"->scheduler;
            case "hasPermission"->!Set.of("townygovernance.bypass","townygovernance.admin").contains(a[0]);
            case "sendMessage"->{if(a[0] instanceof String s)messages.add(s);yield null;}
            case "openInventory"->{top=(Inventory)a[0];yield null;}
            case "getOpenInventory"->Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(v,n,b)->n.getName().equals("getTopInventory")?top:null);
            case "hashCode"->id.hashCode();case "equals"->p==a[0];default->null;});
    }
    String lore(int slot){return String.join(" ",top.getItem(slot).getItemMeta().getLore());}
    Consumer<Player> action(int slot){var action=((GovernanceMenuHolder)top.getHolder()).takeAction(slot);if(action==null)throw new AssertionError("No menu action "+slot);return action;}
    @Override void setup()throws Exception{
        owner=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyGovernance");service=(GovernanceService)field(owner,"governance");repository=(GovernanceRepository)field(owner,"repository");menu=(GovernanceMenuManager)field(owner.getCommand("governance").getExecutor(),"menus");
        check(owner.isEnabled()&&owner.getDescription().getVersion().equals("0.2.0"),"actual Governance 0.2 is enabled on Folia");
        if(!restart){var u=TownyUniverse.getInstance();u.newTownInternal("LawCity",DEF);town=TownyAPI.getInstance().getTown(DEF);var r=u.getDataSource().newResident("LawMayor",MAYOR);r.setTown(town);town.setMayor(r);r.save();var c=new TownBlock(0,0,u.getWorld(world.getName()));u.addTownBlock(c);c.setTown(town);town.setHomeBlock(c);c.save();town.setSpawn(origin);town.setTaxPercentage(false);town.setTaxes(23);town.setBonusBlocks(7);town.save();}else town=TownyAPI.getInstance().getTown(DEF);
        mayor=actor(MAYOR,"LawMayor");if(restart){resume();return;}
        var registry=(DefinitionRegistry)field(owner,"definitions");var file=owner.getDataFolder().toPath().resolve("laws.yml");var original=Files.readString(file);var prior=service.law("construction_subsidies");
        try{var bad=new org.bukkit.configuration.file.YamlConfiguration();bad.loadFromString(original);bad.set("laws.construction_subsidies.effects.construction-cost-multiplier","wrong");Files.writeString(file,bad.saveToString());boolean refused=false;try{registry.reload();}catch(IllegalArgumentException expected){refused=true;}check(refused&&service.law("construction_subsidies").equals(prior),"invalid numeric definition cannot replace the previous law catalog");
            Files.writeString(file,"laws: [broken");refused=false;try{registry.reload();}catch(RuntimeException expected){refused=true;}check(refused&&service.law("construction_subsidies").equals(prior),"malformed YAML cannot silently erase existing law definitions");
        }finally{Files.writeString(file,original);registry.reload();}
        menu.openLaw(mayor,"construction_subsidies");check(top.getItem(22).getType()==Material.COMPARATOR&&lore(22).contains("100% → 85%")&&lore(22).contains("7 → 12"),"law review compares complete construction multiplier and city claim adjustment");
        check(service.open(town).isEmpty()&&town.getBonusBlocks()==7&&service.constructionCost(DEF)==1,"opening review creates no proposal or effects");
        owner.getCommand("governance").execute(mayor,"governance",new String[]{"preview","construction_subsidies"});check(messages.stream().anyMatch(s->s.contains("100% → 85%")),"actual read-only preview command uses identical human impact");
        var stale=action(29);town.setTaxes(24);stale.accept(mayor);check(service.open(town).isEmpty(),"city tax change invalidates old propose action even for a construction law");town.setTaxes(23);
        menu.openLaw(mayor,"construction_subsidies");action(29).accept(mayor);check(service.open(town).size()==1&&((GovernanceMenuHolder)top.getHolder()).type()==GovernanceMenuHolder.Type.VOTE_DETAIL,"fresh review creates one actual proposal and opens voting");
        var proposal=service.open(town).getFirst();check(lore(22).contains("100% → 85%")&&proposal.voteOf(MAYOR)==VoteChoice.YES,"voters see current complete impact before answers");
        var staleVote=action(31);town.setBonusBlocks(8);staleVote.accept(mayor);check(proposal.voteOf(MAYOR)==VoteChoice.YES,"changed effects require a refreshed review before a queued vote is cast");
        action(31).accept(mayor);check(proposal.voteOf(MAYOR)==VoteChoice.NO,"freshly reviewed vote action records intended answer");town.setBonusBlocks(7);service.vote(mayor,proposal.id().toString(),VoteChoice.YES);
        var expected=service.impact(town,service.law("construction_subsidies"),ProposalAction.ENACT);service.resolveNow(proposal.id().toString());check(town.getBonusBlocks()==expected.after().bonusBlocks()&&service.constructionCost(DEF)==expected.proposed().construction(),"actual accepted vote applies the same projected effects");
        var api=Bukkit.getServicesManager().load(TownyGovernanceApi.class);var before=api.lawImpact(DEF,"urban_expansion","ENACT");check(((Number)before.get("constructionBefore")).doubleValue()==.85&&((Number)before.get("constructionAfter")).doubleValue()==1.1,"actual public API exposes full replacement estimate");
        var replacement=service.propose(mayor,"urban_expansion",ProposalAction.ENACT).proposal();service.resolveNow(replacement.id().toString());check(town.getBonusBlocks()==22&&service.constructionCost(DEF)==1.1&&!service.hasLaw(DEF,"construction_subsidies"),"replacement removes old subsidy rather than stacking its cost and claims");
        var taxes=service.impact(town,service.law("low_taxes"),ProposalAction.ENACT);service.enact(town,service.law("low_taxes"),MAYOR,"LawMayor");check(town.getTaxes()==taxes.after().tax()&&service.data(town).baselineTax()==23,"actual Towny tax setter and stored baseline match preview");
        var release=service.impact(town,service.law("tax_release"),ProposalAction.ENACT);service.enact(town,service.law("tax_release"),MAYOR,"LawMayor");check(town.getTaxes()==release.after().tax()&&town.getTaxes()==23&&service.data(town).baselineTax()==null&&!service.hasLaw(DEF,"low_taxes"),"non-tax law replacement restores exact original Towny tax");
        var open=service.propose(mayor,"open_society",ProposalAction.ENACT).proposal();service.vote(mayor,open.id().toString(),VoteChoice.ABSTAIN);Files.writeString(proof.resolve("proposal.txt"),open.id()+"\n"+open.endsAt());
        check(service.data(town).history().stream().anyMatch(h->h.type().equals("ENACT")),"actual law history retains enactments");repository.save();finish();
    }
    void resume()throws Exception{
        check(service.hasLaw(DEF,"urban_expansion")&&service.hasLaw(DEF,"tax_release")&&service.constructionCost(DEF)==1.1&&town.getBonusBlocks()==22,"accepted replacement effects survive full Folia restart");
        check(town.getTaxes()==23&&!town.isTaxPercentage()&&service.data(town).baselineTax()==null,"restored Towny rate and cleared law baseline survive restart");
        var saved=Files.readString(proof.resolve("proposal.txt")).split("\n");var open=service.find(town,saved[0]);check(open!=null&&open.endsAt()==Long.parseLong(saved[1])&&open.voteOf(MAYOR)==VoteChoice.ABSTAIN,"unresolved voting identity, deadline and votes remain unchanged");
        menu.openVote(mayor,open.id().toString());check(top.getItem(22).getType()==Material.COMPARATOR&&lore(22).contains("100% → 125%"),"restored unfinished vote offers exact current ideology experience comparison");
        check(service.data(town).history().stream().anyMatch(h->h.type().equals("ENACT")),"law decision history survives restart");finish();
    }
    @Override void finish()throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; actual Folia/Towny/Governance laws, commands, inventory review actions, vote resolution, exact tax/claims/modifier effects and full restart; explicit offline player/scheduler/view proxies; no GUI client, collection of tax or bank payout claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
