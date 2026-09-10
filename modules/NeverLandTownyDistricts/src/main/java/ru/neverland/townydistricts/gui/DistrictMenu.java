package ru.neverland.townydistricts.gui;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import ru.neverland.townydistricts.command.DistrictCommand;
import ru.neverland.townydistricts.model.*;
import ru.neverland.townydistricts.service.DistrictService;
import java.util.*;

public final class DistrictMenu implements Listener {
    private static final class View implements InventoryHolder{
        final UUID town;final Map<Integer,String[]> actions=new HashMap<>();Inventory inventory;
        View(UUID town){this.town=town;}public Inventory getInventory(){return inventory;}
    }
    private final Plugin plugin;private final DistrictService service;private final DistrictCommand command;
    public DistrictMenu(Plugin plugin,DistrictService service,DistrictCommand command){this.plugin=plugin;this.service=service;this.command=command;}
    public void open(Player player,UUID townId,String districtId,int requestedPage){
        var town=service.town(player);if(town==null||!town.getUUID().equals(townId)||!player.hasPermission("neverlandtownydistricts.use"))return;
        View v=new View(townId);v.inventory=Bukkit.createInventory(v,54,color("&2"+(districtId==null?"Районы города":"Район: "+service.require(townId,districtId).name())));
        boolean manager=service.manager(player,town);
        if(districtId==null){
            var districts=service.list(townId);int pages=Math.max(1,(districts.size()+26)/27),page=Math.max(0,Math.min(pages-1,requestedPage));
            for(int i=page*27;i<Math.min(districts.size(),(page+1)*27);i++){
                var d=districts.get(i);var evaluation=service.view(townId).evaluation();
                long buildings=evaluation.districts().entrySet().stream().filter(e->e.getValue().equals(d.id())&&evaluation.multipliers().containsKey(e.getKey())).count();
                button(v,i%27,material(d.type().icon),"&e"+d.name(),new String[]{"info",d.id()},"ID: "+d.id(),"Тип: "+d.type().title,
                        "Участков: "+d.cells().size(),"Зданий с бонусом: "+buildings,
                        "Бонус: "+percent(service.districtMultiplier(d)),Cell.connected(d.cells())?"Территория связна":"Соедините разорванные части района");
            }
            if(manager){int slot=36;for(var type:DistrictType.values()){
                String id=type.id()+"_1";int n=1;Set<String> used=new HashSet<>();districts.forEach(d->used.add(d.id()));while(used.contains(id))id=type.id()+"_"+(++n);
                button(v,slot++,material(type.icon),"&aСоздать: "+type.title,new String[]{"create",id,type.id()},command.selection(player),"Весь участок должен принадлежать городу.");
            }}
            if(page>0)button(v,45,Material.ARROW,"&aНазад",new String[]{"list",String.valueOf(page-1)});
            button(v,46,Material.BOOK,"&fУправление районами",new String[]{"help"},"Страница "+(page+1)+" / "+pages,"Семь типов районов; бонусы завершённым зданиям.");
            button(v,47,Material.STICK,"&fВыделение территории",manager?new String[]{"clear"}:new String[]{"help"},command.selection(player),"/t district pos1 и pos2","Нажатие сбросит выделение.");
            button(v,48,Material.GLOWSTONE_DUST,"&eПоказать границы",new String[]{"map"});
            button(v,49,Material.SUNFLOWER,"&aОбновить",new String[]{"list",String.valueOf(page)});
            if(page+1<pages)button(v,53,Material.ARROW,"&aДалее",new String[]{"list",String.valueOf(page+1)});
        }else{
            var d=service.require(townId,districtId);var view=service.view(townId);var evaluation=view.evaluation();
            var projects=service.settings().projects().entrySet().stream().filter(e->e.getValue().type()==d.type()||districtId.equals(evaluation.districts().get(e.getKey()))).toList();
            int pages=Math.max(1,(projects.size()+35)/36),page=Math.max(0,Math.min(pages-1,requestedPage));
            for(int i=page*36;i<Math.min(projects.size(),(page+1)*36);i++){
                var entry=projects.get(i);String id=entry.getKey();var p=entry.getValue();
                var physical=view.buildings().stream().filter(b->b.id().equals(id)).findFirst().orElse(null);
                String assigned=evaluation.districts().get(id);double bonus=districtId.equals(assigned)?service.multiplier(townId,id):1;
                String status=view.paused()?"Постройки недоступны; бонусы приостановлены":physical==null?"Здание ещё не размещено":!physical.completed()?"Завершите строительство":
                    assigned==null?"Вся площадка должна быть в одном районе":!districtId.equals(assigned)?"Расположено в другом районе":p.type()!=d.type()?"Зданию нужен другой тип района":"Подходящее здание в этом районе";
                v.inventory.setItem(i%36,item(material(p.icon()),"&e"+p.name(),status,"Подходящий район: "+p.type().title,"Бонус здесь: "+percent(bonus)));
            }
            button(v,40,material(d.type().icon),"&f"+d.name(),new String[]{"info",districtId,String.valueOf(page)},"ID: "+d.id(),"Участков: "+d.cells().size(),
                    evaluation.industrialCombinations().contains(districtId)?"Промышленная связка активна":"Бонусы: подходящий район "+percent(1+service.settings().matching()),
                    d.type()==DistrictType.INDUSTRIAL?"Для связки нужны все настроенные участники.":"Бонусы действуют на показатели зданий.",
                    d.type()==DistrictType.INDUSTRIAL?String.join(", ",service.settings().requires().stream().sorted().map(id->service.settings().projects().get(id).name()).toList()):"Снабжение, рабочие места и городские службы.");
            if(manager){button(v,45,Material.GRASS_BLOCK,"&aДобавить территорию",new String[]{"claim",districtId},command.selection(player));
                button(v,46,Material.SHEARS,"&eУбрать текущий участок",new String[]{"unclaim",districtId},"Участки должны остаться связанными.");}
            button(v,48,Material.GLOWSTONE_DUST,"&eПоказать границы",new String[]{"map"});button(v,49,Material.BELL,"&aВсе районы",new String[]{"list"});
            if(page>0)button(v,52,Material.ARROW,"&aНазад",new String[]{"info",districtId,String.valueOf(page-1)});
            if(page+1<pages)button(v,53,Material.ARROW,"&aДалее",new String[]{"info",districtId,String.valueOf(page+1)});
        }
        player.openInventory(v.inventory);
    }
    private static String percent(double n){return String.format(Locale.ROOT,"+%.0f%%",(n-1)*100);}
    private Material material(String name){Material m=Material.matchMaterial(name);return m==null||!m.isItem()?Material.BRICKS:m;}
    private void button(View v,int slot,Material material,String title,String[] action,String... lore){v.inventory.setItem(slot,item(material,title,lore));v.actions.put(slot,action);}
    private ItemStack item(Material material,String title,String... lore){var item=new ItemStack(material);var meta=item.getItemMeta();meta.setDisplayName(color(title));meta.setLore(Arrays.stream(lore).map(s->color("&7"+s)).toList());item.setItemMeta(meta);return item;}
    private static String color(String text){return ChatColor.translateAlternateColorCodes('&',text);}
    @EventHandler public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof View view))return;event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player)||event.getRawSlot()<0||event.getRawSlot()>=54)return;
        var action=view.actions.get(event.getRawSlot());if(action==null)return;
        Bukkit.getScheduler().runTask(plugin,()->{if(!player.isOnline()||player.getOpenInventory().getTopInventory().getHolder()!=view)return;
            var town=service.town(player);if(town==null||!town.getUUID().equals(view.town)){player.closeInventory();return;}
            command.onCommand(player,null,"townydistricts",action);
        });
    }
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof View)event.setCancelled(true);}
}
