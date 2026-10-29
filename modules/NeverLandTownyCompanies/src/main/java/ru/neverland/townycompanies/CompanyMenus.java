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
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM HH:mm 'МСК'").withZone(ZoneId.of("Europe/Moscow"));
    public static String date(long time){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return DATE.format(Instant.ofEpochMilli(time));} }
    private static final class View implements InventoryHolder {
        final UUID player;final Map<Integer,CompanyCommand.Action> actions=new HashMap<>();Inventory inventory;
        View(UUID player){this.player=player;}public Inventory getInventory(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return inventory;} }
    }
    public CompanyMenus(JavaPlugin plugin,CompanyService service,CompanyCommand command){this.plugin=plugin;this.service=service;this.command=command;}
    private View view(Player p,String title){service.requireUse(p);var v=new View(p.getUniqueId());v.inventory=ru.neverland.core.MenuStyle.inventory(plugin, v,54,"§6"+title);return v;}
    private void put(View v,int slot,Material icon,String title,List<String> lore,CompanyCommand.Action action){
        ItemStack item=new ItemStack(icon);var meta=item.getItemMeta();meta.setDisplayName(ru.neverland.core.MenuStyle.nameLegacy("§e"+title));meta.setLore(ru.neverland.core.MenuStyle.loreStrings(lore.stream().map(s->"§7"+s).toList()));meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);item.setItemMeta(meta);v.inventory.setItem(slot,item);if(action!=null)v.actions.put(slot,action);
    }
    private void show(Player p,View v){p.openInventory(v.inventory);}
    private void pages(Player p,View v,int page,int size,java.util.function.IntConsumer open){
        if(page>0)put(v,45,Material.ARROW,"Предыдущая страница",List.of(),()->open.accept(page-1));
        put(v,49,Material.BOOK,"Моё предприятие",List.of("Страница "+(page+1)),()->open(p));
        if((page+1)*45<size)put(v,53,Material.ARROW,"Следующая страница",List.of(),()->open.accept(page+1));
    }
    public void open(Player p){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        service.requireUse(p);Company c=service.ledger.membership(p.getUniqueId());View v=view(p,"Предприятия города");
        if(c==null){put(v,13,Material.WRITABLE_BOOK,"Зарегистрировать предприятие",List.of("Магазин, шахта, ферма или транспорт","Общий счёт, участники и городские заказы"),()->registration(p));}
        else {
            put(v,13,Material.valueOf(c.kind().icon),c.name(),List.of(c.kind().label,"Ваша роль: "+c.members().get(p.getUniqueId()).label,"Участники: "+c.members().size(),"Статус: "+(service.active(c)?c.debt()>0?"Налоговая задолженность":"Работает":"Владелец покинул город"),"ID: "+c.id()),null);
            put(v,20,Material.GOLD_INGOT,"Общий счёт",List.of("Баланс: "+format(c.balance()),"/company deposit <сумма>","Владелец: /company withdraw <сумма>","Нажмите: журнал платежей"),()->payments(p,0));
            put(v,22,Material.PLAYER_HEAD,"Участники",List.of("Пригласить: /company invite <игрок>","Нажмите для управления ролями"),()->members(p,0));
            put(v,24,Material.PAPER,"Городские контракты",List.of("Доступные и принятые заказы","Награда поступает на общий счёт"),()->contracts(p,0));
            put(v,32,Material.COMPARATOR,"Сметы и доходность",List.of("Материалы, зарплаты и налоги договоров","План и подтверждённые затраты показаны отдельно"),()->profits(p,0));
            put(v,30,Material.EMERALD,"Зарплаты за работу",List.of("Ставки, лимит и незавершённые начисления","Оплачиваются со счёта предприятия"),()->service.payroll.open(p));
            put(v,29,Material.CLOCK,"Налог городу",List.of("Ставка: "+format(service.rate(c.kind())),"Период: "+service.interval()/3_600_000+" ч","Следующее начисление: "+date(c.nextTax()),"Долг: "+format(c.debt()),"Оплата автоматически с общего счёта","При долге новые заказы недоступны"),null);
            if(c.owner().equals(p.getUniqueId()))put(v,33,Material.BARRIER,"Закрыть предприятие",List.of("Сначала завершите заказы и расчёты","Передача: /company transfer <участник>"),()->command.quote(p,"Закрыть «"+c.name()+"»",()->service.disband(p)));
            else put(v,33,Material.OAK_DOOR,"Выйти из компании",List.of("Общий счёт останется компании"),()->command.quote(p,"Выйти из «"+c.name()+"»",()->service.remove(p,p.getUniqueId())));
            if(p.getUniqueId().equals(c.successor())&&c.successorUntil()>System.currentTimeMillis())put(v,31,Material.NAME_TAG,"Принять владение",List.of("Вместе со счётом и налоговыми обязательствами"),()->command.onCommand(p,null,"company",new String[]{"acceptowner"}));
        }
        put(v,45,Material.CHEST,"Компании города",List.of(),()->directory(p,0));
        put(v,49,Material.WRITABLE_BOOK,"Приглашения",List.of(),()->invitations(p,0));
        put(v,53,Material.KNOWLEDGE_BOOK,"Помощь",List.of("Команды предприятия"),()->command.onCommand(p,null,"company",new String[]{"help"}));show(p,v);
    } }
    private void registration(Player p){View v=view(p,"Регистрация предприятия");int slot=10;for(Kind k:Kind.values())put(v,slot+=2,Material.valueOf(k.icon),k.label,List.of("Регистрация бесплатная","Налог: "+format(service.rate(k))+" / "+service.interval()/3_600_000+" ч","Нажмите, чтобы задать название"),()->{p.closeInventory();message(p,"/company create "+k.name().toLowerCase(Locale.ROOT)+" <название>");});pages(p,v,0,0,n->open(p));show(p,v);}
    public void confirm(Player p,String description){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {View v=view(p,"Подтверждение");put(v,13,Material.PAPER,"Условия",wrap(description),null);put(v,20,Material.LIME_DYE,"Подтвердить",List.of("Подтверждение действует 30 секунд"),()->command.confirm(p));put(v,24,Material.RED_DYE,"Отмена",List.of(),()->command.onCommand(p,null,"company",new String[]{"cancel"}));show(p,v);} }
    private List<String> wrap(String text){List<String> lines=new ArrayList<>();String line="";for(String word:text.split(" ")){if(line.length()+word.length()>48){lines.add(line);line="";}line+=(line.isEmpty()?"":" ")+word;}if(!line.isEmpty())lines.add(line);return lines;}
    public void directory(Player p,int requested){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {var town=CompanyService.town(p.getUniqueId());if(town==null)throw new IllegalArgumentException("Вы не состоите в городе");
        List<Company> list=service.ledger.state().companies().values().stream().filter(c->!c.closed()&&c.town().equals(town.getUUID())).sorted(Comparator.comparing(Company::name)).toList();int page=page(requested,list.size());View v=view(p,"Компании: "+town.getName());
        for(int i=page*45;i<Math.min(list.size(),(page+1)*45);i++){Company c=list.get(i);put(v,i%45,Material.valueOf(c.kind().icon),c.name(),List.of(c.kind().label,"Владелец: "+service.residentName(c.owner()),"Участники: "+c.members().size(),"ID: "+c.id()),null);}pages(p,v,page,list.size(),n->directory(p,n));show(p,v);
    } }
    public void members(Player p,int requested){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {Company c=service.own(p);var list=c.members().entrySet().stream().sorted(Comparator.comparing(e->service.residentName(e.getKey()))).toList();int page=page(requested,list.size());View v=view(p,"Участники компании");
        for(int i=page*45;i<Math.min(list.size(),(page+1)*45);i++){var e=list.get(i);put(v,i%45,Material.PLAYER_HEAD,service.residentName(e.getKey()),List.of(e.getValue().label),()->member(p,e.getKey()));}pages(p,v,page,list.size(),n->members(p,n));show(p,v);
    } }
    private void member(Player p,UUID target){Company c=service.own(p);if(!c.members().containsKey(target))throw new IllegalArgumentException("Участник вышел из компании");View v=view(p,"Участник: "+service.residentName(target));
        put(v,13,Material.PLAYER_HEAD,service.residentName(target),List.of(c.members().get(target).label),null);
        if(c.owner().equals(p.getUniqueId())&&!target.equals(c.owner())){
            put(v,20,Material.IRON_INGOT,"Сделать управляющим",List.of("Приглашения и принятие заказов"),()->{service.role(p,target,Role.MANAGER);members(p,0);});
            put(v,22,Material.WOODEN_PICKAXE,"Сделать участником",List.of("Выполнение заказов и пополнение счёта"),()->{service.role(p,target,Role.WORKER);members(p,0);});
            put(v,24,Material.RED_DYE,"Исключить",List.of(),()->command.quote(p,"Исключить "+service.residentName(target),()->service.remove(p,target)));
            put(v,31,Material.NAME_TAG,"Предложить владение",List.of("Получатель должен принять предложение"),()->command.quote(p,"Передать компанию игроку "+service.residentName(target),()->service.offerOwnership(p,target)));
        }pages(p,v,0,0,n->members(p,0));show(p,v);
    }
    public void invitations(Player p,int requested){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {long now=System.currentTimeMillis();var list=service.ledger.state().companies().values().stream().filter(c->!c.closed()&&c.invitations().getOrDefault(p.getUniqueId(),0L)>now).sorted(Comparator.comparing(Company::name)).toList();int page=page(requested,list.size());View v=view(p,"Приглашения в компании");
        for(int i=page*45;i<Math.min(list.size(),(page+1)*45);i++){Company c=list.get(i);put(v,i%45,Material.PAPER,c.name(),List.of(c.kind().label,"Принять приглашение"),()->command.quote(p,"Вступить в «"+c.name()+"»",()->service.join(p,c)));}pages(p,v,page,list.size(),n->invitations(p,n));show(p,v);
    } }
    public void contracts(Player p,int requested){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {Company c=service.own(p);var list=service.contracts.offers(c.town());int page=page(requested,list.size());View v=view(p,"Заказы для компании");
        for(int i=page*45;i<Math.min(list.size(),(page+1)*45);i++){var o=list.get(i);String assigned=(String)o.get("company");boolean mine=c.id().toString().equals(assigned);UUID id=UUID.fromString((String)o.get("id"));
            put(v,i%45,mine?Material.WRITABLE_BOOK:Material.PAPER,(String)o.get("name"),List.of("Цель: "+o.get("target"),"Прогресс: "+o.get("progress")+"/"+o.get("goal"),"Награда компании: "+o.get("reward"),"Срок: "+date(((Number)o.get("expires")).longValue()),"Исполнитель: "+o.get("assignee"),mine?"Нажмите: выполнение и смета":"Нажмите: условия, смета и принятие"),()->contract(p,id));}
        pages(p,v,page,list.size(),n->contracts(p,n));show(p,v);
    } }
    private void contract(Player p,UUID id){var c=service.own(p);var offer=service.contracts.offers(c.town()).stream().filter(o->id.toString().equals(o.get("id"))).findFirst().orElseThrow(()->new IllegalArgumentException("Договор больше не доступен"));boolean mine=c.id().toString().equals(offer.get("company"));boolean free="".equals(offer.get("company"));View v=view(p,"Городской договор");put(v,13,Material.PAPER,(String)offer.get("name"),List.of("Цель: "+offer.get("target"),"Прогресс: "+offer.get("progress")+"/"+offer.get("goal"),"Вознаграждение: "+offer.get("reward"),"Исполнитель: "+offer.get("assignee")),null);
        if(mine){put(v,20,Material.CHEST,"Выполнить договор",List.of("Открыть городские заказы и сдать поставку","Другие цели засчитываются во время игры"),()->{p.closeInventory();p.performCommand("t contracts");});put(v,24,Material.ARROW,"Освободить договор",List.of("Только если выполнение ещё не начато"),()->{service.release(p,id);contracts(p,0);});}
        else if(free)put(v,20,Material.LIME_DYE,"Принять компанией",List.of("Вознаграждение из городской казны","Сначала сравните затраты в смете"),()->command.takeQuote(p,id));
        if(service.profit!=null&&service.profit.available()&&(mine||free)){boolean exists=service.profit.state().reports().containsKey(ContractAccounting.reportId(c.id(),id));put(v,31,Material.COMPARATOR,exists?"Открыть смету":"Создать смету до принятия",List.of("План материалов, зарплат и налогов","Создание сметы не принимает договор и не списывает деньги"),()->{if(exists)profit(p,id);else command.profitQuote(p,id);});}
        put(v,49,Material.ARROW,"К городским договорам",List.of(),()->contracts(p,0));show(p,v);
    }
    private String known(Long cents){return cents==null?"неизвестно":format(cents);}
    private static void suggest(Player p,String text,String cmd){message(p,text);p.sendMessage(net.kyori.adventure.text.Component.text("Нажмите, чтобы заполнить команду").clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(cmd)));}
    public void profits(Player p,int requested){try(var guard=ru.neverland.core.SuiteState.guard()){
        var c=service.own(p);View v=view(p,"Сметы договоров");if(service.profit==null||!service.profit.available()){put(v,22,Material.BARRIER,"Учёт затрат сейчас недоступен",List.of(service.profit==null?"Раздел ещё запускается":service.profit.reason(),"Счёт компании и договоры продолжают работать"),null);pages(p,v,0,0,n->open(p));show(p,v);return;}
        var list=service.profit.reports(p);int page=page(requested,list.size());for(int i=page*45;i<Math.min(list.size(),(page+1)*45);i++){var r=list.get(i);var s=service.profit.summary(r);put(v,i%45,Material.COMPARATOR,r.title(),List.of("Плановый остаток: "+known(s.plannedMargin()),"Получено по договору: "+known(s.received()),"По внесённым затратам: "+known(s.accountedMargin()),"Материалы: заявленная стоимость управляющего","Нажмите: проверить подробный расчёт"),()->profit(p,r.contract()));}if(list.isEmpty())put(v,22,Material.PAPER,"Пока нет смет",List.of("Откройте городские договоры компании","Выберите договор и создайте смету"),()->contracts(p,0));pages(p,v,page,list.size(),n->profits(p,n));put(v,48,Material.PAPER,"Выбрать городской договор",List.of("Смету можно создать до принятия заказа"),()->contracts(p,0));show(p,v);
    }}
    public void profit(Player p,UUID contract){try(var guard=ru.neverland.core.SuiteState.guard()){
        var c=service.own(p);var r=service.profit.report(p,contract);var s=service.profit.summary(r);View v=view(p,"Доходность договора");
        put(v,4,Material.PAPER,r.title(),List.of("Цель: "+r.target()+" • "+r.goal(),"Вознаграждение при создании сметы: "+format(r.reward()),"Получено компанией: "+known(s.received()),"Возвращено городу: "+format(s.refund())+" — не доход компании"),null);
        put(v,19,Material.WRITABLE_BOOK,"План затрат",List.of("Материалы: "+known(r.budget().materials()),"Зарплаты: "+known(r.budget().wages()),"Налоги: "+known(r.budget().taxes()),"Плановый остаток: "+known(s.plannedMargin()),"Неизвестная статья не считается нулевой"),null);
        put(v,22,Material.CHEST,"Стоимость материалов",List.of("Заявлено: "+known(r.declaredMaterials()),r.materialNote().isEmpty()?"Стоимость ещё не внесена":r.materialNote(),"Указывает владелец или управляющий","Цена покупки сервером не подтверждается"),null);
        put(v,25,Material.EMERALD,"Учтённые платежи",List.of("Оплаченные зарплаты: "+format(s.paidWages()),"Оплаченные налоги: "+format(s.paidTaxes()),"Это части подтверждённых платежей компании","Распределение между договорами задаёт управляющий"),()->profitCosts(p,contract,0));
        var lines=new ArrayList<String>(List.of("Остаток по внесённым затратам: "+known(s.accountedMargin()),"Получено − материалы − зарплаты − налоги","Это не подтверждение отсутствия других расходов","Ещё не распределено по всей компании:","Зарплаты "+format(s.unallocatedWages())+", налоги "+format(s.unallocatedTaxes())));if(s.unverified()>0)lines.add("Не подтверждены источники "+s.unverified()+" затрат; нужна проверка");if(service.profit.saving())lines.add("Предыдущее изменение ещё сохраняется");put(v,40,Material.COMPARATOR,"Как получился расчёт",lines,null);
        if(c.manages(p.getUniqueId())&&service.profit.writable()&&!service.profit.saving()){
            put(v,29,Material.BOOK,"Изменить план затрат",List.of("Три суммы в монетах: материалы, зарплаты, налоги","? — сумма пока неизвестна","Перед сохранением появится подтверждение"),()->suggest(p,"Замените нужные суммы; ? оставляет статью неизвестной.","/company profit budget "+contract+" "+(r.budget().materials()==null?"?":format(r.budget().materials()))+" "+(r.budget().wages()==null?"?":format(r.budget().wages()))+" "+(r.budget().taxes()==null?"?":format(r.budget().taxes()))));
            put(v,31,Material.FEATHER,"Указать стоимость материалов",List.of("По вашему учёту, с обязательным пояснением","Деньги повторно не списываются"),()->suggest(p,"Укажите общую стоимость материалов и пояснение. Это заявленные, а не подтверждённые банком затраты.","/company profit materials "+contract+" "+(r.declaredMaterials()==null?"0":format(r.declaredMaterials()))+" пояснение"));
            put(v,33,Material.GOLD_INGOT,"Привязать оплаченные расходы",List.of("Выбрать зарплату или налог компании","Одну часть платежа нельзя учесть дважды"),()->profitPayments(p,contract,0));
        }else if(!c.manages(p.getUniqueId()))put(v,31,Material.BARRIER,"Изменяет владелец или управляющий",List.of("Участники компании могут читать расчёт"),null);
        put(v,49,Material.ARROW,"К сметам",List.of(),()->profits(p,0));show(p,v);
    }}
    public void profitPayments(Player p,UUID contract,int requested){try(var guard=ru.neverland.core.SuiteState.guard()){
        var c=service.own(p);service.profit.report(p,contract);var image=service.profit.state();var totals=ContractAccounting.totals(image);var list=service.ledger.state().payments().values().stream().filter(pay->pay.company().equals(c.id())&&pay.phase()==Phase.DONE&&(pay.purpose()==Purpose.SALARY||pay.purpose()==Purpose.TAX)&&totals.getOrDefault(pay.id(),0L)<pay.amount()).sorted(Comparator.comparingLong(Payment::created).reversed()).toList();int page=page(requested,list.size());View v=view(p,"Оплаченные расходы компании");
        for(int i=page*45;i<Math.min(list.size(),(page+1)*45);i++){var pay=list.get(i);long available=pay.amount()-totals.getOrDefault(pay.id(),0L);put(v,i%45,Material.GOLD_NUGGET,purpose(pay.purpose()),List.of("Всего оплачено: "+format(pay.amount()),"Не распределено: "+format(available),"Дата: "+date(pay.created()),"Квитанция: "+pay.id().toString().substring(0,8),"Нажмите: выбрать часть платежа"),()->profitPayment(p,contract,pay.id()));}if(list.isEmpty())put(v,22,Material.PAPER,"Нет свободных оплаченных затрат",List.of("Нужна выполненная зарплата или налог компании","Ожидающие сверки платежи сюда не входят"),null);pages(p,v,page,list.size(),n->profitPayments(p,contract,n));put(v,49,Material.ARROW,"К расчёту договора",List.of(),()->profit(p,contract));show(p,v);
    }}
    private void profitPayment(Player p,UUID contract,UUID payment){var c=service.own(p);var pay=service.ledger.state().payments().get(payment);if(pay==null||!pay.company().equals(c.id())||pay.phase()!=Phase.DONE)throw new IllegalArgumentException("Платёж больше не доступен для учёта");long available=pay.amount()-ContractAccounting.allocated(service.profit.state(),payment);if(available<1)throw new IllegalArgumentException("Платёж уже распределён целиком");boolean allowed=c.manages(p.getUniqueId())&&service.profit.writable()&&!service.profit.saving();View v=view(p,"Выбрать часть затрат");put(v,13,Material.PAPER,purpose(pay.purpose()),List.of("Не распределено: "+format(available),"Новый платёж не выполняется"),null);long[] amounts={Math.max(1,available/4),Math.max(1,available/2),available};for(int i=0;i<3;i++){long amount=amounts[i];put(v,19+i*3,allowed?Material.GOLD_NUGGET:Material.BARRIER,allowed?"Учесть "+format(amount):"Распределение пока недоступно",List.of("В расходах этого договора: "+format(amount),allowed?"После отдельного подтверждения":"Доступно владельцу или управляющему при работающей записи"),allowed?()->command.allocationQuote(p,contract,payment,amount):null);}put(v,40,Material.FEATHER,"Другая сумма",List.of("В монетах, не больше свободной части"),allowed?()->suggest(p,"Укажите часть уже оплаченного расхода.","/company profit allocate "+contract+" "+payment+" "+format(available)):null);put(v,49,Material.ARROW,"К платежам",List.of(),()->profitPayments(p,contract,0));show(p,v);}
    public void profitCosts(Player p,UUID contract,int requested){try(var guard=ru.neverland.core.SuiteState.guard()){
        var c=service.own(p);var r=service.profit.report(p,contract);var list=service.profit.state().allocations().values().stream().filter(a->a.report().equals(r.id())).sorted(Comparator.comparingLong(ContractAccounting.Allocation::created).reversed()).toList();int page=page(requested,list.size());View v=view(p,"Распределённые затраты договора");for(int i=page*45;i<Math.min(list.size(),(page+1)*45);i++){var a=list.get(i);put(v,i%45,a.reversed()?Material.GRAY_DYE:Material.GOLD_NUGGET,a.cost()==ContractAccounting.Cost.WAGES?"Зарплаты":"Налоги",List.of("Сумма: "+format(a.cents()),a.reversed()?"Распределение снято":"Входит в расходы договора","Автор: "+service.residentName(a.actor()),"Дата: "+date(a.created()),a.reversed()?"Исходная запись сохранена":"Нажмите: снять распределение без отмены платежа"),a.reversed()||!c.manages(p.getUniqueId())?null:()->command.undoQuote(p,contract,a.id()));}if(list.isEmpty())put(v,22,Material.PAPER,"Пока нет привязанных платежей",List.of("Оплаченные расходы добавляются отдельно"),()->profitPayments(p,contract,0));pages(p,v,page,list.size(),n->profitCosts(p,contract,n));put(v,49,Material.ARROW,"К расчёту договора",List.of(),()->profit(p,contract));show(p,v);
    }}
    public void payments(Player p,int requested){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {Company c=service.own(p);var list=service.ledger.state().payments().values().stream().filter(t->t.company().equals(c.id())).sorted(Comparator.comparingLong(Payment::created).reversed()).toList();int page=page(requested,list.size());View v=view(p,"Платежи компании");
        for(int i=page*45;i<Math.min(list.size(),(page+1)*45);i++){Payment pay=list.get(i);put(v,i%45,pay.phase()==Phase.DONE?Material.LIME_DYE:Material.PAPER,purpose(pay.purpose()),List.of("Сумма: "+format(pay.amount()),"Статус: "+phase(pay.phase()),"Дата: "+date(pay.created()),"ID: "+pay.id()),null);}pages(p,v,page,list.size(),n->payments(p,n));show(p,v);
    } }
    private int page(int n,int size){return Math.max(0,Math.min(n,Math.max(0,(size-1)/45)));}
    private String purpose(Purpose p){return switch(p){case SALARY->"Зарплата сотруднику";case DEPOSIT->"Пополнение";case WITHDRAW->"Вывод владельцу";case TAX->"Налог в городскую казну";case REFUND->"Возврат остатка награды городу";};}
    private String phase(Phase p){return switch(p){case READY->"В очереди";case PENDING->"Нужна банковская сверка";case DONE->"Выполнен";case REJECTED->"Отклонён банком";};}
    @EventHandler public void click(InventoryClickEvent e){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyCompanies")){ru.neverland.core.ModuleGate.stopEvent(e);return;}if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!v.player.equals(p.getUniqueId())||e.getRawSlot()<0||e.getRawSlot()>=v.inventory.getSize())return;
        var action=v.actions.get(e.getRawSlot());if(action==null)return;ru.neverland.core.SuiteScheduler.entity(plugin, p,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory()==v.inventory)command.safe(p,action);});} }
    @EventHandler public void drag(InventoryDragEvent e){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyCompanies")){ru.neverland.core.ModuleGate.stopEvent(e);return;}if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);} }
    public void close(){ try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {ru.neverland.core.SuiteScheduler.players(plugin, p -> {if(p.getOpenInventory().getTopInventory().getHolder() instanceof View)p.closeInventory();});} }
}
