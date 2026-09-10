package ru.neverland.minttrade.contract;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.minttrade.integration.TownyHook;
import ru.neverland.minttrade.service.MessageService;
import ru.neverland.minttrade.util.ColorUtil;
import static ru.neverland.minttrade.contract.SupplyContract.*;
public final class SupplyMenus implements Listener {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private final JavaPlugin plugin;private final TownyHook towny;private final SupplyService supply;private final MessageService messages;
    private static final class View implements InventoryHolder {
        final UUID town;final Map<Integer,Runnable> actions=new HashMap<>();Inventory inventory;
        View(UUID town){this.town=town;}public Inventory getInventory(){return inventory;}
    }
    public SupplyMenus(JavaPlugin plugin,TownyHook towny,SupplyService supply,MessageService messages){this.plugin=plugin;this.towny=towny;this.supply=supply;this.messages=messages;}
    public void stop(){for(var p:Bukkit.getOnlinePlayers()){var holder=p.getOpenInventory().getTopInventory().getHolder();if(holder instanceof View||holder instanceof ru.neverland.minttrade.gui.TradeMenuHolder)p.closeInventory();}}
    public boolean access(Player player){if(!player.hasPermission("minttrade.use")||!player.hasPermission("minttrade.contracts.use")){messages.send(player,"no-permission");return false;}return true;}
    public boolean manage(Player player,Town town){if(!access(player))return false;if(!player.hasPermission("minttrade.manage")||!player.hasPermission("minttrade.contracts.manage")||!towny.isManager(player,town)){messages.send(player,"only-manager");return false;}return true;}
    private View view(Player player,String title){var town=towny.town(player);if(town==null||!access(player))throw new IllegalArgumentException("Вы не состоите в городе или нет права доступа");var v=new View(town.getUUID());v.inventory=Bukkit.createInventory(v,54,ColorUtil.color(title));return v;}
    public void open(Player p,int requested){
        var v=view(p,"&2Регулярные торговые договоры");var list=supply.town(v.town);int pages=Math.max(1,(list.size()+44)/45),page=Math.max(0,Math.min(pages-1,requested));
        for(int slot=0;slot<45&&page*45+slot<list.size();slot++){var c=list.get(page*45+slot);button(v,slot,icon(c),c.terms().itemName(),summary(c),()->detail(p,c.terms().id().toString(),page));}
        button(v,45,Material.ARROW,"&fПредыдущая страница",List.of(),()->open(p,page-1));
        button(v,49,Material.WRITABLE_BOOK,"&eСтраница "+(page+1)+" / "+pages,List.of("&7Создание: /t trade contract propose", "&7<город> <предмет|hand> <кол-во> <цена> [дни]", "&7По умолчанию: раз в "+supply.defaultDays()+" дн.","&7Первая поставка — через выбранный период.","&7Списание: бюджет инфраструктуры.",supply.available()?"&aЖурнал договоров доступен":"&cАвтопоставки остановлены: ошибка журнала"),()->{});
        button(v,51,Material.CHEST,"&fГородской склад",List.of("&7Открыть /t inv"),()->p.performCommand("t inv"));
        button(v,52,Material.EMERALD,"&fТорговая доска",List.of(),()->p.performCommand("t trade"));
        button(v,53,Material.ARROW,"&fСледующая страница",List.of(),()->open(p,page+1));p.openInventory(v.inventory);
    }
    public void detail(Player p,String id,int page){
        var v=view(p,"&2Условия торгового договора");var c=supply.find(id);if(c==null||!c.terms().party(v.town))throw new IllegalArgumentException("Договор не найден");
        button(v,13,icon(c),c.terms().itemName(),summary(c),()->{});
        List<String> history=new ArrayList<>();history.add("&7Завершено поставок: &f"+c.deliveries());
        for(var receipt:c.history())history.add("&7"+date(receipt.at())+" &f• "+c.terms().amount()+" шт. / "+money(c.terms().cents()));
        if(c.history().isEmpty())history.add("&7Завершённых поставок пока нет.");
        button(v,22,Material.BOOK,"&fПоследние 20 поставок",history,()->{});
        if(c.status()==Status.PROPOSED&&c.terms().buyer().equals(v.town))button(v,29,Material.LIME_DYE,"&aПринять условия",List.of("&7Перейти к подтверждению."),()->confirm(p,id,"accept",page));
        if(c.status()==Status.ACTIVE){boolean paused=c.pausedBy().contains(v.town);button(v,29,paused?Material.LIME_DYE:Material.CLOCK,paused?"&aСнять паузу своего города":"&eПриостановить поставки",List.of("&7Пауза другого города сохраняется.","&7Начатый расчёт будет завершён."),()->confirm(p,id,paused?"resume":"pause",page));}
        if(c.status()==Status.PROPOSED||c.status()==Status.ACTIVE)button(v,33,Material.RED_DYE,"&cОтменить договор",List.of("&7Будущие поставки прекращаются.","&7Начатый расчёт будет завершён."),()->confirm(p,id,"cancel",page));
        button(v,49,Material.ARROW,"&fНазад",List.of(),()->open(p,page));p.openInventory(v.inventory);
    }
    public void confirm(Player p,String id,String action,int page){
        var v=view(p,"&2Подтверждение договора");var town=towny.town(p);if(!manage(p,town))return;
        var c=supply.find(id);if(c==null||!c.terms().party(v.town))throw new IllegalArgumentException("Договор не найден");
        button(v,13,icon(c),c.terms().itemName(),summary(c),()->{});
        String label=switch(action){case "accept"->"Принять договор";case "pause"->"Приостановить";case "resume"->"Снять свою паузу";default->"Отменить договор";};
        button(v,29,Material.LIME_CONCRETE,"&aПодтвердить: "+label,List.of("&7Количество и цена указаны за одну поставку.","&7Цена фиксированная, дополнительных сборов нет.","&7Первая поставка через "+c.terms().days()+" дн. после принятия."),()->{
            var current=towny.town(p);if(current==null||!current.getUUID().equals(v.town)||!manage(p,current))return;
            try{supply.action(v.town,id,action,c.terms());detail(p,id,page);}catch(Exception ex){error(p,ex);}
        });
        button(v,33,Material.RED_CONCRETE,"&cНазад",List.of(),()->detail(p,id,page));p.openInventory(v.inventory);
    }
    public void error(Player p,Exception ex){messages.send(p,"contract-error",Map.of("reason",ex.getMessage()==null?"Не удалось выполнить действие":ex.getMessage()));}
    private Material icon(SupplyContract c){try{return SupplyGateway.decode(c.terms().itemData()).getType();}catch(RuntimeException ex){return Material.BARRIER;}}
    private String name(UUID id){var town=towny.town(id);return town==null?"Удалённый город":town.getName();}
    public List<String> summary(SupplyContract c){
        var t=c.terms();List<String> lines=new ArrayList<>(List.of("&7ID: &f"+t.shortId(),"&7Продавец: &f"+name(t.seller()),"&7Покупатель: &f"+name(t.buyer()),
            "&7Партия: &f"+t.amount()+" шт.","&7Цена партии: &e"+money(t.cents())+" монет","&7Период: &f"+t.days()+" дн.","&7Статус: &f"+state(c),
            "&7Следующая поставка: &f"+(!c.open()?"Не планируется":c.nextDue()==0?"После подписания":date(c.nextDue())),"&7Состояние: &f"+c.note()));
        if(c.status()==Status.PROPOSED)lines.add("&7Принять до: &f"+date(t.expires()));
        for(UUID party:c.pausedBy())lines.add("&eПауза города: "+name(party));
        if(c.attempt()!=null){lines.add("&7Расчёт: &f"+phase(c.attempt().phase()));lines.add("&7Поставка: &f"+c.attempt().id());}
        return lines;
    }
    public static String state(SupplyContract c){return switch(c.status()){case PROPOSED->"Ожидает покупателя";case ACTIVE->c.pausedBy().isEmpty()?"Действует":"Приостановлен";case CANCELLED->c.attempt()==null?"Отменён":"Завершается начатый расчёт";case EXPIRED->"Предложение истекло";};}
    public static String phase(Phase p){return switch(p){case PREPARED->"Подготовка";case DEBIT_PENDING->"Сверка списания";case PAID->"Оплата зарезервирована";case DELIVERED->"Ожидает оплаты продавцу";case CREDIT_PENDING->"Сверка зачисления";case RETURNING->"Возврат резерва";case COMPLETE->"Завершён";case RETURNED->"Резерв возвращён";};}
    public static String date(long at){return DATE.format(Instant.ofEpochMilli(at));}
    private void button(View v,int slot,Material material,String name,List<String> lore,Runnable action){var item=new ItemStack(material);var meta=item.getItemMeta();meta.setDisplayName(ColorUtil.color(name));meta.setLore(lore.stream().map(ColorUtil::color).toList());item.setItemMeta(meta);v.inventory.setItem(slot,item);v.actions.put(slot,action);}
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||e.getRawSlot()<0||e.getRawSlot()>=v.inventory.getSize())return;
        Runnable action=v.actions.get(e.getRawSlot());if(action==null)return;
        Bukkit.getScheduler().runTask(plugin,()->{var town=towny.town(p);if(!p.isOnline()||p.getOpenInventory().getTopInventory()!=v.inventory)return;
            if(town==null||!town.getUUID().equals(v.town)||!access(p)){p.closeInventory();return;}try{action.run();}catch(Exception ex){error(p,ex);}});
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
}
