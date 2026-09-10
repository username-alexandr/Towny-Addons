package ru.neverland.townymarket;
import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import static ru.neverland.townymarket.MarketData.*;
public final class MarketCommands implements CommandExecutor,TabCompleter {
    private final NeverLandTownyMarket plugin;private final MarketService service;private final MarketMenus menus;
    public MarketCommands(NeverLandTownyMarket plugin,MarketService service,MarketMenus menus){this.plugin=plugin;this.service=service;this.menus=menus;}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){try{
        String action=args.length==0?"local":args[0].toLowerCase(Locale.ROOT);
        if(Set.of("reload","resolve","inspect","admin-close").contains(action)){if(!sender.hasPermission("neverlandtownymarket.admin")){MarketService.tell(sender,"&cНет права администратора рынка");return true;}
            switch(action){case "reload"->{plugin.reloadConfig();service.reload();MarketService.tell(sender,service.available()?"Рынок перезагружен":"Рынок остановлен: проверьте журнал сервера");}
                case "inspect"->{if(args.length!=2){MarketService.tell(sender,"/townymarket inspect <покупка>");return true;}var o=service.order(args[1]);if(o==null)throw new IllegalArgumentException("Покупка не найдена");MarketService.tell(sender,"Покупка: "+o.id()+"; предложение: "+o.lot());MarketService.tell(sender,"Продавец: "+o.seller()+"; покупатель: "+o.buyer()+"; сумма: "+money(o.total()));MarketService.tell(sender,MarketMenus.phase(o.phase())+"; выдача: "+service.bridge.receipt(o));}
                case "resolve"->{if(args.length!=3){MarketService.tell(sender,"/townymarket resolve <полный UUID покупки> <debit-paid|debit-unpaid|credit-paid|credit-unpaid|claim-received|claim-unreceived>");return true;}service.resolve(UUID.fromString(args[1]),args[2]);MarketService.tell(sender,"Результат сверки сохранён. Проверьте также ожидающий платёж Treasury+, если он имеется");}
                case "admin-close"->{if(args.length!=2)throw new IllegalArgumentException("/townymarket admin-close <полный UUID предложения>");service.adminClose(UUID.fromString(args[1]));MarketService.tell(sender,"Предложение снимается с продажи");}
            }return true;
        }
        if(!(sender instanceof Player p)){help(sender);return true;}service.requireUse(p);
        switch(action){
            case "local","global","mine"->menus.open(p,action,args.length==2?Integer.parseInt(args[1])-1:0);
            case "orders"->menus.orders(p,args.length==2?Integer.parseInt(args[1])-1:0);
            case "catalog"->menus.catalog(p,args.length==2?Integer.parseInt(args[1])-1:0);
            case "sell"->{if(args.length!=5){help(p);return true;}Scope scope=switch(args[4].toLowerCase(Locale.ROOT)){case "local"->Scope.LOCAL;case "global"->Scope.GLOBAL;default->throw new IllegalArgumentException("Рынок: local или global");};var l=service.sell(p,args[1],Integer.parseInt(args[2]),args[3],scope);MarketService.tell(p,"Предложение "+l.shortId()+": "+l.note());menus.open(p,"mine",0);}
            case "buy"->{if(args.length!=3){help(p);return true;}menus.confirm(p,service.quote(p,args[1],Integer.parseInt(args[2])));}
            case "show"->{if(args.length!=2){help(p);return true;}menus.detail(p,args[1]);}
            case "close"->{if(args.length!=2){help(p);return true;}menus.detail(p,args[1]);MarketService.tell(p,"Для снятия нажмите «Снять предложение» и подтвердите возврат");}
            case "claim"->{if(args.length!=2){help(p);return true;}String result=service.claim(p,args[1]);MarketService.tell(p,result.equals("CLAIMED")?"Покупка получена":result.equals("MODE")?"Получение доступно в выживании и приключении":MarketPayments.stockNote(result));}
            default->help(p);
        }
    }catch(NumberFormatException ex){MarketService.tell(sender,"&cКоличество и номер страницы должны быть целыми числами");}catch(Exception ex){MarketService.tell(sender,"&c"+(ex.getMessage()==null?"Не удалось выполнить действие":ex.getMessage()));}return true;}
    public static void help(CommandSender p){MarketService.tell(p,"&e/t market <local|global|mine|orders|catalog> [страница]\n&f/t market sell <товар|hand> <кол-во> <цена за штуку|auto> <local|global>\n&f/t market buy <ID предложения> <кол-во>\n&f/t market show <ID>\n&f/t market claim <ID покупки>\n&7Пример: /t market sell iron_ingot 500 auto global");}
    public List<String> onTabComplete(CommandSender s,Command c,String alias,String[] args){List<String> values=List.of();if(args.length==1){var v=new ArrayList<>(List.of("local","global","mine","orders","catalog","sell","buy","show","close","claim"));if(s.hasPermission("neverlandtownymarket.admin"))v.addAll(List.of("reload","inspect","resolve","admin-close"));values=v;}
        else if(args.length==2&&args[0].equalsIgnoreCase("sell")){var v=new ArrayList<>(service.catalog.products().stream().map(MarketCatalog.Product::id).toList());v.add("hand");values=v;}
        else if(args.length==5&&args[0].equalsIgnoreCase("sell"))values=List.of("local","global");
        else if(args.length==4&&args[0].equalsIgnoreCase("sell"))values=List.of("auto");
        else if(args.length==2&&Set.of("buy","show","close").contains(args[0].toLowerCase(Locale.ROOT)))values=service.listings().stream().map(Listing::shortId).toList();
        if(args.length==0)return List.of();String prefix=args[args.length-1].toLowerCase(Locale.ROOT);return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).limit(100).toList();}
}
