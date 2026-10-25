package ru.neverland.mintevents.gui;

import java.util.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.mintevents.integration.TownyHook;
import ru.neverland.mintevents.model.FireRepairPlot;
import ru.neverland.mintevents.service.*;

/** Buttons carry server-owned snapshots and are consumed on the player's scheduler. */
public final class FireRepairMenus implements Listener {
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final EventService events;
    private final FireRepairService repairs;
    private final Consumer<Player> home;
    private static final class Screen implements InventoryHolder {
        final UUID viewer; final boolean admin; final Map<Integer,Runnable> actions=new HashMap<>();
        Inventory inventory; boolean used;
        Screen(Player p,boolean admin){viewer=p.getUniqueId();this.admin=admin;}
        @Override public Inventory getInventory(){return inventory;}
    }
    public FireRepairMenus(JavaPlugin plugin,TownyHook towny,EventService events,Consumer<Player> home){
        this.plugin=plugin;this.towny=towny;this.events=events;this.home=home;repairs=new FireRepairService(plugin,towny,events);
    }
    private void authorize(Player p,boolean admin){
        if(!plugin.isEnabled()||SuiteState.frozen(plugin.getName()))throw new IllegalArgumentException("Ремонт временно недоступен");
        if(admin?!FireRepairService.admin(p):!p.hasPermission("mintevents.use"))throw new IllegalArgumentException("Нет доступа к этому разделу");
    }
    private Screen screen(Player p,String title,boolean admin){
        authorize(p,admin);var s=new Screen(p,admin);s.inventory=MenuStyle.inventory(plugin,s,54,title);
        for(int i=45;i<54;i++)button(s,i,Material.GRAY_STAINED_GLASS_PANE," ",List.of(),null);
        button(s,49,Material.BARRIER,"Закрыть",List.of("Вернуться в игру"),p::closeInventory);return s;
    }
    private void button(Screen s,int slot,Material icon,String title,List<String> lore,Runnable action){
        var item=new ItemStack(icon);var meta=item.getItemMeta();
        meta.displayName(MenuStyle.nameComponent(MenuStyle.decode(title)));meta.lore(MenuStyle.loreComponents(lore.stream().map(MenuStyle::decode).toList()));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);item.setItemMeta(meta);s.inventory.setItem(slot,item);if(action!=null)s.actions.put(slot,action);
    }
    private int page(int requested,int count){return Math.max(0,Math.min(requested,Math.max(0,(count-1)/45)));}
    private void pages(Screen s,int page,int count,java.util.function.IntConsumer go){
        if(page>0)button(s,47,Material.ARROW,"Предыдущая страница",List.of(),()->go.accept(page-1));
        button(s,48,Material.PAPER,"Страница "+(page+1),List.of("Всего: "+count),null);
        if((page+1L)*45<count)button(s,51,Material.ARROW,"Следующая страница",List.of(),()->go.accept(page+1));
    }
    public void towns(Player p,int requested){ synchronized(SuiteState.lock()) {
        authorize(p,true);var all=towny.towns().stream().filter(t->!repairs.plots(t.getUUID()).isEmpty()).sorted(Comparator.comparing(t->t.getName())).toList();
        int page=page(requested,all.size());var s=screen(p,"Ремонт • Города",true);int slot=0;
        for(var town:all.stream().skip(page*45L).limit(45).toList())button(s,slot++,Material.GRASS_BLOCK,town.getName(),List.of("Участков с незавершённым ремонтом: "+repairs.plots(town.getUUID()).size(),"Нажмите, чтобы выбрать участок"),()->open(p,town.getUUID(),true,0));
        if(all.isEmpty())button(s,22,Material.LIME_DYE,"Ремонт не требуется",List.of("В городах нет незакрытых повреждений пожара"),null);
        pages(s,page,all.size(),n->towns(p,n));button(s,45,Material.ARROW,"В админ-меню",List.of(),()->{p.closeInventory();p.performCommand("nltadmin");});p.openInventory(s.inventory);
    } }
    public void open(Player p,UUID town,boolean admin,int requested){ synchronized(SuiteState.lock()) {
        authorize(p,admin);
        if(!admin&&(towny.town(p)==null||!towny.town(p).getUUID().equals(town)))throw new IllegalArgumentException("Вы больше не состоите в этом городе");
        var all=repairs.plots(town);int page=page(requested,all.size());var s=screen(p,"Ремонт • Участки",admin);int slot=0;
        for(var plot:all.stream().skip(page*45L).limit(45).toList())button(s,slot++,Material.BRICKS,"Участок "+plot.x()+", "+plot.z(),lines(plot),()->detail(p,plot,admin,page));
        if(all.isEmpty())button(s,22,Material.LIME_DYE,"Ремонт завершён",List.of("В городе нет незакрытых повреждений пожара"),null);
        pages(s,page,all.size(),n->open(p,town,admin,n));button(s,45,Material.ARROW,"Назад",List.of(),()->{if(admin)towns(p,0);else home.accept(p);});
        button(s,53,Material.SPYGLASS,"Обновить",List.of("Показать актуальный список участков"),()->open(p,town,admin,page));p.openInventory(s.inventory);
    } }
    private List<String> lines(FireRepairPlot plot){
        var world=Bukkit.getWorld(plot.world());var d=plot.damage().get(0);
        var town=com.palmergames.bukkit.towny.TownyAPI.getInstance().getTown(plot.town());
        return List.of("Город: "+(town==null?"Удалён":town.getName()),"Мир: "+(world==null?plot.world():world.getName()),
            "Одно из мест: "+d.x()+" / "+d.y()+" / "+d.z(),"Повреждённых мест: "+plot.damage().size(),"Нажмите, чтобы открыть участок");
    }
    private void detail(Player p,FireRepairPlot plot,boolean admin,int page){
        var s=screen(p,"Ремонт • Участок "+plot.x()+", "+plot.z(),admin);
        button(s,13,Material.BRICKS,"Восстановите участок на свой вкус",lines(plot),null);
        if(events.fireUnfinished(plot.town()))button(s,22,Material.CAMPFIRE,"Пожар ещё не завершён",List.of("Дождитесь окончания события","Затем откройте участок заново"),null);
        else if(!repairs.allowed(p,plot,admin))button(s,22,Material.GRAY_DYE,"Подтверждение недоступно",List.of("Обратитесь к владельцу участка или мэру"),null);
        else button(s,22,Material.LIME_CONCRETE,"Ремонт завершён",List.of("Нажмите, когда закончите восстановление","Можно использовать любые блоки","Закрыть ремонт только выбранного участка","Блоки, вещи и деньги останутся как есть"),()->{
            try {repairs.confirm(p,plot,admin);p.sendMessage("§aРемонт участка "+plot.x()+", "+plot.z()+" завершён.");open(p,plot.town(),admin,page);}
            catch(java.io.IOException error){p.closeInventory();p.sendMessage("§cНе удалось сохранить подтверждение. Ремонт остался в списке. Сообщите администратору.");plugin.getLogger().log(java.util.logging.Level.SEVERE,"Подтверждение ремонта после пожара",error);}
        });
        button(s,45,Material.ARROW,"К списку участков",List.of(),()->open(p,plot.town(),admin,page));p.openInventory(s.inventory);
    }
    @EventHandler public void click(InventoryClickEvent e){ synchronized(SuiteState.lock()) {
        if(!(e.getView().getTopInventory().getHolder() instanceof Screen s))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(s.viewer)||s.used||!e.isLeftClick()||e.isShiftClick())return;
        Runnable action=s.actions.get(e.getRawSlot());if(action==null)return;s.used=true;
        SuiteScheduler.entity(plugin,p,()->{
            if(!p.isOnline()||p.getOpenInventory().getTopInventory()!=s.inventory)return;
            try{authorize(p,s.admin);action.run();}
            catch(IllegalArgumentException error){p.closeInventory();p.sendMessage("§e"+error.getMessage());}
            catch(RuntimeException error){p.closeInventory();p.sendMessage("§cНе удалось завершить действие. Откройте раздел заново.");plugin.getLogger().log(java.util.logging.Level.SEVERE,"Меню ремонта",error);}
        });
    } }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Screen)e.setCancelled(true);}
}
