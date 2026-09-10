package ru.neverland.townytreasury.command;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.TownyAPI;
import java.util.*;
import ru.neverland.townytreasury.NeverLandTownyTreasuryPlus;
import ru.neverland.townytreasury.service.*;
import ru.neverland.townytreasury.gui.TreasuryMenu;
import ru.neverland.townytreasury.model.Week;
import static ru.neverland.townytreasury.service.Ui.tell;
public final class TreasuryCommand implements TabExecutor {
    private final NeverLandTownyTreasuryPlus plugin;private final TreasuryService service;private final TreasuryMenu menu;
    public TreasuryCommand(NeverLandTownyTreasuryPlus plugin,TreasuryService service,TreasuryMenu menu){this.plugin=plugin;this.service=service;this.menu=menu;}
    private String[] clean(String[] args){return args.length>0&&args[0].equalsIgnoreCase("treasury")?Arrays.copyOfRange(args,1,args.length):args;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] raw){String[] args=clean(raw);try{
        String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);if(action.equals("reload")){if(!sender.hasPermission("neverlandtownytreasury.admin"))throw new IllegalArgumentException("Нет прав администратора");tell(sender,plugin.reloadTreasury()?"Настройки казны применены.":"Настройки не применены; проверьте журнал.");return true;}
        if(!(sender instanceof Player player))throw new IllegalArgumentException("Откройте команду в игре");if(!player.hasPermission("neverlandtownytreasury.use")&&!player.hasPermission("neverlandtownytreasury.admin"))throw new IllegalArgumentException("Нет доступа к казне");var town=service.town(player);
        if(action.equals("town")){if(!player.hasPermission("neverlandtownytreasury.admin")||args.length!=2)throw new IllegalArgumentException("town <город> — просмотр администратора");town=TownyAPI.getInstance().getTown(args[1]);if(town==null)throw new IllegalArgumentException("Город не найден");menu.open(player,town.getUUID(),null);return true;}
        if(action.equals("resolve")){if(!player.hasPermission("neverlandtownytreasury.admin")||args.length!=4||!Set.of("paid","unpaid").contains(args[3]))throw new IllegalArgumentException("После сверки журнала: resolve <город> <ID счёта> paid|unpaid");var target=TownyAPI.getInstance().getTown(args[1]);if(target==null)throw new IllegalArgumentException("Город не найден");service.resolve(player,target.getUUID(),UUID.fromString(args[2]),args[3].equals("paid"));tell(player,"Результат сверки сохранён.");return true;}
        if(town==null)throw new IllegalArgumentException("Сначала вступите в город");UUID id=town.getUUID();
        switch(action){case "menu"->menu.open(player,id,null);case "enable","disable","shares","transfer"->menu.prepare(player,id,action,List.of(Arrays.copyOfRange(args,1,args.length)));case "confirm"->{service.confirm(player);tell(player,"Бюджет обновлён.");menu.open(player,id,null);}case "history","reports"->menu.open(player,id,action);case "report"->{String week=args.length>1?args[1]:Week.previous(System.currentTimeMillis());menu.open(player,id,"report/"+week);}case "export"->{if(!service.manager(player,town)&&!player.hasPermission("neverlandtownytreasury.admin"))throw new IllegalArgumentException("Экспорт доступен управляющему бюджетом");String week=args.length>1?args[1]:Week.previous(System.currentTimeMillis());var file=ReportExporter.write(plugin.getDataFolder().toPath().resolve("reports"),id,town.getName(),week,service.sync(town));tell(player,"Отчёт сохранён: reports/"+id+"/"+file.getFileName());}default->tell(player,"/t treasury [enable|disable|transfer|shares|confirm|reports|report|export|history]");}
    }catch(Exception ex){tell(sender,"&c"+ex.getMessage());}return true;}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] raw){var args=clean(raw);List<String> options=args.length<=1?List.of("enable","disable","transfer","shares","confirm","reports","report","export","history"):args[0].equalsIgnoreCase("transfer")&&args.length<=3?List.of("construction","army","infrastructure","social","free"):List.of();String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return options.stream().filter(s->s.startsWith(prefix)).toList();}
}
