package ru.neverland.townyresearch.command;
import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.neverland.townyresearch.NeverLandTownyResearch;
import ru.neverland.townyresearch.gui.ResearchMenu;
import ru.neverland.townyresearch.service.*;
import java.util.*;
public final class ResearchCommand implements CommandExecutor,TabCompleter {
    private final NeverLandTownyResearch plugin;private final ResearchService service;private final ResearchMenu menu;
    public ResearchCommand(NeverLandTownyResearch plugin,ResearchService service,ResearchMenu menu){this.plugin=plugin;this.service=service;this.menu=menu;}
    private void count(String[] a,int n,String usage){if(a.length!=n)throw new IllegalArgumentException(usage);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){try{execute(sender,args);}catch(Exception ex){Ui.tell(sender,"&c"+(ex.getMessage()==null?"Операция не завершена; проверьте консоль":ex.getMessage()));if(!(ex instanceof IllegalArgumentException||ex instanceof IllegalStateException))plugin.getLogger().log(java.util.logging.Level.WARNING,"Ошибка команды исследований",ex);}return true;}
    private void execute(CommandSender sender,String[] args)throws Exception{String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
        if(action.equals("reload")||action.equals("town")){if(!sender.hasPermission("neverlandtownyresearch.admin"))throw new IllegalArgumentException("Недостаточно прав");if(action.equals("reload")){count(args,1,"/townyresearch reload");Ui.tell(sender,plugin.reloadResearch()?"Настройки исследований обновлены.":"Настройки не применены; проверьте консоль.");}else{count(args,2,"/townyresearch town <город>");if(!(sender instanceof Player player))throw new IllegalArgumentException("Меню доступно в игре");var town=TownyAPI.getInstance().getTown(args[1]);if(town==null)throw new IllegalArgumentException("Город не найден");menu.open(player,town.getUUID(),null);}return;}
        if(!(sender instanceof Player player))throw new IllegalArgumentException("Консоль: /townyresearch reload");if(!player.hasPermission("neverlandtownyresearch.use"))throw new IllegalArgumentException("Недостаточно прав");var town=service.town(player);if(town==null)throw new IllegalArgumentException("Вы не состоите в городе");
        switch(action){case "menu"->{count(args,args.length==0?0:1,"/t research");menu.open(player,town.getUUID(),null);}case "info"->{count(args,2,"/t research info <технология>");menu.open(player,town.getUUID(),args[1]);}
            case "start","cancel"->{count(args,action.equals("start")?2:1,"/t research "+action+(action.equals("start")?" <технология>":""));if(!service.manager(player,town))throw new IllegalArgumentException("Нужны права мэра, помощника или управляющего");if(action.equals("start")){service.begin(town.getUUID(),args[1]);Ui.tell(player,"Исследование добавлено. При нехватке свободных знаний оно будет ждать пополнения.");}else{service.cancel(town.getUUID());Ui.tell(player,"Отмена сохранена; зарезервированные знания возвращаются в город.");}menu.open(player,town.getUUID(),null);}
            case "help"->Ui.tell(player,"/t research — технологии; info <технология> — требования; start <технология> — поставить в изучение; cancel — отменить с возвратом знаний. Одновременно изучается одна технология.");default->throw new IllegalArgumentException("Справка: /t research help");}
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){var result=new ArrayList<String>();if(args.length==1){if(sender.hasPermission("neverlandtownyresearch.use"))result.addAll(List.of("menu","info","start","cancel","help"));if(sender.hasPermission("neverlandtownyresearch.admin"))result.addAll(List.of("reload","town"));}else if(args.length==2&&Set.of("start","info").contains(args[0])&&sender.hasPermission("neverlandtownyresearch.use"))result.addAll(service.settings().technologies().keySet());else if(args.length==2&&args[0].equals("town")&&sender.hasPermission("neverlandtownyresearch.admin"))TownyAPI.getInstance().getTowns().forEach(t->result.add(t.getName()));String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return result.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();}
}
