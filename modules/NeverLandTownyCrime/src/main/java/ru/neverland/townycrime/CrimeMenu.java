package ru.neverland.townycrime;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.MenuStyle;
public final class CrimeMenu implements Listener {
    private final JavaPlugin plugin;private final CrimeService service;
    private record Holder(UUID viewer,UUID town,Inventory back)implements InventoryHolder{public Inventory getInventory(){return null;}}
    public CrimeMenu(JavaPlugin p,CrimeService s){plugin=p;service=s;}
    static String number(double n){return String.format(Locale.forLanguageTag("ru"),"%.1f",n);}
    static String resource(String id){return switch(id){case "wood"->"Древесина";case "stone"->"Камень";case "metal"->"Металл";case "materials"->"Стройматериалы";case "food"->"Продовольствие";case "water"->"Вода";case "knowledge"->"Знания";case "influence"->"Влияние";default->id;};}
    private ItemStack item(Material icon,String name,String... lore){var item=new ItemStack(icon);var meta=item.getItemMeta();meta.displayName(MenuStyle.nameComponent(MenuStyle.decode(name)));meta.lore(MenuStyle.loreComponents(Arrays.stream(lore).map(MenuStyle::decode).toList()));item.setItemMeta(meta);return item;}
    public boolean allowed(Player p,UUID town){var resident=TownyAPI.getInstance().getResident(p.getUniqueId());return p.hasPermission("neverlandtownycrime.admin")||p.hasPermission("neverlandtownycrime.use")&&resident!=null&&resident.hasTown()&&resident.getTownOrNull().getUUID().equals(town);}
    public void open(Player p,UUID town,Inventory previous){if(!allowed(p,town))throw new IllegalArgumentException("Недостаточно прав");var s=service.crime(town).orElseThrow(()->new IllegalArgumentException("Город не найден"));boolean paused=(Boolean)s.get("paused");
        var inv=MenuStyle.inventory(plugin,new Holder(p.getUniqueId(),town,previous),45,"Преступность • "+TownyAPI.getInstance().getTown(town).getName());
        for(int slot=0;slot<45;slot++)if(slot<9||slot>=36||slot%9==0||slot%9==8)inv.setItem(slot,item(Material.GRAY_STAINED_GLASS_PANE," "));
        inv.setItem(11,item(Material.IRON_BARS,"&eПорядок в городе","&fПреступность: &e"+number((Double)s.get("level"))+" / 100",paused?"&cРасчёт временно недоступен":"&fК чему движемся: &e"+number((Double)s.get("target")),"","&7Недовольство повышает преступность.","&7Работающая стража постепенно её снижает."));
        inv.setItem(13,item(Material.BREAD,"&aДовольство населения",paused?"&cДанные временно недоступны":"&fДовольство: &a"+number((Double)s.get("happiness"))+" / 100","","&7Обеспечьте жильё, работу, пищу и воду.","&7Подробнее: /t population"));
        inv.setItem(15,item(Material.SHIELD,"&bГородская стража",paused?"&cДанные временно недоступны":"&fЭффективность: &b"+number((Double)s.get("guard"))+" / 100","&fРаботники: &b"+s.get("workers"),"","&7Улучшайте здание «Городская стража».","&7Поддерживайте его работу и назначайте","&7работников через /t jobs."));
        inv.setItem(21,item(Material.GOLD_NUGGET,"&6Выручка лавки","&fГород получает: &e"+number(((Integer)s.get("incomeBasisPoints"))/100.0)+"% от продажи","&fПотери: &c"+number((10000-(Integer)s.get("incomeBasisPoints"))/100.0)+"%","","&7Цена для покупателя не меняется.","&7Сумма города фиксируется до оплаты.",paused?"&cНовые продажи приостановлены":"&aПродажи доступны"));
        var i=(Map<?,?>)s.get("incident");String kind=i.isEmpty()?"":"BURGLARY".equals(i.get("kind"))?"Кража со склада":"Вымогательство";
        String phase=i.isEmpty()?"":switch(String.valueOf(i.get("phase"))){case "PLANNED"->"Ожидает обработки";case "APPLIED","CLOSED"->Boolean.TRUE.equals(i.get("active"))?"Действует":"Завершено";default->"Предотвращено / нет доступного запаса";};
        inv.setItem(23,item(Material.WRITABLE_BOOK,"&dПоследнее происшествие",i.isEmpty()?"&aПроисшествий пока нет":"&f"+kind,"&7"+phase,i.isEmpty()?"":"&7"+("BURGLARY".equals(i.get("kind"))?resource((String)i.get("resource"))+": "+number(((Long)i.get("amount"))/1000.0):"Временно увеличивает потери лавки"),"","&7Кражи затрагивают виртуальный склад.","&7Городские резервы и нужды населения","&7защищены от списания."));
        inv.setItem(36,item(Material.ARROW,previous==null?"&fЗакрыть":"&fНазад"));inv.setItem(40,item(Material.CLOCK,"&bОбновить","&7"+s.get("status")));p.openInventory(inv);
    }
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Holder h))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(h.viewer()))return;if(!allowed(p,h.town())){p.closeInventory();return;}
        try{if(e.getRawSlot()==36)MenuStyle.returnTo(p,h.back());else if(e.getRawSlot()==40)open(p,h.town(),h.back());}catch(IllegalArgumentException ex){p.closeInventory();p.sendMessage(MenuStyle.decode("&c"+ex.getMessage()));}}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true);}
}
