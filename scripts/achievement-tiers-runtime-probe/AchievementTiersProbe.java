package ru.neverland.runtime;

import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.event.inventory.*;
import org.bukkit.plugin.ServicePriority;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.core.*;
import ru.neverland.townyachievements.*;
import ru.neverland.townyachievements.api.*;
import ru.neverland.townyachievements.service.*;
import ru.neverland.townyachievements.data.*;
import ru.neverland.townyachievements.gui.*;
import ru.neverland.townyachievements.integration.*;
import ru.neverland.townyachievements.model.*;
import ru.neverland.townybuilds.api.*;
import ru.neverland.townybuilds.data.DataStore;

/** Actual API/metrics/storage and menus; completed building levels are deliberately seeded fixtures. */
public final class AchievementTiersProbe extends AdmissionsProbe {
    NeverLandTownyAchievements plugin; AchievementService service; AchievementMenu menu; AchievementRepository repository;
    DataStore builds; TownyBuildsApi buildApi; org.bukkit.plugin.Plugin buildPlugin; Path data;
    @Override void setup() throws Exception {
        plugin=(NeverLandTownyAchievements)Bukkit.getPluginManager().getPlugin("NeverLandTownyAchievements");
        service=(AchievementService)Bukkit.getServicesManager().load(TownyAchievementsApi.class);
        menu=(AchievementMenu)field(plugin,"menu"); repository=(AchievementRepository)field(service,"repository");
        buildPlugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");builds=(DataStore)field(buildPlugin,"dataStore");buildApi=Bukkit.getServicesManager().load(TownyBuildsApi.class);
        data=plugin.getDataFolder().toPath().resolve("achievements-data.yml");mayor=actor(M,"TierMayor");
        check(service!=null&&plugin.isEnabled()&&service.settings().definitions().size()==23,"real startup adds eighteen goals to untouched legacy five-goal config");
        check(repository.get(T).progress().get("first_million").earnedAt()==123456&&repository.get(T).progress().get("first_wonder").earnedAt()==123456,"legacy earned timestamps load unchanged");
        if(!restart)check(repository.get(T).title().equals("first_million")&&repository.cosmetics().get(M).achievement().equals("first_million"),"legacy title and personal cosmetic remain selected");
        if(restart){resumed();return;}
        var u=TownyUniverse.getInstance();u.newTownInternal("TierCity",T);town=TownyAPI.getInstance().getTown(T);var resident=u.getDataSource().newResident("TierMayor",M);resident.setTown(town);town.setMayor(resident);resident.save();var claim=new TownBlock(0,0,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);town.setHomeBlock(claim);claim.save();town.setSpawn(origin);town.save();
        service.check(town);check(service.bonus(T,"happiness")==2,"legacy wonder retains exact happiness without new emissions");
        var goal=service.settings().definitions().get("trade_tier_1");var metrics=new CityMetrics();
        builds.town(T).setLevel("market",1);builds.town(T).setLevel("warehouse",1);service.check(town);
        check(!service.unlocked(T,"trade_tier_1")&&repository.get(T).progress().get("trade_tier_1").best()==2,"real Builds API observes two of three required buildings");
        builds.town(T).setLevel("market",0);builds.town(T).setLevel("caravanserai",1);service.check(town);
        check(!service.unlocked(T,"trade_tier_1"),"different incomplete real snapshots cannot accumulate into three buildings");
        var partial=repository.get(T).progress().get("trade_tier_1");SuiteState.freeze(buildPlugin.getName());
        try { check(!metrics.observe(town,goal).ready(),"frozen provider is unknown rather than a zero observation");service.check(town);check(repository.get(T).progress().get("trade_tier_1").equals(partial),"unknown observation preserves partial progress"); } finally { SuiteState.thaw(buildPlugin.getName()); }
        Bukkit.getServicesManager().unregister(TownyBuildsApi.class,buildApi);
        try { check(!metrics.observe(town,goal).ready(),"missing registered API also yields unknown");service.check(town);check(repository.get(T).progress().get("trade_tier_1").equals(partial),"missing API cannot alter partial definition or timestamp"); } finally { Bukkit.getServicesManager().register(TownyBuildsApi.class,buildApi,buildPlugin,ServicePriority.Normal); }
        for(var track:Achievement.Track.values())if(track!=Achievement.Track.COMMON){
            for(int tier=1;tier<=3;tier++){
                String id=track.name().toLowerCase(Locale.ROOT)+"_tier_"+tier;var a=service.settings().definitions().get(id);
                for(String project:a.projects())builds.town(T).setLevel(project,a.minimumLevel());service.check(town);
                check(service.unlocked(T,id),"actual projectLevel unlocks "+id+" at "+a.minimumLevel());
                if(tier<3)check(!service.unlocked(T,track.name().toLowerCase(Locale.ROOT)+"_tier_"+(tier+1)),"next tier waits for harder levels in "+track);
            }
        }
        check(service.bonus(T,"happiness")==2,"all twelve profile tiers together add no happiness inflation");
        Bukkit.getPluginCommand("townyachievements").execute(mayor,"townyachievements",new String[]{"track","trade"});
        check(windows.get(M).getSize()==54&&lore(mayor,0).contains("Ступень I")&&windows.get(M).getItem(3)==null,"actual category command shows three native trade icons with tier labels");
        check(windows.get(M).getItem(37).getItemMeta().getDisplayName().contains("Общие")&&windows.get(M).getItem(41).getItemMeta().getDisplayName().contains("Культура"),"all readable categories are separate menu buttons");
        var click=new InventoryClickEvent(view(mayor,M),InventoryType.SlotType.CONTAINER,0,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY);menu.click(click);
        check(click.isCancelled()&&lore(mayor,13).contains("одновременно"),"native click prevents extraction and explains simultaneous building requirement");
        service.selectTitle(mayor,"trade_tier_3");service.selectCosmetic(mayor,"trade_tier_3");
        check(repository.get(T).title().equals("trade_tier_3")&&repository.cosmetics().get(M).achievement().equals("trade_tier_3"),"new title and one personal cosmetic replace prior choices");
        check(service.achievements(T).stream().anyMatch(row->row.get("id").equals("trade_tier_3")&&row.get("track").equals("TRADE")&&row.get("tier").equals(3)),"registered API exposes backward compatible tier metadata");
        byte[] before=Files.readAllBytes(data);for(var a:service.settings().definitions().values())for(String project:a.projects())builds.town(T).setLevel(project,0);service.check(town);service.control(T,"restart");
        check(Arrays.equals(before,Files.readAllBytes(data)),"lower real building levels and repeated checks neither erase nor re-earn rewards");
        var config=plugin.getDataFolder().toPath().resolve("achievements.yml");var y=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(config.toFile());var s=y.createSection("achievements.trade_tier_3");ru.neverland.townyachievements.config.AchievementSettings.write(s,service.settings().definitions().get("trade_tier_3"));s.set("reward.title","Новая награда только будущим городам");y.save(config.toFile());plugin.reloadAchievements();service.check(town);
        check(Arrays.equals(before,Files.readAllBytes(data))&&!service.title(T).contains("Новая награда"),"real reload cannot rewrite saved tier reward or earned timestamp");
        service.control(T,"pause");builds.markDirty();builds.save();Files.write(proof.resolve("expected-data.yml"),Files.readAllBytes(data));finishTier();
    }
    void resumed()throws Exception{
        town=TownyAPI.getInstance().getTown(T);check(town!=null&&service.state(T).paused(),"Towny city and administrative pause survive separate JVM");
        check(Arrays.equals(Files.readAllBytes(data),Files.readAllBytes(proof.resolve("expected-data.yml"))),"full restart retains exact earned data without provider replay");
        check(repository.get(T).progress().values().stream().filter(Progress::earned).count()==14,"twelve profile tiers and two legacy rewards restore exactly once");
        check(repository.get(T).title().equals("trade_tier_3")&&repository.cosmetics().get(M).achievement().equals("trade_tier_3"),"selected profile title and cosmetic persist through restart");
        check(service.bonus(T,"happiness")==2&&!service.title(T).contains("Новая награда"),"old reward snapshot and happiness survive edited catalogue restart");
        service.control(T,"resume");service.check(town);check(service.unlocked(T,"trade_tier_3")&&buildApi.projectLevel(T,"market")==0,"resuming with zero current building level keeps earned highest tier");
        check(!service.unlocked(UUID.randomUUID(),"trade_tier_3"),"unrelated city receives no inherited reward");finishTier();
    }
    void finishTier()throws Exception {TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve((restart?"restart":"first")+"-passed.txt"),"PASS "+checks+" assertions; actual Folia/Towny/Builds/Achievements services, legacy schema-1, real level API, missing/frozen provider, native command/inventory, reload and two JVM boots. Building completion levels are explicitly seeded; Player/InventoryView/scheduler are offline substitutes. No player construction, bank payment or client rendering claimed.\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());}
}
