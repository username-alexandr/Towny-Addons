package ru.neverland.townycompanies;

import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import static ru.neverland.townycompanies.CompanyData.*;
import static ru.neverland.townycompanies.CompanyService.message;

public final class CompanyCommand implements CommandExecutor,TabCompleter {
    @FunctionalInterface public interface Action {void run()throws Exception;}
    private record Quote(long until,UUID company,long revision,Action action){}
    private final JavaPlugin plugin;private final CompanyService service;private CompanyMenus menus;
    private final Map<UUID,Quote> quotes=new HashMap<>();
    public CompanyCommand(JavaPlugin plugin,CompanyService service){this.plugin=plugin;this.service=service;}
    public void menus(CompanyMenus menus){this.menus=menus;}
    public void quote(Player p,String description,Action action){
        service.requireUse(p);Company c=service.ledger.membership(p.getUniqueId());long now=System.currentTimeMillis();quotes.values().removeIf(q->q.until()<now);
        quotes.put(p.getUniqueId(),new Quote(now+30_000,c==null?null:c.id(),c==null?0:c.revision(),action));
        message(p,description+". Подтвердите: /company confirm (30 секунд)");menus.confirm(p,description);
    }
    public void confirm(Player p)throws Exception {
        service.requireUse(p);Quote q=quotes.remove(p.getUniqueId());Company c=service.ledger.membership(p.getUniqueId());
        if(q==null||q.until()<System.currentTimeMillis()||!Objects.equals(q.company(),c==null?null:c.id())||(c!=null&&q.revision()!=c.revision()))throw new IllegalArgumentException("Подтверждение истекло или состояние компании изменилось. Повторите действие");
        q.action().run();message(p,"Готово");menus.open(p);
    }
    public void safe(Player p,Action action){try{service.requireUse(p);action.run();}catch(Exception ex){message(p,ex.getMessage()==null?"Операция недоступна":ex.getMessage());if(ex instanceof java.io.IOException)plugin.getLogger().severe(ex.toString());}}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
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
    }
    public void takeQuote(Player p,UUID id){Company c=service.manage(p,false,true);Map<String,Object> offer=service.contracts.offers(c.town()).stream().filter(o->id.toString().equals(o.get("id"))).findFirst().orElseThrow(()->new IllegalArgumentException("Контракт не найден"));
        quote(p,"Принять заказ «"+offer.get("name")+"», цель "+offer.get("goal")+", награда на счёт компании "+offer.get("reward")+", срок до "+CompanyMenus.date(((Number)offer.get("expires")).longValue()),()->service.take(p,id));}
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
        message(p,"/company deposit <сумма>; withdraw <сумма>; payments");
        message(p,"/company contracts; take <UUID>; release <UUID>; /t contracts — выполнение заказов");
        message(p,"/company transfer <игрок>; acceptowner; disband; confirm; cancel");
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        List<String> values=args.length==1?List.of("help","list","create","members","invite","invitations","join","role","kick","leave","deposit","withdraw","payments","contracts","take","release","transfer","acceptowner","disband","confirm","cancel")
                :args.length==2&&args[0].equalsIgnoreCase("create")?List.of("shop","mine","farm","transport")
                :args.length==3&&args[0].equalsIgnoreCase("role")?List.of("manager","worker"):List.of();
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return values.stream().filter(s->s.startsWith(prefix)).toList();
    }
}
