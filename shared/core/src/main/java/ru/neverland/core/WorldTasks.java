package ru.neverland.core;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Loaded references are indexed from owning-region events. Never synchronously load a foreign chunk. */
public final class WorldTasks implements Listener {
    private static final Map<UUID,Entity> ENTITIES=new ConcurrentHashMap<>();
    private static final Set<Plugin> INITIALIZED=ConcurrentHashMap.newKeySet();
    private static final Map<UUID,Location> POSITIONS=new ConcurrentHashMap<>();
    private WorldTasks() {}
    public static void initialize(Plugin plugin) {
        if (!INITIALIZED.add(plugin)) return;
        Bukkit.getPluginManager().registerEvents(new WorldTasks(),plugin);
        eachChunk(plugin,chunk->{ for(Entity entity:chunk.getEntities()) ENTITIES.put(entity.getUniqueId(),entity); });
        SuiteScheduler.get().runTaskTimer(plugin,()->SuiteScheduler.players(plugin,p->POSITIONS.put(p.getUniqueId(),p.getLocation())),1,20);
    }
    @EventHandler public void load(EntitiesLoadEvent event) { for(Entity entity:event.getEntities()) ENTITIES.put(entity.getUniqueId(),entity); }
    @EventHandler public void unload(EntitiesUnloadEvent event) { for(Entity entity:event.getEntities()) ENTITIES.remove(entity.getUniqueId(),entity); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void spawn(EntitySpawnEvent event) { Entity entity=event.getEntity();ENTITIES.put(entity.getUniqueId(),entity); }
    public static Entity find(UUID id) { return id==null?null:ENTITIES.get(id); }
    /** Position snapshots can be read globally; interaction still requires entity ownership. */
    public static Location position(Entity entity) {
        if(entity==null)return null;
        if(SuiteScheduler.owns(entity))return entity.getLocation();
        Location location=POSITIONS.get(entity.getUniqueId());return location==null?null:location.clone();
    }
    @EventHandler public void move(org.bukkit.event.player.PlayerMoveEvent event) { POSITIONS.put(event.getPlayer().getUniqueId(),event.getTo().clone()); }
    @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent event) { POSITIONS.remove(event.getPlayer().getUniqueId()); }
    public static void eachChunk(Plugin plugin,Consumer<Chunk> action) {
        for(World world:Bukkit.getWorlds())for(Chunk chunk:world.getLoadedChunks()) {
            int x=chunk.getX(),z=chunk.getZ();
            SuiteScheduler.region(plugin,new Location(world,x*16.0,0,z*16.0),()->{
                if(world.isChunkLoaded(x,z)) action.accept(world.getChunkAt(x,z));
            });
        }
    }
    public static void eachEntity(Plugin plugin,Consumer<Entity> action) {
        eachChunk(plugin,chunk->{ for(Entity entity:chunk.getEntities()) SuiteScheduler.entity(plugin,entity,()->action.accept(entity)); });
    }
    public static boolean ownsBox(Location center,int radius) {
        if(center==null||center.getWorld()==null)return false;
        World world=center.getWorld();
        for(int x=(center.getBlockX()-radius)>>4;x<=(center.getBlockX()+radius)>>4;x++)
            for(int z=(center.getBlockZ()-radius)>>4;z<=(center.getBlockZ()+radius)>>4;z++)
                if(!Bukkit.isOwnedByCurrentRegion(world,x,z))return false;
        return true;
    }
    public static void requireBox(Location center,int radius) {
        if(!ownsBox(center,radius))throw new IllegalStateException("Участок ещё не готов. Подойдите к месту постройки и повторите действие.");
    }
    public static void remove(Plugin plugin,Entity entity) {
        if(entity==null)return;
        if(SuiteScheduler.owns(entity)) { entity.remove();ENTITIES.remove(entity.getUniqueId(),entity);return; }
        Plugin owner=cleanupOwner(plugin);
        if(owner!=null)SuiteScheduler.entity(owner,entity,()->{ entity.remove();ENTITIES.remove(entity.getUniqueId(),entity); });
    }
    public static Plugin cleanupOwner(Plugin plugin) {
        Plugin control=Bukkit.getPluginManager().getPlugin("NeverLandTownyControl");
        return control!=null&&control.isEnabled()?control:plugin.isEnabled()?plugin:null;
    }
}
