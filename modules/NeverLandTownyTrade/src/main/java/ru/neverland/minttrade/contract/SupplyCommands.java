package ru.neverland.minttrade.contract;
import java.util.*;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.minttrade.integration.TownyHook;
import ru.neverland.minttrade.service.TradeService;
import ru.neverland.minttrade.util.ColorUtil;
public final class SupplyCommands {
    private final TownyHook towny;private final TradeService trade;private final SupplyService service;private final SupplyMenus menus;
    public SupplyCommands(TownyHook towny,TradeService trade,SupplyService service,SupplyMenus menus){this.towny=towny;this.trade=trade;this.service=service;this.menus=menus;}
    public void execute(Player p,Town town,String[] args){
        if(!menus.access(p))return;
        try{
            if(args.length==0||args[0].equalsIgnoreCase("list")){menus.open(p,args.length>1?Math.max(0,Integer.parseInt(args[1])-1):0);return;}
            if(args[0].equalsIgnoreCase("show")&&args.length==2){menus.detail(p,args[1],0);return;}
            if(!menus.manage(p,town))return;
            switch(args[0].toLowerCase(Locale.ROOT)){
                case "propose" -> {
                    if(args.length<5||args.length>6){help(p);return;}
                    var buyer=towny.town(args[1]);if(buyer==null)throw new IllegalArgumentException("Город не найден");
                    var item=args[2].equalsIgnoreCase("hand")?p.getInventory().getItemInMainHand():trade.registry().parseItem(args[2]);
                    var c=service.propose(town,buyer,item,Integer.parseInt(args[3]),SupplyContract.price(args[4]),args.length==6?Integer.parseInt(args[5]):service.defaultDays());
                    menus.detail(p,c.terms().id().toString(),0);
                }
                case "accept", "pause", "resume", "cancel", "reject" -> {
                    if(args.length!=2){help(p);return;}menus.confirm(p,args[1],args[0].equalsIgnoreCase("reject")?"cancel":args[0].toLowerCase(Locale.ROOT),0);
                }
                default -> help(p);
            }
        }catch(NumberFormatException ex){menus.error(p,new IllegalArgumentException("Количество, период и номер страницы должны быть целыми числами"));}
        catch(Exception ex){menus.error(p,ex);}
    }
    private void help(Player p){p.sendMessage(ColorUtil.color("&e/t trade contracts [list <страница>]\n&f/t trade contract propose <город> <предмет|hand> <кол-во> <цена> [дни]\n&f/t trade contract show <ID>\n&f/t trade contract <accept|pause|resume|cancel> <ID>\n&7Пример: /t trade contract propose Москва IRON_INGOT 500 2000 7\n&7ItemsAdder: itemsadder:namespace:item; hand — точная копия предмета в руке."));}
}
