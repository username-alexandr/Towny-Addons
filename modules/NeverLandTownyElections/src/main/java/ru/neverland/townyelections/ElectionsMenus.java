package ru.neverland.townyelections;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.MenuStyle;

public final class ElectionsMenus implements Listener {
    public record Holder(UUID viewer,UUID town,UUID election,String race,int page,List<String> entries,Set<UUID> selected,Inventory back) implements InventoryHolder {public Inventory getInventory(){return null;}}
    private static final int[] CELLS={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private final NeverLandTownyElections plugin;private final ElectionsService service;
    public ElectionsMenus(NeverLandTownyElections plugin,ElectionsService service){this.plugin=plugin;this.service=service;}
    public String name(String race){return service.settings().names().getOrDefault(race,race);}
    public static String residentName(UUID id){var r=TownyAPI.getInstance().getResident(id);return r==null?id.toString():r.getName();}
    public static String phase(Election.Phase p){return switch(p){case WAITING->"Ожидание";case NOMINATION->"Приём кандидатов";case VOTING->"Голосование";case APPLYING->"Применение итогов";case REVIEW->"Проверка администратором";case COMPLETE->"Завершено";case CANCELLED->"Отменено";};}
    private static String time(long n){return n==0?"—":DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(n));}
    public void open(Player p,String race,int requestedPage,Set<UUID> selection,Inventory previous) throws Exception {
        if(!p.hasPermission("townyelections.use"))throw new IllegalArgumentException("Нет доступа к меню выборов.");
        var town=ElectionsService.requireTown(p);var e=service.state(town);String form=service.form(town);
        boolean disabled=service.settings().authoritarian()&&!form.equals("DEMOCRACY");
        if(previous==null){var old=p.getOpenInventory().getTopInventory().getHolder();previous=old instanceof Holder h?h.back():MenuStyle.previousMenu(p);}
        var races=e.seats.isEmpty()?service.settings().seats():e.seats;
        if(race!=null&&!races.containsKey(race))throw new IllegalArgumentException("Должность больше не настроена.");
        List<String> entries=race==null?new ArrayList<>(races.keySet()):e.candidates.entrySet().stream().filter(v->v.getValue().equals(race)).map(v->v.getKey().toString()).sorted().toList();
        int page=Math.max(0,Math.min(requestedPage,Math.max(0,(entries.size()-1)/CELLS.length)));
        Set<UUID> selected=selection==null?new LinkedHashSet<>(race==null?List.of():e.ballots.getOrDefault(race,Map.of()).getOrDefault(p.getUniqueId(),List.of())):new LinkedHashSet<>(selection);
        var holder=new Holder(p.getUniqueId(),town.getUUID(),e.id,race,page,List.copyOf(entries),Set.copyOf(selected),previous);
        var inv=MenuStyle.inventory(plugin,holder,54,race==null?"Выборы • "+town.getName():name(race));
        for(int i=0;i<54;i++)inv.setItem(i,item(Material.BLACK_STAINED_GLASS_PANE," ",List.of()));
        inv.setItem(4,item(disabled?Material.GOLDEN_HELMET:Material.WRITABLE_BOOK,disabled?"&6Выборы отключены":"&e"+(e.adminPausedAt>0?"Пауза • ":"")+phase(e.phase),List.of(
                "&7Форма: &f"+switch(form){case "MONARCHY"->"Монархия";case "AUTOCRACY"->"Автократия";default->"Демократия";},
                "&7Выдвижение до: &f"+(e.adminPausedAt>0?"пауза":time(e.nominationEnd)),"&7Голосование до: &f"+(e.adminPausedAt>0?"пауза":time(e.votingEnd)),"", "&7Следующая кампания: &f"+(disabled?"после смены формы":time(e.next)),
                "&7Ваше право голоса: &f"+(ElectionsService.eligible(town,p.getUniqueId(),"VOTE")?"есть":"нет"))));
        for(int i=0;i<CELLS.length && page*CELLS.length+i<entries.size();i++){
            String entry=entries.get(page*CELLS.length+i);
            if(race==null)inv.setItem(CELLS[i],item(entry.equals("mayor")?Material.GOLDEN_HELMET:entry.equals("councillor")?Material.OAK_SIGN:Material.BOOK,
                    "&f"+name(entry),List.of("&7Мест: &e"+races.get(entry),"&7Кандидатов: &f"+e.candidates.values().stream().filter(entry::equals).count(),"", "&aНажмите: кандидаты и голосование", "&8ID: "+entry)));
            else {UUID id=UUID.fromString(entry);boolean chosen=selected.contains(id);boolean valid=ElectionsService.eligible(town,id,"HOLD_OFFICE");
                inv.setItem(CELLS[i],item(chosen?Material.LIME_DYE:valid?Material.PAPER:Material.GRAY_DYE,(chosen?"&a✓ ":"&f")+residentName(id),List.of(
                        "&7Кандидат: &f"+name(race),valid?"&7Право занимать должность: &aесть":"&cКандидат утратил право на должность", "",e.phase==Election.Phase.VOTING?"&eНажмите, чтобы выбрать или убрать":"&7Выбор доступен во время голосования")));}
        }
        inv.setItem(45,item(Material.ARROW,race!=null||previous!=null?"&fНазад":"&fЗакрыть",List.of("&7Вернуться в предыдущее меню.")));
        if(page>0)inv.setItem(48,item(Material.ARROW,"&fПредыдущая страница",List.of()));
        if((page+1)*CELLS.length<entries.size())inv.setItem(50,item(Material.ARROW,"&fСледующая страница",List.of()));
        if(race!=null&&!disabled && e.phase==Election.Phase.NOMINATION)inv.setItem(49,item(Material.WRITABLE_BOOK,"&eВыдвинуть свою кандидатуру",List.of("&7Одна должность на кампанию.","&7Нужно право занимать должность.","", "&aНажмите, чтобы выдвинуться.")));
        else if(race!=null&&!disabled && e.phase==Election.Phase.VOTING)inv.setItem(49,item(Material.LIME_CONCRETE,"&aПодтвердить голос",List.of("&7Выбрано: &f"+selected.size()+" / "+races.get(race),"&7Сохранится только после подтверждения.","&7Новый выбор заменит прежний.","", "&aНажмите для сохранения.")));
        else inv.setItem(49,item(Material.BOOK,"&eПомощь и результаты",List.of("&7Нажмите для списка команд.","&7Итоги: /t elections results")));
        inv.setItem(53,item(Material.CLOCK,"&fОбновить",List.of("&7Заново загрузить состояние кампании.")));
        p.openInventory(inv);
    }
    private static ItemStack item(Material m,String title,List<String> lore){var i=new ItemStack(m);var meta=i.getItemMeta();meta.setDisplayName(MenuStyle.nameLegacy(title));meta.setLore(MenuStyle.loreStrings(lore));i.setItemMeta(meta);return i;}
    @EventHandler public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Holder h))return;event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(h.viewer())||event.getRawSlot()<0||event.getRawSlot()>=54)return;
        int slot=event.getRawSlot();Bukkit.getScheduler().runTask(plugin,()->{
            try{
                var town=ElectionsService.requireTown(p);if(!town.getUUID().equals(h.town()))throw new IllegalArgumentException("Вы сменили город. Откройте меню заново.");
                if(!p.hasPermission("townyelections.use"))throw new IllegalArgumentException("Доступ отозван.");
                var e=service.state(town);if(!e.id.equals(h.election())){open(p,null,0,null,h.back());return;}
                if(slot==45){if(h.race()==null)MenuStyle.returnTo(p,h.back());else open(p,null,0,null,h.back());return;}
                if(slot==53){open(p,h.race(),h.page(),null,h.back());return;}
                if(slot==48||slot==50){open(p,h.race(),h.page()+(slot==48?-1:1),h.selected(),h.back());return;}
                if(slot==49){
                    if(h.race()!=null && e.phase==Election.Phase.NOMINATION){service.nominate(p,h.race());p.sendMessage("§aКандидатура сохранена.");}
                    else if(h.race()!=null && e.phase==Election.Phase.VOTING){service.vote(p,h.race(),List.copyOf(h.selected()));p.sendMessage("§aГолос сохранён.");}
                    else{ElectionsCommand.help(p);return;}
                    open(p,h.race(),h.page(),null,h.back());return;
                }
                for(int i=0;i<CELLS.length;i++)if(slot==CELLS[i] && h.page()*CELLS.length+i<h.entries().size()){
                    String entry=h.entries().get(h.page()*CELLS.length+i);
                    if(h.race()==null){open(p,entry,0,null,h.back());return;}
                    if(e.phase!=Election.Phase.VOTING)return;
                    var selected=new LinkedHashSet<>(h.selected());UUID id=UUID.fromString(entry);
                    if(!selected.remove(id)){if(selected.size()>=e.seats.get(h.race()))throw new IllegalArgumentException("Сначала уберите один выбор: все места заполнены.");selected.add(id);}
                    open(p,h.race(),h.page(),selected,h.back());return;
                }
            }catch(Exception ex){p.sendMessage("§cВыборы: "+ex.getMessage());}
        });
    }
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Holder)event.setCancelled(true);}
}
