package ru.neverland.townybuilds.construction;

import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.*;
import ru.neverland.core.*;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.integration.TownyHook;

/** Choice precedes Towny's price confirmation. Reservations survive a crash during town creation. */
public final class CityStyles implements Listener,CommandExecutor,AutoCloseable {
    private record Reservation(UUID mayor,String name,String style,long created,long expires){}
    private static final class View implements InventoryHolder {
        final UUID viewer,town;final String name;final BuildingStyle selected;final long expires=System.currentTimeMillis()+120000;
        Inventory inventory;boolean used;
        View(Player p,UUID town,String name,BuildingStyle selected){viewer=p.getUniqueId();this.town=town;this.name=name;this.selected=selected;}
        public Inventory getInventory(){return inventory;}
    }
    private final JavaPlugin plugin;private final DataStore data;private final TownyHook towny;
    private final Path file;private Map<UUID,Reservation> reservations=Map.of();private boolean ready,command;
    public CityStyles(JavaPlugin plugin,DataStore data,TownyHook towny){
        this.plugin=plugin;this.data=data;this.towny=towny;file=plugin.getDataFolder().toPath().resolve("city-style-reservations.yml");
        try{load();reconcile();ready=true;}catch(Exception ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Выбор стиля временно недоступен; сохранённые города не изменены",ex);}
        Bukkit.getPluginManager().registerEvents(this,plugin);command=towny.registerTownCommand("style",this);
        var standalone=plugin.getCommand("townystyle");if(standalone!=null)standalone.setExecutor(this);
    }
    private void load()throws Exception{
        var y=SafeYaml.load(file);if(y.contains("schema")&&SafeYaml.integer(y,"schema")!=1)throw new IllegalArgumentException("Неизвестная схема стилей");
        var out=new LinkedHashMap<UUID,Reservation>();var root=SafeYaml.section(y,"choices");if(root!=null)for(String key:root.getKeys(false)){
            var s=SafeYaml.section(root,key);UUID id=UUID.fromString(key);out.put(id,new Reservation(id,SafeYaml.text(s,"name"),BuildingStyle.parse(SafeYaml.text(s,"style")).id(),SafeYaml.integer(s,"created"),SafeYaml.integer(s,"expires")));
        }reservations=Map.copyOf(out);AtomicFiles.loaded(file);
    }
    private void save(Map<UUID,Reservation> next)throws Exception{
        var y=new YamlConfiguration();y.set("schema",1);for(var r:next.values()){
            String p="choices."+r.mayor();y.set(p+".name",r.name());y.set(p+".style",r.style());y.set(p+".created",r.created());y.set(p+".expires",r.expires());
        }AtomicFiles.write(file,y::saveToString);reservations=Map.copyOf(next);
    }
    private void apply(UUID city,BuildingStyle style)throws Exception{
        var state=data.town(city);String old=state.buildingStyle();boolean chosen=state.styleChosen();
        if(chosen)return;state.buildingStyle(style.id(),true);try{data.markDirty();data.saveOrThrow();}catch(Exception e){state.buildingStyle(old,chosen);throw e;}
    }
    private void reconcile()throws Exception{
        var next=new LinkedHashMap<>(reservations);long now=System.currentTimeMillis();
        for(var r:reservations.values()){var t=towny.town(r.name());
            if(t!=null&&t.getMayor()!=null&&t.getMayor().getUUID().equals(r.mayor())&&t.getRegistered()>=r.created()-1000){apply(t.getUUID(),BuildingStyle.parse(r.style()));next.remove(r.mayor());}
            else if(r.expires()<now)next.remove(r.mayor());
        }if(!next.equals(reservations))save(next);
    }
    private void available(){if(!ready||!data.writable()||!plugin.isEnabled()||SuiteState.frozen(plugin.getName()))throw new IllegalArgumentException("Выбор стиля временно недоступен. Обратитесь к администратору");}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void before(PreNewTownEvent e){try(var guard=SuiteState.guard()){
        if(SuiteState.frozen(plugin.getName()))return;
        Player p=e.getPlayer();try{available();if(!p.hasPermission("neverlandtownybuilds.style"))throw new IllegalArgumentException("Нет права выбора стиля города");
            var r=reservations.get(p.getUniqueId());if(r!=null&&r.name().equalsIgnoreCase(e.getTownName())&&r.expires()>=System.currentTimeMillis())return;
            e.setCancelled(true);e.setCancelMessage("Выберите стиль будущего города в открывшемся меню. Деньги ещё не списаны");
            SuiteScheduler.player(plugin,p,current->open(current,null,e.getTownName(),null));
        }catch(Exception ex){e.setCancelled(true);e.setCancelMessage(ex.getMessage());}
    }}
    @EventHandler(priority=EventPriority.MONITOR)
    public void created(NewTownEvent e){try(var guard=SuiteState.guard()){
        if(!ready||SuiteState.frozen(plugin.getName()))return;var t=e.getTown();if(t.getMayor()==null)return;var r=reservations.get(t.getMayor().getUUID());
        if(r==null||!r.name().equalsIgnoreCase(t.getName())||r.expires()<System.currentTimeMillis())return;
        try{apply(t.getUUID(),BuildingStyle.parse(r.style()));var next=new LinkedHashMap<>(reservations);next.remove(r.mayor());save(next);
            AuditTrail.record(plugin,"style",t.getUUID().toString(),"CITY_STYLE","COMPLETED",AuditTrail.player(r.mayor()),AuditRecord.Party.system(),AuditTrail.town(t.getUUID()),"",0,"","Стиль города: "+BuildingStyle.parse(r.style()).title());
        }catch(Exception ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Город создан; выбор стиля сохранён в резерве и восстановится при запуске",ex);}
    }}
    private ItemStack item(Material m,String title,List<String> lore){var item=new ItemStack(m);var meta=item.getItemMeta();meta.displayName(MenuStyle.nameComponent(MenuStyle.decode(title)));meta.lore(MenuStyle.loreComponents(lore.stream().map(MenuStyle::decode).toList()));item.setItemMeta(meta);return item;}
    private void open(Player p,UUID city,String name,BuildingStyle selected){
        available();var v=new View(p,city,name,selected);v.inventory=MenuStyle.inventory(plugin,v,27,selected==null?"Стиль города":"Подтверждение стиля");
        if(selected==null){for(var style:BuildingStyle.values())v.inventory.setItem(11+style.ordinal()*2,item(style.icon(),style.title(),List.of(
            switch(style){case MEDIEVAL->"Камень, дерево и традиционные крыши";case MODERN->"Светлые фасады, кварц и голубое стекло";case FANTASY->"Вишнёвое дерево, пурпурные крыши и светлый камень";},"Стиль будущих зданий города","Цена и прочность от выбора не меняются","Нажмите, чтобы выбрать")));
        }else{
            v.inventory.setItem(13,item(selected.icon(),selected.title(),List.of(city==null?"Будущий город: "+name:"Стиль для новых зданий города","Существующие площадки сохранят свой облик","Выбор закрепляется за городом")));
            v.inventory.setItem(20,item(Material.LIME_DYE,"Подтвердить",List.of("Сохранить выбранный стиль")));v.inventory.setItem(24,item(Material.ARROW,"Назад",List.of("Выбрать другой стиль")));
        }p.openInventory(v.inventory);
    }
    private void confirm(Player p,View v)throws Exception{
        available();if(!p.hasPermission("neverlandtownybuilds.style"))throw new IllegalArgumentException("Нет права выбора стиля");
        if(v.town==null){if(towny.town(p)!=null)throw new IllegalArgumentException("Вы уже состоите в городе");
            var next=new LinkedHashMap<>(reservations);long now=System.currentTimeMillis();next.values().removeIf(r->r.expires()<now);
            if(next.size()>=1000&&!next.containsKey(p.getUniqueId()))throw new IllegalArgumentException("Выбор стиля занят, повторите позже");
            next.put(p.getUniqueId(),new Reservation(p.getUniqueId(),v.name,v.selected.id(),now,now+600000));save(next);p.closeInventory();p.performCommand("town new "+v.name);
        }else{var city=towny.town(p);if(city==null||!city.getUUID().equals(v.town)||!towny.isMayor(p,city))throw new IllegalArgumentException("Стиль выбирает мэр своего города");
            var state=data.town(v.town);if(state.styleChosen())throw new IllegalArgumentException("Стиль уже выбран: "+BuildingStyle.parse(state.buildingStyle()).title());
            if(!state.placements().isEmpty())throw new IllegalArgumentException("Сначала завершите выбор места уже выданных чертежей");
            apply(v.town,v.selected);p.closeInventory();p.sendMessage("§aВыбран стиль города: "+v.selected.title()+". Новые здания будут построены в этом стиле.");
            AuditTrail.record(plugin,"style",v.town.toString(),"CITY_STYLE","COMPLETED",AuditTrail.player(p.getUniqueId()),AuditRecord.Party.system(),AuditTrail.town(v.town),"",0,"","Стиль будущих зданий: "+v.selected.title());
        }
    }
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(v.viewer)||v.used||v.expires<System.currentTimeMillis())return;int slot=e.getRawSlot();if(slot<0||slot>=27)return;
        SuiteScheduler.player(plugin,p,current->{try{if(current.getOpenInventory().getTopInventory()!=v.inventory||v.used)return;
            if(v.selected==null){if(slot==11||slot==13||slot==15){v.used=true;open(current,v.town,v.name,BuildingStyle.values()[(slot-11)/2]);}}
            else if(slot==24){v.used=true;open(current,v.town,v.name,null);}else if(slot==20){v.used=true;confirm(current,v);}
        }catch(Exception ex){current.sendMessage("§e"+ex.getMessage());}});
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){try(var guard=SuiteState.guard()){try{
        available();if(!(sender instanceof Player p))throw new IllegalArgumentException("Выбор стиля доступен в игре");if(!p.hasPermission("neverlandtownybuilds.style"))throw new IllegalArgumentException("Нет доступа к стилям");
        var city=towny.town(p);if(city==null)throw new IllegalArgumentException("Создайте город: /t new <название>. Выбор стиля откроется перед созданием");var state=data.town(city.getUUID());
        if(state.styleChosen()){p.sendMessage("§aСтиль города: "+BuildingStyle.parse(state.buildingStyle()).title()+". Сохранённые здания сохраняют свой вариант чертежа.");return true;}
        if(!towny.isMayor(p,city))throw new IllegalArgumentException("Стиль выбирает мэр города");open(p,city.getUUID(),city.getName(),null);
    }catch(Exception ex){sender.sendMessage("§e"+ex.getMessage());}return true;}}
    public void close(){if(command)towny.unregisterTownCommand("style");}
}
