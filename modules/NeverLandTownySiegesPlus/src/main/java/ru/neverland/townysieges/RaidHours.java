package ru.neverland.townysieges;
import java.util.*;
import java.nio.file.*;
import java.time.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.*;

/** Announced changes never close an already-declared raid. Only declaration consults these rules. */
public final class RaidHours implements Listener {
    private record Entry(RaidWindow current,RaidWindow next,long effective){}
    private final NeverLandTownySiegesPlus plugin;private final Path file;private Map<UUID,Entry> rows=Map.of();
    private boolean enabled;private int minimum,delay;private ZoneId zone;private RaidWindow fallback;
    private static final class View implements InventoryHolder{
        final UUID viewer,town;final Integer start;final int duration;final long expires=System.currentTimeMillis()+120000;Inventory inventory;boolean used;
        View(Player player,UUID town,Integer start,int duration){viewer=player.getUniqueId();this.town=town;this.start=start;this.duration=duration;}
        public Inventory getInventory(){return inventory;}
    }
    public RaidHours(NeverLandTownySiegesPlus plugin,ConfigurationSection config)throws Exception{
        this.plugin=plugin;file=plugin.getDataFolder().toPath().resolve("raid-hours.yml");reload(config);var y=SafeYaml.load(file);
        if(y.contains("schema")&&SafeYaml.integer(y,"schema")!=1)throw new IllegalArgumentException("Неизвестная схема расписания рейдов");
        var next=new HashMap<UUID,Entry>();var root=SafeYaml.section(y,"towns");if(root!=null)for(String key:root.getKeys(false)){var s=SafeYaml.section(root,key);var current=new RaidWindow(Math.toIntExact(SafeYaml.integer(s,"start")),Math.toIntExact(SafeYaml.integer(s,"end")));RaidWindow pending=s.contains("next-start")?new RaidWindow(Math.toIntExact(SafeYaml.integer(s,"next-start")),Math.toIntExact(SafeYaml.integer(s,"next-end"))):null;next.put(UUID.fromString(key),new Entry(current,pending,SafeYaml.integer(s,"effective")));}
        rows=Map.copyOf(next);AtomicFiles.loaded(file);Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    public void reload(ConfigurationSection config){boolean on=config.getBoolean("raid-hours.enabled",true);int min=config.getInt("raid-hours.minimum-hours",4),notice=config.getInt("raid-hours.change-notice-hours",24);var tz=ZoneId.of(config.getString("raid-hours.timezone","Europe/Moscow"));var defaults=new RaidWindow(RaidWindow.minute(config.getString("raid-hours.default-start","00:00")),RaidWindow.minute(config.getString("raid-hours.default-end","00:00")));
        if(min<1||min>24||notice<24||notice>168||defaults.minutes()<min*60)throw new IllegalArgumentException("Неверные ограничения расписания рейдов");enabled=on;minimum=min;delay=notice;zone=tz;fallback=defaults;
    }
    private RaidWindow window(UUID town,long now){var row=rows.get(town);return row==null?fallback:row.next()!=null&&now>=row.effective()?row.next():row.current();}
    public String describe(UUID town){var row=rows.get(town);String result=enabled?window(town,System.currentTimeMillis()).describe()+" ("+zone+")":"Ограничение времени отключено";
        if(row!=null&&row.next()!=null&&row.effective()>System.currentTimeMillis())result+="; с "+Instant.ofEpochMilli(row.effective()).atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"))+": "+row.next().describe();return result;
    }
    public void declaration(UUID target,long now,long duration){if(enabled&&!window(target,now).fits(now,duration,zone))throw new IllegalArgumentException("Рейд не помещается в часы нападения города: "+describe(target)+". Учитываются подготовка и весь бой");}
    private void save(Map<UUID,Entry> next)throws Exception{var y=new YamlConfiguration();y.set("schema",1);for(var e:next.entrySet()){String p="towns."+e.getKey();var row=e.getValue();y.set(p+".start",row.current().start());y.set(p+".end",row.current().end());y.set(p+".effective",row.effective());if(row.next()!=null){y.set(p+".next-start",row.next().start());y.set(p+".next-end",row.next().end());}}AtomicFiles.write(file,y::saveToString);rows=Map.copyOf(next);}
    private UUID mayor(Player player){var town=SiegeService.town(player.getUniqueId());if(town==null||!town.isMayor(TownyAPI.getInstance().getResident(player.getUniqueId())))throw new IllegalArgumentException("Расписание меняет мэр своего города");return town.getUUID();}
    public void set(Player p,RaidWindow selected)throws Exception{UUID town=mayor(p);long now=System.currentTimeMillis();if(selected.minutes()<minimum*60)throw new IllegalArgumentException("Город должен быть доступен для нападений не меньше "+minimum+" часов в сутки");
        var old=rows.get(town);if(old!=null&&old.next()!=null&&old.effective()>now)throw new IllegalArgumentException("Уже объявлено новое расписание. Дождитесь его вступления в силу");
        var next=new HashMap<>(rows);long effective=Math.addExact(now,delay*3600000L);next.put(town,new Entry(window(town,now),selected,effective));save(next);
        AuditTrail.record(plugin,"hours",town+":"+effective,"RAID_HOURS","COMPLETED",AuditTrail.player(p.getUniqueId()),AuditTrail.town(town),AuditTrail.town(town),"",0,"","Новое расписание: "+selected.describe()+" "+zone+"; действует с "+effective);
        plugin.raids().notifyTown(town,"Объявлены часы нападения: "+selected.describe()+" ("+zone+"). Вступят в силу через "+delay+" ч. Начатые войны продолжаются по прежним условиям.");
    }
    private ItemStack item(Material type,String title,List<String> lore){var i=new ItemStack(type);var m=i.getItemMeta();m.displayName(MenuStyle.nameComponent(MenuStyle.decode(title)));m.lore(MenuStyle.loreComponents(lore.stream().map(MenuStyle::decode).toList()));i.setItemMeta(m);return i;}
    public void menu(Player player){var town=SiegeService.town(player.getUniqueId());if(town==null)throw new IllegalArgumentException("Нужен свой город");menu(player,new View(player,town.getUUID(),null,0));}
    private void menu(Player player,View v){v.inventory=MenuStyle.inventory(plugin,v,54,v.start==null?"Часы нападения • начало":v.duration==0?"Часы нападения • длительность":"Подтверждение расписания");
        v.inventory.setItem(4,item(Material.CLOCK,"Расписание города",List.of(describe(v.town),"Зона времени: "+zone,"Минимум открытых часов: "+minimum,"Изменение применяется через "+delay+" ч.","Начатые войны выходом из игры не останавливаются")));
        if(v.duration>0){var w=new RaidWindow(v.start,(v.start+v.duration*60)%1440);v.inventory.setItem(20,item(Material.LIME_DYE,"Подтвердить "+w.describe(),List.of("Изменение будет объявлено всем жителям")));v.inventory.setItem(24,item(Material.ARROW,"Назад",List.of("Выбрать другое время")));}
        else for(int h=0;h<24;h++){if(v.start!=null&&h+1<minimum)continue;v.inventory.setItem(9+h,item(Material.CLOCK,v.start==null?String.format(Locale.ROOT,"Начало %02d:00",h):(h+1)+" ч. для нападения",List.of("Текущее: "+describe(v.town),"Изменить может только мэр")));}
        player.openInventory(v.inventory);
    }
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(v.viewer)||v.used||v.expires<System.currentTimeMillis())return;int slot=e.getRawSlot();
        SuiteScheduler.player(plugin,p,current->{try{if(current.getOpenInventory().getTopInventory()!=v.inventory||v.used)return;if(!mayor(current).equals(v.town))throw new IllegalArgumentException("Город изменился");
            if(v.duration>0&&slot==20){v.used=true;set(current,new RaidWindow(v.start,(v.start+v.duration*60)%1440));current.closeInventory();}
            else if(v.duration>0&&slot==24){v.used=true;menu(current);}
            else if(v.duration==0&&slot>=9&&slot<33){int value=slot-9;if(v.start!=null&&value+1<minimum)return;v.used=true;menu(current,v.start==null?new View(current,v.town,value*60,0):new View(current,v.town,v.start,value+1));}
        }catch(Exception ex){current.sendMessage("§e"+ex.getMessage());}});
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
}
