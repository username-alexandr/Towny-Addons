package ru.neverland.mintespionage.command;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ru.neverland.mintespionage.MintTownyEspionage;
import ru.neverland.mintespionage.integration.TownyHook;
import ru.neverland.mintespionage.model.SpyOperation;
import ru.neverland.mintespionage.service.EspionageService;
import ru.neverland.mintespionage.service.MessageService;
import ru.neverland.mintespionage.util.TimeUtil;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AdminCommand implements CommandExecutor,TabCompleter {
    private final MintTownyEspionage plugin;private final TownyHook towny;private final EspionageService service;private final MessageService messages;
    public AdminCommand(MintTownyEspionage plugin,TownyHook towny,EspionageService service,MessageService messages){this.plugin=plugin;this.towny=towny;this.service=service;this.messages=messages;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission("mintespionage.admin")){messages.send(sender,"no-permission");return true;}if(args.length==0){sender.sendMessage("/townyespionage <list|complete|cancel|set|reload>");return true;}
        switch(args[0].toLowerCase(Locale.ROOT)){
            case "reload"->{plugin.reloadPlugin();messages.send(sender,"reload");}
            case "list"->{List<SpyOperation> active=service.repository().operations().stream().filter(v->v.status().name().equals("ACTIVE")).toList();sender.sendMessage("Активные операции: "+active.size());for(SpyOperation value:active){var definition=service.registry().get(value.type());sender.sendMessage(value.shortId()+" | "+value.attackerName()+" → "+value.targetName()+" | "+(definition==null?value.type():ru.neverland.mintespionage.util.ColorUtil.strip(definition.name()))+" | "+TimeUtil.format(value.completesAt()-System.currentTimeMillis()));}}
            case "complete","cancel"->{if(args.length<2){sender.sendMessage("Укажите ID операции.");return true;}SpyOperation operation=find(args[1]);if(operation==null){messages.send(sender,"admin-not-found",Map.of("id",args[1]));return true;}boolean complete=args[0].equalsIgnoreCase("complete");boolean ok=complete?service.forceComplete(operation.id()):service.cancel(operation.id());messages.send(sender,ok?(complete?"admin-complete":"admin-cancel"):"admin-not-found",Map.of("id",operation.shortId()));}
            case "set"->{if(args.length<4){sender.sendMessage("/townyespionage set <город> <network|defense> <уровень>");return true;}Town town=towny.town(args[1]);if(town==null){messages.send(sender,"town-not-found",Map.of("town",args[1]));return true;}boolean network=args[2].equalsIgnoreCase("network");if(!network&&!args[2].equalsIgnoreCase("defense")){messages.send(sender,"invalid-upgrade");return true;}try{int level=Integer.parseInt(args[3]);service.setLevel(town,network,level);int actual=network?service.data(town).networkLevel():service.data(town).defenseLevel();messages.send(sender,"admin-set",Map.of("town",town.getName(),"type",network?"разведывательная сеть":"контрразведка","level",actual));}catch(NumberFormatException exception){sender.sendMessage("Уровень должен быть числом.");}}
            default->sender.sendMessage("/townyespionage <list|complete|cancel|set|reload>");
        }return true;
    }
    private SpyOperation find(String input){for(SpyOperation operation:service.repository().operations())if(operation.id().toString().equalsIgnoreCase(input)||operation.id().toString().startsWith(input.toLowerCase(Locale.ROOT)))return operation;try{return service.repository().operation(UUID.fromString(input));}catch(IllegalArgumentException ignored){return null;}}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){if(args.length==1)return filter(List.of("list","complete","cancel","set","reload"),args[0]);if(args.length==2&&(args[0].equalsIgnoreCase("complete")||args[0].equalsIgnoreCase("cancel")))return filter(service.repository().operations().stream().map(SpyOperation::shortId).toList(),args[1]);if(args.length==2&&args[0].equalsIgnoreCase("set"))return filter(towny.towns().stream().map(Town::getName).toList(),args[1]);if(args.length==3&&args[0].equalsIgnoreCase("set"))return filter(List.of("network","defense"),args[2]);return List.of();}
    private List<String> filter(List<String> values,String input){String lower=input.toLowerCase(Locale.ROOT);return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(lower)).toList();}
}
