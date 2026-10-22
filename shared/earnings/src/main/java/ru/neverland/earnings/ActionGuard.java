package ru.neverland.earnings;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Conservative provenance: mined/placed/moved ore coordinates are never paid twice. */
public final class ActionGuard implements Listener {
    private final Path file;private final Set<String> denied=new HashSet<>();private boolean fault;
    private final JavaPlugin plugin;private final NamespacedKey natural;
    public ActionGuard(JavaPlugin plugin,String filename)throws IOException{
        this.plugin=plugin;natural=new NamespacedKey(plugin,"income_natural");file=plugin.getDataFolder().toPath().resolve(filename);
        if(Files.exists(file))for(String line:Files.readAllLines(file)){if(!line.matches("[0-9a-f-]{36}:-?[0-9]+:-?[0-9]+:-?[0-9]+"))throw new IOException("Повреждён журнал происхождения блоков");denied.add(line);}
    }
    public static boolean ore(Material m){return m.name().endsWith("_ORE")||m==Material.ANCIENT_DEBRIS;}
    public static String key(Block b){return b.getWorld().getUID()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
    public boolean untouched(Block b){synchronized(ru.neverland.core.SuiteState.lock()){return !fault&&!denied.contains(key(b));}}
    public void deny(Block b){synchronized(ru.neverland.core.SuiteState.lock()){
        String key=key(b);if(denied.contains(key))return;if(fault)throw new IllegalStateException("Журнал блоков остановлен");
        try{Files.createDirectories(file.getParent());try(var channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND)){var bytes=ByteBuffer.wrap((key+"\n").getBytes(StandardCharsets.UTF_8));while(bytes.hasRemaining())channel.write(bytes);channel.force(true);}denied.add(key);}
        catch(IOException ex){fault=true;throw new UncheckedIOException(ex);}
    }}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(ore(e.getBlock().getType()))deny(e.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void extend(BlockPistonExtendEvent e){for(Block b:e.getBlocks())if(ore(b.getType())){deny(b);deny(b.getRelative(e.getDirection()));}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void retract(BlockPistonRetractEvent e){for(Block b:e.getBlocks())if(ore(b.getType())){deny(b);deny(b.getRelative(e.getDirection()));}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void spawn(CreatureSpawnEvent e){
        // Old entities and spawner/egg/command/custom mobs intentionally have no proof.
        if(e.getSpawnReason()==CreatureSpawnEvent.SpawnReason.NATURAL)e.getEntity().getPersistentDataContainer().set(natural,PersistentDataType.BYTE,(byte)1);
    }
    public Player hunter(EntityDeathEvent e){
        if(e.isCancelled()||!(e.getEntity() instanceof Enemy)||!e.getEntity().getPersistentDataContainer().has(natural,PersistentDataType.BYTE))return null;
        return HuntingCredit.killer(e);
    }
    public boolean hunted(EntityDeathEvent e){return hunter(e)!=null;}
    public static boolean survival(Player p){return ru.neverland.core.SuiteScheduler.owns(p)&&p.getGameMode()==GameMode.SURVIVAL&&!p.isFlying()&&!p.isGliding()&&!p.isInsideVehicle();}
}
