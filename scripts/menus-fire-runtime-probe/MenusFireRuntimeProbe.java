package ru.neverland.runtime;

import com.mojang.authlib.GameProfile;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintevents.service.EventService;
import ru.neverland.mintevents.service.FireService;
import ru.neverland.mintevents.service.FireMaterials;
import ru.neverland.mintevents.gui.EventMenuManager;
import ru.neverland.townybuilds.construction.*;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.integration.FireRepairAccess;
import ru.neverland.townytreasury.gui.TreasuryMenu;
import ru.neverland.townytreasury.model.Week;

/** Real Bukkit inventories/world/player data; the absent client's open view/messages are intercepted. */
public final class MenusFireRuntimeProbe extends JavaPlugin {
    private static final UUID TOWN=UUID.nameUUIDFromBytes("menus-fire-town".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final UUID ACTOR=UUID.nameUUIDFromBytes("menus-fire-actor".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private Path proof;
    private int checks;
    private World world;
    private Town town;
    private Player player,nativePlayer;
    private Inventory top;
    private InventoryView view;
    private final Map<String,Boolean> permissions=new HashMap<>();
    private final List<String> messages=new ArrayList<>();
    private EventService events;
    private FireService fires;
    private ConstructionService construction;
    private DataStore data;
    private Block damaged;
    private String original;
    private JavaPlugin plugin(String suffix){return (JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTowny"+suffix);}
    private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void invokePrivate(Object target,String name)throws Exception{Method m=target.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(target);}
    private void check(boolean good,String message)throws Exception{if(!good)throw new AssertionError(message);checks++;getLogger().info("CHECK "+message);Files.writeString(proof.resolve("checks.log"),message+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    private static String plain(Component text){return PlainTextComponentSerializer.plainText().serialize(text==null?Component.empty():text);}
    private void fails(Throwing work,String reason)throws Exception{try{work.run();}catch(Exception expected){check(true,reason);return;}throw new AssertionError(reason);}
    private interface Throwing{void run()throws Exception;}

    @Override public void onEnable(){
        if(!Boolean.getBoolean("neverland.runtimeProbe")||!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RELIABILITY_PROBE"))||!"127.0.0.1".equals(getServer().getIp())){getServer().getPluginManager().disablePlugin(this);return;}
        getServer().getScheduler().runTaskLater(this,this::run,100L);
    }
    private void run(){try{
        proof=getDataFolder().toPath();Files.createDirectories(proof);
        world=Bukkit.getWorlds().getFirst();
        check(Arrays.stream(Bukkit.getPluginManager().getPlugins()).filter(p->p.getName().startsWith("NeverLandTowny")&&p.isEnabled()).count()==27,"all 27 release plugins enabled");
        events=(EventService)field(plugin("Events"),"events");fires=events.fires();
        construction=(ConstructionService)field(plugin("Builds"),"construction");data=(DataStore)field(plugin("Builds"),"dataStore");
        metadata();actor();
        if(!Files.exists(proof.resolve("restart-ready"))){
            setup();menus();firstFire();
            world.save();nativePlayer.saveData();data.save();TownyUniverse.getInstance().getDataSource().saveAll();
            Files.writeString(proof.resolve("first-passed.txt"),"PASS "+checks+" assertions\n");Files.writeString(proof.resolve("restart-ready"),"ready\n");
            getLogger().info("MENUS_FIRE_FIRST_PASS "+checks);Bukkit.getScheduler().runTaskLater(this,()->Runtime.getRuntime().halt(23),40L);
        }else{
            town=TownyAPI.getInstance().getTown(TOWN);check(town!=null,"town persisted");restartFire();
            Files.writeString(proof.resolve("restart-passed.txt"),"PASS "+checks+" assertions\n");
            getLogger().info("MENUS_FIRE_RESTART_PASS "+checks);Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,20L);
        }
    }catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"MENUS_FIRE_FAIL",error);try{var s=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(s));Files.writeString(proof.resolve("failed.txt"),s.toString());}catch(Exception ignored){}Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,20L);}}
    private void metadata()throws Exception{
        int count=0;for(Class<?> contract:Bukkit.getServicesManager().getKnownServices())if(contract.getName().startsWith("ru.neverland.")){
            Object provider=Bukkit.getServicesManager().load(contract);if(provider==null)continue;
            var expected=new TreeSet<String>();for(Method m:contract.getMethods())if(!Modifier.isStatic(m.getModifiers())&&m.getDeclaringClass()!=Object.class&&!m.getDeclaringClass().getName().equals("ru.neverland.core.ApiContract")&&!Set.of("apiVersion","capabilities").contains(m.getName()))expected.add(m.getName());
            check(expected.equals(contract.getMethod("capabilities").invoke(provider)),"API "+contract.getSimpleName());count++;
        }check(count==28,"28 public contracts registered");
    }
    private void actor(){
        ServerPlayer handle=new ServerPlayer(((CraftServer)Bukkit.getServer()).getServer(),((CraftWorld)world).getHandle(),new GameProfile(ACTOR,"MenusFireActor"),ClientInformation.createDefault());
        handle.absSnapTo(1620,92,1620,0,0);nativePlayer=handle.getBukkitEntity();nativePlayer.loadData();
        top=Bukkit.createInventory(null,9,Component.text("Fixture base"));
        player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->{
            switch(m.getName()){
                case "isOnline":return true;
                case "sendMessage":if(a!=null&&a.length>0)messages.add(String.valueOf(a[a.length-1]));return null;
                case "hasPermission":return permissions.getOrDefault(String.valueOf(a[0]),true);
                case "isOp":return false;
                case "getOpenInventory":return view;
                case "openInventory":top=(Inventory)a[0];return view;
                case "closeInventory":top=Bukkit.createInventory(null,9,Component.text("Fixture base"));return null;
                case "performCommand":command(String.valueOf(a[0]).replaceFirst("^(?:town|t) ",""));return true;
                case "spawnParticle":return null;
            }
            try{return m.invoke(nativePlayer,a);}catch(InvocationTargetException e){throw e.getCause();}
        });
        view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(p,m,a)->switch(m.getName()){
            case "getTopInventory"->top;case "getBottomInventory"->nativePlayer.getInventory();case "getPlayer"->player;
            case "getType"->InventoryType.CHEST;case "getCursor"->new ItemStack(Material.AIR);case "countSlots"->top.getSize()+36;
            case "getTitle","getOriginalTitle"->"Fixture view";case "title"->Component.text("Fixture view");
            case "getSlotType"->InventoryType.SlotType.CONTAINER;case "convertSlot"->a[0];
            case "getInventory"->((Integer)a[0])<top.getSize()?top:nativePlayer.getInventory();
            default->null;
        });
    }
    private void setup()throws Exception{
        var u=TownyUniverse.getInstance();u.newTownInternal("MenusFireTown",TOWN);town=TownyAPI.getInstance().getTown(TOWN);
        Resident resident=u.getDataSource().newResident("MenusFireActor",ACTOR);resident.setTown(town);town.setMayor(resident);
        for(int x=100;x<=103;x++)for(int z=100;z<=103;z++){
            TownBlock claim=new TownBlock(x,z,u.getWorld(world.getName()));u.addTownBlock(claim);claim.setTown(town);if(x==100&&z==100)town.setHomeBlock(claim);claim.save();world.getChunkAt(x,z).load();
        }
        town.setSpawn(new Location(world,1620,92,1620));resident.save();town.save();town.getAccount().deposit(10000,"Disposable GUI fixture");
        for (String suffix : List.of("Policies","Population","Power","Resources","Research","Specialization","Upkeep","TreasuryPlus")) {
            Object service=null;String expectedName=(suffix.equals("TreasuryPlus")?"Treasury":suffix)+"Service";
            for(Class<?> contract:Bukkit.getServicesManager().getKnownServices()){
                Object candidate=Bukkit.getServicesManager().load(contract);
                if(candidate!=null&&candidate.getClass().getSimpleName().equals(expectedName)){service=candidate;break;}
            }
            if(service==null)throw new IllegalStateException("Missing service "+expectedName);
            if(suffix.equals("Research")){service.getClass().getMethod("refresh",int.class).invoke(service,0);continue;}
            if(suffix.equals("Upkeep")){invokePrivate(service,"scan");continue;}
            if(suffix.equals("TreasuryPlus")){service.getClass().getMethod("sync",Town.class).invoke(service,town);continue;}
            try { service.getClass().getMethod("refreshView").invoke(service); }
            catch (NoSuchMethodException missingView) {
                try { service.getClass().getMethod("refresh").invoke(service); }
                catch (NoSuchMethodException missingRefresh) {
                    try { Method refresh=service.getClass().getDeclaredMethod("refresh",boolean.class);refresh.setAccessible(true);refresh.invoke(service,false); }
                    catch (NoSuchMethodException noRefresh) { getLogger().info("No manual refresh for "+suffix); }
                }
            }
        }
        town.getAccount().deposit(50,"Disposable CSV income");
        check(TownyAPI.getInstance().getTown(player).getUUID().equals(TOWN),"native actor is town mayor");
    }
    private void command(String line)throws Exception{
        String[] parts=line.split(" ");var cmd=TownyCommandAddonAPI.getAddonCommand(TownyCommandAddonAPI.CommandType.TOWN,parts[0]);
        if(cmd==null)throw new IllegalStateException("Missing /t "+parts[0]);
        cmd.getCommandExecutor().onCommand(player,cmd,"town",Arrays.copyOfRange(parts,1,parts.length));
    }
    private void styled(String name)throws Exception{
        int named=0;for(ItemStack item:top.getContents())if(item!=null&&item.hasItemMeta()){
            var meta=item.getItemMeta();if(meta.hasDisplayName()&&!plain(meta.displayName()).isBlank()){
                if(meta.displayName().decoration(TextDecoration.ITALIC)==TextDecoration.State.TRUE)throw new AssertionError(name+": italic name");named++;
                verifyColors(meta.displayName(),name);
            }
            if(meta.hasLore())for(Component line:meta.lore())verifyColors(line,name);
        }check(named>0,"readable inventory: "+name);
    }
    private void verifyColors(Component text,String name){
        var c=text.color();if(c!=null&&.2126*c.red()+.7152*c.green()+.0722*c.blue()<163)throw new AssertionError(name+": dark color "+c);
        if(text.decoration(TextDecoration.ITALIC)==TextDecoration.State.TRUE)throw new AssertionError(name+": italic lore");
        for(Component child:text.children())verifyColors(child,name);
    }
    @SuppressWarnings("unchecked") private void treasuryAction(int slot)throws Exception{Map<Integer,Runnable> actions=(Map<Integer,Runnable>)field(top.getHolder(),"actions");Runnable r=actions.get(slot);if(r==null)throw new AssertionError("missing treasury action "+slot);r.run();}
    private String treasuryPage()throws Exception{return (String)field(top.getHolder(),"page");}
    private boolean hasCsv(){ItemStack i=top.getItem(40);return i!=null&&i.hasItemMeta()&&plain(i.getItemMeta().displayName()).contains("CSV");}
    private long csvCount()throws Exception{Path p=plugin("TreasuryPlus").getDataFolder().toPath().resolve("reports");if(!Files.exists(p))return 0;try(var walk=Files.walk(p)){return walk.filter(f->f.toString().endsWith(".csv")).count();}}
    private void menus()throws Exception{
        for(String cmd:List.of("builds","wonders","army","museum","chronicles","companies","contracts","district","espionage","events","expeditions","governance","ideologies","jobs","logistics","market","policies","population","power","reputation","research","resources","specialization","trade","treasury","upkeep")){
            if(!TownyCommandAddonAPI.hasCommand(TownyCommandAddonAPI.CommandType.TOWN,cmd)){getLogger().info("GUI alias unavailable: "+cmd);continue;}
            player.closeInventory();Inventory before=top;command(cmd);check(top!=before,"command opens menu: "+cmd);styled(cmd);
        }
        var camp=Bukkit.getPluginCommand("camp");camp.getExecutor().onCommand(player,camp,"camp",new String[0]);styled("camps");
        command("shop purchases");styled("shop purchases");check(top.getItem(22)!=null&&plain(top.getItem(22).getItemMeta().displayName()).contains("Нет покупок"),"empty purchases explain current state");
        command("trade");check(top.getItem(4).getType()==Material.BELL,"trade center is a separate bell");
        command("builds");Inventory parent=top;command("treasury");treasuryAction(45);check(top==parent,"treasury back restores prior addon menu");
        player.closeInventory();command("treasury");check(top.getItem(45).getType()==Material.BARRIER,"direct treasury root closes");
        command("treasury reports");treasuryAction(10);check(treasuryPage().startsWith("report/"),"weekly report opens");treasuryAction(45);check("reports".equals(treasuryPage()),"report back returns to weeks");treasuryAction(45);check(treasuryPage()==null,"weeks back returns to root");
        permissions.put("neverlandtownytreasury.export",false);String week=Week.key(System.currentTimeMillis());command("treasury report "+week);check(!hasCsv(),"CSV hidden without dedicated permission");
        long files=csvCount();command("treasury export "+week);check(csvCount()==files,"denied command writes no CSV");
        permissions.put("neverlandtownytreasury.export",true);command("treasury report "+week);check(hasCsv(),"CSV visible with permission");
        permissions.put("neverlandtownytreasury.export",false);treasuryAction(40);check(csvCount()==files,"revoked permission blocks open-menu export");
        permissions.put("neverlandtownytreasury.export",true);treasuryAction(40);check(csvCount()==files+1,"permitted CSV exported");
        var logCmd=TownyCommandAddonAPI.getAddonCommand(TownyCommandAddonAPI.CommandType.TOWN,"logistics");messages.clear();command("logistics help");check(messages.stream().anyMatch(s->s.contains("/t logistics filter"))&&messages.stream().anyMatch(s->s.contains("/t logistics resume")),"logistics sends complete separate commands");
    }
    private void firstFire()throws Exception{
        var generator=(BuildingBlueprintGenerator)field(construction,"generator");BlueprintPlan plan=generator.generate("warehouse",1);
        var site=new ConstructionSite("warehouse",world.getUID(),1616,90,1616,BlockFace.NORTH,1,1,2,false);
        // A resident has installed wooden interior steps inside this completed building.
        var entry=plan.blocks().entrySet().iterator().next();
        damaged=site.location(world,entry.getKey()).getBlock();damaged.setType(Material.OAK_STAIRS,false);var stairs=(org.bukkit.block.data.type.Stairs)damaged.getBlockData();stairs.setFacing(BlockFace.WEST);stairs.setHalf(org.bukkit.block.data.Bisected.Half.TOP);damaged.setBlockData(stairs,false);original=damaged.getBlockData().getAsString();
        data.town(TOWN).setConstructionSite(site);data.markDirty();data.save();
        check(events.startEvent(town,events.registry().get("fire")),"fire event starts");var active=events.active(TOWN);
        Block visual=world.getBlockAt(1635,92,1635);visual.setType(Material.OAK_PLANKS,false);
        check(fires.ignite(town,active,visual,false)&&visual.getType()==Material.OAK_PLANKS&&!fires.requiresRepair(world.getUID(),1635,92,1635),"visual fire preserves wood");
        fires.tick(town,active,false,List.of(player));check(fires.burning(TOWN)==1,"visual hotspot renders on server");
        visual.getRelative(BlockFace.UP).setType(Material.WATER,false);events.extinguish(player,visual.getLocation().add(0,1,0));check(active.progress()>0,"water response contributes protection progress");
        for(Material excluded:List.of(Material.STONE,Material.CHEST,Material.BARREL,Material.OAK_LOG)){
            Block block=world.getBlockAt(1640,120,1640);block.setType(excluded,false);check(!fires.ignite(town,active,block,true)&&block.getType()==excluded,"fire excludes "+excluded);
        }
        Block outside=world.getBlockAt(1680,92,1680);outside.setType(Material.OAK_PLANKS,false);check(!fires.ignite(town,active,outside,true),"foreign or unclaimed land protected");
        check(fires.ignite(town,active,damaged,true)&&damaged.getType()==Material.AIR,"wood damage creates a hole");
        check(fires.damage(TOWN).size()==1&&fires.damage(TOWN).getFirst().blockData().equals(original),"exact original block persisted");
        check(!fires.ignite(town,active,damaged,true),"same location cannot burn twice");
        fails(()->fires.confirmRepairs(player,true),"repair confirmation blocked during active fire");
        events.resolve(town,true);check(damaged.getType()==Material.AIR&&fires.damage(TOWN).size()==1,"successful event leaves mandatory repairs");
        check(!events.startEvent(town,events.registry().get("fire")),"new fire waits for repairs");
        check(FireRepairAccess.blocked(TOWN),"Builds reads public repair debt");invokePrivate(construction,"repairCompletedSites");check(damaged.getType()==Material.AIR,"Builds automatic repair preserves fire hole");
        fails(()->construction.commit(new ConstructionPreparation(ConstructionPreparation.Status.READY,TOWN,site,plan,List.of()),player,town),"building upgrade cannot bypass fire repairs");
        site.setArchitectureVersion(5);invokePrivate(construction,"migrateLegacySites");
        check(site.architectureVersion()==5&&damaged.getType()==Material.AIR,"legacy architecture migration waits for fire repairs");
        site.setArchitectureVersion(ConstructionSite.CURRENT_ARCHITECTURE_VERSION);data.markDirty();data.save();
        command("events repairs");styled("fire repairs");check(top.getItem(0)!=null&&top.getItem(0).getType()==Material.OAK_STAIRS,"repair menu shows required material");
        var receipt=fires.damage(TOWN).getFirst();getConfig().set("damage.x",receipt.x());getConfig().set("damage.y",receipt.y());getConfig().set("damage.z",receipt.z());getConfig().set("damage.original",receipt.blockData());saveConfig();
        Path file=plugin("Events").getDataFolder().toPath().resolve("fire-damage.yml"),backup=file.resolveSibling("fire-damage.fixture-backup");
        Files.move(file,backup);Files.createDirectory(file);Files.writeString(file.resolve("blocker"),"intentional fault");
        Block fault=world.getBlockAt(1644,92,1644);fault.setType(Material.OAK_PLANKS,false);
        fails(()->fires.ignite(town,active,fault,true),"write fault stops block destruction");check(fault.getType()==Material.OAK_PLANKS&&!fires.repository().writable(),"write fault leaves block and blocks retries");
        check(FireRepairAccess.blocked(TOWN),"unavailable ledger blocks Builds restoration");
        Files.delete(file.resolve("blocker"));Files.delete(file);Files.move(backup,file);fires.reload();
        check(fires.damage(TOWN).size()==1,"ledger recovers without losing debt");
    }
    private void restartFire()throws Exception{
        int x=getConfig().getInt("damage.x"),y=getConfig().getInt("damage.y"),z=getConfig().getInt("damage.z");world.getChunkAt(x>>4,z>>4).load();damaged=world.getBlockAt(x,y,z);original=getConfig().getString("damage.original");
        check(damaged.getType()==Material.AIR,"hard restart and Builds startup retain hole");check(fires.damage(TOWN).size()==1&&events.hasFireDamage(TOWN),"hard restart retains repair journal");
        damaged.setType(Material.STONE,false);check(fires.confirmRepairs(player,false)==0&&fires.damage(TOWN).size()==1,"wrong replacement cannot clear debt");
        var expected=Bukkit.createBlockData(original);damaged.setType(expected.getMaterial(),false);
        permissions.put("mintevents.repair",false);fails(()->fires.confirmRepairs(player,false),"repair permission enforced");
        permissions.put("mintevents.repair",true);check(fires.confirmRepairs(player,false)==1,"manual material placement confirms repair");
        check(damaged.getBlockData().getAsString().equals(original),"original block orientation restored");fires.reload();
        check(fires.damage(TOWN).isEmpty()&&!FireRepairAccess.blocked(TOWN),"confirmed repair persisted and unblocks Builds");
        check(events.startEvent(town,events.registry().get("fire")),"new fire allowed after repair");events.resolve(town,true);
    }
}
