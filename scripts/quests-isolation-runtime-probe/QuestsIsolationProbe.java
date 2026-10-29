package ru.neverland.runtime;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.core.*;
import ru.neverland.townyquests.*;
import ru.neverland.townyquests.service.*;
import ru.neverland.townyquests.data.*;
import ru.neverland.townyquests.model.*;
import ru.neverland.townypolicies.service.*;

/** Real startup failure, control disable event, persisted completed-project fixture and real policy selection. */
public final class QuestsIsolationProbe extends AdmissionsProbe {
    List<String> others=List.of("Citizens","Control","Builds","Districts","Population","Resources","Upkeep","Policies");
    org.bukkit.plugin.Plugin questPlugin;QuestService quests;PoliciesService policies;String phase;Path questConfig,questData;byte[] policyBefore;
    void in(Work work){try{work.run();}catch(Throwable e){fail(e);}}
    @Override void check(boolean good,String why)throws Exception{if(!good)throw new AssertionError(why);checks++;Files.writeString(proof.resolve(phase+"-checks.txt"),"PASS "+why+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+why);}
    @Override void setup()throws Exception{
        phase=!Files.exists(proof.resolve("first-passed.txt"))?"first":!Files.exists(proof.resolve("broken-passed.txt"))?"broken":"restart";
        var expected=proof.resolve("expected-versions.yml");
        if(Files.exists(expected)){
            var versions=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(expected.toFile());
            others=versions.getKeys(false).stream().filter(name->!name.equals("NeverLandTownyQuests")).map(name->name.substring("NeverLandTowny".length())).sorted().toList();
            for(String name:versions.getKeys(false)){var plugin=Bukkit.getPluginManager().getPlugin(name);check(plugin!=null&&plugin.getDescription().getVersion().equals(versions.getString(name)),"exact installed version "+name+" "+versions.getString(name));}
        }
        questPlugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyQuests");quests=questPlugin.isEnabled()?(QuestService)field(questPlugin,"service"):null;policies=(PoliciesService)Bukkit.getServicesManager().load(ru.neverland.townypolicies.api.TownyPoliciesApi.class);questConfig=questPlugin.getDataFolder().toPath().resolve("quests.yml");questData=questPlugin.getDataFolder().toPath().resolve("quests-data.yml");mayor=actor(M,"QuestsIsolationMayor");
        for(String name:others)check(Bukkit.getPluginManager().isPluginEnabled("NeverLandTowny"+name)&&!SuiteState.frozen("NeverLandTowny"+name),"native "+phase+" keeps "+name+" enabled and unfrozen");
        if(phase.equals("broken")){broken();return;}if(phase.equals("restart")){resumed();return;}
        check(questPlugin.isEnabled()&&quests.settings().projects().size()==4&&quests.settings().projects().get("sanitation").name().equals("Старый городской водопровод"),"legacy two-project config inherits both new branches without resetting custom name");Files.write(proof.resolve("legacy.yml"),Files.readAllBytes(questConfig));
        var u=TownyUniverse.getInstance();u.newTownInternal("QuestsIsolationCity",T);town=TownyAPI.getInstance().getTown(T);var r=u.getDataSource().newResident("QuestsIsolationMayor",M);r.setTown(town);town.setMayor(r);r.save();var claim=new TownBlock(0,0,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);town.setHomeBlock(claim);claim.save();town.setSpawn(origin);town.save();
        var repo=(QuestRepository)field(quests,"repository");var def=quests.settings().projects().get("sanitation");repo.put(T,new CityProject(UUID.randomUUID(),def,def.stages().size(),0,CityProject.Status.COMPLETED));check(quests.unlocked(T,"sanitation_reform"),"explicit completed-project fixture provides real public unlock");
        policies.refresh();await(policies.choose(mayor,T,"sanitation","sanitary",0,policies.settings(),false),saved->in(()->{check(policies.effect(T,"happiness")==5&&Math.abs(policies.upkeepMultiplier(T,"baths")-1.1)<1e-8,"actual acknowledged policy selection applies earned benefits and costs");policyBefore=Files.readAllBytes(Bukkit.getPluginManager().getPlugin("NeverLandTownyPolicies").getDataFolder().toPath().resolve("policies-data.yml"));Files.write(proof.resolve("policy-confirmed.yml"),policyBefore);Bukkit.getGlobalRegionScheduler().run(this,task->{Bukkit.getPluginManager().disablePlugin(questPlugin);later(8,this::disabled);});}));
    }
    void disabled()throws Exception{
        check(!questPlugin.isEnabled(),"real unexpected plugin disable event fired for Quests");for(String name:others)check(Bukkit.getPluginManager().isPluginEnabled("NeverLandTowny"+name)&&!SuiteState.frozen("NeverLandTowny"+name),"Quests disable does not cascade into "+name);
        var state=ModulePauseStore.load(ModulePauseStore.file(questPlugin.getDataFolder().toPath().getParent()));check(state.requests().equals(Set.of("NeverLandTownyQuests"))&&state.modules().entrySet().stream().filter(e->e.getValue().disabled()).allMatch(e->e.getKey().equals("NeverLandTownyQuests")),"durable quarantine contains only Quests, not 33 unrelated modules");
        check(policies.effect(T,"happiness")==5&&Math.abs(policies.upkeepMultiplier(T,"baths")-1.1)<1e-8,"accepted sanitation benefits and costs remain unchanged while quest provider is down");var group=policies.settings().groups().get("sanitation");check(!policies.unavailable(T,group,group.options().get("public_health")).isEmpty(),"new quest-gated policy selection stays unavailable");check(Arrays.equals(policyBefore,Files.readAllBytes(Bukkit.getPluginManager().getPlugin("NeverLandTownyPolicies").getDataFolder().toPath().resolve("policies-data.yml"))),"provider failure never rewrites accepted policy choice or history");
        Files.copy(questData,proof.resolve("quest-confirmed.yml"));var bad=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(questConfig.toFile());bad.set("projects.sanitation.stages.aqueduct.type","BROKEN_KIND");bad.save(questConfig.toFile());enableOnlyQuests();finishPhase();
    }
    void broken()throws Exception{
        town=TownyAPI.getInstance().getTown(T);check(!questPlugin.isEnabled()&&quests==null,"malformed explicit stage type really fails Quests startup before service registration");var state=ModulePauseStore.load(ModulePauseStore.file(questPlugin.getDataFolder().toPath().getParent()));check(state.modules().entrySet().stream().filter(e->e.getValue().disabled()).allMatch(e->e.getKey().equals("NeverLandTownyQuests")),"startup failure persists no unrelated protective pause");policies.refresh();check(policies.effect(T,"happiness")==5&&policies.state(T).revision()==1,"confirmed policy survives full restart with failed Quests provider");check(Arrays.equals(Files.readAllBytes(proof.resolve("quest-confirmed.yml")),Files.readAllBytes(questData)),"failed configuration load leaves completed projects untouched");check(Files.readString(questConfig).contains("BROKEN_KIND"),"invalid administrator value is rejected without silently overwriting file");Files.write(questConfig,Files.readAllBytes(proof.resolve("legacy.yml")));enableOnlyQuests();finishPhase();
    }
    void resumed()throws Exception{town=TownyAPI.getInstance().getTown(T);check(questPlugin.isEnabled()&&quests.settings().projects().size()==4&&quests.unlocked(T,"sanitation_reform"),"targeted enable and repaired legacy file restore Quests with preserved completion");policies.refresh();check(policies.effect(T,"happiness")==5&&policies.state(T).revision()==1,"restored provider does not duplicate policy history or rewards");check(Arrays.equals(Files.readAllBytes(proof.resolve("quest-confirmed.yml")),Files.readAllBytes(questData)),"all three JVM boots retain exact completed quest data");var state=ModulePauseStore.load(ModulePauseStore.file(questPlugin.getDataFolder().toPath().getParent()));check(state.requests().isEmpty()&&state.modules().values().stream().noneMatch(ModulePauseStore.Entry::disabled),"targeted recovery clears only Quests pause without enable all");finishPhase();}
    void enableOnlyQuests(){Bukkit.getPluginCommand("nltmodules").execute(Bukkit.getConsoleSender(),"nltmodules",new String[]{"enable","Quests"});}
    void finishPhase()throws Exception{TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve(phase+"-passed.txt"),"PASS "+checks+" assertions; actual Folia Towny Quests and "+others.size()+" other addon instances; inherited legacy defaults, real plugin disable, real malformed-config startup failure, durable quarantine, selected policy continuity and repaired restart; completed quest is explicit fixture, mayor is offline Player substitute; Towny economy disabled, no bank transfer or client claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
