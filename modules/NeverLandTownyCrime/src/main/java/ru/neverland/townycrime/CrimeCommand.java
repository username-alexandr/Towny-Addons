package ru.neverland.townycrime;
import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.MenuStyle;
public final class CrimeCommand implements CommandExecutor,TabCompleter {
    private final NeverLandTownyCrime plugin;private final CrimeMenu menu;
    public CrimeCommand(NeverLandTownyCrime p,CrimeMenu m){plugin=p;menu=m;}
    @Override public boolean onCommand(CommandSender s,Command c,String label,String[] args){try{
        String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
        if(Set.of("reload","town").contains(action)){
            if(!s.hasPermission("neverlandtownycrime.admin"))throw new IllegalArgumentException("Недостаточно прав");
            if(action.equals("reload")){if(args.length!=1)throw new IllegalArgumentException("/townycrime reload");plugin.reloadCrime();s.sendMessage(MenuStyle.decode("&aНастройки преступности обновлены."));return true;}
            if(args.length!=2||!(s instanceof Player p))throw new IllegalArgumentException("В игре: /townycrime town <город>");var town=TownyAPI.getInstance().getTown(args[1]);if(town==null)throw new IllegalArgumentException("Город не найден");menu.open(p,town.getUUID(),MenuStyle.previousMenu(p));return true;
        }
        if(!s.hasPermission("neverlandtownycrime.use"))throw new IllegalArgumentException("Недостаточно прав");
        if(action.equals("help")){s.sendMessage(MenuStyle.decode("&b/t crime &f— преступность, довольство и работа стражи"));s.sendMessage(MenuStyle.decode("&fВысокая преступность снижает выручку лавки и вызывает происшествия."));s.sendMessage(MenuStyle.decode("&fУлучшайте довольство населения, стройте стражу и назначайте работников."));return true;}
        if(!action.equals("menu")||args.length>1)throw new IllegalArgumentException("Справка: /t crime help");if(!(s instanceof Player p))throw new IllegalArgumentException("Меню доступно в игре");var resident=TownyAPI.getInstance().getResident(p.getUniqueId());if(resident==null||!resident.hasTown())throw new IllegalArgumentException("Вы не состоите в городе");menu.open(p,resident.getTownOrNull().getUUID(),MenuStyle.previousMenu(p));
    }catch(Exception ex){s.sendMessage(MenuStyle.decode("&c"+(ex instanceof IllegalArgumentException?ex.getMessage():"Операция не завершена; проверьте консоль")));if(!(ex instanceof IllegalArgumentException))plugin.getLogger().log(java.util.logging.Level.WARNING,"Команда преступности",ex);}return true;}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String alias,String[] args){var choices=new ArrayList<String>();if(args.length==1){if(s.hasPermission("neverlandtownycrime.use"))choices.addAll(List.of("menu","help"));if(s.hasPermission("neverlandtownycrime.admin"))choices.addAll(List.of("town","reload"));}else if(args.length==2&&args[0].equals("town")&&s.hasPermission("neverlandtownycrime.admin"))TownyAPI.getInstance().getTowns().forEach(t->choices.add(t.getName()));String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return choices.stream().filter(x->x.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();}
}
