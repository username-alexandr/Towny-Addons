package ru.neverland.townycompanies;

import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import static ru.neverland.townycompanies.CompanyData.*;
import static ru.neverland.townycompanies.CompanyService.message;

public final class CompanyCommand implements CommandExecutor,TabCompleter {
    @FunctionalInterface public interface Action {void run()throws Exception;}
    @FunctionalInterface public interface AsyncAction{java.util.concurrent.CompletionStage<?> run()throws Exception;}
    private record Quote(long until,UUID company,long revision,Action action,AsyncAction async){}
    private final JavaPlugin plugin;private final CompanyService service;private CompanyMenus menus;
    private final Map<UUID,Quote> quotes=new HashMap<>();
    public CompanyCommand(JavaPlugin plugin,CompanyService service){this.plugin=plugin;this.service=service;}
    public void menus(CompanyMenus menus){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {this.menus=menus;} }
    public void quote(Player p,String description,Action action){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        service.requireUse(p);Company c=service.ledger.membership(p.getUniqueId());long now=System.currentTimeMillis();quotes.values().removeIf(q->q.until()<now);
        quotes.put(p.getUniqueId(),new Quote(now+30_000,c==null?null:c.id(),c==null?0:c.revision(),action,null));
        message(p,description+". Подтвердите: /company confirm (30 секунд)");menus.confirm(p,description);
    } }
    public void quoteAsync(Player p,String description,AsyncAction action){try(var guard=ru.neverland.core.SuiteState.guard()){var company=service.manage(p,false,true);quotes.values().removeIf(q->q.until()<System.currentTimeMillis());quotes.put(p.getUniqueId(),new Quote(System.currentTimeMillis()+30000,company.id(),company.revision(),null,action));message(p,description+". Подтвердите: /company confirm (30 секунд)");menus.confirm(p,description);}}
    public void confirm(Player p)throws Exception { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        service.requireUse(p);Quote q=quotes.remove(p.getUniqueId());Company c=service.ledger.membership(p.getUniqueId());
        if(q==null||q.until()<System.currentTimeMillis()||!Objects.equals(q.company(),c==null?null:c.id())||(c!=null&&q.revision()!=c.revision()))throw new IllegalArgumentException("Подтверждение истекло или состояние компании изменилось. Повторите действие");
        if(q.async()!=null){var expected=p.getOpenInventory().getTopInventory();q.async().run().whenComplete((value,error)->ru.neverland.core.SuiteScheduler.entity(plugin,p,()->{if(!p.isOnline())return;if(error!=null){message(p,"Изменение не подтверждено. Проверьте сохранённую смету перед повтором");return;}message(p,"Изменение учёта сохранено. Деньги и предметы не перемещались");try{var current=service.ledger.membership(p.getUniqueId());if(current!=null&&current.id().equals(q.company())&&p.getOpenInventory().getTopInventory()==expected)menus.profits(p,0);}catch(Exception ignored){/* The receipt remains valid after membership or window changes. */}}));return;}
        q.action().run();message(p,"Готово");menus.open(p);
    } }
    public void safe(Player p,Action action){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {try{service.requireUse(p);action.run();}catch(Exception ex){message(p,ex.getMessage()==null?"Операция недоступна":ex.getMessage());if(ex instanceof java.io.IOException)plugin.getLogger().severe(ex.toString());}} }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyCompanies")){return true;}
        if(args.length>0&&args[0].equalsIgnoreCase("admin")){admin(sender,args);return true;}
        if(!(sender instanceof Player p)){message(sender,"Команда доступна игроку; /company admin pending");return true;}
        safe(p,()->{
            if(args.length==0){menus.open(p);return;}
            switch(args[0].toLowerCase(Locale.ROOT)) {
                case "help" -> help(p);
                case "list" -> menus.directory(p,0);
                case "members" -> menus.members(p,0);
                case "contracts" -> menus.contracts(p,0);
                case "payments" -> menus.payments(p,0);
                case "profit" -> profit(p,args);
                case "payroll" -> {
                    if(args.length==1){service.payroll.open(p);break;}
                    service.manage(p,true,true);String a=args[1].toLowerCase(Locale.ROOT);String profession="";long amount=0;
                    if(a.equals("rate")){need(args,4);profession=args[2].toLowerCase(Locale.ROOT);amount=args[3].equals("0")?0:cents(args[3]);}
                    else if(a.equals("budget")){need(args,3);amount=args[2].equals("0")?0:cents(args[2]);}
                    else if(!a.equals("on")&&!a.equals("off"))throw new IllegalArgumentException("/company payroll [rate <профессия> <сумма>|budget <сумма>|on|off]");
                    String job=profession;long money=amount;quote(p,"Изменить зарплаты предприятия: "+a+" "+(job.isEmpty()?"":CompanyPayroll.title(job)+" ")+format(money)+". Уже начисленные выплаты сохраняются",()->service.payroll.configure(p,a,job,money));
                }
                case "invitations" -> menus.invitations(p,0);
                case "confirm" -> confirm(p);
                case "cancel" -> {quotes.remove(p.getUniqueId());menus.open(p);}
                case "create" -> {need(args,3);Kind kind=Kind.parse(args[1]);String name=cleanName(String.join(" ",Arrays.copyOfRange(args,2,args.length)));long rate=service.rate(kind),interval=service.interval();UUID town=CompanyService.town(p.getUniqueId())==null?null:CompanyService.town(p.getUniqueId()).getUUID();
                    quote(p,"Зарегистрировать «"+name+"»: "+kind.label+". Налог "+format(rate)+" каждые "+interval/3_600_000+" ч",()->{var t=CompanyService.town(p.getUniqueId());if(t==null||!t.getUUID().equals(town)||rate!=service.rate(kind)||interval!=service.interval())throw new IllegalArgumentException("Условия регистрации изменились");service.create(p,kind,name);});}
                case "deposit", "withdraw" -> {need(args,2);long amount=cents(args[1]);Purpose purpose=args[0].equalsIgnoreCase("deposit")?Purpose.DEPOSIT:Purpose.WITHDRAW;
                    quote(p,(purpose==Purpose.DEPOSIT?"Пополнить общий счёт из личных средств на ":"Вывести владельцу из общего счёта ")+format(amount),()->service.money(p,purpose,amount));}
                case "invite" -> {need(args,2);service.invite(p,service.resident(args[1]));message(p,"Приглашение создано");}
                case "join" -> {need(args,2);Company c=service.find(args[1]);quote(p,"Вступить в «"+c.name()+"» участником",()->service.join(p,c));}
                case "role" -> {need(args,3);service.role(p,service.resident(args[1]),Role.valueOf(args[2].toUpperCase(Locale.ROOT)));message(p,"Роль обновлена");}
                case "kick" -> {need(args,2);service.remove(p,service.resident(args[1]));message(p,"Участник или приглашение удалены");}
                case "leave" -> {service.remove(p,p.getUniqueId());message(p,"Вы вышли из компании");}
                case "transfer" -> {need(args,2);UUID target=service.resident(args[1]);quote(p,"Предложить владение компанией игроку "+service.residentName(target),()->service.offerOwnership(p,target));}
                case "acceptowner" -> {Company c=service.own(p);quote(p,"Принять владение «"+c.name()+"», счёт "+format(c.balance())+", долг "+format(c.debt()),()->service.acceptOwnership(p));}
                case "disband" -> quote(p,"Закрыть предприятие",()->service.disband(p));
                case "take" -> {need(args,2);UUID id=UUID.fromString(args[1]);takeQuote(p,id);}
                case "release" -> {need(args,2);service.release(p,UUID.fromString(args[1]));message(p,"Контракт снова доступен городу");}
                default -> help(p);
            }
        });return true;
    } }
    private Long cost(String raw){return raw.equals("?")?null:raw.equals("0")?0:cents(raw);}
    private String costText(Long value){return value==null?"неизвестно":format(value);}
    private void profit(Player p,String[] a){
        if(a.length==1){menus.profits(p,0);return;}if(a.length==2){menus.profit(p,UUID.fromString(a[1]));return;}UUID id=UUID.fromString(a[2]);switch(a[1].toLowerCase(Locale.ROOT)){
            case "create"->{if(a.length!=3)throw new IllegalArgumentException("/company profit create <договор>");profitQuote(p,id);}
            case "payments"->{if(a.length!=3)throw new IllegalArgumentException("/company profit payments <договор>");menus.profitPayments(p,id,0);}
            case "costs"->{if(a.length!=3)throw new IllegalArgumentException("/company profit costs <договор>");menus.profitCosts(p,id,0);}
            case "budget"->{if(a.length!=6)throw new IllegalArgumentException("/company profit budget <договор> <материалы> <зарплаты> <налоги>; ? — неизвестно");var r=service.profit.report(p,id);var b=new ContractAccounting.Budget(cost(a[3]),cost(a[4]),cost(a[5]));quoteAsync(p,"Смета «"+r.title()+"»: материалы "+costText(b.materials())+", зарплаты "+costText(b.wages())+", налоги "+costText(b.taxes())+". Это план, без списания средств",()->service.profit.budget(p,id,r.revision(),b));}
            case "materials"->{if(a.length<4)throw new IllegalArgumentException("/company profit materials <договор> <стоимость|?> <пояснение>");var r=service.profit.report(p,id);Long amount=cost(a[3]);String note=amount==null?"":String.join(" ",Arrays.copyOfRange(a,4,a.length));if(amount!=null&&(note.isBlank()||note.length()>200))throw new IllegalArgumentException("Добавьте пояснение стоимости материалов длиной до 200 символов");quoteAsync(p,"Заявленная стоимость материалов: "+costText(amount)+". "+note+". Цена покупки сервером не подтверждается; новый платёж не выполняется",()->service.profit.materials(p,id,r.revision(),amount,note));}
            case "allocate"->{if(a.length!=5)throw new IllegalArgumentException("/company profit allocate <договор> <платёж> <сумма>");allocationQuote(p,id,UUID.fromString(a[3]),cents(a[4]));}
            case "undo"->{if(a.length!=4)throw new IllegalArgumentException("/company profit undo <договор> <распределение>");undoQuote(p,id,UUID.fromString(a[3]));}
            default->throw new IllegalArgumentException("/company profit [<договор>|create|budget|materials|payments|costs|allocate|undo]");
        }
    }
    public void profitQuote(Player p,UUID contract){var r=service.profit.proposal(p,contract);quoteAsync(p,"Создать смету «"+r.title()+"»: цель "+r.goal()+", вознаграждение "+format(r.reward())+" из городской казны. Затраты пока неизвестны; договор ещё не принимается",()->service.profit.create(p,r));}
    public void allocationQuote(Player p,UUID contract,UUID payment,long amount){var r=service.profit.report(p,contract);UUID allocation=UUID.randomUUID();long created=System.currentTimeMillis();quoteAsync(p,"Отнести "+format(amount)+" уже оплаченных затрат к договору «"+r.title()+"». Эта часть платежа станет недоступна другим сметам. Деньги повторно не списываются",()->service.profit.allocate(p,contract,r.revision(),payment,amount,allocation,created));}
    public void undoQuote(Player p,UUID contract,UUID allocation){var r=service.profit.report(p,contract);quoteAsync(p,"Снять распределение затрат с договора «"+r.title()+"». Подтверждённый банковский платёж останется выполненным; освободится только его учёт в смете",()->service.profit.reverse(p,contract,r.revision(),allocation));}
    public void takeQuote(Player p,UUID id){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {Company c=service.manage(p,false,true);Map<String,Object> offer=service.contracts.offers(c.town()).stream().filter(o->id.toString().equals(o.get("id"))).findFirst().orElseThrow(()->new IllegalArgumentException("Контракт не найден"));
        quote(p,"Принять заказ «"+offer.get("name")+"», цель "+offer.get("goal")+", награда на счёт компании "+offer.get("reward")+", срок до "+CompanyMenus.date(((Number)offer.get("expires")).longValue()),()->service.take(p,id));} }
    private void admin(CommandSender sender,String[] args){
        if(!sender.hasPermission("neverlandtownycompanies.admin")){message(sender,"Нет разрешения");return;}
        try {
            if(args.length>=2&&args[1].equalsIgnoreCase("pending")){
                service.ledger.state().payments().values().stream().filter(p->p.phase()==Phase.PENDING||p.phase()==Phase.READY).forEach(p->message(sender,p.id()+" | компания "+p.company()+" | счёт "+p.account()+" | "+p.purpose()+" | "+format(p.amount())+" | "+p.phase()));
                message(sender,"Сверьте операции с банковским журналом; /company admin resolve <UUID> applied|rejected");
            }else if(args.length==5&&args[1].equalsIgnoreCase("resolve")&&args[4].equalsIgnoreCase("confirm")){
                if(!args[3].equalsIgnoreCase("applied")&&!args[3].equalsIgnoreCase("rejected"))throw new IllegalArgumentException("Укажите applied или rejected");UUID id=UUID.fromString(args[2]);boolean applied=args[3].equalsIgnoreCase("applied");
                service.ledger.finish(id,applied);plugin.getLogger().warning(sender.getName()+" сверил платёж компании "+id+": "+args[3]);message(sender,"Результат сверки сохранён");
            }else if(args.length==4&&args[1].equalsIgnoreCase("resolve"))message(sender,"Только после проверки банковского журнала повторите команду с confirm: /company admin resolve "+args[2]+" "+args[3]+" confirm");
            else message(sender,"/company admin pending; /company admin resolve <UUID> applied|rejected confirm");
        }catch(Exception ex){message(sender,ex.getMessage());}
    }
    private static void need(String[] args,int n){if(args.length<n)throw new IllegalArgumentException("Недостаточно аргументов. /company help");}
    private void help(Player p){
        message(p,"/company — предприятие; /company list — компании города");
        message(p,"/company create <shop|mine|farm|transport> <название>");
        message(p,"/company invite <игрок>; join <ID>; role <игрок> <manager|worker>; kick <игрок>; leave");
        message(p,"/company deposit <сумма>; withdraw <сумма>; payments; payroll — зарплаты сотрудникам");
        message(p,"/company profit — сметы и доходность договоров; ? в бюджете означает неизвестные затраты");
        message(p,"/company contracts; take <UUID>; release <UUID>; /t contracts — выполнение заказов");
        message(p,"/company transfer <игрок>; acceptowner; disband; confirm; cancel");
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        List<String> values=args.length==1?List.of("help","list","create","members","invite","invitations","join","role","kick","leave","deposit","withdraw","payments","payroll","profit","contracts","take","release","transfer","acceptowner","disband","confirm","cancel")
                :args.length==2&&args[0].equalsIgnoreCase("profit")?List.of("create","budget","materials","payments","costs","allocate","undo")
                :args.length==2&&args[0].equalsIgnoreCase("create")?List.of("shop","mine","farm","transport")
                :args.length==3&&args[0].equalsIgnoreCase("role")?List.of("manager","worker"):List.of();
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return values.stream().filter(s->s.startsWith(prefix)).toList();
    } }
}
