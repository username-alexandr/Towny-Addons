package ru.neverland.townyupkeep.gui;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import ru.neverland.townyupkeep.api.UpkeepSnapshot;
import ru.neverland.townyupkeep.service.*;
import ru.neverland.townyupkeep.model.Cost;
import java.util.*;
import static ru.neverland.townyupkeep.service.Ui.*;
public final class UpkeepMenu implements Listener {
    private final org.bukkit.plugin.Plugin plugin;private final UpkeepService service;
    private static final class View implements InventoryHolder {
        final UUID town;final int page;final String project;final Map<Integer,Runnable> actions=new HashMap<>();Inventory inventory;
        View(UUID town,int page,String project){this.town=town;this.page=page;this.project=project;}
        @Override public Inventory getInventory(){return inventory;}
    }
    public UpkeepMenu(org.bukkit.plugin.Plugin plugin,UpkeepService service){this.plugin=plugin;this.service=service;}
    private boolean access(Player p,UUID town){var t=service.town(p);return p.hasPermission("neverlandtownyupkeep.admin")||(p.hasPermission("neverlandtownyupkeep.use")&&t!=null&&t.getUUID().equals(town));}
    public void open(Player p,UUID town,int requested,String project){
        if(!access(p,town)){tell(p,"&cНет доступа к обслуживанию этого города.");return;}
        var buildings=service.buildings(town);int pages=Math.max(1,(buildings.size()+35)/36);int page=Math.max(0,Math.min(pages-1,requested));var v=new View(town,page,project);
        v.inventory=Bukkit.createInventory(v,54,color("&6Обслуживание города"));
        if(project!=null){var b=buildings.stream().filter(s->s.key().project().equals(project)).findFirst().orElse(null);if(b==null){tell(p,"Построенное здание не найдено.");return;}detail(p,v,b);}
        else {
            for(int i=page*36;i<Math.min(buildings.size(),(page+1)*36);i++){var b=buildings.get(i);var lore=lore(b);lore.add("Нажмите: расходы и повтор оплаты.");button(v,i%36,icon(b.icon()),(b.active()?"&a":"&c")+b.name(),lore,()->open(p,town,page,b.key().project()));}
            long active=buildings.stream().filter(UpkeepSnapshot::active).count();long money=buildings.stream().filter(b->service.settings().profiles().get(b.key().project()).enabled()).mapToLong(b->b.cost().money()).sum();
            button(v,40,Material.BELL,"&eСостояние города",List.of("Обслуживается: "+active+"; без обслуживания: "+(buildings.size()-active),"Оценка денег за период: "+Cost.format(money,2),"Период: "+service.settings().period()+" сек. работы сервера","Расход растёт с размером города и уровнем зданий.",service.fault()?"Ошибка расчёта — обратитесь к администратору":"Здания сохраняются при неоплате."),null);
            Map<String,Long> totals=new TreeMap<>();for(var b:buildings)if(service.settings().profiles().get(b.key().project()).enabled())b.cost().resources().forEach((id,n)->totals.merge(id,n,Math::addExact));
            var costs=new ArrayList<String>();for(var row:totals.entrySet())if(row.getValue()>0)costs.add(resourceName(row.getKey())+": "+Cost.format(row.getValue(),3));if(costs.isEmpty())costs.add("Нет расхода стратегических ресурсов.");costs.add("Оценка полного периода для всех зданий города.");
            button(v,41,Material.PAPER,"&eРасход ресурсов города",costs,null);
            if(buildings.isEmpty())button(v,22,Material.BRICKS,"&eНет завершённых зданий",List.of("Нужна построенная площадка на территории города."),null);
            if(page>0)button(v,45,Material.ARROW,"&aНазад",List.of(),()->open(p,town,page-1,null));if(page+1<pages)button(v,53,Material.ARROW,"&aДалее",List.of(),()->open(p,town,page+1,null));
        }
        button(v,49,Material.CLOCK,"&aОбновить",List.of("Страница "+(page+1)+" / "+pages),()->open(p,town,page,project));
        button(v,48,Material.CHEST,"&eРесурсы города",List.of("Открыть стратегические запасы."),()->p.performCommand("townyresources"));if(Bukkit.getPluginManager().getPlugin("NeverLandTownyPower")!=null)button(v,50,Material.REDSTONE,"&eЭнергоснабжение",List.of("Для работы зданию может требоваться питание."),()->p.performCommand("townypower"));p.openInventory(v.inventory);
    }
    private List<String> lore(UpkeepSnapshot b){var lines=new ArrayList<String>();lines.add(b.active()?"&aОБСЛУЖИВАЕТСЯ":"&cБЕЗ ОБСЛУЖИВАНИЯ");lines.add(b.status());lines.add("Завершённый уровень: "+b.level());lines.add("До оплаты / повтора: "+b.seconds()+" сек. работы сервера");lines.add("Оценка следующего периода:");lines.addAll(cost(b.cost()));return lines;}
    private void detail(Player p,View v,UpkeepSnapshot b){
        button(v,13,icon(b.icon()),"&e"+b.name(),lore(b),null);
        if(b.invoice()!=null){var lines=new ArrayList<>(cost(b.invoice().cost()));lines.add("Цена текущего счёта уже зафиксирована.");if(p.hasPermission("neverlandtownyupkeep.admin"))lines.add("Счёт: "+b.invoice().id());button(v,22,Material.PAPER,"&eТекущий счёт",lines,null);}
        button(v,31,Material.GOLD_INGOT,"&eПовторить оплату",List.of("Из казны и стратегических запасов города.","Доступно мэру и управляющим.","Активное здание не оплачивается заранее.","Автоматический повтор: каждые "+service.settings().retry()+" сек."),()->{
            var town=service.town(p);if(town==null||!town.getUUID().equals(v.town)||!service.manager(p,town)){tell(p,"&cНужны права мэра, помощника или управляющего.");return;}
            try{service.retry(v.town,b.key().project());tell(p,"Здание поставлено в очередь оплаты.");open(p,v.town,v.page,v.project);}catch(Exception ex){tell(p,"&c"+ex.getMessage());}
        });
        button(v,45,Material.ARROW,"&aВсе здания",List.of(),()->open(p,v.town,v.page,null));
    }
    private void button(View v,int slot,Material icon,String name,List<String> lore,Runnable action){var item=new ItemStack(icon);var meta=item.getItemMeta();meta.setDisplayName(color(name));meta.setLore(lore.stream().map(s->color("&7"+s)).toList());item.setItemMeta(meta);v.inventory.setItem(slot,item);if(action!=null)v.actions.put(slot,action);}
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p))return;if(!access(p,v.town)){p.closeInventory();return;}var action=v.actions.get(e.getRawSlot());if(action!=null)Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory().getHolder()==v&&access(p,v.town))action.run();});}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
}
