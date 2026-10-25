package ru.neverland.townybuilds.construction;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.plugin.Plugin;
import org.joml.Matrix4f;
import ru.neverland.core.*;

/** Full building, personal displays. Every entity lives at one owned anchor; transforms position the visible blocks. */
final class PlacementProjection {
    record Part(BlockOffset offset,BlueprintBlock block){}
    private final Plugin plugin;
    private final Player viewer;
    private ConstructionSite site;
    private final List<Part> parts;
    private final List<BlockDisplay> entities=new ArrayList<>();
    private final Location anchor;
    private boolean valid;
    private boolean closed;
    private int next,updated;
    PlacementProjection(Plugin plugin,Player viewer,ConstructionSite site,BlueprintPlan plan,boolean valid){
        this.plugin=plugin;this.viewer=viewer;this.site=site;this.valid=valid;
        anchor=new Location(viewer.getWorld(),site.originX(),site.originY(),site.originZ());
        parts=surface(plan);
    }
    static List<Part> surface(BlueprintPlan plan){
        // Hidden solid interiors need no entities. Transparent and non-cube blocks are kept.
        int[][] adjacent={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        List<Part> result=new ArrayList<>();
        for(var entry:plan.blocks().entrySet()){
            var o=entry.getKey();var b=entry.getValue();if(b.material().isAir())continue;
            boolean hidden=b.material().isOccluding();
            if(hidden)for(var d:adjacent){var n=plan.blocks().get(new BlockOffset(o.x()+d[0],o.y()+d[1],o.z()+d[2]));if(n==null||!n.material().isOccluding()){hidden=false;break;}}
            if(!hidden)result.add(new Part(o,b));
        }
        result.sort(Comparator.comparingInt((Part p)->p.offset().y()).thenComparingInt(p->p.offset().x()).thenComparingInt(p->p.offset().z()));return List.copyOf(result);
    }
    int required(){return parts.size();}
    boolean complete(){return !closed&&next==parts.size()&&entities.size()==parts.size()&&updated==entities.size();}
    boolean near(){return viewer.isOnline()&&viewer.getWorld().equals(anchor.getWorld())&&viewer.getLocation().distanceSquared(anchor)<=96*96;}
    void retarget(ConstructionSite target,boolean valid){
        if(closed||!target.worldId().equals(site.worldId())||target.facing()!=site.facing()||!SuiteScheduler.owns(anchor))throw new IllegalStateException("Подойдите ближе к проекции");
        site=target;this.valid=valid;updated=0;
    }
    private Matrix4f matrix(Part part){
        var target=site.location(anchor.getWorld(),part.offset());
        return new Matrix4f().translation((float)(target.getX()-anchor.getX())+.005f,(float)(target.getY()-anchor.getY())+.005f,(float)(target.getZ()-anchor.getZ())+.005f).scale(.99f);
    }
    void render(int budget){
        if(closed||!near()||!SuiteScheduler.owns(anchor))return;
        int moveBudget=Math.max(32,Math.min(1024,((org.bukkit.plugin.java.JavaPlugin)plugin).getConfig().getInt("settings.construction.placement.moves-per-tick",512)));
        for(int i=0;i<moveBudget&&updated<entities.size();i++,updated++){
            var entity=entities.get(updated);if(!entity.isValid()){close();throw new IllegalStateException("Проекция удалена. Откройте чертёж заново.");}
            entity.setTransformationMatrix(matrix(parts.get(updated)));entity.setGlowColorOverride(valid?Color.LIME:Color.RED);
        }
        if(updated<entities.size())return;
        for(int i=0;i<budget&&next<parts.size();i++){
            Part part=parts.get(next);BlockData data=part.block().material().createBlockData();ConstructionService.orientExpectedState(data,part.block(),site);
            BlockDisplay entity=anchor.getWorld().spawn(anchor,BlockDisplay.class,d->{
                d.setPersistent(false);d.setVisibleByDefault(false);d.setInvulnerable(true);d.setGravity(false);d.setBlock(data);
                d.setTransformationMatrix(matrix(part));
                d.setBrightness(new Display.Brightness(15,15));d.setShadowRadius(0);d.setViewRange(3);
                d.setDisplayWidth(256);d.setDisplayHeight(256);d.setGlowing(true);d.setGlowColorOverride(valid?Color.LIME:Color.RED);
                d.getPersistentDataContainer().set(new NamespacedKey(plugin,"placement_display"),org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);
            });
            if(!entity.isValid()){close();throw new IllegalStateException("Сервер не разрешил показать проекцию");}
            entities.add(entity);next++;updated++;viewer.showEntity(plugin,entity);
        }
    }
    void close(){
        if(closed)return;closed=true;
        // Towny is a required dependency and remains enabled when Builds alone is disabled.
        Plugin cleanup=WorldTasks.cleanupOwner(plugin);
        if(cleanup==null){var towny=Bukkit.getPluginManager().getPlugin("Towny");if(towny!=null&&towny.isEnabled())cleanup=towny;}
        final Plugin owner=cleanup;
        entities.forEach(e->{if(SuiteScheduler.owns(e))e.remove();else if(owner!=null)SuiteScheduler.entity(owner,e,e::remove);});entities.clear();
    }
}
