package ru.neverland.townycompanies;

import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import static ru.neverland.townycompanies.CompanyData.*;
import static ru.neverland.townycompanies.CompanyService.message;

public final class CompanyMenus implements Listener {
    private final JavaPlugin plugin;private final CompanyService service;private final CompanyCommand command;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    public static String date(long time){return DATE.format(Instant.ofEpochMilli(time));}
    private static final class View implements InventoryHolder {
        final UUID player;final Map<Integer,CompanyCommand.Action> actions=new HashMap<>();Inventory inventory;
        View(UUID player){this.player=player;}public Inventory getInventory(){return inventory;}
    }
    public CompanyMenus(JavaPlugin plugin,CompanyService service,CompanyCommand command){this.plugin=plugin;this.service=service;this.command=command;}
    private View view(Player p,String title){service.requireUse(p);var v=new View(p.getUniqueId());v.inventory=Bukkit.createInventory(v,54,"§6"+title);return v;}
    private void put(View v,int slot,Material icon,String title,List<String> lore,CompanyCommand.Action action){
        ItemStack item=new ItemStack(icon);var meta=item.getItemMeta();meta.setDisplayName("§e"+title);meta.setLore(lore.stream().map(s->"§7"+s).toList());meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);item.setItemMeta(meta);v.inventory.setItem(slot,item);if(action!=null)v.actions.put(slot,action);
    }
    private void show(Player p,View v){p.openInventory(v.inventory);}
    private void pages(Player p,View v,int page,int size,java.util.function.IntConsumer open){
        if(page>0)put(v,45,Material.ARROW,"Предыдущая страница",List.of(),()->open.accept(page-1));
        put(v,49,Material.BOOK,"Моё предприятие",List.of("Страница "+(page+1)),()->open(p));
        if((page+1)*45<size)put(v,53,Material.ARROW,"Следующая страница",List.of(),()->open.accept(page+1));
    }
    public void open(Player p){
        service.requireUse(p);Company c=service.ledger.membership(p.getUniqueId());View v=view(p,"Предприятия города");
        if(c==null){put(v,13,Material.WRITABLE_BOOK,"Зарегистрировать предприятие",List.of("Магазин, шахта, ферма или транспорт","Общий счёт, участники и городские заказы"),()->registration(p));}
        else {
            put(v,13,Material.valueOf(c.kind().icon),c.name(),List.of(c.kind().label,"Ваша роль: "+c.members().get(p.getUniqueId()).label,"Участники: "+c.members().size(),"Статус: "+(service.active(c)?c.debt()>0?"Налоговая задолженность":"Работает":"Владелец покинул город"),"ID: "+c.id()),null);
            put(v,20,Material.GOLD_INGOT,"Общий счёт",List.of("Баланс: "+format(c.balance()),"/company deposit <сумма>","Владелец: /company withdraw <сумма>","Нажмите: журнал платежей"),()->payments(p,0));
            put(v,22,Material.PLAYER_HEAD,"Участники",List.of("Пригласить: /company invite <игрок>","Нажмите для управления ролями"),()->members(p,0));
            put(v,24,Material.PAPER,"Городские контракты",List.of("Доступные и принятые заказы","Награда поступает на общий счёт"),()->contracts(p,0));
            put(v,29,Material.CLOCK,"Налог городу",List.of("Ставка: "+format(service.rate(c.kind())),"Период: "+service.interval()/3_600_000+" ч","Следующее начисление: "+date(c.nextTax()),"Долг: "+format(c.debt()),"Оплата автоматически с общего счёта","При долге новые заказы недоступны"),null);
            if(c.owner().equals(p.getUniqueId()))put(v,33,Material.BARRIER,"Закрыть предприятие",List.of("Сначала завершите заказы и расчёты","Передача: /company transfer <участник>"),()->command.quote(p,"Закрыть «"+c.name()+"»",()->service.disband(p)));
            else put(v,33,Material.OAK_DOOR,"Выйти из компании",List.of("Общий счёт останется компании"),()->command.quote(p,"Выйти из «"+c.name()+"»",()->service.remove(p,p.getUniqueId())));
            if(p.getUniqueId().equals(c.successor())&&c.successorUntil()>System.currentTimeMillis())put(v,31,Material.NAME_TAG,"Принять владение",List.of("Вместе со счётом и налоговыми обязательствами"),()->command.onCommand(p,null,"company",new String[]{"acceptowner"}));
        }
        put(v,45,Material.CHEST,"Компании города",List.of(),()->directory(p,0));
        put(v,49,Material.WRITABLE_BOOK,"Приглашения",List.of(),()->invitations(p,0));
        put(v,53,Material.KNOWLEDGE_BOOK,"Помощь",List.of("Команды предприятия"),()->command.onCommand(p,null,"company",new String[]{"help"}));show(p,v);
    }
    private void registration(Player p){View v=view(p,"Регистрация предприятия");int slot=10;for(Kind k:Kind.values())put(v,slot+=2,Material.valueOf(k.icon),k.label,List.of("Регистрация бесплатная","Налог: "+format(service.rate(k))+" / "+service.interval()/3_600_000+" ч","Нажмите, чтобы задать название"),()->{p.closeInventory();message(p,"/company create "+k.name().toLowerCase(Locale.ROOT)+" <название>");});pages(p,v,0,0,n->open(p));show(p,v);}
    public void confirm(Player p,String description){View v=view(p,"Подтверждение");put(v,13,Material.PAPER,"Условия",wrap(description),null);put(v,20,Material.LIME_DYE,"Подтвердить",List.of("Подтверждение действует 30 секунд"),()->command.confirm(p));put(v,24,Material.RED_DYE,"Отмена",List.of(),()->command.onCommand(p,null,"company",new String[]{"cancel"}));show(p,v);}
    private List<String> wrap(String text){List<String> lines=new ArrayList<>();String line="";for(String word:text.split(" ")){if(line.length()+word.length()>48){lines.add(line);line="";}line+=(line.isEmpty()?"":" ")+word;}if(!line.isEmpty())lines.add(line);return lines;}
    public void directory(Player p,int requested){var town=CompanyService.town(p.getUniqueId());if(town==null)throw new IllegalArgumentException("Вы не состоите в городе");
        List<Company> list=service.ledger.state().companies().values().stream().filter(c->!c.closed()&&c.town().equals(town.getUUID())).sorted(Comparator.comparing(Company::name)).toList();int page=page(requested,list.size());View v=view(p,"Компании: "+town.getName());
        for(int i=page*45;i<Math.min(list.size(),(page+1)*45);i++){Company c=list.get(i);put(v,i%45,Material.valueOf(c.kind().icon),c.name(),List.of(c.kind().label,"Владелец: "+service.residentName(c.owner()),"Участники: "+c.members().size(),"ID: "+c.id()),null);}pages(p,v,page,list.size(),n->directory(p,n));show(p,v);
    }
    public void members(Player p,int requested){Company c=service.own(p);var list=c.members().entrySet().stream().sorted(Comparator.comparing(e->service.residentName(e.getKey()))).toList();int page=page(requested,list.size());View v=view(p,"Участники компании");
        for(int i=page*45;i<Math.min(list.size(),(page+1)*45);i++){var e=list.get(i);put(v,i%45,Material.PLAYER_HEAD,service.residentName(e.getKey()),List.of(e.getValue().label),()->member(p,e.getKey()));}pages(p,v,page,list.size(),n->members(p,n));show(p,v);
    }
    private void member(Player p,UUID target){Company c=service.own(p);if(!c.members().containsKey(target))throw new IllegalArgumentException("Участник вышел из компании");View v=view(p,"Участник: "+service.residentName(target));
        put(v,13,Material.PLAYER_HEAD,service.residentName(target),List.of(c.members().get(target).label),null);
        if(c.owner().equals(p.getUniqueId())&&!target.equals(c.owner())){
            put(v,20,Material.IRON_INGOT,"Сделать управляющим",List.of("Приглашения и принятие заказов"),()->{service.role(p,target,Role.MANAGER);members(p,0);});
            put(v,22,Material.WOODEN_PICKAXE,"Сделать участником",List.of("Выполнение заказов и пополнение счёта"),()->{service.role(p,target,Role.WORKER);members(p,0);});
            put(v,24,Material.RED_DYE,"Исключить",List.of(),()->command.quote(p,"Исключить "+service.residentName(target),()->service.remove(p,target)));
            put(v,31,Material.NAME_TAG,"Предложить владение",List.of("Получатель должен принять предложение"),()->command.quote(p,"Передать компанию игроку "+service.residentName(target),()->service.offerOwnership(p,target)));
        }pages(p,v,0,0,n->members(p,0));show(p,v);
    }
    public void invitations(Player p,int requested){long now=System.currentTimeMillis();var list=service.ledger.state().companies().values().stream().filter(c->!c.closed()&&c.invitations().getOrDefault(p.getUniqueId(),0L)>now).sorted(Comparator.comparing(Company::name)).toList();int page=page(requested,list.size());View v=view(p,"Приглашения в компании");
        for(int i=page*45;i<Math.min(list.size(),(page+1)*45);i++){Company c=list.get(i);put(v,i%45,Material.PAPER,c.name(),List.of(c.kind().label,"Принять приглашение"),()->command.quote(p,"Вступить в «"+c.name()+"»",()->service.join(p,c)));}pages(p,v,page,list.size(),n->invitations(p,n));show(p,v);
    }
    public void contracts(Player p,int requested){Company c=service.own(p);var list=service.contracts.offers(c.town());int page=page(requested,list.size());View v=view(p,"Заказы для компании");
        for(int i=page*45;i<Math.min(list.size(),(page+1)*45);i++){var o=list.get(i);String assigned=(String)o.get("company");boolean mine=c.id().toString().equals(assigned);UUID id=UUID.fromString((String)o.get("id"));
            put(v,i%45,mine?Material.WRITABLE_BOOK:Material.PAPER,(String)o.get("name"),List.of("Цель: "+o.get("target"),"Прогресс: "+o.get("progress")+"/"+o.get("goal"),"Награда компании: "+o.get("reward"),"Срок: "+date(((Number)o.get("expires")).longValue()),"Исполнитель: "+o.get("assignee"),mine?"Нажмите: выполнение / освобождение":"Нажмите: принять свободный заказ"),()->{if(mine)contract(p,id);else command.takeQuote(p,id);});}
        pages(p,v,page,list.size(),n->contracts(p,n));show(p,v);
    }
    private void contract(Player p,UUID id){service.own(p);View v=view(p,"Выполнение заказа");put(v,20,Material.CHEST,"Доска городских заказов",List.of("Поставка: нажмите заказ на доске","Остальные цели учитываются при игре"),()->{p.closeInventory();p.performCommand("t contracts");});put(v,24,Material.ARROW,"Освободить заказ",List.of("Только с нулевым прогрессом"),()->{service.release(p,id);contracts(p,0);});pages(p,v,0,0,n->contracts(p,0));show(p,v);}
    public void payments(Player p,int requested){Company c=service.own(p);var list=service.ledger.state().payments().values().stream().filter(t->t.company().equals(c.id())).sorted(Comparator.comparingLong(Payment::created).reversed()).toList();int page=page(requested,list.size());View v=view(p,"Платежи компании");
        for(int i=page*45;i<Math.min(list.size(),(page+1)*45);i++){Payment pay=list.get(i);put(v,i%45,pay.phase()==Phase.DONE?Material.LIME_DYE:Material.PAPER,purpose(pay.purpose()),List.of("Сумма: "+format(pay.amount()),"Статус: "+phase(pay.phase()),"Дата: "+date(pay.created()),"ID: "+pay.id()),null);}pages(p,v,page,list.size(),n->payments(p,n));show(p,v);
    }
    private int page(int n,int size){return Math.max(0,Math.min(n,Math.max(0,(size-1)/45)));}
    private String purpose(Purpose p){return switch(p){case DEPOSIT->"Пополнение";case WITHDRAW->"Вывод владельцу";case TAX->"Налог в городскую казну";case REFUND->"Возврат остатка награды городу";};}
    private String phase(Phase p){return switch(p){case READY->"В очереди";case PENDING->"Нужна банковская сверка";case DONE->"Выполнен";case REJECTED->"Отклонён банком";};}
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!v.player.equals(p.getUniqueId())||e.getRawSlot()<0||e.getRawSlot()>=v.inventory.getSize())return;
        var action=v.actions.get(e.getRawSlot());if(action==null)return;Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory()==v.inventory)command.safe(p,action);});}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
    public void close(){for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof View)p.closeInventory();}
}
