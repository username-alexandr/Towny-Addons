package ru.neverland.townyresources.command;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.neverland.townyresources.NeverLandTownyResources;
import ru.neverland.townyresources.gui.ResourcesMenu;
import ru.neverland.townyresources.model.*;
import ru.neverland.townyresources.service.*;
import java.util.*;
import java.io.IOException;
public final class ResourcesCommand implements CommandExecutor,TabCompleter {
    private final NeverLandTownyResources plugin;private final ResourcesService service;private final ResourcesMenu menu;
    public ResourcesCommand(NeverLandTownyResources plugin,ResourcesService service,ResourcesMenu menu){this.plugin=plugin;this.service=service;this.menu=menu;}
    private static void count(String[] args,int count,String usage){if(args.length!=count)throw new IllegalArgumentException(usage);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        try{execute(sender,args);}catch(IOException ex){plugin.getLogger().warning("Операция ресурсов не сохранена: "+ex.getMessage());Ui.tell(sender,"&cОшибка записи данных; проверьте консоль.");}
        catch(NumberFormatException ex){Ui.tell(sender,"&cОжидается целое число.");}
        catch(IllegalArgumentException|IllegalStateException|ArithmeticException ex){Ui.tell(sender,"&c"+(ex.getMessage()==null?"Проверьте аргументы команды":ex.getMessage()));}return true;
    }
    private Town findTown(String text){Town town;try{town=TownyAPI.getInstance().getTown(UUID.fromString(text));}catch(IllegalArgumentException ex){town=TownyAPI.getInstance().getTown(text);}if(town==null)throw new IllegalArgumentException("Город не найден");return town;}
    private void execute(CommandSender sender,String[] args)throws IOException{
        String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
        if(Set.of("reload","set","add","take","town").contains(action)){
            if(!sender.hasPermission("neverlandtownyresources.admin"))throw new IllegalArgumentException("Недостаточно прав");
            if(action.equals("reload")){count(args,1,"/townyresources reload");Ui.tell(sender,plugin.reloadResources()?"Настройки ресурсов обновлены.":"Настройки не применены; проверьте консоль.");return;}
            if(action.equals("town")){count(args,2,"/townyresources town <город>");if(!(sender instanceof Player p))throw new IllegalArgumentException("Просмотр меню доступен в игре");menu.open(p,findTown(args[1]).getUUID(),"menu",null,0);return;}
            count(args,4,"/townyresources "+action+" <город или UUID> <ресурс> <количество>");Town town=findTown(args[1]);Resource resource=Resource.parse(args[2]);long amount=Amounts.parse(args[3]);
            service.adjust(town.getUUID(),resource,amount,action);plugin.getLogger().info("Ресурсы: "+sender.getName()+" "+action+" "+town.getUUID()+" "+resource.id()+" "+Amounts.decimal(amount));Ui.tell(sender,"&aЗапас города обновлён.");return;
        }
        if(!(sender instanceof Player player))throw new IllegalArgumentException("Эта команда выполняется в игре");
        if(!player.hasPermission("neverlandtownyresources.use"))throw new IllegalArgumentException("Недостаточно прав");Town town=service.town(player);if(town==null)throw new IllegalArgumentException("Вы не состоите в городе");UUID id=town.getUUID();
        if(action.equals("menu")||action.equals("buildings")){menu.open(player,id,action,null,args.length>1?Integer.parseInt(args[1]):0);return;}
        if(action.equals("info")){count(args,2,"/t resources info <здание>");service.profile(args[1]);menu.open(player,id,"info",args[1],0);return;}
        if(action.equals("help")){Ui.tell(player,"&e/t resources &7— запасы и баланс; buildings — вклад зданий.");Ui.tell(player,"&einfo <здание>; pause/resume <здание>; priority <здание> <0..100>.");Ui.tell(player,"&ekeep <ресурс> <количество> &7— резерв для расходов зданий.");return;}
        if(!service.manager(player,town))throw new IllegalArgumentException("Нужны права мэра, помощника или управляющего ресурсами");
        switch(action){
            case "pause","resume"->{count(args,2,"/t resources "+action+" <здание>");service.pause(id,args[1],action.equals("pause"));}
            case "priority"->{count(args,3,"/t resources priority <здание> <0..100>");service.priority(id,args[1],Integer.parseInt(args[2]));}
            case "keep"->{count(args,3,"/t resources keep <ресурс> <количество>");service.reserve(id,Resource.parse(args[1]),Amounts.parse(args[2]));}
            default->throw new IllegalArgumentException("Справка: /t resources help");
        }
        Ui.tell(player,"&aНастройки города сохранены.");menu.open(player,id,"menu",null,0);
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        List<String> options=new ArrayList<>();boolean admin=sender.hasPermission("neverlandtownyresources.admin");
        if(args.length==1){if(admin)options.addAll(List.of("reload","set","add","take","town"));if(sender.hasPermission("neverlandtownyresources.use"))options.addAll(List.of("menu","buildings","info","help","pause","resume","priority","keep"));}
        else if(args.length==2&&Set.of("info","pause","resume","priority").contains(args[0]))options.addAll(service.settings().buildings().keySet());
        else if((args.length==2&&args[0].equals("keep"))||(args.length==3&&admin&&Set.of("set","add","take").contains(args[0])))for(var r:Resource.values())options.add(r.id());
        else if(args.length==2&&admin&&Set.of("set","add","take","town").contains(args[0]))for(var town:TownyAPI.getInstance().getTowns())options.add(town.getName());
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return options.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
