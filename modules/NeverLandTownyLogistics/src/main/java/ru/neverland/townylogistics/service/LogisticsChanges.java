package ru.neverland.townylogistics.service;
import java.util.*;
import org.bukkit.block.Block;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.*;
/** Only affected chunks are invalidated; no event reads blocks outside its own region. */
public final class LogisticsChanges implements Listener {
    private final LogisticsService service;public LogisticsChanges(LogisticsService service){this.service=service;}
    private void changed(Block block){service.changedChunk(block.getWorld().getUID(),block.getX()>>4,block.getZ()>>4);}
    private void changed(Collection<Block> blocks){var seen=new HashSet<String>();for(var block:blocks){String key=block.getWorld().getUID()+"/"+(block.getX()>>4)+"/"+(block.getZ()>>4);if(seen.add(key))changed(block);}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void place(BlockPlaceEvent event){changed(event.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void breakBlock(BlockBreakEvent event){changed(event.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void explode(EntityExplodeEvent event){changed(event.blockList());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void explode(BlockExplodeEvent event){changed(event.blockList());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void extend(BlockPistonExtendEvent event){changed(event.getBlocks());changed(event.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void retract(BlockPistonRetractEvent event){changed(event.getBlocks());changed(event.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void interact(PlayerInteractEvent event){if(event.getClickedBlock()!=null&&(event.getClickedBlock().getBlockData() instanceof org.bukkit.block.data.Openable))changed(event.getClickedBlock());}
    @EventHandler(priority=EventPriority.MONITOR)public void load(ChunkLoadEvent event){service.changedChunk(event.getWorld().getUID(),event.getChunk().getX(),event.getChunk().getZ());}
    @EventHandler(priority=EventPriority.MONITOR)public void unload(ChunkUnloadEvent event){service.changedChunk(event.getWorld().getUID(),event.getChunk().getX(),event.getChunk().getZ());}
}
