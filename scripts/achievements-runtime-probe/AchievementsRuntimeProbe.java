package ru.neverland.runtime;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.townyachievements.api.TownyAchievementsApi;
import ru.neverland.townyachievements.service.*;
import ru.neverland.townyachievements.integration.CityMetrics;
import ru.neverland.townybuilds.api.TownyBuildsApi;
import ru.neverland.townypopulation.api.*;
import ru.neverland.townypopulation.service.PopulationService;
import ru.neverland.mintevents.api.MintTownyEventsApi;

/** Actual native services and inventories; provider observations controlled for deterministic thresholds. */
public final class AchievementsRuntimeProbe extends JavaPlugin {
    static final UUID T=UUID.nameUUIDFromBytes("achievements-city-039".getBytes()), M=UUID.nameUUIDFromBytes("achievements-mayor-039".getBytes()), R=UUID.nameUUIDFromBytes("achievements-resident-039".getBytes());
    Town town; World world; AchievementService achievements; PopulationService actualPopulation; int checks, population=999; long victories=9;
    boolean populationPaused; Map<String,Integer> levels=new HashMap<>(); Path proof; Actor mayor, resident;
    JavaPlugin plugin(String name){return (JavaPlugin)Objects.requireNonNull(Bukkit.getPluginManager().getPlugin(name));}
    void check(boolean value,String label)throws Exception{if(!value)throw new AssertionError(label);checks++;Files.writeString(proof.resolve("checks.txt"),label+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    interface Step{void run()throws Exception;}
    void rejected(Step step,String label)throws Exception{try{step.run();}catch(Exception expected){check(true,label);return;}throw new AssertionError(label);}
    @Override public void onEnable(){
        if(!Boolean.getBoolean("neverland.runtimeProbe")||!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RELIABILITY_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){Bukkit.getPluginManager().disablePlugin(this);return;}
        Bukkit.getScheduler().runTaskLater(this,()->guard(this::run),40);
    }
    void guard(Step action){try{action.run();}catch(Throwable ex){getLogger().log(java.util.logging.Level.SEVERE,"ACHIEVEMENTS PROBE FAILED",ex);try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("failed.txt"),ex.toString());}catch(Exception ignored){}Bukkit.shutdown();}}
    void run()throws Exception{
        proof=getDataFolder().toPath();Files.createDirectories(proof);world=Bukkit.getWorlds().getFirst();
        achievements=(AchievementService)Bukkit.getServicesManager().load(TownyAchievementsApi.class);
        actualPopulation=(PopulationService)Bukkit.getServicesManager().load(TownyPopulationApi.class);
        check(achievements!=null&&actualPopulation!=null&&plugin("NeverLandTownyControl").isEnabled(),"Achievements, Population and Control load together");
        if(Files.exists(proof.resolve("first-passed.txt"))){restart();return;}
        var universe=TownyUniverse.getInstance();universe.newTownInternal("AchievementCity",T);town=TownyAPI.getInstance().getTown(T);
        var m=universe.getDataSource().newResident("AchievementMayor",M);m.setTown(town);town.setMayor(m);m.save();
        var r=universe.getDataSource().newResident("AchievementResident",R);r.setTown(town);r.save();
        var block=new TownBlock(0,0,universe.getWorld(world.getName()));universe.addTownBlock(block);block.setTown(town);town.setHomeBlock(block);block.save();town.setSpawn(new Location(world,8,90,8));town.save();
        mayor=new Actor(M,"AchievementMayor");resident=new Actor(R,"AchievementResident");actualPopulation.refreshView();
        double before=actualPopulation.population(T).orElseThrow().capacity().happiness();installProviders();
        achievements.check(town);check(!achievements.unlocked(T,"population_1000")&&!achievements.unlocked(T,"raid_veterans"),"below thresholds cannot unlock");
        check(!new CityMetrics().observe(town,achievements.settings().definitions().get("first_million")).ready(),"unavailable Towny economy cannot invent a balance");
        population=1000;populationPaused=true;achievements.check(town);check(!achievements.unlocked(T,"population_1000"),"paused population cannot unlock");
        populationPaused=false;achievements.control(T,"pause");achievements.check(town);check(!achievements.unlocked(T,"population_1000"),"admin pause stops new unlocks");
        achievements.control(T,"resume");achievements.check(town);check(achievements.unlocked(T,"population_1000"),"population unlock at 1000");
        rejected(()->achievements.selectTitle(resident.player,"population_1000"),"ordinary resident cannot change city title");
        achievements.selectTitle(mayor.player,"population_1000");check(!achievements.title(T).isBlank(),"mayor selects earned title");
        achievements.selectCosmetic(resident.player,"population_1000");check(true,"resident may select unlocked personal cosmetic");
        rejected(()->achievements.selectTitle(mayor.player,"raid_veterans"),"mayor cannot select unearned title");
        victories=10;achievements.check(town);check(achievements.unlocked(T,"raid_veterans"),"raid provider count unlocks at ten");
        var buildings=achievements.settings().definitions().get("all_buildings_v").projects();for(String id:buildings)levels.put(id,5);
        levels.put(buildings.getFirst(),4);achievements.check(town);check(!achievements.unlocked(T,"all_buildings_v"),"89 of 90 buildings cannot satisfy full catalogue");
        levels.put(buildings.getFirst(),5);achievements.check(town);check(achievements.unlocked(T,"all_buildings_v"),"all ninety level V buildings unlock");
        levels.put("sun_pyramid",1);achievements.check(town);check(achievements.unlocked(T,"first_wonder")&&achievements.bonus(T,"happiness")==2,"first real wonder observation unlocks happiness");
        actualPopulation.refreshView();check(actualPopulation.population(T).orElseThrow().capacity().happiness()==before+2,"real Population consumes earned achievement happiness");
        var command=Bukkit.getPluginCommand("townyachievements");command.execute(mayor.player,"townyachievements",new String[0]);
        check(mayor.top.getSize()==54,"native achievements menu opens");
        var click=new InventoryClickEvent(mayor.view,InventoryType.SlotType.CONTAINER,0,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY);Bukkit.getPluginManager().callEvent(click);check(click.isCancelled(),"native achievement inventory blocks extraction");
        mayor.held=new ItemStack(Material.WHITE_BANNER,2);rejected(()->BannerRewards.apply(plugin("NeverLandTownyAchievements"),achievements,mayor.player,"population_1000"),"banner stack cannot be exchanged incorrectly");
        mayor.held=new ItemStack(Material.WHITE_BANNER);BannerRewards.apply(plugin("NeverLandTownyAchievements"),achievements,mayor.player,"population_1000");
        check(mayor.held.getAmount()==1&&mayor.held.hasItemMeta(),"one blank banner becomes one native decorated reward");
        rejected(()->BannerRewards.apply(plugin("NeverLandTownyAchievements"),achievements,mayor.player,"population_1000"),"decorated banner cannot be mistaken for a new blank");
        String saved=Files.readString(plugin("NeverLandTownyAchievements").getDataFolder().toPath().resolve("achievements-data.yml"));
        levels.clear();population=1;victories=0;achievements.check(town);achievements.control(T,"restart");
        check(Files.readString(plugin("NeverLandTownyAchievements").getDataFolder().toPath().resolve("achievements-data.yml")).equals(saved),"falling metrics and repeated admin checks cannot erase or duplicate earned state");
        r.removeTown();r.save();rejected(()->achievements.selectCosmetic(resident.player,"population_1000"),"former resident cannot use city rewards");r.setTown(town);r.save();
        var attachment=mayor.permissions.addAttachment(this,"neverlandtownycontrol.admin",true);mayor.permissions.recalculatePermissions();
        Bukkit.getPluginCommand("nltadmin").execute(mayor.player,"nltadmin",new String[]{"logs"});
        check(Arrays.stream(mayor.top.getContents()).filter(Objects::nonNull).anyMatch(i->i.hasItemMeta()&&ChatColor.stripColor(i.getItemMeta().getDisplayName()).contains("Достижения")),"admin log menu includes dedicated achievements category");
        var executor=Bukkit.getPluginCommand("townyachievements").getExecutor();
        var targets=(List<?>)executor.getClass().getMethod("adminMenuTargets",org.bukkit.command.CommandSender.class).invoke(executor,mayor.player);
        check(!targets.isEmpty(),"root admin permission reaches achievement activity protocol");
        achievements.control(T,"pause");check(achievements.bonus(T,"happiness")==2,"admin pause preserves earned bonus");
        Bukkit.getPluginManager().disablePlugin(plugin("NeverLandTownyAchievements"));
        check(!plugin("NeverLandTownyPopulation").isEnabled()||actualPopulation.population(T).isPresent(),"disable retains population data");
        rejected(()->ru.neverland.core.AchievementBonuses.happiness(T),"unavailable installed reward provider blocks dependent calculations");
        Bukkit.getPluginCommand("nltmodules").execute(Bukkit.getConsoleSender(),"nltmodules",new String[]{"enable","all"});
        Files.writeString(proof.resolve("first-passed.txt"),"PASS "+checks+" native assertions\n");Bukkit.shutdown();
    }
    void restart()throws Exception{
        town=TownyAPI.getInstance().getTown(T);check(town!=null,"Towny city survives another JVM");
        check(achievements.state(T).paused(),"administrator pause persists after restart");
        check(achievements.achievements(T).stream().filter(a->Boolean.TRUE.equals(a.get("earned"))).count()==4,"four earned achievements restored without provider replay");
        check(achievements.bonus(T,"happiness")==2&&!achievements.title(T).isBlank(),"title and happiness persist after restart");
        check(!achievements.unlocked(UUID.randomUUID(),"first_wonder"),"unrelated city UUID inherits no reward");
        actualPopulation.refreshView();check(ru.neverland.core.AchievementBonuses.happiness(T)==2,"public bonus bridge restored");
        Files.writeString(proof.resolve("restart-passed.txt"),"PASS "+checks+" restart assertions\n");Bukkit.shutdown();
    }
    static final Object UNHANDLED=new Object();
    <T> void provider(Class<T> type,InvocationHandler handler){
        Object original=Bukkit.getServicesManager().load(type);
        T proxy=type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->{
            if(m.getName().equals("apiVersion")||m.getName().equals("capabilities"))return m.invoke(original,a);
            Object result=handler.invoke(p,m,a);return result==UNHANDLED?m.invoke(original,a):result;
        }));Bukkit.getServicesManager().register(type,proxy,this,ServicePriority.Highest);
    }
    void installProviders(){
        provider(TownyBuildsApi.class,(p,m,a)->m.getName().equals("projectLevel")&&T.equals(a[0])?levels.getOrDefault(a[1],0):UNHANDLED);
        provider(TownyPopulationApi.class,(p,m,a)->m.getName().equals("population")&&T.equals(a[0])?Optional.of(new PopulationSnapshot(T,"AchievementCity",population,null,null,0,0,populationPaused,Map.of())):UNHANDLED);
        provider(MintTownyEventsApi.class,(p,m,a)->m.getName().equals("raidVictories")&&T.equals(a[0])?victories:UNHANDLED);
    }
    final class Actor {
        final Player player; PermissibleBase permissions; Inventory top = Bukkit.createInventory(null, 9), bottom = Bukkit.createInventory(null, 36); InventoryView view;
        ItemStack held = new ItemStack(Material.AIR); PlayerInventory inventory;
        Actor(UUID id, String name) {
            inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(), new Class<?>[]{PlayerInventory.class}, (p,m,a) -> switch(m.getName()) { case "getItemInMainHand" -> held; case "setItemInMainHand" -> { held = (ItemStack)a[0]; yield null; } default -> null; });
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p, m, a) -> switch (m.getName()) {
                case "getUniqueId" -> id; case "getName" -> name; case "getServer" -> Bukkit.getServer(); case "getWorld" -> world;
                case "getLocation" -> new Location(world, 8, 90, 8); case "isOnline" -> true; case "isOp", "isDead" -> false;
                case "hasPermission", "isPermissionSet", "addAttachment", "removeAttachment", "recalculatePermissions", "getEffectivePermissions" -> m.invoke(permissions, a);
                case "getInventory" -> inventory; case "getOpenInventory" -> view; case "openInventory" -> { top = (Inventory) a[0]; yield view; } case "closeInventory" -> { top = Bukkit.createInventory(null, 9); yield null; }
                case "hashCode" -> id.hashCode(); case "equals" -> p == a[0]; case "toString" -> "Achievement fixture actor"; default -> null;
            });
            permissions = new PermissibleBase(player);
            view = (InventoryView) Proxy.newProxyInstance(InventoryView.class.getClassLoader(), new Class<?>[]{InventoryView.class}, (p, m, a) -> switch (m.getName()) {
                case "getTopInventory" -> top; case "getBottomInventory" -> bottom; case "getPlayer" -> player; case "getType" -> InventoryType.CHEST;
                case "getCursor" -> new ItemStack(Material.AIR); case "countSlots" -> top.getSize() + 36; case "getTitle", "getOriginalTitle" -> "Fixture";
                case "getSlotType" -> InventoryType.SlotType.CONTAINER; case "convertSlot" -> a[0]; case "getInventory" -> (Integer) a[0] < top.getSize() ? top : bottom; default -> null;
            });
        }
    }
}
