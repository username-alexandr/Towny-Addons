package ru.neverland.townychronicles.command;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.neverland.townychronicles.gui.ChronicleMenuManager;
import ru.neverland.townychronicles.integration.TownyHook;
import ru.neverland.townychronicles.model.ChronicleCategory;
import ru.neverland.townychronicles.service.ChronicleBookService;
import ru.neverland.townychronicles.service.MessageService;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChroniclesCommand implements CommandExecutor,TabCompleter {
    private final ru.neverland.townychronicles.service.ContributionsMenu contributions;private final TownyHook towny;private final ChronicleMenuManager menus;private final ChronicleBookService books;private final MessageService messages;
    public ChroniclesCommand(TownyHook towny,ChronicleMenuManager menus,ChronicleBookService books,MessageService messages,ru.neverland.townychronicles.service.ContributionsMenu contributions){this.contributions=contributions;this.towny=towny;this.menus=menus;this.books=books;this.messages=messages;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[]args){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyChronicles")){return true;}if(!(sender instanceof Player player)){messages.send(sender,"players-only");return true;}if(!player.hasPermission("townychronicles.use")){messages.send(player,"no-permission");return true;}Town town=towny.town(player);if(town==null){messages.send(player,"no-town");return true;}if(args.length==0){menus.open(player,null,0);return true;}switch(args[0].toLowerCase(Locale.ROOT)){case "contributions","вклад"->{try{contributions.open(player,0);}catch(Exception ex){player.sendMessage("§e"+ex.getMessage());}}case "thank","благодарность"->{try{if(args.length<3)throw new IllegalArgumentException("/t chronicles thank <житель> <текст>");contributions.thank(player,args[1],String.join(" ",Arrays.copyOfRange(args,2,args.length)));}catch(Exception ex){player.sendMessage("§e"+ex.getMessage());}}case "help","помощь"->messages.help(player);case "book","книга"->{if(!player.hasPermission("townychronicles.book")){messages.send(player,"no-permission");return true;}if(books.give(player,town))messages.send(player,"book-given",Map.of("town",town.getName()));else messages.send(player,"book-no-space");}case "stats","статистика"->menus.sendStats(player,town);default->{ChronicleCategory category=ChronicleCategory.parse(args[0]);if(category==null)messages.send(player,"invalid-category",Map.of("category",args[0]));else menus.open(player,category,0);}}return true;} }
    @Override public List<String>onTabComplete(CommandSender sender,Command command,String alias,String[]args){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {if(args.length!=1)return List.of();List<String>values=new java.util.ArrayList<>(List.of("book","stats","contributions","thank","help"));Arrays.stream(ChronicleCategory.values()).forEach(v->values.add(v.name().toLowerCase(Locale.ROOT)));String input=args[0].toLowerCase(Locale.ROOT);return values.stream().filter(v->v.startsWith(input)).toList();} }
}
