package ru.neverland.townydistricts.service;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.TownBlockSettingsChangedEvent;
import com.palmergames.bukkit.towny.event.plot.toggle.PlotToggleMobsEvent;
import com.palmergames.bukkit.towny.object.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.core.*;
import ru.neverland.townydistricts.api.MobFarmPlot;
import java.util.*;

/** Uses Towny's persisted plot flag, so disabling this addon never destroys a working farm. */
public final class MobFarmService implements Listener {
    public record Confirmation(UUID operation,TownBlock block,UUID town,UUID world,int x,int z,int size,String permissions,boolean allow){}
    private final JavaPlugin plugin;
    private final Map<UUID,String> entered=new HashMap<>();
    private final Map<UUID,Long> previews=new HashMap<>();
    private final BukkitTask task;
    public MobFarmService(JavaPlugin plugin){this.plugin=plugin;task=SuiteScheduler.get().runTaskTimer(plugin,this::tick,20,20);}
    public boolean enabled(){return plugin.getConfig().getBoolean("mob-farms.enabled",true);}
    public void stop(){task.cancel();entered.clear();previews.clear();}
    public static boolean admin(Player p){return p.isOp()||p.hasPermission("neverlandtownydistricts.admin");}
    public Town ownTown(Player p){var r=TownyAPI.getInstance().getResident(p);return r==null?null:r.getTownOrNull();}
    public Town viewTown(Player p){var at=TownyAPI.getInstance().getTown(p.getLocation());return admin(p)&&at!=null?at:ownTown(p);}
    public boolean manager(Player p,Town t){
        if(t==null)return false;if(admin(p))return true;
        var r=TownyAPI.getInstance().getResident(p);
        return r!=null&&t.equals(r.getTownOrNull())&&(t.isMayor(r)||ru.neverland.core.CouncilAccess.check(p,t.getUUID(),"mob_farms",r.hasTownRank("assistant")||p.hasPermission("neverlandtownydistricts.farms.manage")));
    }
    public TownBlock block(UUID world,int x,int z){
        World w=Bukkit.getWorld(world);if(w==null)return null;
        return TownyAPI.getInstance().getTownBlock(new WorldCoord(w.getName(),x,z));
    }
    public Optional<MobFarmPlot> at(UUID world,int x,int z){
        return snapshot(block(world,Math.floorDiv(x,Coord.getCellSize()),Math.floorDiv(z,Coord.getCellSize())));
    }
    public Optional<MobFarmPlot> snapshot(TownBlock b){
        if(b==null||b.getTownOrNull()==null)return Optional.empty();
        var w=b.getWorld();var c=b.getWorldCoord();
        String status=MobFarmPolicy.status(w.isUsingTowny(),w.hasWorldMobs(),w.isForceTownMobs()||b.getTownOrNull().isAdminEnabledMobs(),b.getPermissions().mobs);
        String text=switch(status){case "ALLOWED"->"Мобы разрешены — зона фермы";case "FORCED_ALLOWED"->"Мобы разрешены администратором";
            case "TOWNY_DISABLED"->"Городская защита в мире отключена";case "WORLD_RESTRICTED"->"Мобы ограничены настройками мира";default->"Защита от мобов включена";};
        return Optional.of(new MobFarmPlot(b.getTownOrNull().getUUID(),w.getUUID(),w.getName(),c.getX(),c.getZ(),Coord.getCellSize(),b.getPermissions().mobs,status,text));
    }
    public List<MobFarmPlot> plots(UUID town){
        Town t=TownyAPI.getInstance().getTown(town);if(t==null)return List.of();
        return t.getTownBlocks().stream().map(this::snapshot).flatMap(Optional::stream)
            .sorted(Comparator.comparing(MobFarmPlot::worldName).thenComparingInt(MobFarmPlot::plotX).thenComparingInt(MobFarmPlot::plotZ)).toList();
    }
    public Confirmation prepare(Player p,TownBlock b,boolean allow){
        requireEnabled();validate(p,b,allow);
        var c=b.getWorldCoord();return new Confirmation(UUID.randomUUID(),b,b.getTownOrNull().getUUID(),b.getWorld().getUUID(),c.getX(),c.getZ(),Coord.getCellSize(),b.getPermissions().toString(),allow);
    }
    private void validate(Player p,TownBlock b,boolean allow){
        if(b==null||!manager(p,b.getTownOrNull()))throw new IllegalStateException("Менять защиту может мэр, помощник или уполномоченный житель своего города");
        if(b.getTownOrNull().isRuined())throw new IllegalStateException("Город находится в руинах");
        var w=b.getWorld();MobFarmPolicy.validate(w.isUsingTowny(),w.hasWorldMobs(),w.isForceTownMobs()||b.getTownOrNull().isAdminEnabledMobs(),b.hasPlotObjectGroup(),allow);
    }
    public void apply(Player p,Confirmation expected){ synchronized(SuiteState.lock()){
        requireEnabled();TownBlock b=block(expected.world(),expected.x(),expected.z());
        if(b!=expected.block()||b.getTownOrNull()==null||!expected.town().equals(b.getTownOrNull().getUUID())||expected.size()!=Coord.getCellSize()
                ||!expected.permissions().equals(b.getPermissions().toString()))throw new IllegalStateException("Участок изменился. Откройте подтверждение заново");
        validate(p,b,expected.allow());if(b.getPermissions().mobs==expected.allow())return;
        var event=new PlotToggleMobsEvent(b,p,expected.allow());Bukkit.getPluginManager().callEvent(event);
        if(event.isCancelled())throw new IllegalStateException("Изменение запрещено правилами сервера");
        // A listener can transfer the plot or change permissions; revalidate before the write.
        if(block(expected.world(),expected.x(),expected.z())!=b||!expected.town().equals(b.getTownOrNull()==null?null:b.getTownOrNull().getUUID())
                ||!expected.permissions().equals(b.getPermissions().toString()))throw new IllegalStateException("Участок изменился во время проверки");
        validate(p,b,expected.allow());
        if(!audit(p,expected,"REQUESTED"))throw new IllegalStateException("Журнал недоступен. Настройка участка не изменена");
        boolean before=b.getPermissions().mobs,changed=b.isChanged();
        b.getPermissions().mobs=expected.allow();b.setChanged(true);
        try{if(!com.palmergames.bukkit.towny.TownyUniverse.getInstance().getDataSource().saveTownBlock(b))throw new IllegalStateException("Towny не смог сохранить настройку участка");}
        catch(RuntimeException ex){b.getPermissions().mobs=before;b.setChanged(changed);throw ex;}
        Bukkit.getPluginManager().callEvent(new TownBlockSettingsChangedEvent(b));
        audit(p,expected,"DONE");
        p.sendMessage(expected.allow()?"§6Мобы разрешены на выбранном участке. Огородите ферму: здесь они могут атаковать игроков.":"§aЗащита от мобов включена на выбранном участке. Towny снова может удалять здесь враждебных мобов.");
        previews.put(p.getUniqueId(),System.currentTimeMillis()+30000);
    }}
    private boolean audit(Player p,Confirmation c,String outcome){return AuditTrail.record(plugin,"MOB_FARM_"+outcome,c.operation().toString(),"MOB_FARM_ZONE",outcome,
        new AuditRecord.Party("PLAYER",p.getUniqueId().toString(),p.getName(),""),AuditRecord.Party.unknown(),AuditTrail.town(c.town()),"",0,"0",
        "world="+c.world()+"; plot="+c.x()+","+c.z()+"; mobs="+c.allow());}
    public void show(Player p){requireEnabled();previews.put(p.getUniqueId(),System.currentTimeMillis()+30000);p.sendMessage("§6Оранжевые границы — мобы разрешены. §aЗелёные — защита включена. §7Показ рядом с вами на 30 секунд.");}
    public void requireEnabled(){if(SuiteState.frozen(plugin.getName())||!enabled())throw new IllegalStateException("Управление зонами ферм временно отключено; настройки участков сохранены");}
    private void tick(){
        long now=System.currentTimeMillis();previews.entrySet().removeIf(e->e.getValue()<now||Bukkit.getPlayer(e.getKey())==null);
        if(!enabled())return;
        SuiteScheduler.players(plugin,p->{notifyArea(p);if(previews.containsKey(p.getUniqueId()))render(p);});
    }
    private void notifyArea(Player p){
        if(!plugin.getConfig().getBoolean("mob-farms.entry-notifications",true))return;
        var loc=p.getLocation();var info=at(p.getWorld().getUID(),loc.getBlockX(),loc.getBlockZ()).orElse(null);
        String key=info==null?"outside":info.townId()+":"+info.status();String previous=entered.put(p.getUniqueId(),key);
        if(key.equals(previous))return;
        if(info!=null&&(info.protectionDisabled()||previous!=null&&!previous.equals("outside")))
            p.sendActionBar(Component.text((info.protectionDisabled()?"⚠ ":"✓ ")+info.description()));
        else if(info==null&&previous!=null&&!previous.equals("outside"))p.sendActionBar(Component.text("Вы вышли за пределы города"));
    }
    private void render(Player p){
        var town=viewTown(p);if(town==null)return;var loc=p.getLocation();int size=Coord.getCellSize(),cx=Math.floorDiv(loc.getBlockX(),size),cz=Math.floorDiv(loc.getBlockZ(),size),count=0;
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            var info=snapshot(block(p.getWorld().getUID(),cx+dx,cz+dz)).orElse(null);if(info==null||!info.townId().equals(town.getUUID()))continue;
            var color=info.status().equals("PROTECTED")?Color.LIME:info.protectionDisabled()?Color.ORANGE:Color.GRAY;
            var dust=new Particle.DustOptions(color,1.2f);
            for(int side=0;side<4;side++)for(int offset=0;offset<=size;offset+=Math.max(1,size/4)){
                double x=info.minBlockX()+(side<2?(side==1?size:0):offset),z=info.minBlockZ()+(side<2?offset:(side==3?size:0));
                if(Math.hypot(x-loc.getX(),z-loc.getZ())>48||count++>=400)continue;
                p.spawnParticle(Particle.DUST,x,loc.getY()+0.15,z,1,0,0,0,0,dust);
            }
        }
    }
    /** Towny's native hook is also used by splash/lingering potions and dispenser arrows. */
    @EventHandler(priority=EventPriority.LOWEST)
    public void dispenser(com.palmergames.bukkit.towny.event.damage.TownyDispenserDamageEntityEvent e){
        synchronized(SuiteState.lock()){
            if(!e.isCancelled()||SuiteState.frozen(plugin.getName())||!enabled()
                    ||!plugin.getConfig().getBoolean("mob-farms.dispenser-effects",true))return;
            if(!(e.getEntity() instanceof org.bukkit.entity.Enemy)||e.getEntity() instanceof Player)return;
            if(com.palmergames.bukkit.towny.hooks.PluginIntegrations.getInstance().isNPC(e.getEntity()))return;
            var source=e.getDispenserBlock();if(source==null)return;
            var from=TownyAPI.getInstance().getTownBlock(source.getLocation());
            var to=e.getTownBlock();
            if(from==null||to==null||from.getTownOrNull()==null||to.getTownOrNull()==null)return;
            if(!from.getWorld().isUsingTowny()||!to.getWorld().isUsingTowny())return;
            if(!source.getWorld().getUID().equals(e.getLocation().getWorld().getUID())
                    ||!from.getTownOrNull().getUUID().equals(to.getTownOrNull().getUUID())||to.getTownOrNull().isRuined())return;
            if(snapshot(from).map(MobFarmPlot::protectionDisabled).orElse(false)
                    &&snapshot(to).map(MobFarmPlot::protectionDisabled).orElse(false))e.setCancelled(false);
        }
    }
    @EventHandler public void quit(PlayerQuitEvent e){synchronized(SuiteState.lock()){entered.remove(e.getPlayer().getUniqueId());previews.remove(e.getPlayer().getUniqueId());}}
}
