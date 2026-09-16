package ru.neverland.townyseasons;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import ru.neverland.core.MenuStyle;

public final class SeasonMenu implements Listener {
    private final NeverLandTownySeasons plugin;private final SeasonService service;
    public SeasonMenu(NeverLandTownySeasons plugin,SeasonService service) { this.plugin=plugin;this.service=service; }
    static final class Holder implements InventoryHolder {
        final UUID viewer,world;final Inventory previous;Inventory inventory;
        Holder(UUID viewer,UUID world,Inventory previous) { this.viewer=viewer;this.world=world;this.previous=previous; }
        @Override public Inventory getInventory(){return inventory;}
    }
    static ItemStack item(Material material,String title,List<String> lines) {
        var item=new ItemStack(material);var meta=item.getItemMeta();meta.displayName(MenuStyle.nameComponent(MenuStyle.decode(title)));
        meta.lore(MenuStyle.loreComponents(lines.stream().map(MenuStyle::decode).toList()));item.setItemMeta(meta);return item;
    }
    public void open(Player player,UUID world,Inventory previous) {
        if(!player.hasPermission("neverlandtownyseasons.use"))throw new IllegalArgumentException("Нет права просмотра календаря");
        var current=service.calendar(world);var h=new Holder(player.getUniqueId(),world,previous);
        var inv=MenuStyle.inventory(plugin,h,54,"Сезоны • "+current.get("title"));h.inventory=inv;
        var clock=new ArrayList<>(List.of("&fМир: &b"+current.get("worldName"),"&fСезон: &e"+current.get("title"),""));
        String source=(String)current.get("source");
        clock.add(switch(source){case "CUSTOM"->"&fКалендарь сервера • собственные сутки";case "MINECRAFT"->"&fКалендарь по тикам мира Minecraft";case "MANUAL"->"&eСезон зафиксирован администратором";case "REALISTIC_SEASONS"->"&fСезон синхронизирован с RealisticSeasons";default->"&fСезонные эффекты в этом мире отключены";});
        if((int)current.get("day")>0){clock.add("&fГод: "+current.get("year")+" • день "+current.get("day")+" из "+current.get("daysPerSeason"));long left=(long)current.get("remaining");clock.add(source.equals("CUSTOM")?"&fДо смены: "+Math.max(1,(left+59999)/60000)+" мин.":"&fДо смены: "+left+" тиков мира");}
        inv.setItem(4,item(Material.CLOCK,"&6Текущий календарь",clock));
        int[] slots={19,21,23,25};Material[] icons={Material.CHERRY_SAPLING,Material.SUNFLOWER,Material.PUMPKIN,Material.SNOWBALL};
        for(Season s:Season.values()) {
            var effects=service.settings().effects().get(s);var lore=new ArrayList<String>();
            lore.add(s.name().equals(current.get("season"))?"&a● Текущий сезон":"&7○ Другой сезон");lore.add("");
            lore.add("&fАграрный комплекс: &e"+Math.round(effects.production().getOrDefault("agrarian_complex",1d)*100)+"% выпуска");
            lore.add("&fНаводнение: &b×"+effects.events().getOrDefault("flood",1d));lore.add("&fЗасуха: &6×"+effects.events().getOrDefault("drought",1d));lore.add("&fПожар: &c×"+effects.events().getOrDefault("fire",1d));
            lore.add("");lore.add("&7Риски — относительные веса выбора.");inv.setItem(slots[s.ordinal()],item(icons[s.ordinal()],"&e"+s.title,lore));
        }
        inv.setItem(39,item(Material.WHEAT,"&aЭкономика города",List.of("&fВлияет на будущий выпуск","&fстратегических ресурсов.","&fЗапасы и расходы не изменяются.","","&fЗимой подготовьте запас еды.","&fСезон города — в мире домашнего участка.")));
        inv.setItem(41,item(Material.WATER_BUCKET,"&bПодготовка к стихии",List.of("&fВесной чаще выбирается наводнение.","&fЛетом чаще выбирается засуха.","&fАкведук и канал смягчают наводнение.","","&e/t events &f— событие и необходимые вклады.","&fЗдания и рельеф не затапливаются блоками.")));
        inv.setItem(45,item(Material.ARROW,"&e← Назад",List.of("Вернуться в предыдущее меню")));
        inv.setItem(49,item(Material.CLOCK,"&aОбновить",List.of("Нажмите для обновления календаря")));
        if(player.hasPermission("neverlandtownyseasons.admin"))inv.setItem(53,item(Material.COMPARATOR,"&eУправление сезонами",List.of("/tseasons help","/tseasons status")));
        player.openInventory(inv);
    }
    @EventHandler public void click(InventoryClickEvent e) {
        if(!(e.getView().getTopInventory().getHolder() instanceof Holder h))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(h.viewer))return;
        if(!p.hasPermission("neverlandtownyseasons.use")){p.closeInventory();return;}
        if(e.getRawSlot()==45)MenuStyle.returnTo(p,h.previous);
        else if(e.getRawSlot()==49)try{open(p,h.world,h.previous);}catch(RuntimeException ex){p.closeInventory();p.sendMessage(MenuStyle.decode("&c"+ex.getMessage()));}
    }
    @EventHandler public void drag(InventoryDragEvent e) { if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true); }
}
