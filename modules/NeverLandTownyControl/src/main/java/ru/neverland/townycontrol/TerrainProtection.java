package ru.neverland.townycontrol;

import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.event.TownClaimEvent;
import com.palmergames.bukkit.towny.event.NewTownEvent;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.regen.TownyRegenAPI;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import static ru.neverland.townycontrol.DeletionFiles.*;

/** Disable Towny's unbounded native erasers; only explicit deletion plans can change terrain. */
final class TerrainProtection implements Listener {
    private final JavaPlugin plugin;private final Path root,settings;
    private long installedAt;
    private final Map<Plot,UUID> captures=new HashMap<>();
    TerrainProtection(JavaPlugin plugin)throws IOException{
        this.plugin=plugin;root=plugin.getDataFolder().toPath().resolve("town-removal");settings=root.resolve("worlds-before-protection.yml");
        protectWorlds();Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    void protectWorlds()throws IOException {
        var original=SafeYaml.load(settings);boolean changed=false;
        if(!original.contains("installed-at")){original.set("installed-at",System.currentTimeMillis());changed=true;}
        installedAt=SafeYaml.longValue(original,"installed-at");
        for(var world:TownyUniverse.getInstance().getTownyWorlds()){
            String key="worlds."+world.getUUID();
            if(!original.contains(key)){
                original.set(key+".name",world.getName());original.set(key+".revert",world.isUsingPlotManagementRevert());
                original.set(key+".delete-blocks",world.isUsingPlotManagementDelete());original.set(key+".delete-entities",world.isDeletingEntitiesOnUnclaim());
                original.set(key+".suspended-queue",TownyRegenAPI.getRegenQueueList().stream().filter(c->c.getWorldName().equals(world.getName())).map(c->c.getX()+","+c.getZ()).toList());changed=true;
            }
        }
        if(changed)AtomicFiles.write(settings,original::saveToString);
        for(var world:TownyUniverse.getInstance().getTownyWorlds()){
            boolean save=world.isUsingPlotManagementRevert()||world.isUsingPlotManagementDelete()||world.isDeletingEntitiesOnUnclaim();
            world.setUsingPlotManagementRevert(false);world.setUsingPlotManagementDelete(false);world.setDeletingEntitiesOnUnclaim(false);
            TownyRegenAPI.turnOffRevertOnUnclaimForWorld(world);
            if(save)world.save();
        }
    }
    void verify(){
        if(TownySettings.getTownBlockSize()!=16)throw new IllegalStateException("Безопасное удаление требует town_block_size=16; нестандартная сетка не поддерживается");
        for(var world:TownyUniverse.getInstance().getTownyWorlds())if(world.isUsingPlotManagementRevert()||world.isUsingPlotManagementDelete()||world.isDeletingEntitiesOnUnclaim())throw new IllegalStateException("В мире "+world.getName()+" снова включён автоматический откат Towny. Перезапустите сервер с Control.");
    }
    static Plot plot(TownBlock block){return new Plot(block.getWorld().getUUID(),block.getX(),block.getZ());}
    Path baselineFile(Plot plot){return root.resolve("baselines").resolve(plot.key()+".gz");}
    @EventHandler public void world(WorldLoadEvent event){SuiteScheduler.get().runTaskLater(plugin,()->{try{protectWorlds();}catch(Exception ex){plugin.getLogger().severe("Не удалось включить защиту территории: "+ex.getMessage());}},20);}
    @EventHandler(priority=EventPriority.MONITOR) public void created(NewTownEvent event){try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()){for(var block:event.getTown().getTownBlocks())capture(block);}}
    @EventHandler(priority=EventPriority.MONITOR) public void claim(TownClaimEvent event){try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()){capture(event.getTownBlock());}}
    private void capture(TownBlock block){
        if(TownySettings.getTownBlockSize()!=16)return;
        Town town=block.getTownOrNull();if(town==null)return;
        Plot plot=plot(block);UUID owner=town.getUUID(),capture=UUID.randomUUID();long claimed=block.getClaimedAt();captures.put(plot,capture);
        World world=Bukkit.getWorld(plot.world());if(world==null)return;
        SuiteScheduler.region(plugin,new Location(world,plot.x()*16,world.getMinHeight(),plot.z()*16),()->{
            var current=TownyAPI.getInstance().getTownBlock(new Location(world,plot.x()*16,world.getMinHeight(),plot.z()*16));
            if(current==null||current.getTownOrNull()==null||!current.getTownOrNull().getUUID().equals(owner)||current.getClaimedAt()!=claimed)return;
            var snapshot=world.getChunkAt(plot.x(),plot.z()).getChunkSnapshot(false,false,false);
            int min=world.getMinHeight(),max=world.getMaxHeight();
            SuiteScheduler.get().runTaskAsynchronously(plugin,()->{
                try{
                    var data=new ArrayList<String>();for(int z=0;z<16;z++)for(int x=0;x<16;x++)for(int y=max-1;y>min;y--)data.add(snapshot.getBlockData(x,y,z).getAsString());
                    var baseline=new Baseline(plot,owner,claimed,min,max,data);
                    try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()){if(!capture.equals(captures.get(plot)))return;saveBaseline(baselineFile(plot),baseline);captures.remove(plot);}
                }catch(Exception ex){plugin.getLogger().severe("Снимок участка не сохранён; полный откат этого участка будет запрещён: "+ex.getMessage());}
            });
        });
    }
    /** Called off-thread; returns a complete immutable snapshot or fails before town deletion. */
    Baseline baseline(TownBlock block,UUID town,int min,int max)throws IOException {
        Plot plot=plot(block);Path own=baselineFile(plot);
        try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()){if(captures.containsKey(plot))throw new IOException("Снимок нового участка ещё сохраняется. Повторите позже.");}
        if(Files.exists(own)){
            var b=loadBaseline(own);
            if(!b.plot().equals(plot)||!b.town().equals(town)||b.claimed()!=block.getClaimedAt()||b.minY()!=min||b.maxY()!=max)throw new IOException("Снимок относится к другому владельцу или высоте мира: "+plot.key());
            return b;
        }
        if(block.getClaimedAt()>=installedAt)throw new IOException("Снимок нового привата не сохранён; старый снимок Towny использовать нельзя: "+plot.key());
        var legacy=TownyRegenAPI.getPlotChunkSnapshot(block);
        if(legacy==null||legacy.getVersion()!=5||legacy.getSize()!=16||legacy.getMinHeight()!=min||legacy.getHeight()!=max-1)throw new IOException("Нет подходящего полного снимка Towny: "+plot.key()+". Администратор может сохранить базу режимом registered.");
        try{return new Baseline(plot,town,block.getClaimedAt(),min,max,legacy.getBlockList());}
        catch(IllegalArgumentException ex){throw new IOException("Снимок Towny обрезан или повреждён: "+plot.key(),ex);}
    }
}
