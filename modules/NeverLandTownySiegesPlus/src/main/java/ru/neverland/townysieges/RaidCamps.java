package ru.neverland.townysieges;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.core.*;

/** Player-built campfire and banner; every world check uses the camp's owning region. */
public final class RaidCamps implements Listener {
    public record Camp(UUID id,UUID attacker,UUID defender,UUID actor,UUID world,int x,int y,int z,int bx,int by,int bz,String banner,long created,String state){}
    private final NeverLandTownySiegesPlus plugin;private final RaidController raids;private final RaidStore storage;private final NamespacedKey marker;
    private volatile CampSettings settings;private volatile Map<UUID,Camp> camps=Map.of();
    private final Map<UUID,Long> alive=new ConcurrentHashMap<>(),checking=new ConcurrentHashMap<>();private final Set<UUID> lost=ConcurrentHashMap.newKeySet();
    public RaidCamps(NeverLandTownySiegesPlus plugin,RaidController raids,CampSettings settings){this.plugin=plugin;this.raids=raids;this.storage=raids.store();this.settings=settings;marker=new NamespacedKey(plugin,"raid_camp");storage.ready().thenCompose(v->read()).whenComplete((rows,error)->{if(error==null)camps=rows;});}
    public Camp camp(UUID town){return camps.get(town);}
    public CampSettings settings(){return settings;}public void reload(CampSettings next){settings=next;}
    static void initialize(Connection db)throws SQLException{try(var s=db.createStatement()){s.execute("CREATE TABLE IF NOT EXISTS raid_camps(id TEXT PRIMARY KEY,attacker TEXT NOT NULL UNIQUE,defender TEXT NOT NULL,actor TEXT NOT NULL,world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,bx INTEGER NOT NULL,by INTEGER NOT NULL,bz INTEGER NOT NULL,banner TEXT NOT NULL,created INTEGER NOT NULL,state TEXT NOT NULL)");}}
    private Map<UUID,Camp> rows()throws SQLException{var out=new HashMap<UUID,Camp>();try(var s=storage.connection().createStatement();var r=s.executeQuery("SELECT * FROM raid_camps")){while(r.next()){var c=new Camp(UUID.fromString(r.getString(1)),UUID.fromString(r.getString(2)),UUID.fromString(r.getString(3)),UUID.fromString(r.getString(4)),UUID.fromString(r.getString(5)),r.getInt(6),r.getInt(7),r.getInt(8),r.getInt(9),r.getInt(10),r.getInt(11),r.getString(12),r.getLong(13),r.getString(14));out.put(c.attacker(),c);}}return Map.copyOf(out);}
    private CompletionStage<Map<UUID,Camp>> read(){return storage.submit(this::rows);}
    private boolean fresh(Camp c){return c!=null&&c.state().equals("READY")&&System.currentTimeMillis()-c.created()<settings.lifetimeSeconds()*1000L&&System.currentTimeMillis()-alive.getOrDefault(c.id(),0L)<5000;}
    public boolean active(UUID attacker,UUID defender){var c=camps.get(attacker);return !settings.enabled()||fresh(c)&&c.defender().equals(defender);}
    private static boolean campfire(Block b){return b.getType()==Material.CAMPFIRE||b.getType()==Material.SOUL_CAMPFIRE;}
    private boolean marked(Block b,Camp camp){return b.getState(false) instanceof TileState s&&camp.id().toString().equals(s.getPersistentDataContainer().get(marker,PersistentDataType.STRING));}
    private void mark(Block b,Camp camp){var state=(TileState)b.getState();state.getPersistentDataContainer().set(marker,PersistentDataType.STRING,camp.id().toString());if(!state.update(false,false))throw new IllegalStateException("Лагерь изменён до регистрации");}
    private boolean wilderness(Block b){return TownyAPI.getInstance().getTown(b.getLocation())==null;}
    public void register(Player player,String targetName)throws Exception{
        if(!settings.enabled())throw new IllegalArgumentException("Осадные лагеря отключены");var own=SiegeService.town(player.getUniqueId());var target=TownyAPI.getInstance().getTown(targetName);
        raids.validateCommander(player,own,target);
        if(raids.raid(own.getUUID(),target.getUUID(),System.currentTimeMillis()).isPresent())throw new IllegalArgumentException("Лагерь нельзя переносить во время рейда");
        if(!WorldTasks.ownsBox(player.getLocation(),6))throw new IllegalArgumentException("Подойдите ближе к костру");
        Block fire=player.getTargetBlockExact(6);if(fire==null||!campfire(fire))throw new IllegalArgumentException("Постройте костёр и знамя рядом, затем посмотрите на костёр");
        if(!WorldTasks.ownsBox(fire.getLocation(),4))throw new IllegalArgumentException("Подойдите ближе к месту лагеря");
        if(!wilderness(fire))throw new IllegalArgumentException("Лагерь нужен на свободной земле за границей города");
        Block banner=null;for(int x=-3;x<=3&&banner==null;x++)for(int y=-2;y<=3&&banner==null;y++)for(int z=-3;z<=3;z++){Block b=fire.getRelative(x,y,z);if(b.getType().name().endsWith("_BANNER")&&wilderness(b)){banner=b;break;}}
        if(banner==null)throw new IllegalArgumentException("Поставьте знамя не дальше трёх блоков от костра");
        boolean near=false;int radius=settings.borderDistance();for(int x=-radius;x<=radius&&!near;x++)for(int z=-radius;z<=radius;z++){
            if(Math.hypot(Math.max(0,Math.abs(x)-.5),Math.max(0,Math.abs(z)-.5))>radius)continue;
            var town=TownyAPI.getInstance().getTown(new Location(fire.getWorld(),fire.getX()+x,fire.getY(),fire.getZ()+z));if(town!=null&&town.getUUID().equals(target.getUUID())){near=true;break;}
        }
        if(!near)throw new IllegalArgumentException("Лагерь должен стоять не дальше "+radius+" блоков от границы выбранного города");
        var camp=new Camp(UUID.randomUUID(),own.getUUID(),target.getUUID(),player.getUniqueId(),fire.getWorld().getUID(),fire.getX(),fire.getY(),fire.getZ(),banner.getX(),banner.getY(),banner.getZ(),banner.getType().name(),System.currentTimeMillis(),"READY");
        storage.submit(()->{try(var p=storage.connection().prepareStatement("INSERT OR REPLACE INTO raid_camps VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){p.setString(1,camp.id().toString());p.setString(2,camp.attacker().toString());p.setString(3,camp.defender().toString());p.setString(4,camp.actor().toString());p.setString(5,camp.world().toString());p.setInt(6,camp.x());p.setInt(7,camp.y());p.setInt(8,camp.z());p.setInt(9,camp.bx());p.setInt(10,camp.by());p.setInt(11,camp.bz());p.setString(12,camp.banner());p.setLong(13,camp.created());p.setString(14,camp.state());p.executeUpdate();}return rows();}).whenComplete((rows,error)->{
            if(error!=null){raids.reply(player,RaidController.problem(error));return;}camps=rows;
            SuiteScheduler.region(plugin,new Location(fire.getWorld(),camp.x(),camp.y(),camp.z()),()->{try{
                Block f=fire.getWorld().getBlockAt(camp.x(),camp.y(),camp.z()),b=fire.getWorld().getBlockAt(camp.bx(),camp.by(),camp.bz());
                if(!campfire(f)||!b.getType().name().equals(camp.banner())||!wilderness(f)||!wilderness(b))throw new IllegalStateException("Постройки лагеря изменились");mark(f,camp);mark(b,camp);alive.put(camp.id(),System.currentTimeMillis());audit(camp,"READY","Лагерь зарегистрирован");raids.reply(player,"Лагерь готов. Соберите у костра минимум "+settings.minimumArmy()+" мобилизованных и откройте /sieges camp");
            }catch(Exception failure){lost(camp);raids.reply(player,failure.getMessage());}});
        });
    }
    private Camp nearby(Player player,Town target){var own=SiegeService.town(player.getUniqueId());var c=own==null?null:camps.get(own.getUUID());if(!fresh(c)||!c.defender().equals(target.getUUID()))throw new IllegalArgumentException("Сначала постройте и зарегистрируйте осадный лагерь: /sieges camp <город>");var at=player.getLocation();if(!at.getWorld().getUID().equals(c.world())||at.distanceSquared(new Location(at.getWorld(),c.x()+.5,c.y(),c.z()+.5))>settings.musterRadius()*settings.musterRadius())throw new IllegalArgumentException("Начните рейд у костра своего осадного лагеря");return c;}
    public void muster(Player actor,Town target,Runnable start){
        Camp camp=nearby(actor,target);Set<UUID> soldiers=ConcurrentHashMap.newKeySet();var players=List.copyOf(Bukkit.getOnlinePlayers());var left=new AtomicInteger(players.size());var complete=new AtomicBoolean();
        Runnable done=()->{if(complete.compareAndSet(false,true))SuiteScheduler.player(plugin,actor,p->{try{var current=nearby(p,target);if(!current.id().equals(camp.id()))throw new IllegalArgumentException("Лагерь изменился");if(soldiers.size()<settings.minimumArmy())throw new IllegalArgumentException("У костра собрано "+soldiers.size()+" / "+settings.minimumArmy()+" мобилизованных. Подойдите всей армией к лагерю");start.run();}catch(Exception error){raids.reply(p,error.getMessage());}});};
        Runnable retired=()->{if(left.decrementAndGet()==0)done.run();};
        for(Player p:players)SuiteScheduler.entityLater(plugin,p,()->{try{var town=SiegeService.town(p.getUniqueId());var at=p.getLocation();if(p.isOnline()&&!p.isDead()&&p.getGameMode()==GameMode.SURVIVAL&&town!=null&&town.getUUID().equals(camp.attacker())&&at.getWorld().getUID().equals(camp.world())&&at.distanceSquared(new Location(at.getWorld(),camp.x()+.5,camp.y(),camp.z()+.5))<=settings.musterRadius()*settings.musterRadius()&&raids.mobilized(p.getUniqueId()))soldiers.add(p.getUniqueId());}catch(Exception ignored){}finally{retired.run();}},retired,1);
        SuiteScheduler.get().runTaskLater(plugin,done,40);if(players.isEmpty())done.run();
    }
    public void pulse(){if(!settings.enabled())return;long now=System.currentTimeMillis();checking.entrySet().removeIf(e->now-e.getValue()>5000);for(var c:camps.values())if(c.state().equals("READY")&&checking.putIfAbsent(c.id(),now)==null){
        World world=Bukkit.getWorld(c.world());if(world==null){checking.remove(c.id());lost(c);continue;}
        SuiteScheduler.region(plugin,new Location(world,c.x(),c.y(),c.z()),()->{try{
            if(!world.isChunkLoaded(c.x()>>4,c.z()>>4)||!world.isChunkLoaded(c.bx()>>4,c.bz()>>4)||!WorldTasks.ownsBox(new Location(world,c.x(),c.y(),c.z()),4)){alive.remove(c.id());return;}
            Block fire=world.getBlockAt(c.x(),c.y(),c.z()),banner=world.getBlockAt(c.bx(),c.by(),c.bz());
            if(System.currentTimeMillis()-c.created()>=settings.lifetimeSeconds()*1000L||!campfire(fire)||!banner.getType().name().equals(c.banner())||!marked(fire,c)||!marked(banner,c)||!wilderness(fire)||!wilderness(banner)){lost(c);return;}alive.put(c.id(),System.currentTimeMillis());
        }finally{checking.remove(c.id());}});
    }}
    private void lost(Camp c){if(!c.state().equals("READY")||!lost.add(c.id()))return;alive.remove(c.id());
        storage.submit(()->{try(var p=storage.connection().prepareStatement("UPDATE raid_camps SET state='LOST' WHERE id=? AND state='READY'")){p.setString(1,c.id().toString());p.executeUpdate();}return rows();}).whenComplete((rows,error)->{if(error==null)camps=rows;});
        audit(c,"LOST","Костёр или знамя лагеря уничтожены / лагерь недоступен");
        if(raids.raid(c.attacker(),c.defender(),System.currentTimeMillis()).isPresent()){raids.cancel(Bukkit.getConsoleSender(),c.defender());raids.notifyTown(c.attacker(),"Осадный лагерь потерян. Рейд остановлен.");raids.notifyTown(c.defender(),"Осадный лагерь противника потерян. Нападение остановлено.");}
    }
    private void changed(Block block){for(var c:camps.values())if(c.state().equals("READY")&&c.world().equals(block.getWorld().getUID())&&(block.getX()==c.x()&&block.getY()==c.y()&&block.getZ()==c.z()||block.getX()==c.bx()&&block.getY()==c.by()&&block.getZ()==c.bz()))lost(c);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void breakCamp(BlockBreakEvent e){try(var guard=SuiteState.guard()){changed(e.getBlock());}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void explode(EntityExplodeEvent e){try(var guard=SuiteState.guard()){e.blockList().forEach(this::changed);}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void explode(BlockExplodeEvent e){try(var guard=SuiteState.guard()){e.blockList().forEach(this::changed);}}
    private void audit(Camp c,String outcome,String note){AuditTrail.record(plugin,"camp:"+outcome,c.id().toString(),"RAID_CAMP",outcome,AuditTrail.player(c.actor()),AuditTrail.town(c.attacker()),AuditTrail.town(c.defender()),"",0,"",note+"; "+c.world()+" "+c.x()+","+c.y()+","+c.z());}
    private static final class View implements InventoryHolder{final UUID viewer,camp;Inventory inventory;View(UUID viewer,UUID camp){this.viewer=viewer;this.camp=camp;}public Inventory getInventory(){return inventory;}}
    public void menu(Player player){var own=SiegeService.town(player.getUniqueId());Camp camp=own==null?null:camps.get(own.getUUID());if(camp==null||!fresh(camp))throw new IllegalArgumentException("Постройте костёр и знамя у чужого города, посмотрите на костёр: /sieges camp <город>");
        var target=TownyAPI.getInstance().getTown(camp.defender());if(target==null)throw new IllegalArgumentException("Город не найден");var h=new View(player.getUniqueId(),camp.id());h.inventory=MenuStyle.inventory(plugin,h,27,"Осадный лагерь • "+target.getName());
        h.inventory.setItem(11,SiegeMenu.item(Material.CAMPFIRE,"&6Осадный лагерь",List.of("&fЦель: "+target.getName(),"&fСбор армии: радиус "+settings.musterRadius()+" блоков","&fНужно мобилизованных: "+settings.minimumArmy(),"&fЕсли костёр или знамя уничтожены,","&fнападение прекращается.")));
        h.inventory.setItem(13,SiegeMenu.item(Material.CROSSBOW,"&cНачать рейд",List.of("&fСоберите армию рядом с костром.","&fНачать может мобилизованный мэр","&fили генерал армии города.","&fЗатем начнётся время подготовки.")));
        var lootLore=new ArrayList<String>();lootLore.add("&fПробейте двери и стены боевым TNT.");lootLore.add("&fРазрушьте основную кафедру ратуши.");
        if(plugin.loot().settings().enabled())try{var q=plugin.loot().protection(target.getUUID());lootLore.add("&fКазна: "+q.effective().moneyBps()/100.0+"%; склад: "+q.effective().resourcesBps()/100.0+"%.");lootLore.add("&fЗащита снижает добычу на "+q.reductionBps()/100.0+"%.");}catch(Exception unavailable){lootLore.add("&eРасчёт защиты временно недоступен.");}else lootLore.add("&7Добыча за кафедру отключена.");
        lootLore.add("&fПолучатель — ваш город.");lootLore.add("&fДобыча выдаётся один раз за рейд.");h.inventory.setItem(15,SiegeMenu.item(Material.LECTERN,"&eЦель — кафедра ратуши",lootLore));
        h.inventory.setItem(18,SiegeMenu.item(Material.CHEST,"Снабжение лагеря",List.of("Запасы, расход и доставка логистикой")));
        h.inventory.setItem(22,SiegeMenu.item(Material.BARRIER,"&eЗакрыть",List.of("Вернуться в игру")));player.openInventory(h.inventory);
    }
    @EventHandler public void click(InventoryClickEvent e){try(var guard=SuiteState.guard()){if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(v.viewer)||SuiteState.frozen(plugin.getName()))return;if(e.getRawSlot()==18){try{plugin.supplies().open(p);}catch(Exception ex){raids.reply(p,ex.getMessage());}return;}if(e.getRawSlot()==22){p.closeInventory();return;}if(e.getRawSlot()==13){var own=SiegeService.town(p.getUniqueId());var c=own==null?null:camps.get(own.getUUID());if(c==null||!c.id().equals(v.camp)){p.closeInventory();return;}var target=TownyAPI.getInstance().getTown(c.defender());try{raids.declare(p,target==null?"":target.getName());}catch(Exception error){raids.reply(p,error.getMessage());}}}}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
}
