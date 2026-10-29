package ru.neverland.townypopulation.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import ru.neverland.core.*;
import ru.neverland.townypopulation.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiConsumer;

/** A read-only breakdown of the existing city population; never transfers stock or creates residents. */
final class CohortMenu {
    private static final int[] SLOTS={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss 'МСК'").withZone(ZoneId.of("Europe/Moscow"));
    private final Plugin plugin;private final PopulationService service;private final Messages messages;private final BiConsumer<Player,UUID> back;
    private static final class View implements InventoryHolder {
        final UUID town;int page;Inventory inventory;
        View(UUID town,int page){this.town=town;this.page=Math.max(0,page);}
        @Override public Inventory getInventory(){return inventory;}
    }
    CohortMenu(Plugin plugin,PopulationService service,Messages messages,BiConsumer<Player,UUID> back){this.plugin=plugin;this.service=service;this.messages=messages;this.back=back;}
    private boolean allowed(Player player,UUID town){
        if(!player.hasPermission("neverlandtownypopulation.use"))return false;
        if(player.hasPermission("neverlandtownypopulation.admin"))return true;
        var resident=com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(player);var current=resident==null?null:resident.getTownOrNull();return current!=null&&current.getUUID().equals(town);
    }
    void open(Player player,UUID town,int page){try(var guard=SuiteState.guard()){
        if(!allowed(player,town)){messages.send(player,"no-permission");return;}
        var view=new View(town,page);view.inventory=MenuStyle.inventory(plugin,view,54,Messages.color("&2Жители и районы"));
        boolean pending=render(view);player.openInventory(view.inventory);if(pending)retry(player,view,6);
    }}
    private boolean render(View view){
        service.refreshCohorts(view.town);var report=service.cohorts(view.town);view.inventory.clear();
        view.inventory.setItem(48,item(Material.BELL,"&aОбзор населения","Вернуться к условиям жизни всего города."));
        view.inventory.setItem(49,item(Material.SUNFLOWER,"&aОбновить","Проверить жителей и потребности районов."));
        if(!Boolean.TRUE.equals(report.get("available"))){boolean pending=Boolean.TRUE.equals(report.get("pending"));view.inventory.setItem(22,item(pending?Material.CLOCK:Material.BARRIER,pending?"&eГотовим сведения о районах":"&eСведения пока недоступны",String.valueOf(report.getOrDefault("issue","Попробуйте обновить окно позже.")),"Подтверждённое население города сохранено."));return pending;}
        var totals=map(report.get("totals"));var header=new ArrayList<String>();header.add("Всего жителей: "+totals.get("population"));header.add("Это население города, а не онлайн-игроки.");header.add("Жители распределены по доступному жилью.");header.add("Работают: "+totals.get("employed")+"; ищут работу: "+totals.get("unemployed"));header.add("Доли еды и воды — из общего снабжения.");header.add("Дополнительного списания ресурсов нет.");if(Boolean.TRUE.equals(report.get("paused")))header.add("Расчёт города на паузе; показаны сохранённые жители.");header.add("Данные: "+date(report.get("sourceAt")));header.add("Расчёт: "+date(report.get("checkedAt")));
        view.inventory.setItem(4,item(Material.PLAYER_HEAD,"&aЖители и потребности районов",header.toArray(String[]::new)));
        var rows=new ArrayList<>(map(report.get("rows")).entrySet());rows.sort(Map.Entry.comparingByKey());int pages=Math.max(1,(rows.size()+SLOTS.length-1)/SLOTS.length);view.page=Math.min(view.page,pages-1);
        for(int i=view.page*SLOTS.length;i<Math.min(rows.size(),(view.page+1)*SLOTS.length);i++){var row=map(rows.get(i).getValue());var lore=new ArrayList<String>();boolean district=Boolean.TRUE.equals(row.get("district"));
            lore.add("Жители: "+row.get("population")+"; мест жилья: "+row.get("housing"));lore.add("Работают: "+row.get("employed")+"; ищут работу: "+row.get("unemployed"));lore.add("Остальные жители: "+row.get("other"));lore.add("Рабочих мест здесь: "+row.get("jobs"));lore.add("Жители могут работать в других районах.");lore.add("Еда за цикл: нужно "+decimal(row.get("foodDemand")));lore.add("Доля общего снабжения: "+decimal(row.get("foodSupply")));lore.add("Вода за цикл: нужно "+decimal(row.get("waterDemand")));lore.add("Доля общего снабжения: "+decimal(row.get("waterSupply")));
            if(district){lore.add(Boolean.TRUE.equals(row.get("connectionKnown"))?"Связь с центром: "+row.get("connected")+" / "+row.get("plots")+" участков":"Связь с центром: пока неизвестна");var services=new TreeMap<>(map(row.get("services")));for(var entry:services.entrySet()){var supply=map(entry.getValue());lore.add(supply.get("title")+": "+(Boolean.TRUE.equals(supply.get("known"))?supply.get("served")+" / "+supply.get("total")+" участков":"сведения недоступны"));}}
            else{lore.add("Начальное жильё и здания вне районов.");lore.add("Эти жители остаются частью города.");}
            view.inventory.setItem(SLOTS[i%SLOTS.length],item(district?Material.FILLED_MAP:Material.BELL,"&e"+row.get("name"),lore.toArray(String[]::new)));
        }
        if(view.page>0)view.inventory.setItem(45,item(Material.ARROW,"&aПредыдущая страница"));if(view.page+1<pages)view.inventory.setItem(53,item(Material.ARROW,"&aСледующая страница"));view.inventory.setItem(50,item(Material.PAPER,"&fСтраница "+(view.page+1)+" / "+pages));return false;
    }
    private void retry(Player player,View view,int remaining){if(remaining<=0)return;SuiteScheduler.entityLater(plugin,player,()->{
        if(!player.isOnline()||player.getOpenInventory().getTopInventory()!=view.inventory)return;
        if(!allowed(player,view.town)){player.closeInventory();return;}
        if(render(view))retry(player,view,remaining-1);
    },null,10);}
    boolean click(InventoryClickEvent event){if(!(event.getView().getTopInventory().getHolder() instanceof View view))return false;event.setCancelled(true);if(!(event.getWhoClicked() instanceof Player player))return true;
        if(!allowed(player,view.town)){player.closeInventory();return true;}int slot=event.getRawSlot();if(slot!=45&&slot!=48&&slot!=49&&slot!=53)return true;
        if(event.getView().getTopInventory().getItem(slot)==null)return true;
        SuiteScheduler.entity(plugin,player,()->{if(!player.isOnline()||player.getOpenInventory().getTopInventory()!=view.inventory)return;if(!allowed(player,view.town)){player.closeInventory();return;}if(slot==48)back.accept(player,view.town);else open(player,view.town,slot==45?view.page-1:slot==53?view.page+1:view.page);});return true;
    }
    boolean drag(InventoryDragEvent event){if(!(event.getView().getTopInventory().getHolder() instanceof View))return false;event.setCancelled(true);return true;}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return value instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();}
    private static String decimal(Object value){try{return new BigDecimal(String.valueOf(value)).stripTrailingZeros().toPlainString();}catch(NumberFormatException error){return "нет данных";}}
    private static String date(Object value){return value instanceof Number n&&n.longValue()>0?DATE.format(Instant.ofEpochMilli(n.longValue())):"ожидаем сведения";}
    private static ItemStack item(Material material,String title,String... lore){var result=new ItemStack(material);var meta=result.getItemMeta();meta.setDisplayName(MenuStyle.nameLegacy(Messages.color(title)));meta.setLore(MenuStyle.loreStrings(Arrays.stream(lore).map(s->Messages.color("&7"+s)).toList()));meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);result.setItemMeta(meta);return result;}
}
