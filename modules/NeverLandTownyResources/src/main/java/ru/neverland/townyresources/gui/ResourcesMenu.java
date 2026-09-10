package ru.neverland.townyresources.gui;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import ru.neverland.townyresources.api.ResourceSnapshot;
import ru.neverland.townyresources.model.*;
import ru.neverland.townyresources.service.*;
import java.util.*;
import static ru.neverland.townyresources.service.Ui.*;
public final class ResourcesMenu implements Listener {
    private final org.bukkit.plugin.Plugin plugin;private final ResourcesService service;
    private static final class View implements InventoryHolder {
        final UUID town;final String kind,project;final int page;final Map<Integer,Runnable> actions=new HashMap<>();Inventory inventory;
        View(UUID town,String kind,String project,int page){this.town=town;this.kind=kind;this.project=project;this.page=page;}
        @Override public Inventory getInventory(){return inventory;}
    }
    public ResourcesMenu(org.bukkit.plugin.Plugin plugin,ResourcesService service){this.plugin=plugin;this.service=service;}
    private boolean access(Player p,UUID id){var town=service.town(p);return p.hasPermission("neverlandtownyresources.admin")||(p.hasPermission("neverlandtownyresources.use")&&town!=null&&town.getUUID().equals(id));}
    public void open(Player p,UUID id,String kind,String project,int requested){
        if(!access(p,id)){tell(p,"&cНет доступа к ресурсам этого города.");return;}
        var s=service.resources(id).orElse(null);if(s==null){tell(p,"Первый расчёт города ещё не выполнен.");return;}
        int pages=Math.max(1,(service.settings().buildings().size()+35)/36);int page=Math.max(0,Math.min(pages-1,requested));var view=new View(id,kind,project,page);
        view.inventory=Bukkit.createInventory(view,54,color("&2"+(kind.equals("buildings")?"Производство города":kind.equals("info")?"Ресурсы здания":"Городские ресурсы")));
        if(kind.equals("buildings"))buildings(p,view,s,pages);else if(kind.equals("info"))detail(p,view,s);else overview(p,view,s);
        button(view,49,Material.SUNFLOWER,"&aОбновить",List.of("Показать текущие запасы и прогноз."),()->open(p,id,kind,project,page));p.openInventory(view.inventory);
    }
    private void overview(Player p,View v,ResourceSnapshot s){
        button(v,4,Material.BELL,"&a"+s.townName(),List.of("Восемь стратегических запасов города.","Цикл: "+service.settings().interval()+" сек.",s.status()),null);
        int[] slots={10,12,14,16,28,30,32,34};int i=0;
        for(var r:Resource.values()){var display=service.settings().display().get(r);long in=s.forecastIncome().get(r),out=s.forecastExpense().get(r);long stock=s.state().balances().get(r);var lore=new ArrayList<String>();
            lore.add("Запас: "+amount(stock)+" / "+amount(s.capacity().get(r)));lore.add("Прогноз за цикл: +"+amount(in)+" / −"+amount(out));lore.add("Баланс за цикл: "+signed(in-out));lore.add("Прошлый цикл: +"+amount(s.state().income().get(r))+" / −"+amount(s.state().expense().get(r)));lore.add("Резерв города: "+amount(s.state().reserves().get(r)));
            if(stock>s.capacity().get(r))lore.add("Запас сохранён сверх уменьшившегося лимита.");if(r==Resource.FOOD||r==Resource.WATER)lore.add("Население требует за цикл: "+amount(s.populationDemand().get(r)));
            lore.add("Нажмите: здания и их вклад.");button(v,slots[i++],icon(display.icon()),"&e"+display.name(),lore,()->open(p,v.town,"buildings",null,0));
        }
        button(v,40,Material.BREAD,"&eСнабжение населения",List.of(s.populationLinked()?"Население: "+s.population():"Связь с населением отключена","Продовольствие: "+percent(s.foodCoverage())+"%; вода: "+percent(s.waterCoverage())+"%","Питание жителей защищено от расходов зданий."),null);
        button(v,45,Material.BRICKS,"&aЗдания и производства",List.of("Приоритет, расход, выпуск и остановки."),()->open(p,v.town,"buildings",null,0));
        button(v,48,Material.CLOCK,"&fСледующий цикл",List.of(s.paused()?"Расчёт приостановлен":"Примерно через "+Math.max(0,(s.nextCycle()-System.currentTimeMillis()+999)/1000)+" сек.","Простой сервера не начисляет ресурсы."),null);
        if(Bukkit.getPluginManager().isPluginEnabled("NeverLandTownySpecialization"))button(v,51,Material.NETHER_STAR,"&eСпециализация",List.of("Направление и уникальные городские здания."),()->p.performCommand("townyspecialization"));
        if(Bukkit.getPluginManager().isPluginEnabled("NeverLandTownyResearch"))button(v,50,Material.ENCHANTED_BOOK,"&aИсследования",List.of("Использовать знания для открытия технологий."),()->p.performCommand("townyresearch"));
        if(Bukkit.getPluginManager().isPluginEnabled("NeverLandTownyLogistics"))button(v,53,Material.CHEST,"&aПредметы и курьеры",List.of("Открыть логистику обычных предметов."),()->p.performCommand("townylogistics"));
    }
    private void buildings(Player p,View v,ResourceSnapshot s,int pages){
        var profiles=new ArrayList<>(service.settings().buildings().values());profiles.sort(Comparator.comparingInt((BuildingProfile b)->s.state().priorities().getOrDefault(b.id(),b.priority())).thenComparing(BuildingProfile::id));
        for(int i=v.page*36;i<Math.min(profiles.size(),(v.page+1)*36);i++){var b=profiles.get(i);var a=s.buildings().get(b.id());List<String> lore=new ArrayList<>();lore.add(a==null?"Ожидает данных":a.status());lore.add("Приоритет: "+s.state().priorities().getOrDefault(b.id(),b.priority()));lore.add("Этапов в работе: "+(a==null?0:a.level()));lore.add("Операций в прогнозе: "+(a==null?0:a.operations()));lore.add("Нажмите для подробностей.");button(v,i%36,icon(b.icon()),"&e"+b.name(),lore,()->open(p,v.town,"info",b.id(),0));}
        if(v.page>0)button(v,45,Material.ARROW,"&aНазад",List.of(),()->open(p,v.town,"buildings",null,v.page-1));button(v,48,Material.BELL,"&aОбзор запасов",List.of("Страница "+(v.page+1)+" / "+pages),()->open(p,v.town,"menu",null,0));if(v.page+1<pages)button(v,53,Material.ARROW,"&aДалее",List.of(),()->open(p,v.town,"buildings",null,v.page+1));
    }
    private void detail(Player p,View v,ResourceSnapshot s){
        var b=service.profile(v.project);var a=s.buildings().get(b.id());int priority=s.state().priorities().getOrDefault(b.id(),b.priority());
        button(v,4,icon(b.icon()),"&e"+b.name(),List.of(a==null?"Ожидает данных":a.status(),"Этапы: "+(a==null?0:a.level())+" / "+b.maximumLevel(),"Приоритет: "+priority),null);
        var produces=new ArrayList<>(amounts("",b.produces(),service.settings()));produces.add("За активный этап; район усиливает выпуск.");button(v,20,Material.HOPPER,"&aПроизводит за этап",produces,null);
        var consumes=new ArrayList<>(amounts("",b.consumes(),service.settings()));consumes.add("При нехватке операция не списывает ресурсы.");button(v,22,Material.FURNACE,"&6Потребляет за этап",consumes,null);
        button(v,24,Material.CHEST,"&eДобавляет вместимость за этап",amounts("",b.capacity(),service.settings()),null);
        boolean paused=s.state().paused().contains(b.id());button(v,31,paused?Material.LIME_DYE:Material.RED_DYE,paused?"&aВозобновить":"&cПриостановить",List.of("Управление доступно мэру и помощникам."),()->manage(p,v,()->service.pause(v.town,b.id(),!paused)));
        button(v,30,Material.FEATHER,"&aВыполнять раньше",List.of("Приоритет −5; меньшее число раньше."),()->manage(p,v,()->service.priority(v.town,b.id(),Math.max(0,priority-5))));button(v,32,Material.ANVIL,"&eВыполнять позже",List.of("Приоритет +5."),()->manage(p,v,()->service.priority(v.town,b.id(),Math.min(100,priority+5))));
        button(v,45,Material.ARROW,"&aВсе здания",List.of(),()->open(p,v.town,"buildings",null,0));
    }
    private interface Edit {void run()throws java.io.IOException;}
    private void manage(Player p,View v,Edit action){var town=service.town(p);if(town==null||!town.getUUID().equals(v.town)||!service.manager(p,town)){tell(p,"&cНедостаточно прав для управления этим городом.");return;}
        try{action.run();open(p,v.town,"info",v.project,0);}catch(Exception ex){tell(p,"&cОперация не завершена: "+ex.getMessage());}}
    private static String percent(double n){return String.format(Locale.forLanguageTag("ru-RU"),"%.1f",n*100);}
    private void button(View v,int slot,Material icon,String name,List<String> lore,Runnable action){var item=new ItemStack(icon);var meta=item.getItemMeta();meta.setDisplayName(color(name));meta.setLore(lore.stream().map(s->color("&7"+s)).toList());item.setItemMeta(meta);v.inventory.setItem(slot,item);if(action!=null)v.actions.put(slot,action);}
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p))return;if(!access(p,v.town)){p.closeInventory();return;}var action=v.actions.get(e.getRawSlot());if(action!=null)Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory().getHolder()==v&&access(p,v.town))action.run();});}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
}
