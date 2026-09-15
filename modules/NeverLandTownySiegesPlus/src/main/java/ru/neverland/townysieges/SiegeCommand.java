package ru.neverland.townysieges;

import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.MenuStyle;

public final class SiegeCommand implements TabExecutor {
    private final NeverLandTownySiegesPlus plugin;private final SiegeService service;private final SiegeMenu menu;
    public SiegeCommand(NeverLandTownySiegesPlus plugin,SiegeService service,SiegeMenu menu){this.plugin=plugin;this.service=service;this.menu=menu;}
    static void tell(CommandSender p,String text){p.sendMessage(MenuStyle.decode("&6[Осады] &f"+text));}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        try{
            if(!sender.hasPermission("neverlandtownysiegesplus.use"))throw new IllegalArgumentException("Недостаточно прав");
            String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
            switch(action){
                case "menu"->{if(!(sender instanceof Player p))throw new IllegalArgumentException("Меню доступно в игре");if(args.length>2)throw new IllegalArgumentException("/sieges menu [город]");var town=args.length==2?TownyAPI.getInstance().getTown(args[1]):SiegeService.town(p.getUniqueId());if(town==null)throw new IllegalArgumentException("Укажите город: /sieges menu <город>");menu.open(p,town.getUUID(),MenuStyle.previousMenu(p));}
                case "status"->{if(!sender.hasPermission("neverlandtownysiegesplus.admin"))throw new IllegalArgumentException("Диагностика доступна администратору");var s=service.warStatus();tell(sender,s.get("state")+": "+s.get("detail"));if(!s.get("dependencyError").isBlank())tell(sender,"&e"+s.get("dependencyError"));}
                case "reload"->{if(!sender.hasPermission("neverlandtownysiegesplus.admin"))throw new IllegalArgumentException("Недостаточно прав");if(args.length!=1)throw new IllegalArgumentException("/sieges reload");plugin.reloadSieges();tell(sender,"Настройки осад обновлены.");}
                default->{tell(sender,"&e/sieges menu [город] &f— оборонительные постройки и ограничения.");tell(sender,"&e/t sieges &f— открыть меню своего города.");tell(sender,"Осаду и боевые сессии проводит SiegeWar.");if(sender.hasPermission("neverlandtownysiegesplus.admin"))tell(sender,"&e/sieges status &f— диагностика; &e/sieges reload &f— настройки.");}
            }
        }catch(Exception|LinkageError e){tell(sender,"&c"+e.getMessage());}
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(!sender.hasPermission("neverlandtownysiegesplus.use"))return List.of();
        var out=new ArrayList<String>();if(args.length==1){out.addAll(List.of("menu","help"));if(sender.hasPermission("neverlandtownysiegesplus.admin"))out.addAll(List.of("status","reload"));}
        if(args.length==2&&args[0].equalsIgnoreCase("menu"))TownyAPI.getInstance().getTowns().forEach(t->out.add(t.getName()));
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return out.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().limit(60).toList();
    }
}
