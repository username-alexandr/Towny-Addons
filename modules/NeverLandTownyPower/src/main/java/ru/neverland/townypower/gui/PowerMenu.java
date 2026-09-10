package ru.neverland.townypower.gui;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import ru.neverland.townypower.api.PowerSnapshot;
import ru.neverland.townypower.model.*;
import ru.neverland.townypower.service.*;
import java.util.*;
import static ru.neverland.townypower.service.Ui.*;
public final class PowerMenu implements Listener {
    private final org.bukkit.plugin.Plugin plugin;private final PowerService service;
    private static final class View implements InventoryHolder {
        final UUID town;final int page;final String filter,project;final Map<Integer,Runnable> actions=new HashMap<>();Inventory inventory;
        View(UUID town,int page,String filter,String project){this.town=town;this.page=page;this.filter=filter;this.project=project;}
        @Override public Inventory getInventory(){return inventory;}
    }
    public PowerMenu(org.bukkit.plugin.Plugin plugin,PowerService service){this.plugin=plugin;this.service=service;}
    private boolean access(Player p,UUID town){var t=service.town(p);return p.hasPermission("neverlandtownypower.admin")||(p.hasPermission("neverlandtownypower.use")&&t!=null&&t.getUUID().equals(town));}
    public void open(Player p,UUID town,int requested,String filter,String project){
        if(!access(p,town)){tell(p,"&cНет доступа к энергосети этого города.");return;}
        var snapshot=service.power(town).orElse(null);if(snapshot==null){tell(p,"Ожидается расчёт энергосети.");return;}
        var profiles=service.settings().profiles().values().stream().filter(PowerProfile::relevant).filter(b->!filter.equals("generators")||b.producer()).filter(b->!filter.equals("consumers")||b.consumer()).toList();
        int pages=Math.max(1,(profiles.size()+35)/36),page=Math.max(0,Math.min(pages-1,requested));var v=new View(town,page,filter,project);v.inventory=Bukkit.createInventory(v,54,color("&6Энергия города"));
        if(project!=null){var profile=service.settings().profiles().get(project);if(profile==null){tell(p,"Здание не найдено.");return;}detail(p,v,snapshot,profile);}
        else{
            for(int i=page*36;i<Math.min(profiles.size(),(page+1)*36);i++){var profile=profiles.get(i);var lines=lore(snapshot,profile);lines.add("Нажмите: уровни и управление питанием.");button(v,i%36,icon(profile.icon()),"&e"+profile.name(),lines,()->open(p,town,page,filter,profile.id()));}
            var g=snapshot.grid();button(v,40,Material.REDSTONE,"&eБаланс мощности",List.of("Выработка: "+g.generation()+" ед.","Запрос: "+g.demand()+"; подано: "+g.supplied(),"Свободно: "+g.spare()+"; не обеспечено: "+g.deficit(),snapshot.paused()?"&c"+snapshot.status():"Обновление: каждые "+service.settings().interval()+" сек.","Энергия не накапливается.","Приоритет: 0 раньше 100.","Зданию требуется питание в полном объёме."),null);
            button(v,38,Material.WIND_CHARGE,"&aИсточники",List.of("Мельница → колесо → генератор → электростанция"),()->open(p,town,0,"generators",null));
            button(v,42,Material.REDSTONE_LAMP,"&aПотребители",List.of("Продвинутые здания и чудеса света."),()->open(p,town,0,"consumers",null));
            if(page>0)button(v,45,Material.ARROW,"&aНазад",List.of(),()->open(p,town,page-1,filter,null));if(page+1<pages)button(v,53,Material.ARROW,"&aДалее",List.of(),()->open(p,town,page+1,filter,null));
        }
        button(v,49,Material.CLOCK,"&aОбновить",List.of("Страница "+(page+1)+" / "+pages),()->open(p,town,page,filter,project));
        button(v,48,Material.PAPER,"&eВся энергосеть",List.of(),()->open(p,town,0,"all",null));
        if(Bukkit.getPluginManager().getPlugin("NeverLandTownyUpkeep")!=null)button(v,50,Material.GOLD_INGOT,"&eОбслуживание",List.of("Источникам энергии тоже требуется содержание."),()->p.performCommand("townyupkeep"));p.openInventory(v.inventory);
    }
    private List<String> lore(PowerSnapshot s,PowerProfile p){var a=s.grid().buildings().get(p.id());var lines=new ArrayList<String>();lines.add(s.paused()?"&c"+s.status():a==null?"Не построено":(a.powered()?"&a":"&c")+a.status().title);lines.add("Завершённый уровень: "+(a==null?0:a.level()));lines.add("Выработка: "+(a==null?0:a.generation())+" ед.");lines.add("Требуется: "+(a==null?0:a.demand())+"; подано: "+(a==null?0:a.supplied()));lines.add("Приоритет: "+s.state().priorities().getOrDefault(p.id(),p.priority()));return lines;}
    private void detail(Player player,View v,PowerSnapshot s,PowerProfile p){button(v,13,icon(p.icon()),"&e"+p.name(),lore(s,p),null);
        var levels=new ArrayList<String>();for(int i=1;i<=p.generation().size();i++)levels.add("Уровень "+i+": +"+p.generation(i)+" / −"+p.demand(i)+" ед.");levels.add("Выработка указана без районного бонуса.");button(v,22,Material.PAPER,"&eМощность по уровням",levels,null);
        if(p.relevant()){
            boolean stopped=s.state().stopped().contains(p.id());button(v,30,stopped?Material.LIME_DYE:Material.RED_DYE,stopped?"&aВозобновить работу":"&cОстановить здание",List.of("Доступно мэру и управляющим.","Остановка сохраняет постройку.","Содержание продолжает начисляться."),()->change(player,v,p.id(),!stopped,null));
            int priority=s.state().priorities().getOrDefault(p.id(),p.priority());
            button(v,32,Material.REPEATER,"&eПовысить приоритет",List.of("Текущий: "+priority,"Новый: "+Math.max(0,priority-10),"Меньшее число получает питание раньше."),()->change(player,v,p.id(),null,Math.max(0,priority-10)));
            button(v,33,Material.COMPARATOR,"&eПонизить приоритет",List.of("Новый: "+Math.min(100,priority+10)),()->change(player,v,p.id(),null,Math.min(100,priority+10)));
        }
        button(v,45,Material.ARROW,"&aК списку",List.of(),()->open(player,v.town,v.page,v.filter,null));
    }
    private void change(Player p,View v,String project,Boolean stop,Integer priority){var town=service.town(p);if(town==null||!town.getUUID().equals(v.town)||!service.manager(p,town)){tell(p,"&cНужны права мэра, помощника или управляющего этого города.");return;}try{service.change(v.town,project,stop,priority);open(p,v.town,v.page,v.filter,v.project);}catch(Exception ex){tell(p,"&c"+ex.getMessage());}}
    private void button(View v,int slot,Material icon,String name,List<String> lore,Runnable action){var item=new ItemStack(icon);var meta=item.getItemMeta();meta.setDisplayName(color(name));meta.setLore(lore.stream().map(s->color("&7"+s)).toList());item.setItemMeta(meta);v.inventory.setItem(slot,item);if(action!=null)v.actions.put(slot,action);}
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p))return;if(!access(p,v.town)){p.closeInventory();return;}var action=v.actions.get(e.getRawSlot());if(action!=null)Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory().getHolder()==v&&access(p,v.town))action.run();});}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
}
