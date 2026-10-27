package ru.neverland.townysieges;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.MenuStyle;
import static ru.neverland.townysieges.SiegeRules.*;

public final class SiegeMenu implements Listener {
    private final NeverLandTownySiegesPlus plugin;private final SiegeService service;
    public SiegeMenu(NeverLandTownySiegesPlus plugin,SiegeService service){this.plugin=plugin;this.service=service;}
    static final class Holder implements InventoryHolder {final UUID viewer,town;final Inventory previous;Inventory inventory;Holder(UUID viewer,UUID town,Inventory previous){this.viewer=viewer;this.town=town;this.previous=previous;}@Override public Inventory getInventory(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return inventory;} }}
    static ItemStack item(Material material,String title,List<String> lore){var item=new ItemStack(material);var meta=item.getItemMeta();meta.displayName(MenuStyle.nameComponent(MenuStyle.decode(title)));meta.lore(MenuStyle.loreComponents(lore.stream().map(MenuStyle::decode).toList()));item.setItemMeta(meta);return item;}
    static List<String> effect(Fort f,SiegeSettings s){return switch(f){
        case WALL->List.of("&fБлокирует жемчуг Края и плоды хоруса","&fв пределах осаждённого города.","","&bЗащита рядом со стеной: &f"+Math.round(s.wallReduction()*100)+"% за уровень.");
        case GATE->List.of("&fУдерживает проход в город.","&fВо время рейда двери можно пробить","&fбоевым TNT, исчерпав их прочность.","","&fАтакующие не управляют механизмами.");
        case MOAT->List.of("&fЗамедляет атакующих рядом со рвом.","&fОтключает спринт и быстрое плавание.","","&bЗамедление: &f"+Math.round(s.moatRate()*100)+"% за уровень.","&bПредел: &f"+Math.round(s.moatCap()*100)+"%.");
        case TOWER,KEEP->List.of("&fПрепятствует воздушному входу","&fна элитрах и с тягуном.","&fЗапрещает раскрывать элитры в зоне.","","&bЗащита рядом: &f"+Math.round(s.towerReduction()*100)+"% за уровень.");
        case PORT->List.of("&fБлокирует вход в гавань на лодках","&fс бойцами атакующей стороны.","&fНе даёт сесть в лодку внутри зоны.","","&aВыход из гавани остаётся свободным.");};}
    public void open(Player player,UUID town,Inventory previous){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if(!player.hasPermission("neverlandtownysiegesplus.use"))throw new IllegalArgumentException("Нет права просмотра осад");
        var city=TownyAPI.getInstance().getTown(town);if(city==null)throw new IllegalArgumentException("Город не найден");
        var holder=new Holder(player.getUniqueId(),town,previous);var inv=MenuStyle.inventory(plugin,holder,54,"Осада • "+city.getName());holder.inventory=inv;
        var status=service.warStatus();String combat="&7"+plugin.raids().phase(town);
        try{var context=service.war().context(city,player);if(context.active())combat=context.battle()?"&cИдёт боевая сессия SiegeWar":"&eОсада: перерыв между битвами";}catch(Exception ignored){}
        inv.setItem(4,item(Material.SHIELD,"&6Оборона города",List.of(combat,"&fПостройки действуют на атакующих.","&fРейд объявляет мэр или генерал армии.",plugin.camps().settings().enabled()?"&fСначала соберите армию в осадном лагере.":"&fПри наличии SiegeWar действуют его бои.","","&bПредел снижения урона: &f"+Math.round(service.settings().defenseCap()*100)+"%","&fПодготовленный гарнизон усиливает защиту.")));
        var rows=service.defenses(town);int index=0;
        for(var row:rows){var f=Fort.values()[index];int slot=new int[]{19,20,21,23,24,25}[index++];int level=(int)row.get("activeLevel");var lore=new ArrayList<String>();lore.add(level>0?"&aДействует • уровень "+level:(int)row.get("completed")>0?"&eПриостановлено • проверьте содержание":"&7Нет завершённой постройки");lore.add("");lore.addAll(effect(f,service.settings()));lore.add("");lore.add("&7Радиус от границы: "+Math.round(service.settings().radii().get(f))+" блоков");
            if(Boolean.TRUE.equals(row.get("located")))lore.add("&7Зона: "+Math.round((double)row.get("minX"))+", "+Math.round((double)row.get("minZ"))+" → "+Math.round((double)row.get("maxX"))+", "+Math.round((double)row.get("maxZ")));
            Material icon=switch(f){case WALL->Material.STONE_BRICKS;case GATE->Material.IRON_DOOR;case MOAT->Material.LILY_PAD;case TOWER->Material.SPYGLASS;case KEEP->Material.POLISHED_BLACKSTONE_BRICKS;case PORT->Material.CROSSBOW;};inv.setItem(slot,item(icon,"&b"+f.title,lore));}
        inv.setItem(31,item(Material.TNT,"&eРейды и прочность",List.of("&f"+plugin.raids().phase(town),"&f/sieges camp <город> — лагерь у цели","&f/sieges camp — сбор и начало рейда","&f/sieges hp — прочность блока под прицелом","","&fБоевой TNT доступен мобилизованным.","&fЧем дальше взрыв, тем меньше урон.","&fЩит новичка и союз запрещают нападение.","&7Нажмите, чтобы посмотреть текущие рейды.")));
        if(plugin.camps().settings().enabled())inv.setItem(30,item(Material.CAMPFIRE,"&6Осадный лагерь",List.of("&fПостройте костёр и знамя у чужого города.","&fПосмотрите на костёр: /sieges camp <город>","&fЗдесь можно открыть меню своего лагеря.")));
        inv.setItem(40,item(Material.BOOK,"&eПравила осады",List.of("&fОборона работает на территории города.","&fНезавершённые и отключённые здания","&fне предоставляют защиту.","","&fЗдания ремонтируются за счёт казны.","&fЦена зависит от утраченной прочности.","&fОбычные блоки восстанавливают HP после затишья.","&fСодержимое хранилищ защищено.")));
        inv.setItem(45,item(Material.ARROW,"&e← Назад",List.of("Вернуться в предыдущее меню")));
        inv.setItem(49,item(Material.CLOCK,"&aОбновить",List.of("Нажмите для обновления состояния")));
        if(player.hasPermission("neverlandtownysiegesplus.admin"))inv.setItem(53,item(Material.COMPARATOR,"&eИнтеграция осад",List.of("&f"+status.get("state"),"&f"+status.get("detail"),"/sieges status")));
        player.openInventory(inv);
    } }
    @EventHandler public void click(InventoryClickEvent e){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownySiegesPlus")){ru.neverland.core.ModuleGate.stopEvent(e);return;}if(!(e.getView().getTopInventory().getHolder() instanceof Holder h))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(h.viewer))return;
        if(!p.hasPermission("neverlandtownysiegesplus.use")){p.closeInventory();return;}
        if(e.getRawSlot()==45)MenuStyle.returnTo(p,h.previous);
        else if(e.getRawSlot()==31)plugin.raids().status(p);
        else if(e.getRawSlot()==30)try{plugin.camps().menu(p);}catch(Exception ex){p.sendMessage(MenuStyle.decode("&e"+ex.getMessage()));}
        else if(e.getRawSlot()==49)try{open(p,h.town,h.previous);}catch(Exception ex){p.closeInventory();p.sendMessage(MenuStyle.decode("&c"+ex.getMessage()));}
    } }
    @EventHandler public void drag(InventoryDragEvent e){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownySiegesPlus")){ru.neverland.core.ModuleGate.stopEvent(e);return;}if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true);} }
}
