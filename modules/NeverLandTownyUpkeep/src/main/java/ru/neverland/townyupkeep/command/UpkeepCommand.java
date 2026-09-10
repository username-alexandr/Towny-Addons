package ru.neverland.townyupkeep.command;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.neverland.townyupkeep.NeverLandTownyUpkeep;
import ru.neverland.townyupkeep.gui.UpkeepMenu;
import ru.neverland.townyupkeep.service.*;
import java.util.*;
public final class UpkeepCommand implements CommandExecutor,TabCompleter {
    private final NeverLandTownyUpkeep plugin;private final UpkeepService service;private final UpkeepMenu menu;
    public UpkeepCommand(NeverLandTownyUpkeep plugin,UpkeepService service,UpkeepMenu menu){this.plugin=plugin;this.service=service;this.menu=menu;}
    private void count(String[] a,int n,String usage){if(a.length!=n)throw new IllegalArgumentException(usage);}
    private UUID townId(String name){try{return UUID.fromString(name);}catch(IllegalArgumentException ex){Town t=TownyAPI.getInstance().getTown(name);if(t==null)throw new IllegalArgumentException("Город не найден");return t.getUUID();}}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        try{execute(sender,args);}catch(Exception ex){Ui.tell(sender,"&c"+(ex.getMessage()==null?"Операция не завершена; проверьте консоль":ex.getMessage()));if(!(ex instanceof IllegalArgumentException||ex instanceof IllegalStateException))plugin.getLogger().log(java.util.logging.Level.WARNING,"Ошибка команды обслуживания",ex);}return true;
    }
    private void execute(CommandSender sender,String[] args)throws Exception{
        String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
        if(Set.of("reload","pending","resolve","town").contains(action)){
            if(!sender.hasPermission("neverlandtownyupkeep.admin"))throw new IllegalArgumentException("Недостаточно прав");
            switch(action){
                case "reload"->{count(args,1,"/townyupkeep reload");Ui.tell(sender,plugin.reloadUpkeep()?"Настройки обслуживания обновлены.":"Настройки не применены; проверьте консоль.");}
                case "pending"->{count(args,1,"/townyupkeep pending");var rows=service.pending();Ui.tell(sender,"Незавершённых счетов: "+rows.size());for(var e:rows)Ui.tell(sender,e.getKey().town()+" "+e.getKey().project()+" • "+e.getValue().invoice().id()+" • "+e.getValue().invoice().phase());}
                case "town"->{count(args,2,"/townyupkeep town <город или UUID>");if(!(sender instanceof Player p))throw new IllegalArgumentException("Меню доступно в игре");menu.open(p,townId(args[1]),0,null);}
                case "resolve"->{count(args,5,"/townyupkeep resolve <город или UUID> <здание> <UUID счёта> <paid|unpaid>");if(!Set.of("paid","unpaid").contains(args[4]))throw new IllegalArgumentException("Укажите paid или unpaid после проверки журнала Towny");UUID town=townId(args[1]),invoice=UUID.fromString(args[3]);
                    // Audit the reconciliation intent before changing a potentially ambiguous external transaction.
                    plugin.getLogger().warning("Сверка Upkeep: "+sender.getName()+" town="+town+" project="+args[2]+" invoice="+invoice+" result="+args[4]);service.resolve(town,args[2],invoice,args[4].equals("paid"));Ui.tell(sender,"Результат сверки сохранён.");}
            }return;
        }
        if(!(sender instanceof Player p))throw new IllegalArgumentException("/townyupkeep reload | pending | resolve");if(!p.hasPermission("neverlandtownyupkeep.use"))throw new IllegalArgumentException("Недостаточно прав");var town=service.town(p);if(town==null)throw new IllegalArgumentException("Вы не состоите в городе");
        switch(action){
            case "menu"->{if(args.length>2)throw new IllegalArgumentException("/t upkeep [menu <страница>]");menu.open(p,town.getUUID(),args.length==2?Integer.parseInt(args[1]):0,null);}
            case "info"->{count(args,2,"/t upkeep info <здание>");menu.open(p,town.getUUID(),0,args[1]);}
            case "pay"->{count(args,2,"/t upkeep pay <здание>");if(!service.manager(p,town))throw new IllegalArgumentException("Нужны права мэра, помощника или управляющего");service.retry(town.getUUID(),args[1]);Ui.tell(p,"Здание поставлено в очередь оплаты.");}
            case "help"->Ui.tell(p,"/t upkeep — обслуживание; info <здание> — расходы; pay <здание> — повтор оплаты. При нехватке средств здание сохраняется и временно не работает.");
            default->throw new IllegalArgumentException("Справка: /t upkeep help");
        }
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){List<String> values=new ArrayList<>();boolean admin=sender.hasPermission("neverlandtownyupkeep.admin");
        if(args.length==1){if(sender.hasPermission("neverlandtownyupkeep.use"))values.addAll(List.of("menu","info","pay","help"));if(admin)values.addAll(List.of("reload","pending","resolve","town"));}
        else if(args.length==2&&Set.of("info","pay").contains(args[0])&&sender instanceof Player p){var town=service.town(p);if(town!=null)for(var b:service.buildings(town.getUUID()))values.add(b.key().project());}
        else if(args.length==2&&admin&&Set.of("resolve","town").contains(args[0]))for(var t:TownyAPI.getInstance().getTowns())values.add(t.getName());
        else if(args.length==5&&admin&&args[0].equals("resolve"))values.addAll(List.of("paid","unpaid"));
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return values.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();}
}
