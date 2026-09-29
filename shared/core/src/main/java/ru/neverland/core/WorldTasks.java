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
    @SuppressWarnings("unchecked")
    private static final Map<UUID,Entity> ENTITIES=(Map<UUID,Entity>)System.getProperties().computeIfAbsent("ru.neverland.towny.entities.v1",k->new ConcurrentHashMap<UUID,Entity>());
    @SuppressWarnings("unchecked")
    private static final Set<Plugin> INITIALIZED=(Set<Plugin>)System.getProperties().computeIfAbsent("ru.neverland.towny.trackers.v1",k->ConcurrentHashMap.<Plugin>newKeySet());
    @SuppressWarnings("unchecked")
    private static final Map<UUID,Location> POSITIONS=(Map<UUID,Location>)System.getProperties().computeIfAbsent("ru.neverland.towny.positions.v1",k->new ConcurrentHashMap<UUID,Location>());
    private WorldTasks() {}
    public static void initialize(Plugin plugin) {
        plugin=cleanupOwner(plugin);if(plugin==null||!INITIALIZED.add(plugin))return;
        final Plugin tracker=plugin;
        Bukkit.getPluginManager().registerEvents(new WorldTasks(),plugin);
        eachChunk(plugin,chunk->{ for(Entity entity:chunk.getEntities()) ENTITIES.put(entity.getUniqueId(),entity); });
        SuiteScheduler.get().runTaskTimer(tracker,()->SuiteScheduler.players(tracker,p->POSITIONS.put(p.getUniqueId(),p.getLocation())),1,20);
    }
    @EventHandler public void load(EntitiesLoadEvent event) { for(Entity entity:event.getEntities()) ENTITIES.put(entity.getUniqueId(),entity); }
    @EventHandler public void unload(EntitiesUnloadEvent event) { for(Entity entity:event.getEntities()) ENTITIES.remove(entity.getUniqueId(),entity); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void spawn(EntitySpawnEvent event) { Entity entity=event.getEntity();ENTITIES.put(entity.getUniqueId(),entity); }
    @EventHandler public void removed(org.bukkit.event.entity.EntityRemoveEvent event) {
        Entity entity=event.getEntity();ENTITIES.remove(entity.getUniqueId(),entity);POSITIONS.remove(entity.getUniqueId());
    }
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
    public static void eachEntityCleanup(Plugin plugin,Consumer<Entity> action) {
        Plugin owner=cleanupOwner(plugin);if(owner!=null)eachEntity(owner,action);
    }
    /** Load asynchronously, then enter the owning region. No waiting while holding the ledger. */
    public static void loaded(Plugin plugin,Location location,Runnable action) {
        if(location==null||location.getWorld()==null||!plugin.isEnabled())return;
        location.getWorld().getChunkAtAsync(location).whenComplete((chunk,error)->{
            if(error!=null){plugin.getLogger().warning("Не удалось загрузить участок: "+error.getMessage());return;}
            if(plugin.isEnabled()&&!SuiteState.frozen(plugin.getName()))SuiteScheduler.region(plugin,location,action);
        });
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
