import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.command.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.event.*;
import ru.neverland.core.*;
import ru.neverland.townybuilds.api.*;
import ru.neverland.townybuilds.construction.*;
import ru.neverland.townybuilds.data.*;

/** Runs exclusively in a disposable Folia server; never include this JAR in a release. */
public final class DeletionProbe extends JavaPlugin {
    interface Step {void run() throws Exception;}
    private final List<String> results=new ArrayList<>();private int failed;private Object service,protection;
    private World world;private JavaPlugin control;private TownyBuildsApi builds;private int nextChunk=40;
    private void check(String text,boolean ok){results.add((ok?"PASS ":"FAIL ")+text);if(!ok)failed++;}
    private void safe(Step action){synchronized(SuiteState.lock()){try{action.run();}catch(Throwable ex){getLogger().log(java.util.logging.Level.SEVERE,"Deletion probe",ex);check(ex.toString(),false);finish();}}}
    private void global(Step action){Bukkit.getGlobalRegionScheduler().runDelayed(this,t->safe(action),2);}
    private void region(int cx,Step action){Bukkit.getRegionScheduler().runDelayed(this,world,cx,0,t->safe(action),2);}
    private Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private Object call(Object target,String name,Object...args)throws Exception{
        for(var m:target.getClass().getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);try{return m.invoke(target,args);}catch(InvocationTargetException ex){throw new Exception(ex.getCause());}}
        throw new NoSuchMethodException(name);
    }
    private Object construct(String suffix,Object...args)throws Exception{
        var type=Class.forName("ru.neverland.townycontrol.DeletionFiles$"+suffix,true,control.getClass().getClassLoader());
        for(var c:type.getDeclaredConstructors())if(c.getParameterCount()==args.length){c.setAccessible(true);return c.newInstance(args);}throw new NoSuchMethodException(suffix);
    }
    private void files(String name,Object...args)throws Exception{
        var type=Class.forName("ru.neverland.townycontrol.DeletionFiles",true,control.getClass().getClassLoader());
        for(var m:type.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);m.invoke(null,args);return;}throw new NoSuchMethodException(name);
    }
    @SuppressWarnings("unchecked") private Map<String,Object> offers()throws Exception{return (Map<String,Object>)field(service,"offers");}
    @SuppressWarnings("unchecked") private Map<UUID,Object> jobs()throws Exception{return (Map<UUID,Object>)field(service,"jobs");}
    private UUID token(CommandSender sender)throws Exception{return (UUID)call(offers().get("console:"+sender.getName()),"token");}
    private void command(CommandSender sender,String...args){Bukkit.getPluginCommand("nltdelete").getExecutor().onCommand(sender,Bukkit.getPluginCommand("nltdelete"),"nltdelete",args);}
    private CommandSender sender(String name,boolean allowed){return (CommandSender)Proxy.newProxyInstance(CommandSender.class.getClassLoader(),new Class<?>[]{CommandSender.class},(p,m,a)->switch(m.getName()){
        case "getName"->name;case "isOp"->false;case "hasPermission"->allowed&&a[0].equals("neverlandtownycontrol.towns.delete");case "sendMessage"->null;case "getServer"->Bukkit.getServer();default->throw new UnsupportedOperationException(m.getName());});}
    @Override public void onEnable(){Bukkit.getGlobalRegionScheduler().runDelayed(this,t->safe(()->{
        control=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyControl");service=field(control,"townDeletion");protection=field(service,"protection");world=Bukkit.getWorlds().getFirst();
        builds=Bukkit.getServicesManager().load(TownyBuildsApi.class);
        check("Control and Builds enabled",control.isEnabled()&&builds!=null);
        check("all three native erasers disabled",TownyUniverse.getInstance().getTownyWorlds().stream().allMatch(w->!w.isUsingPlotManagementRevert()&&!w.isUsingPlotManagementDelete()&&!w.isDeletingEntitiesOnUnclaim()));
        if(Files.exists(getDataFolder().toPath().resolve("restart.yml")))restartChecks(0);else registered();
    }),60);}
    private Town create(int cx)throws Exception{
        var universe=TownyUniverse.getInstance();String name="DeleteProbe"+cx;UUID id=UUID.randomUUID();var resident=universe.getDataSource().newResident("DP"+cx,id);
        universe.newTown(name);var town=universe.getTown(name);resident.setTown(town);town.setMayor(resident);
        var block=new TownBlock(cx,0,universe.getWorld(world.getName()));block.setTown(town);town.setHomeBlock(block);town.setSpawn(new Location(world,cx*16+8,80,8));block.save();resident.save();town.save();return town;
    }
    private void waitJob(Town town,Consumer<Object> done,int attempt)throws Exception{
        Object found=null;for(var j:jobs().values())if(field(j,"town").equals(town.getUUID()))found=j;
        if(found!=null&&Set.of("DONE","CANCELLED","REVIEW").contains(field(found,"state"))){done.accept(found);return;}
        if(attempt>300)throw new IllegalStateException("Timed out: "+town.getName()+" "+(found==null?"no job":field(found,"state")));
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->safe(()->waitJob(town,done,attempt+1)),2);
    }
    private void registered()throws Exception{
        int cx=nextChunk;nextChunk+=3;region(cx,()->{
            Town town=create(cx);Town neighbor=create(cx+1);
            var site=new ConstructionSite("forge",world.getUID(),cx*16+8,80,8,BlockFace.NORTH,1,1,1,false);
            var store=(DataStore)field(Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds"),"dataStore");store.town(town.getUUID()).setConstructionSite(site);
            var listed=builds.registeredBuildingBlocks(town.getUUID());check("catalog exports exact registered blocks",!listed.isEmpty());
            var inside=listed.stream().filter(b->Math.floorDiv(b.x(),16)==cx&&Math.floorDiv(b.z(),16)==0&&b.y()>80&&Material.valueOf(b.material()).isSolid()).toList();
            if(inside.size()<3)throw new IllegalStateException("Fixture needs three registered positions");
            var remove=inside.get(0);var modified=inside.get(1);var container=inside.get(2);
            world.getBlockAt(remove.x(),remove.y(),remove.z()).setType(Material.valueOf(remove.material()),false);
            world.getBlockAt(modified.x(),modified.y(),modified.z()).setType(Material.DIAMOND_BLOCK,false);
            var chestBlock=world.getBlockAt(container.x(),container.y(),container.z());chestBlock.setType(Material.CHEST,false);((Chest)chestBlock.getState()).getBlockInventory().setItem(0,new ItemStack(Material.DIAMOND,7));
            world.getBlockAt(cx*16,100,0).setType(Material.OAK_PLANKS,false);world.getBlockAt((cx+1)*16,100,0).setType(Material.GOLD_BLOCK,false);
            var console=Bukkit.getConsoleSender();Bukkit.getPluginCommand("town").getExecutor().onCommand(console,Bukkit.getPluginCommand("town"),"town",new String[]{"delete",town.getName()});
            check("native town delete asks before deletion",TownyAPI.getInstance().getTown(town.getUUID())!=null&&offers().containsKey("console:"+console.getName()));command(console,"cancel");
            var denied=sender("Denied",false);command(denied,town.getName(),"registered");check("missing permission cannot create offer",!offers().containsKey("console:Denied"));
            var granted=sender("Granted",true);command(granted,town.getName(),"registered");UUID token=token(granted);check("explicit permission works without OP",token!=null);
            command(denied,"confirm",token.toString());check("confirmation bound to actor",jobs().isEmpty()&&TownyAPI.getInstance().getTown(town.getUUID())!=null);
            command(granted,"confirm",token.toString());int count=jobs().size();command(granted,"confirm",token.toString());check("confirmation cannot replay",jobs().size()==count);
            global(()->waitJob(town,j->region(cx,()->{
                check("registered deletion completes",field(j,"state").equals("DONE")&&TownyAPI.getInstance().getTown(town.getUUID())==null);
                check("registered matching block removed",world.getBlockAt(remove.x(),remove.y(),remove.z()).getType().isAir());
                check("changed registered position preserved",world.getBlockAt(modified.x(),modified.y(),modified.z()).getType()==Material.DIAMOND_BLOCK);
                check("ordinary base preserved",world.getBlockAt(cx*16,100,0).getType()==Material.OAK_PLANKS);
                check("neighbor town preserved",TownyAPI.getInstance().getTown(neighbor.getUUID())!=null&&world.getBlockAt((cx+1)*16,100,0).getType()==Material.GOLD_BLOCK);
                check("container and contents preserved",((Chest)chestBlock.getState()).getBlockInventory().getItem(0).getAmount()==7);
                global(()->full(false,false));
            }),0));
        });
    }
    private Path baseline(Town town){var b=town.getTownBlocks().iterator().next();return control.getDataFolder().toPath().resolve("town-removal/baselines/"+world.getUID()+"_"+b.getX()+"_"+b.getZ()+".gz");}
    private void waitCapture(Town town,Step then,int attempt)throws Exception{
        if(Files.exists(baseline(town))&&((Map<?,?>)field(protection,"captures")).isEmpty()){then.run();return;}
        if(attempt>200)throw new IllegalStateException("Snapshot capture timeout");
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->safe(()->waitCapture(town,then,attempt+1)),2);
    }
    private void full(boolean corrupt,boolean invalid)throws Exception{
        int cx=nextChunk;nextChunk+=3;region(cx,()->{
            Town town=create(cx);world.getBlockAt(cx*16+1,100,1).setType(Material.STONE,false);
            call(protection,"created",new NewTownEvent(town));
            global(()->waitCapture(town,()->region(cx,()->{
                check("new town initial claim snapshot captured",Files.exists(baseline(town)));
                world.getBlockAt(cx*16+1,100,1).setType(Material.OAK_PLANKS,false);
                if(corrupt)Files.write(baseline(town),new byte[]{1,2,3});
                if(invalid){
                    var block=town.getTownBlocks().iterator().next();int min=world.getMinHeight(),max=world.getMaxHeight();var snapshot=world.getChunkAt(cx,0).getChunkSnapshot();var data=new ArrayList<String>();
                    for(int z=0;z<16;z++)for(int x=0;x<16;x++)for(int y=max-1;y>min;y--)data.add(x==1&&z==1&&y==100?"minecraft:not_a_real_block":snapshot.getBlockData(x,y,z).getAsString());
                    var plot=construct("Plot",world.getUID(),cx,0);files("saveBaseline",baseline(town),construct("Baseline",plot,town.getUUID(),block.getClaimedAt(),min,max,data));
                }
                var console=Bukkit.getConsoleSender();command(console,town.getName(),"full");command(console,"confirm",token(console).toString());
                global(()->waitJob(town,j->region(cx,()->{
                    if(corrupt||invalid){
                        check((invalid?"unknown material":"corrupt snapshot")+" refuses before deleting town",field(j,"state").equals("CANCELLED")&&TownyAPI.getInstance().getTown(town.getUUID())!=null&&world.getBlockAt(cx*16+1,100,1).getType()==Material.OAK_PLANKS);
                    }else{
                        check("full mode restores captured base",field(j,"state").equals("DONE")&&TownyAPI.getInstance().getTown(town.getUUID())==null&&world.getBlockAt(cx*16+1,100,1).getType()==Material.STONE);
                        // Replay a durable checkpoint after a real server restart. A later player change must survive.
                        world.getBlockAt(cx*16+1,100,1).setType(Material.DIAMOND_BLOCK,false);
                        set(j,"state","APPLYING");set(j,"chunk",0);set(j,"cursor",0);call(service,"save",j);
                        var y=new org.bukkit.configuration.file.YamlConfiguration();y.set("job",field(j,"id").toString());y.set("x",cx*16+1);y.set("y",100);y.set("z",1);getDataFolder().mkdirs();y.save(getDataFolder().toPath().resolve("restart.yml").toFile());
                    }
                    if(!corrupt&&!invalid)global(()->full(true,false));else if(corrupt)global(()->full(false,true));else global(this::deepCut);
                }),0));
            }),0));
        });
    }
    private void deepCut()throws Exception{
        int cx=nextChunk;region(cx,()->{
            Town town=create(cx);call(protection,"created",new NewTownEvent(town));
            global(()->waitCapture(town,()->region(cx,()->{
                for(int x=0;x<5;x++)for(int y=80;y<150;y++)world.getBlockAt(cx*16+x,y,1).setType(Material.STONE,false);
                var console=Bukkit.getConsoleSender();command(console,town.getName(),"full");command(console,"confirm",token(console).toString());
                global(()->waitJob(town,j->region(cx,()->{
                    check("five-wide deep trench refused before deleting town",field(j,"state").equals("CANCELLED")&&TownyAPI.getInstance().getTown(town.getUUID())!=null&&world.getBlockAt(cx*16,80,1).getType()==Material.STONE);
                    finish();
                }),0));
            }),0));
        });
    }
    private void restartChecks(int attempt)throws Exception{
        var y=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(getDataFolder().toPath().resolve("restart.yml").toFile());Object job=jobs().get(UUID.fromString(y.getString("job")));
        if(job==null)throw new IllegalStateException("Recovery job missing");
        if(!field(job,"state").equals("DONE")&&attempt<200){Bukkit.getGlobalRegionScheduler().runDelayed(this,t->safe(()->restartChecks(attempt+1)),2);return;}
        region(Math.floorDiv(y.getInt("x"),16),()->{
            check("durable deletion resumes after real restart",field(job,"state").equals("DONE"));
            check("restart replay preserves later player change",world.getBlockAt(y.getInt("x"),y.getInt("y"),y.getInt("z")).getType()==Material.DIAMOND_BLOCK);finish();
        });
    }
    private void finish(){try{getDataFolder().mkdirs();Files.writeString(getDataFolder().toPath().resolve("result.txt"),String.join("\n",results)+"\nfailed="+failed+"\n");}catch(Exception ex){getLogger().severe(ex.toString());}}
}
