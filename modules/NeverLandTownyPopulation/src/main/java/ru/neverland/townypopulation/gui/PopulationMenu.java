package ru.neverland.townypopulation.gui;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import ru.neverland.townypopulation.api.PopulationSnapshot;
import ru.neverland.townypopulation.service.*;
import java.util.*;
import static ru.neverland.townypopulation.service.Messages.number;

public final class PopulationMenu implements Listener {
    private final org.bukkit.plugin.Plugin plugin;
    private final PopulationService service;
    private final Messages messages;
    private static final class View implements InventoryHolder {
        final UUID town; final boolean buildings; final int page;
        Inventory inventory;
        View(UUID town, boolean buildings, int page) { this.town=town; this.buildings=buildings; this.page=page; }
        @Override public Inventory getInventory() { return inventory; }
    }
    public PopulationMenu(org.bukkit.plugin.Plugin plugin, PopulationService service, Messages messages) {
        this.plugin=plugin; this.service=service; this.messages=messages;
    }
    public void open(Player player, UUID townId, boolean buildings, int requestedPage) {
        if (!player.hasPermission("neverlandtownypopulation.use")) { messages.send(player,"no-permission"); return; }
        service.refreshView();
        var s=service.population(townId).orElse(null);
        if (s==null || !canView(player,townId)) { messages.send(player,"no-town"); return; }
        int pages=Math.max(1,(service.settings().buildings().size()+44)/45);
        int page=Math.max(0,Math.min(pages-1,requestedPage));
        View view=new View(townId,buildings,page);
        view.inventory=Bukkit.createInventory(view,54,Messages.color("&2"+(buildings?"Здания и население":"Население города")));
        if (buildings) sources(view,s,pages); else overview(view,s);
        view.inventory.setItem(49,item(Material.SUNFLOWER,"&aОбновить", "Нажмите, чтобы обновить показатели."));
        player.openInventory(view.inventory);
    }
    private boolean canView(Player player, UUID town) {
        if (player.hasPermission("neverlandtownypopulation.admin")) return true;
        var resident=com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(player);
        var current=resident==null?null:resident.getTownOrNull();
        return current!=null && current.getUUID().equals(town);
    }
    private void overview(View v, PopulationSnapshot s) {
        var c=s.capacity(); var m=s.metrics();
        v.inventory.setItem(4,item(Material.BELL,"&a"+s.townName(),"Городское население и условия жизни."));
        v.inventory.setItem(11,item(Material.PLAYER_HEAD,"&aНаселение: "+s.population(),
                "Прошлый цикл: "+signed(s.lastChange()),"Прогноз за цикл: "+signed(s.paused()?0:m.change()),
                "Дробный прирост накапливается."));
        v.inventory.setItem(13,item(Material.RED_BED,"&eЖильё: "+s.population()+" / "+c.housing(),
                "Свободных мест: "+Math.max(0,c.housing()-s.population()),
                "Жилой квартал, приют и постоялый двор."));
        v.inventory.setItem(15,item(Material.SUNFLOWER,"&eДовольство: "+number(m.happiness())+"%",
                "Зависит от снабжения, жилья и работы.","Культура и городские службы повышают его."));
        v.inventory.setItem(21,item(Material.IRON_PICKAXE,"&eЗанятость и безработица",
                "Рабочих мест: "+c.jobs(),"Трудоспособных: "+m.workforce(),"Работают: "+m.employed(),
                "Безработных: "+m.unemployed()+" ("+number(m.unemployment()*100)+"%)"));
        v.inventory.setItem(23,item(Material.BREAD,"&eЕда: "+number(m.foodCoverage()*100)+"%",
                "Обеспечение: "+number(c.food()),"Потребность: "+number(s.population()*service.settings().rules().foodDemand()),
                "Единицы снабжения за цикл.","Аграрный комплекс, пекарня и мельница."));
        v.inventory.setItem(25,item(Material.WATER_BUCKET,"&bВода: "+number(m.waterCoverage()*100)+"%",
                "Обеспечение: "+number(c.water()),"Потребность: "+number(s.population()*service.settings().rules().waterDemand()),
                "Водонапорная башня и резервуар."));
        List<String> reasons=new ArrayList<>(m.reasons());
        if(s.paused()) reasons.add(0,"Расчёт приостановлен: постройки недоступны.");
        v.inventory.setItem(31,item(s.paused()?Material.BARRIER:Material.OAK_SAPLING,"&fУсловия роста",reasons.toArray(String[]::new)));
        v.inventory.setItem(33,item(Material.CLOCK,"&fСледующий цикл",
                s.paused()?"Ожидаем данные построек.":"Через "+Math.max(0,(s.nextCycle()-System.currentTimeMillis()+999)/1000)+" сек.",
                "Длительность: "+service.settings().intervalMillis()/1000+" сек.","Во время выключения сервера расчёт не идёт."));
        v.inventory.setItem(45,item(Material.BRICKS,"&aВклад зданий","Показать жильё, снабжение и рабочие места."));
    }
    private void sources(View v, PopulationSnapshot s, int pages) {
        var entries=new ArrayList<>(service.settings().buildings().entrySet());
        for(int i=v.page*45;i<Math.min(entries.size(),(v.page+1)*45);i++) {
            var entry=entries.get(i); var b=entry.getValue(); int level=s.buildingLevels().getOrDefault(entry.getKey(),0);
            var c=b.atLevel(level); Material icon=Material.matchMaterial(b.icon());
            v.inventory.setItem(i%45,item(icon==null||!icon.isItem()?Material.BRICKS:icon,"&e"+b.name(),
                    "Этап: "+level+" / "+b.maximumLevel(),
                    level<b.minimumLevel()?"Начнёт работать с этапа "+b.minimumLevel():"Здание обеспечивает население",
                    "Жильё: +"+c.housing()+"; рабочих мест: +"+c.jobs(),
                    "Еда: +"+number(c.food())+"; вода: +"+number(c.water()),"Довольство: "+signed(c.happiness()),
                    "На активный уровень: жильё "+b.capacity().housing()+", работа "+b.capacity().jobs(),
                    "Еда "+number(b.capacity().food())+", вода "+number(b.capacity().water())));
        }
        if(v.page>0) v.inventory.setItem(45,item(Material.ARROW,"&aНазад"));
        v.inventory.setItem(48,item(Material.BELL,"&aОбзор населения","Страница "+(v.page+1)+" / "+pages));
        if(v.page+1<pages) v.inventory.setItem(53,item(Material.ARROW,"&aДалее"));
    }
    private ItemStack item(Material material, String title, String... lore) {
        ItemStack item=new ItemStack(material); var meta=item.getItemMeta(); meta.setDisplayName(Messages.color(title));
        meta.setLore(Arrays.stream(lore).map(line->Messages.color("&7"+line)).toList()); item.setItemMeta(meta); return item;
    }
    private static String signed(double n) { return (n>0?"+":"")+number(n); }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof View view)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot()<0 || event.getRawSlot()>=54) return;
        if (!player.hasPermission("neverlandtownypopulation.use") || !canView(player,view.town)) { player.closeInventory(); return; }
        int slot=event.getRawSlot();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder()!=view) return;
            if(slot==49) open(player,view.town,view.buildings,view.page);
            else if(!view.buildings && slot==45) open(player,view.town,true,0);
            else if(view.buildings && slot==48) open(player,view.town,false,0);
            else if(view.buildings && slot==45) open(player,view.town,true,view.page-1);
            else if(view.buildings && slot==53) open(player,view.town,true,view.page+1);
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof View) event.setCancelled(true);
    }
}
