package ru.neverland.mintcontracts.command;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.neverland.mintcontracts.gui.ContractMenuManager;
import ru.neverland.mintcontracts.integration.TownyHook;
import ru.neverland.mintcontracts.model.ContractType;
import ru.neverland.mintcontracts.service.*;
import ru.neverland.mintcontracts.util.ColorUtil;
import java.util.*;
public final class TownContractsCommand implements CommandExecutor {
    private final TownyHook towny;private final ContractService contracts;private final ContractMenuManager menus;private final MessageService messages;
    public TownContractsCommand(TownyHook towny,ContractService contracts,ContractMenuManager menus,MessageService messages){this.towny=towny;this.contracts=contracts;this.menus=menus;this.messages=messages;}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(!(sender instanceof Player p)){messages.send(sender,"only-player");return true;}if(!p.hasPermission("mintcontracts.use")){messages.send(p,"no-permission");return true;}if(towny.town(p)==null){messages.send(p,"no-town");return true;}
        try{if(args.length==0){menus.open(p);return true;}
            switch(args[0].toLowerCase(Locale.ROOT)){
                case "history"->menus.openHistory(p,towny.town(p));
                case "claim"->{double n=contracts.claim(p);messages.send(p,n>0?"claim-success":n==0?"no-pending-reward":"economy-error",Map.of("amount",contracts.economy().format(Math.max(0,n))));}
                case "pos1","pos2"->menus.drafts().select(p,args[0].equalsIgnoreCase("pos1"));
                case "confirm"->menus.confirm(p);
                case "info"->{if(args.length!=2)throw new IllegalArgumentException("/t contracts info <ID>");menus.detail(p,args[1]);}
                case "cancel"->{if(args.length!=2)throw new IllegalArgumentException("/t contracts cancel <ID>");menus.cancel(p,args[1]);}
                case "start"->{if(args.length!=2)throw new IllegalArgumentException("/t contracts start <шаблон>");menus.drafts().template(p,contracts.registry().get(args[1]));menus.preview(p);}
                case "create"->create(p,args);
                default->help(p);
            }
        }catch(IllegalArgumentException|IllegalStateException ex){p.sendMessage(ColorUtil.color("&6NeverLand &8» &c"+(ex instanceof NumberFormatException?"Количество, награда и срок должны быть числами.":ex.getMessage())));}return true;
    }
    private void create(Player p,String[] a){if(a.length==1){menus.create(p);return;}String kind=a[1].toLowerCase(Locale.ROOT);
        // Preserve the old create <template> alias.
        if(a.length==2&&contracts.registry().get(a[1])!=null){menus.drafts().template(p,contracts.registry().get(a[1]));menus.preview(p);return;}
        switch(kind){
            case "delivery","hunt"->{if(a.length<5||a.length>6)throw new IllegalArgumentException("/t contracts create "+kind+" <цель> <количество> <награда> [часы]");menus.drafts().create(p,kind.equals("delivery")?ContractType.DELIVERY:ContractType.MOB_KILL,a[2],Integer.parseInt(a[3]),a[4],a.length==6?Long.parseLong(a[5]):24);}
            case "road"->{if(a.length<4||a.length>5)throw new IllegalArgumentException("/t contracts create road <материал> <награда> [часы]");menus.drafts().create(p,ContractType.ROAD,a[2],1,a[3],a.length==5?Long.parseLong(a[4]):24);}
            case "scout"->{if(a.length<3||a.length>4)throw new IllegalArgumentException("/t contracts create scout <награда> [часы]");menus.drafts().create(p,ContractType.SCOUT,"AREA",1,a[2],a.length==4?Long.parseLong(a[3]):24);}
            default->throw new IllegalArgumentException("Типы: delivery, hunt, road, scout. Без аргументов откроется конструктор.");
        }menus.preview(p);
    }
    private void help(Player p){p.sendMessage(ColorUtil.color("&f/t contracts &7— доска заказов\n&f/t contracts create &7— конструктор\n&f/t contracts create delivery hand 1000 3000 24\n&f/t contracts create hunt ZOMBIE 100 3000 24\n&f/t contracts pos1 &7и &f/t contracts pos2 &7— участок\n&f/t contracts create road STONE_BRICKS 3000 24\n&f/t contracts create scout 3000 24\n&f/t contracts confirm &7— подтвердить черновик\n&f/t contracts info <ID> &7— условия\n&f/t contracts cancel <ID> &7— отмена с подтверждением\n&f/t contracts claim &7— отложенная награда"));}
}
