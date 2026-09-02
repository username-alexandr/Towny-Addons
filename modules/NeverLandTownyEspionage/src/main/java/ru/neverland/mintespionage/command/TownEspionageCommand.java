package ru.neverland.mintespionage.command;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.neverland.mintespionage.gui.EspionageMenuManager;
import ru.neverland.mintespionage.integration.TownyHook;
import ru.neverland.mintespionage.model.OperationDefinition;
import ru.neverland.mintespionage.service.EspionageService;
import ru.neverland.mintespionage.service.MessageService;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TownEspionageCommand implements CommandExecutor,TabCompleter {
    private final TownyHook towny;private final EspionageService service;private final EspionageMenuManager menus;private final MessageService messages;
    public TownEspionageCommand(TownyHook towny,EspionageService service,EspionageMenuManager menus,MessageService messages){this.towny=towny;this.service=service;this.menus=menus;this.messages=messages;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player player)){messages.send(sender,"players-only");return true;}if(!player.hasPermission("mintespionage.use")){messages.send(player,"no-permission");return true;}Town own=towny.town(player);if(own==null){messages.send(player,"no-town");return true;}
        if(args.length==0){menus.open(player);return true;}switch(args[0].toLowerCase(Locale.ROOT)){
            case "help","помощь"->messages.help(player);case "targets","цели"->menus.openTargets(player);case "reports","report","отчёты"->{if(service.repository().reports(own.getUUID()).isEmpty())messages.send(player,"no-reports");menus.openReports(player);}case "active","активные"->{if(service.repository().active(own.getUUID()).isEmpty())messages.send(player,"no-active");menus.openActive(player);}
            case "start","начать"->start(player,own,args);case "upgrade","улучшить"->upgrade(player,own,args);default->messages.help(player);
        }return true;
    }
    private void start(Player player,Town own,String[] args){
        if(!manager(player,own))return;if(args.length<3){messages.help(player);return;}Town target=towny.town(args[1]);if(target==null){messages.send(player,"town-not-found",Map.of("town",args[1]));return;}OperationDefinition definition=service.registry().get(args[2]);if(definition==null){messages.send(player,"operation-not-found",Map.of("operation",args[2]));return;}menus.sendStart(player,service.start(player,target,definition),definition,target);
    }
    private void upgrade(Player player,Town town,String[] args){
        if(!manager(player,town))return;if(args.length<2){messages.send(player,"invalid-upgrade");return;}String type=args[1].toLowerCase(Locale.ROOT);boolean network=type.equals("network")||type.equals("сеть");boolean defense=type.equals("defense")||type.equals("counterintelligence")||type.equals("защита");if(!network&&!defense){messages.send(player,"invalid-upgrade");return;}menus.sendUpgrade(player,service.upgrade(town,network),network);
    }
    private boolean manager(Player player,Town town){if(!player.hasPermission("mintespionage.manage")||!towny.isManager(player,town)){messages.send(player,"only-manager");return false;}return true;}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1)return filter(List.of("start","reports","active","targets","upgrade","help"),args[0]);
        if(args.length==2&&args[0].equalsIgnoreCase("start"))return filter(towny.towns().stream().map(Town::getName).toList(),args[1]);
        if(args.length==3&&args[0].equalsIgnoreCase("start"))return filter(service.registry().all().stream().map(OperationDefinition::id).toList(),args[2]);
        if(args.length==2&&args[0].equalsIgnoreCase("upgrade"))return filter(List.of("network","defense"),args[1]);return List.of();
    }
    private List<String> filter(Collection<String> values,String input){String lower=input.toLowerCase(Locale.ROOT);return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();}
}
