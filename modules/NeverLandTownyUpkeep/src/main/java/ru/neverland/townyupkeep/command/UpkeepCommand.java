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
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyUpkeep")){return true;}
        try{execute(sender,args);}catch(Exception ex){Ui.tell(sender,"&c"+(ex.getMessage()==null?"Операция не завершена; проверьте консоль":ex.getMessage()));if(!(ex instanceof IllegalArgumentException||ex instanceof IllegalStateException))plugin.getLogger().log(java.util.logging.Level.WARNING,"Ошибка команды обслуживания",ex);}return true;
    } }
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
            case "pause"->{count(args,3,"/t upkeep pause <здание> confirm — остановить производство и бонусы ради экономии");if(!args[2].equalsIgnoreCase("confirm"))throw new IllegalArgumentException("Подтвердите остановку словом confirm");if(!service.manager(p,town))throw new IllegalArgumentException("Нужны полномочия управления содержанием");service.suspend(town.getUUID(),args[1],true,p.getUniqueId());Ui.tell(p,"Здание остановлено. Новые счета не создаются, производство и бонусы отключены. Оплаченный срок не продлевается.");menu.open(p,town.getUUID(),0,args[1]);}
            case "resume"->{count(args,2,"/t upkeep resume <здание>");if(!service.manager(p,town))throw new IllegalArgumentException("Нужны полномочия управления содержанием");service.suspend(town.getUUID(),args[1],false,p.getUniqueId());Ui.tell(p,"Остановка снята. Если оплаченный срок истёк, здание начнёт работать после обычной оплаты содержания.");menu.open(p,town.getUUID(),0,args[1]);}
            case "savings"->{count(args,2,"/t upkeep savings <здание,здание> — проверить экономию за сутки без остановки");var summary=service.savings(town.getUUID(),Set.of(args[1].split(",",-1)),86400);Ui.tell(p,"За сутки: сейчас "+ru.neverland.townyupkeep.model.Cost.format(((Number)summary.get("beforeCents")).longValue(),2)+", после остановки "+ru.neverland.townyupkeep.model.Cost.format(((Number)summary.get("afterCents")).longValue(),2)+" монет.");Ui.tell(p,"Экономия: "+ru.neverland.townyupkeep.model.Cost.format(((Number)summary.get("savingCents")).longValue(),2)+". Производство, рабочие места и бонусы выбранных зданий отключатся; запасы и сами здания сохранятся.");if(!Boolean.TRUE.equals(summary.get("known")))Ui.tell(p,"Есть незавершённые счета; итог неполный. Сначала дождитесь оплаты или сверки.");}
            case "help"->Ui.tell(p,"/t upkeep — обслуживание; info <здание> — расходы; pay <здание> — повтор оплаты; savings <здание,здание> — план экономии; pause <здание> confirm / resume <здание> — остановка и запуск. Здания и запасы сохраняются.");
            default->throw new IllegalArgumentException("Справка: /t upkeep help");
        }
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {List<String> values=new ArrayList<>();boolean admin=sender.hasPermission("neverlandtownyupkeep.admin");
        if(args.length==1){if(sender.hasPermission("neverlandtownyupkeep.use"))values.addAll(List.of("menu","info","pay","help","savings","pause","resume"));if(admin)values.addAll(List.of("reload","pending","resolve","town"));}
        else if(args.length==2&&Set.of("info","pay","pause","resume","savings").contains(args[0])&&sender instanceof Player p){var town=service.town(p);if(town!=null)for(var b:service.buildings(town.getUUID()))values.add(b.key().project());}
        else if(args.length==3&&args[0].equals("pause"))values.add("confirm");
        else if(args.length==2&&admin&&Set.of("resolve","town").contains(args[0]))for(var t:TownyAPI.getInstance().getTowns())values.add(t.getName());
        else if(args.length==5&&admin&&args[0].equals("resolve"))values.addAll(List.of("paid","unpaid"));
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return values.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();} }
}
