package ru.neverland.townymarket;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import static ru.neverland.townymarket.MarketData.*;
public final class MarketMenus implements Listener {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private final JavaPlugin plugin;private final MarketService service;
    private static final class View implements InventoryHolder {final Map<Integer,Runnable> actions=new HashMap<>();Inventory inventory;public Inventory getInventory(){return inventory;}}
    public MarketMenus(JavaPlugin plugin,MarketService service){this.plugin=plugin;this.service=service;}
    private View view(Player p,String title){service.requireUse(p);var v=new View();v.inventory=Bukkit.createInventory(v,54,color(title));return v;}
    public void open(Player p,String mode,int requested)throws Exception {
        var town=service.bridge.town(p);boolean mine=mode.equals("mine"),global=mode.equals("global");var v=view(p,mine?"&2Предложения города":global?"&2Международный рынок":"&2Городской рынок");
        var list=service.listings().stream().filter(l->mine?town!=null&&l.town().equals(town.getUUID()):l.state()==ListingState.ACTIVE&&(global?l.scope()==Scope.GLOBAL:l.scope()==Scope.LOCAL&&town!=null&&l.town().equals(town.getUUID()))).toList();
        int pages=Math.max(1,(list.size()+44)/45),page=Math.max(0,Math.min(pages-1,requested));
        for(int i=0;i<45&&page*45+i<list.size();i++){var l=list.get(page*45+i);button(v,i,MarketCatalog.decode(l.item()).getType(),l.label(),summary(l),()->run(p,()->detail(p,l.id().toString())));}
        button(v,45,Material.ARROW,"&fПредыдущая страница",List.of(),()->run(p,()->open(p,mode,page-1)));
        button(v,46,Material.CHEST,"&aГородской рынок",List.of("&7Покупки жителями за личные деньги."),()->run(p,()->open(p,"local",0)));
        button(v,47,Material.COMPASS,"&bМеждународный рынок",List.of("&7Закупки городами в склад /t inv.","&7Расход: бюджет инфраструктуры."),()->run(p,()->open(p,"global",0)));
        button(v,48,Material.WRITABLE_BOOK,"&eПредложения города",List.of("&7Управление товаром и возврат остатков."),()->run(p,()->open(p,"mine",0)));
        button(v,49,Material.EMERALD,"&fСтраница "+(page+1)+" / "+pages,List.of(service.available()?"&aРынок работает":"&cОперации остановлены: ошибка журнала", "&7Рынок: "+(town==null?0:service.bridge.level(town.getUUID(),"market"))+" ур.","&7Гильдия: "+(town==null?0:service.bridge.level(town.getUUID(),"merchant_guild"))+" ур.","&7Местных предложений: до "+(town==null?0:service.limit(town.getUUID(),Scope.LOCAL)),"&7Международных: до "+(town==null?0:service.limit(town.getUUID(),Scope.GLOBAL))),()->run(p,()->open(p,mode,page)));
        button(v,50,Material.BARREL,"&fПокупки и получение",List.of("&7Забрать оплаченные личные покупки.","&7Посмотреть расчёты города."),()->run(p,()->orders(p,0)));
        button(v,51,Material.BOOK,"&fКаталог и цены",List.of("&7Товары с автоматической ценой."),()->run(p,()->catalog(p,0)));
        button(v,52,Material.PAPER,"&fВыставить товар",List.of("&7/t market sell <товар|hand> <кол-во>","&7<цена за штуку|auto> <local|global>","&7Пример: iron_ingot 500 auto global", "&7Товар резервируется из /t inv."),()->{p.closeInventory();MarketCommands.help(p);});
        button(v,53,Material.ARROW,"&fСледующая страница",List.of(),()->run(p,()->open(p,mode,page+1)));p.openInventory(v.inventory);
    }
    public void detail(Player p,String id)throws Exception {var l=service.find(id);if(l==null)throw new IllegalArgumentException("Предложение не найдено");var v=view(p,"&2Товар рынка");button(v,13,MarketCatalog.decode(l.item()).getType(),l.label(),summary(l),()->{});
        int[] quantities={1,16,64};for(int i=0;i<quantities.length;i++){int n=quantities[i];button(v,28+i*2,Material.EMERALD,"&aКупить "+n+" шт.",List.of("&7Открыть итоговую цену и подтверждение."),()->run(p,()->confirm(p,service.quote(p,id,n))));}
        var town=service.bridge.town(p);if(town!=null&&l.town().equals(town.getUUID())&&service.manage(p,town))button(v,40,Material.RED_DYE,"&cСнять предложение",List.of("&7Вернуть непроданный товар на склад.","&7Оплаченные покупки будут завершены."),()->run(p,()->closeConfirmation(p,l)));
        button(v,49,Material.ARROW,"&fНазад",List.of(),()->run(p,()->open(p,l.scope()==Scope.GLOBAL?"global":"local",0)));p.openInventory(v.inventory);
    }
    private void closeConfirmation(Player p,Listing l){var v=view(p,"&2Снять товар с продажи?");button(v,13,Material.CHEST,l.label(),List.of("&7Предложение: "+l.shortId(),"&7Остаток вернётся в /t inv."),()->{});
        button(v,29,Material.LIME_CONCRETE,"&aПодтвердить снятие",List.of(),()->run(p,()->{service.close(p,l.id().toString());open(p,"mine",0);}));button(v,33,Material.RED_CONCRETE,"&cНазад",List.of(),()->run(p,()->detail(p,l.id().toString())));p.openInventory(v.inventory);}
    public void confirm(Player p,Quote q)throws Exception {var l=service.repo.listing(q.lot());if(l==null)throw new IllegalArgumentException("Предложение не найдено");var v=view(p,"&2Подтверждение покупки");
        button(v,13,MarketCatalog.decode(l.item()).getType(),l.label(),List.of("&7Продавец: &f"+service.bridge.name(l.town()),"&7Количество: &f"+q.amount(),"&7За штуку: &e"+money(q.unit()),"&7Итого: &e"+money(q.unit()*q.amount())+" монет",l.scope()==Scope.GLOBAL?"&7Списание из бюджета инфраструктуры.":"&7Списание с вашего личного счёта.",l.scope()==Scope.GLOBAL?"&7Доставка в склад города /t inv.":"&7Получение: «Покупки и получение».","&7Подтверждение действует 30 секунд."),()->{});
        button(v,29,Material.LIME_CONCRETE,"&aКупить за "+money(q.unit()*q.amount()),List.of("&7Цена проверится повторно.","&7Изменённая цена требует нового согласия."),()->run(p,()->{var o=service.confirm(p,q);MarketService.tell(p,"Покупка принята. ID: "+o.shortId());orders(p,0);}));
        button(v,33,Material.RED_CONCRETE,"&cНазад",List.of(),()->run(p,()->detail(p,l.id().toString())));p.openInventory(v.inventory);
    }
    public void orders(Player p,int requested)throws Exception {var list=service.orders(p);var v=view(p,"&2Покупки и получение");int pages=Math.max(1,(list.size()+44)/45),page=Math.max(0,Math.min(pages-1,requested));
        for(int i=0;i<45&&page*45+i<list.size();i++){var o=list.get(page*45+i);var l=service.repo.listing(o.lot());if(l==null)continue;List<String> lore=new ArrayList<>(List.of("&7ID: &f"+o.shortId(),"&7Продавец: &f"+service.bridge.name(o.seller()),"&7Количество: &f"+o.amount(),"&7Оплата: &e"+money(o.total()),"&7Дата: &f"+DATE.format(Instant.ofEpochMilli(o.created())),"&7Состояние: &f"+phase(o.phase()),"&7"+o.note()));
            if(!o.city()&&o.buyer().equals(p.getUniqueId()))lore.add(o.finalized()?"&aТовар получен":"&aНажмите, чтобы забрать оплаченную покупку");
            button(v,i,MarketCatalog.decode(l.item()).getType(),l.label(),lore,()->run(p,()->{if(!o.city()&&o.buyer().equals(p.getUniqueId())){String result=service.claim(p,o.id().toString());MarketService.tell(p,result.equals("CLAIMED")?"Покупка получена":result.equals("MODE")?"Получение доступно в выживании и приключении":MarketPayments.stockNote(result));}orders(p,page);}));}
        button(v,45,Material.ARROW,"&fПредыдущая",List.of(),()->run(p,()->orders(p,page-1)));button(v,49,Material.CLOCK,"&fОбновить",List.of("&7Страница "+(page+1)+" / "+pages),()->run(p,()->orders(p,page)));button(v,51,Material.CHEST,"&fРынок",List.of(),()->run(p,()->open(p,"local",0)));button(v,53,Material.ARROW,"&fСледующая",List.of(),()->run(p,()->orders(p,page+1)));p.openInventory(v.inventory);
    }
    public void catalog(Player p,int requested){var list=service.catalog.products().stream().sorted(Comparator.comparing(MarketCatalog.Product::id)).toList();var v=view(p,"&2Каталог автоматических цен");int pages=Math.max(1,(list.size()+44)/45),page=Math.max(0,Math.min(pages-1,requested));
        for(int i=0;i<45&&page*45+i<list.size();i++){var product=list.get(page*45+i);button(v,i,product.item().getType(),service.catalog.label(product.item()),List.of("&7Код товара: &f"+product.id(),"&7Базовая цена за штуку: &e"+money(product.base()),"&7Автоцена учитывает реальный остаток", "&7и оплаченные покупки за 24 часа.","&7Покупки одного покупателя ограничены", "&7при расчёте спроса."),()->{});}
        button(v,45,Material.ARROW,"&fПредыдущая",List.of(),()->catalog(p,page-1));button(v,49,Material.CHEST,"&fРынок",List.of("&7Страница "+(page+1)+" / "+pages),()->run(p,()->open(p,"local",0)));button(v,53,Material.ARROW,"&fСледующая",List.of(),()->catalog(p,page+1));p.openInventory(v.inventory);
    }
    public List<String> summary(Listing l)throws Exception{return List.of("&7ID: &f"+l.shortId(),"&7Город: &f"+service.bridge.name(l.town()),"&7Рынок: &f"+(l.scope()==Scope.GLOBAL?"Международный":"Городской"),"&7Цена за штуку: &e"+money(service.unit(l)),"&7Цена: &f"+(l.auto()?"По спросу и предложению":"Фиксированная"),"&7Доступно: &f"+service.bridge.stock(l),"&7Состояние: &f"+state(l.state()),"&7"+l.note());}
    public static String state(ListingState s){return switch(s){case PREPARING->"Резервирование товара";case ACTIVE->"Выставлен";case CLOSING->"Возврат остатка";case CLOSED->"Снят с продажи";};}
    public static String phase(Phase p){return switch(p){case PREPARED->"Подготовка";case DEBIT_PENDING->"Сверка списания";case PAID->"Оплачено, ожидает доставки";case DELIVERED->"Ожидает расчёта с продавцом";case CREDIT_PENDING->"Сверка зачисления";case RETURNING->"Возврат резерва";case COMPLETE->"Завершено";case CANCELLED->"Отменено";};}
    private static String color(String s){return ChatColor.translateAlternateColorCodes('&',s);}
    private void button(View v,int slot,Material type,String title,List<String> lore,Runnable action){var item=new ItemStack(type);var meta=item.getItemMeta();meta.setDisplayName(color(title));meta.setLore(lore.stream().map(MarketMenus::color).toList());item.setItemMeta(meta);v.inventory.setItem(slot,item);v.actions.put(slot,action);}
    public interface Action{void run()throws Exception;}public static void run(Player p,Action action){try{action.run();}catch(Exception ex){MarketService.tell(p,"&c"+(ex.getMessage()==null?"Не удалось выполнить действие":ex.getMessage()));}}
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||e.getRawSlot()<0||e.getRawSlot()>=54)return;var action=v.actions.get(e.getRawSlot());if(action==null)return;
        Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory()==v.inventory)run(p,()->{service.requireUse(p);action.run();});});}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
    public void stop(){for(var p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof View)p.closeInventory();}
}
