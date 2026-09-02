package ru.neverland.townychronicles.command;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ru.neverland.townychronicles.TownyChronicles;
import ru.neverland.townychronicles.integration.TownyHook;
import ru.neverland.townychronicles.model.ChronicleCategory;
import ru.neverland.townychronicles.model.ChronicleEntry;
import ru.neverland.townychronicles.service.ChronicleService;
import ru.neverland.townychronicles.service.ChronicleTracker;
import ru.neverland.townychronicles.service.MessageService;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdminCommand implements CommandExecutor,TabCompleter {
    private final TownyChronicles plugin;private final TownyHook towny;private final ChronicleService chronicles;private final ChronicleTracker tracker;private final MessageService messages;
    public AdminCommand(TownyChronicles plugin,TownyHook towny,ChronicleService chronicles,ChronicleTracker tracker,MessageService messages){this.plugin=plugin;this.towny=towny;this.chronicles=chronicles;this.tracker=tracker;this.messages=messages;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[]args){if(!sender.hasPermission("townychronicles.admin")){messages.send(sender,"no-permission");return true;}if(args.length==0){help(sender);return true;}switch(args[0].toLowerCase(Locale.ROOT)){case "reload"->{plugin.reloadPlugin();messages.send(sender,"reload");}case "rescan"->messages.send(sender,"rescan",Map.of("count",tracker.scanAll(true)));case "add"->add(sender,args);case "remove"->remove(sender,args);case "war"->war(sender,args);default->help(sender);}return true;}
    private void add(CommandSender sender,String[]args){if(args.length<4){sender.sendMessage("/townychronicles add <город> <категория> <текст>");return;}Town town=towny.town(args[1]);if(town==null){messages.send(sender,"town-not-found",Map.of("town",args[1]));return;}ChronicleCategory category=ChronicleCategory.parse(args[2]);if(category==null){messages.send(sender,"invalid-category",Map.of("category",args[2]));return;}String text=String.join(" ",Arrays.copyOfRange(args,3,args.length));ChronicleEntry entry=chronicles.record(town.getUUID(),category,text,List.of("Ручная запись администратора."),sender.getName(),"Admin");chronicles.repository().save();messages.send(sender,"entry-added",Map.of("town",town.getName(),"id",entry.shortId()));}
    private void remove(CommandSender sender,String[]args){if(args.length<2){sender.sendMessage("/townychronicles remove <ID>");return;}ChronicleEntry entry=chronicles.repository().find(args[1]);if(entry==null||!chronicles.repository().remove(entry.id())){messages.send(sender,"entry-not-found",Map.of("id",args[1]));return;}chronicles.repository().save();messages.send(sender,"entry-removed",Map.of("id",entry.shortId()));}
    private void war(CommandSender sender,String[]args){if(args.length<4){sender.sendMessage("/townychronicles war <город> <start|end> <противник>");return;}Town town=towny.town(args[1]);if(town==null){messages.send(sender,"town-not-found",Map.of("town",args[1]));return;}boolean start=args[2].equalsIgnoreCase("start");String opponent=String.join(" ",Arrays.copyOfRange(args,3,args.length));ChronicleEntry entry=chronicles.record(town.getUUID(),ChronicleCategory.WAR,start?"Началась война с "+opponent:"Завершилась война с "+opponent,List.of("Записано администратором."),sender.getName(),"Admin");chronicles.repository().save();messages.send(sender,"entry-added",Map.of("town",town.getName(),"id",entry.shortId()));}
    private void help(CommandSender sender){sender.sendMessage("/townychronicles <add|remove|war|rescan|reload>");}
    @Override public List<String>onTabComplete(CommandSender sender,Command command,String alias,String[]args){if(args.length==1)return filter(List.of("add","remove","war","rescan","reload"),args[0]);if(args.length==2&&(args[0].equalsIgnoreCase("add")||args[0].equalsIgnoreCase("war")))return filter(towny.towns().stream().map(Town::getName).toList(),args[1]);if(args.length==3&&args[0].equalsIgnoreCase("add"))return filter(Arrays.stream(ChronicleCategory.values()).map(v->v.name().toLowerCase(Locale.ROOT)).toList(),args[2]);if(args.length==3&&args[0].equalsIgnoreCase("war"))return filter(List.of("start","end"),args[2]);return List.of();}
    private List<String>filter(List<String>values,String input){String lower=input.toLowerCase(Locale.ROOT);return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(lower)).toList();}
}
