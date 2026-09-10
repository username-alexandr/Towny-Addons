package ru.neverland.townypower.command;
import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.neverland.townypower.NeverLandTownyPower;
import ru.neverland.townypower.gui.PowerMenu;
import ru.neverland.townypower.service.*;
import java.util.*;
public final class PowerCommand implements CommandExecutor,TabCompleter {
    private final NeverLandTownyPower plugin;private final PowerService service;private final PowerMenu menu;
    public PowerCommand(NeverLandTownyPower plugin,PowerService service,PowerMenu menu){this.plugin=plugin;this.service=service;this.menu=menu;}
    private void count(String[] a,int n,String usage){if(a.length!=n)throw new IllegalArgumentException(usage);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){try{execute(sender,args);}catch(Exception ex){Ui.tell(sender,"&c"+(ex.getMessage()==null?"Операция не завершена; проверьте консоль":ex.getMessage()));if(!(ex instanceof IllegalArgumentException||ex instanceof IllegalStateException))plugin.getLogger().log(java.util.logging.Level.WARNING,"Ошибка команды энергетики",ex);}return true;}
    private void execute(CommandSender sender,String[] args)throws Exception{
        String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
        if(action.equals("reload")||action.equals("town")){
            if(!sender.hasPermission("neverlandtownypower.admin"))throw new IllegalArgumentException("Недостаточно прав");
            if(action.equals("reload")){count(args,1,"/townypower reload");Ui.tell(sender,plugin.reloadPower()?"Настройки энергетики обновлены.":"Настройки не применены; проверьте консоль.");}
            else{count(args,2,"/townypower town <город>");if(!(sender instanceof Player p))throw new IllegalArgumentException("Меню доступно в игре");var town=TownyAPI.getInstance().getTown(args[1]);if(town==null)throw new IllegalArgumentException("Город не найден");menu.open(p,town.getUUID(),0,"all",null);}return;
        }
        if(!(sender instanceof Player p))throw new IllegalArgumentException("Консоль: /townypower reload");if(!p.hasPermission("neverlandtownypower.use"))throw new IllegalArgumentException("Недостаточно прав");var town=service.town(p);if(town==null)throw new IllegalArgumentException("Вы не состоите в городе");
        switch(action){
            case "menu","generators","consumers"->{if(args.length>2)throw new IllegalArgumentException("/t power [generators|consumers] [страница]");int page=args.length==2?Integer.parseInt(args[1])-1:0;menu.open(p,town.getUUID(),page,action.equals("menu")?"all":action,null);}
            case "info"->{count(args,2,"/t power info <здание>");menu.open(p,town.getUUID(),0,"all",args[1]);}
            case "pause","resume","priority"->{count(args,action.equals("priority")?3:2,"/t power "+action+" <здание>"+(action.equals("priority")?" <0..100>":""));if(!service.manager(p,town))throw new IllegalArgumentException("Нужны права мэра, помощника или управляющего");service.change(town.getUUID(),args[1],action.equals("priority")?null:action.equals("pause"),action.equals("priority")?Integer.valueOf(args[2]):null);Ui.tell(p,"Настройки сохранены, питание перераспределено.");}
            case "help"->Ui.tell(p,"/t power — сеть города; generators / consumers — списки; info <здание> — мощность; pause / resume <здание> — остановка и запуск; priority <здание> <0..100> — очерёдность (0 раньше 100).");
            default->throw new IllegalArgumentException("Справка: /t power help");
        }
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){List<String> result=new ArrayList<>();
        if(args.length==1){if(sender.hasPermission("neverlandtownypower.use"))result.addAll(List.of("menu","generators","consumers","info","pause","resume","priority","help"));if(sender.hasPermission("neverlandtownypower.admin"))result.addAll(List.of("reload","town"));}
        else if(args.length==2&&Set.of("info","pause","resume","priority").contains(args[0])&&sender.hasPermission("neverlandtownypower.use"))service.settings().profiles().values().stream().filter(p->p.relevant()).forEach(p->result.add(p.id()));
        else if(args.length==2&&args[0].equals("town")&&sender.hasPermission("neverlandtownypower.admin"))TownyAPI.getInstance().getTowns().forEach(t->result.add(t.getName()));
        else if(args.length==3&&args[0].equals("priority")&&sender.hasPermission("neverlandtownypower.use"))result.addAll(List.of("0","10","20","50","100"));
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return result.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
