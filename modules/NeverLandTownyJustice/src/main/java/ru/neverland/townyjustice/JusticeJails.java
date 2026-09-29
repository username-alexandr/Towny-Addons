package ru.neverland.townyjustice;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.object.jail.*;
import com.palmergames.bukkit.towny.utils.JailUtil;
/** Uses Towny's public jail registry, save results, release rules and teleport system. */
public final class JusticeJails {
    private final Map<UUID,Long> releaseAttempts=new HashMap<>();
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private record Safety(boolean safe,long at){}
    private final Map<Location,Safety> safety=new HashMap<>();private final Set<Location> checking=new HashSet<>();
    public JusticeJails(org.bukkit.plugin.java.JavaPlugin plugin){this.plugin=plugin;}
    private boolean remotelySafe(Location at){
        if(ru.neverland.core.SuiteScheduler.owns(at)){boolean value=safe(at);safety.put(at.clone(),new Safety(value,System.currentTimeMillis()));return value;}
        Location key=at.clone();if(checking.add(key)) {
            at.getWorld().getChunkAtAsync(at).whenComplete((chunk,error)->ru.neverland.core.SuiteState.run(()->{
                if(error!=null||!plugin.isEnabled()){checking.remove(key);return;}
                ru.neverland.core.SuiteScheduler.region(plugin,key,()->{checking.remove(key);safety.put(key,new Safety(safe(key),System.currentTimeMillis()));});
            }));
        }
        Safety cached=safety.get(key);return cached!=null&&cached.safe()&&System.currentTimeMillis()-cached.at()<5000;
    }
    public Location point(JusticePrison p){ synchronized (ru.neverland.core.SuiteState.lock()) {World world=Bukkit.getWorld(p.world());if(world==null)throw new IllegalStateException("Мир тюрьмы не загружен");return new Location(world,p.x()+.5,p.y(),p.z()+.5);} }
    private TownBlock plot(JusticePrison p){var block=TownyAPI.getInstance().getTownBlock(point(p));if(block==null||block.getTownOrNull()==null||!block.getTownOrNull().getUUID().equals(p.town())||block.hasResident())throw new IllegalArgumentException("Нужен муниципальный участок своего города");return block;}
    public boolean safe(Location at){ synchronized (ru.neverland.core.SuiteState.lock()) {if(!ru.neverland.core.SuiteScheduler.owns(at)||at.getWorld()==null||at.getY()<=at.getWorld().getMinHeight()||at.getY()+1>=at.getWorld().getMaxHeight())return false;var feet=at.getBlock();var head=feet.getRelative(0,1,0);var floor=feet.getRelative(0,-1,0);return feet.isPassable()&&head.isPassable()&&!Set.of(Material.FIRE,Material.SOUL_FIRE,Material.SWEET_BERRY_BUSH,Material.POWDER_SNOW,Material.WITHER_ROSE).contains(feet.getType())&&!Set.of(Material.FIRE,Material.SOUL_FIRE,Material.SWEET_BERRY_BUSH,Material.POWDER_SNOW,Material.WITHER_ROSE).contains(head.getType())&&!feet.isLiquid()&&!head.isLiquid()&&floor.getType().isSolid()&&!Set.of(Material.MAGMA_BLOCK,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.CACTUS).contains(floor.getType());} }
    public void validateNew(JusticePrison p){ synchronized (ru.neverland.core.SuiteState.lock()) {var block=plot(p);if(!block.getType().equals(TownBlockType.RESIDENTIAL)&&!block.isJail())throw new IllegalArgumentException("Нужен обычный муниципальный участок или существующая тюрьма");if(block.getJail()!=null&&!block.getJail().getUUID().equals(p.id()))throw new IllegalArgumentException("На участке уже есть другая тюрьма; используйте /justice prison bind <имя>");if(!safe(point(p)))throw new IllegalArgumentException("Точка камеры должна иметь безопасный пол и два свободных блока");} }
    public void ensure(JusticePrison p)throws Exception{ synchronized (ru.neverland.core.SuiteState.lock()) {
        validateNew(p);var block=plot(p);var town=TownyAPI.getInstance().getTown(p.town());var universe=TownyUniverse.getInstance();var jail=universe.getJail(p.id());
        if(jail==null){jail=new Jail(p.id(),town,block,List.of(point(p)));universe.registerJail(jail);}else if(!jail.getTown().getUUID().equals(p.town())||jail.getTownBlock()!=block)throw new IllegalStateException("UUID тюрьмы занят другой записью");
        if(!town.hasJails()||!town.hasJail(jail))town.addJail(jail);block.setJail(jail);block.setType(TownBlockType.JAIL);block.setName(p.name());var data=universe.getDataSource();if(!data.saveJail(jail)||!data.saveTownBlock(block)||!data.saveTown(town))throw new IllegalStateException("Towny не подтвердил сохранение тюрьмы");
    } }
    public Jail usable(JusticePrison p){ synchronized (ru.neverland.core.SuiteState.lock()) {if(p==null||!p.ready())return null;try{var block=plot(p);var jail=TownyUniverse.getInstance().getJail(p.id());return jail!=null&&block.isJail()&&block.getJail()==jail&&jail.hasJailCell(0)&&remotelySafe(jail.getJailCellLocations().get(0))?jail:null;}catch(RuntimeException e){return null;}} }
    public void arrest(Player hunter,Player target,JusticeCase c,JusticePrison p){ synchronized (ru.neverland.core.SuiteState.lock()) {if(!ru.neverland.core.SuiteScheduler.owns(target))throw new IllegalStateException("Подойдите к игроку и повторите поимку");var jail=usable(p);if(jail==null)throw new IllegalStateException("Тюрьма недоступна");var resident=TownyAPI.getInstance().getResident(target.getUniqueId());if(resident==null||resident.isJailed()||JailUtil.isQueuedToBeJailed(resident))throw new IllegalStateException("Игрок уже заключён или доставляется в тюрьму");JailUtil.jailResident(resident,jail,1,c.hours(),JailReason.MAYOR,hunter);} }
    public boolean confirmed(JusticeCase c,JusticePrison p){ synchronized (ru.neverland.core.SuiteState.lock()) {var jail=usable(p);var resident=TownyAPI.getInstance().getResident(c.subject());var player=Bukkit.getPlayer(c.subject());if(jail==null||resident==null)return false;boolean inside=false;Location at=ru.neverland.core.WorldTasks.position(player);if(at!=null&&at.getWorld().equals(jail.getJailCellLocations().get(0).getWorld()))inside=at.distanceSquared(jail.getJailCellLocations().get(0))<=9;
        if(!CaptureRules.confirmed(c.prison(),resident.isJailed()&&resident.getJail()!=null?resident.getJail().getUUID():null,player!=null&&player.isOnline(),JailUtil.isQueuedToBeJailed(resident),inside))return false;
        if(!TownyUniverse.getInstance().getDataSource().saveResident(resident))throw new IllegalStateException("Towny не подтвердил сохранение заключённого");return true;
    } }
    public boolean ownsCustody(JusticeCase c){ synchronized (ru.neverland.core.SuiteState.lock()) {var r=TownyAPI.getInstance().getResident(c.subject());return r!=null&&r.isJailed()&&r.getJail()!=null&&r.getJail().getUUID().equals(c.prison());} }
    public boolean queued(JusticeCase c){ synchronized (ru.neverland.core.SuiteState.lock()) {var r=TownyAPI.getInstance().getResident(c.subject());return r!=null&&JailUtil.isQueuedToBeJailed(r);} }
    public void release(JusticeCase c){ synchronized (ru.neverland.core.SuiteState.lock()) {var r=TownyAPI.getInstance().getResident(c.subject());var online=Bukkit.getPlayer(c.subject());if(online!=null&&!ru.neverland.core.SuiteScheduler.owns(online)){ru.neverland.core.SuiteScheduler.player(plugin,online,p->release(c));return;}if(ownsCustody(c)){
            if(!r.isOnline()){JailUtil.unJailResident(r);Bukkit.getPluginManager().callEvent(new com.palmergames.bukkit.towny.event.resident.ResidentUnjailEvent(r,UnJailReason.PARDONED));}
            else{long now=System.currentTimeMillis();long delay=Math.max(5000L,TownySettings.getTeleportWarmupTime()*1000L+5000L);Long started=releaseAttempts.get(c.id());if(started==null||now-started>=delay){releaseAttempts.put(c.id(),now);JailUtil.unJailResident(r,UnJailReason.PARDONED);}}
        }if(!ownsCustody(c))releaseAttempts.remove(c.id());if(r!=null&&!TownyUniverse.getInstance().getDataSource().saveResident(r))throw new IllegalStateException("Towny не подтвердил освобождение");} }
}
