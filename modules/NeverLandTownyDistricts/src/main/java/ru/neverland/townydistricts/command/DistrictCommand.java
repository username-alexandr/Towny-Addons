package ru.neverland.townydistricts.command;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import ru.neverland.townydistricts.NeverLandTownyDistricts;
import ru.neverland.townydistricts.gui.DistrictMenu;
import ru.neverland.townydistricts.model.*;
import ru.neverland.townydistricts.service.*;
import java.util.*;
import java.io.IOException;

public final class DistrictCommand implements CommandExecutor,TabCompleter,org.bukkit.event.Listener {
    private record Selection(UUID town,Cell first,Cell second){}
    private final NeverLandTownyDistricts plugin;private final DistrictService service;private final BorderPreview borders;
    private final Map<UUID,Selection> selections=new HashMap<>();private DistrictMenu menu;
    public DistrictCommand(NeverLandTownyDistricts plugin,DistrictService service,BorderPreview borders){this.plugin=plugin;this.service=service;this.borders=borders;}
    @org.bukkit.event.EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent event){selections.remove(event.getPlayer().getUniqueId());}
    public void menu(DistrictMenu menu){this.menu=menu;}
    public String selection(Player player){var s=selections.get(player.getUniqueId());return s==null?"Текущий участок":s.second()==null?"Первая точка задана; укажите pos2":"Прямоугольник между pos1 и pos2";}
    private Set<Cell> selected(Player player,UUID town){var s=selections.get(player.getUniqueId());
        if(s==null)return Set.of(service.cell(player));
        if(!s.town().equals(town))throw new IllegalArgumentException("Город изменился: сбросьте выделение /t district clear");
        if(s.second()==null)throw new IllegalArgumentException("Задайте вторую точку /t district pos2 или сбросьте выделение");
        return Cell.rectangle(s.first(),s.second(),service.settings().maxSelection());
    }
    public static void tell(CommandSender sender,String text){sender.sendMessage(ChatColor.translateAlternateColorCodes('&',"&8[&aNeverLand &8• &fРайоны&8] &r"+text));}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        try{execute(sender,args);}catch(IllegalArgumentException|IllegalStateException ex){tell(sender,"&c"+ex.getMessage());}
        catch(IOException ex){plugin.getLogger().warning("Ошибка сохранения районов: "+ex.getMessage());tell(sender,"&cНе удалось сохранить изменение. Проверьте консоль.");}
        return true;
    }
    private void execute(CommandSender sender,String[] args)throws IOException{
        String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
        if(action.equals("reload")){
            if(!sender.hasPermission("neverlandtownydistricts.admin"))throw new IllegalArgumentException("Недостаточно прав");
            tell(sender,plugin.reloadDistricts()?"&aНастройки районов обновлены.":"&cОшибка настроек; прежние значения сохранены.");return;
        }
        if(!(sender instanceof Player player))throw new IllegalArgumentException("Эта команда выполняется в игре");
        if(!player.hasPermission("neverlandtownydistricts.use"))throw new IllegalArgumentException("Недостаточно прав");
        var town=service.town(player);if(town==null)throw new IllegalArgumentException("Вы не состоите в городе");
        service.refresh();UUID townId=town.getUUID();
        if(action.equals("menu")||action.equals("list")){menu.open(player,townId,null,args.length>1?Integer.parseInt(args[1]):0);return;}
        if(action.equals("info")){if(args.length<2){menu.open(player,townId,null,0);return;}service.require(townId,args[1]);menu.open(player,townId,args[1],args.length>2?Integer.parseInt(args[2]):0);return;}
        if(action.equals("map")){borders.show(player);tell(player,"Границы районов показаны на 30 секунд в радиусе 64 блоков.");return;}
        if(action.equals("help")){tell(player,"/t district create <id> <тип>; claim <id>; unclaim <id>; rename <id> <имя>; delete <id>.");tell(player,"/t district pos1 и pos2 — выделить прямоугольник; clear — сброс; map — границы.");return;}
        if(!service.manager(player,town))throw new IllegalArgumentException("Районами управляет мэр, помощник или уполномоченный житель");
        if(action.equals("clear")){selections.remove(player.getUniqueId());tell(player,"Выделение сброшено; выбран текущий участок.");return;}
        if(action.equals("pos1")||action.equals("pos2")){
            Cell cell=service.cell(player);if(!service.owned(town).contains(cell))throw new IllegalArgumentException("Точка должна находиться на территории вашего города");
            if(action.equals("pos1"))selections.put(player.getUniqueId(),new Selection(townId,cell,null));
            else{var first=selections.get(player.getUniqueId());if(first==null||!first.town().equals(townId))throw new IllegalArgumentException("Сначала задайте pos1");
                Cell.rectangle(first.first(),cell,service.settings().maxSelection());selections.put(player.getUniqueId(),new Selection(townId,first.first(),cell));}
            tell(player,"Точка сохранена: участок "+cell.x()+", "+cell.z()+".");return;
        }
        if(action.equals("create")){
            if(args.length!=3)throw new IllegalArgumentException("/t district create <id> <тип>");
            var type=DistrictType.parse(args[2]);District d=new District(townId,args[1].toLowerCase(Locale.ROOT),type.title,type,selected(player,townId));
            service.put(d,true);selections.remove(player.getUniqueId());tell(player,"&aСоздан район «"+d.name()+"»: "+d.id()+".");menu.open(player,townId,d.id(),0);return;
        }
        if(args.length<2)throw new IllegalArgumentException("Команды районов: /t district help");
        var old=service.require(townId,args[1]);
        switch(action){
            case "claim"->{Set<Cell> cells=new HashSet<>(old.cells());cells.addAll(selected(player,townId));service.put(old.withCells(cells),false);selections.remove(player.getUniqueId());}
            case "unclaim"->{Set<Cell> cells=new HashSet<>(old.cells());if(!cells.remove(service.cell(player)))throw new IllegalArgumentException("Текущий участок не принадлежит этому району");
                if(cells.isEmpty())throw new IllegalArgumentException("Для удаления последнего участка используйте delete");service.put(old.withCells(cells),false);}
            case "delete"->{service.delete(townId,old.id());tell(player,"Район удалён. Земля остаётся у города.");menu.open(player,townId,null,0);return;}
            case "rename"->{if(args.length<3)throw new IllegalArgumentException("/t district rename <id> <название>");service.put(new District(townId,old.id(),String.join(" ",Arrays.copyOfRange(args,2,args.length)),old.type(),old.cells()),false);}
            default->throw new IllegalArgumentException("Неизвестная команда. /t district help");
        }
        tell(player,"&aРайон обновлён.");menu.open(player,townId,old.id(),0);
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(!(sender instanceof Player player)||!player.hasPermission("neverlandtownydistricts.use"))return List.of();
        var town=service.town(player);if(town==null)return List.of();List<String> options=new ArrayList<>();
        if(args.length==1){options.addAll(List.of("list","info","map","help"));if(service.manager(player,town))options.addAll(List.of("create","claim","unclaim","delete","rename","pos1","pos2","clear"));if(player.hasPermission("neverlandtownydistricts.admin"))options.add("reload");}
        else if(args.length==2&&Set.of("info","claim","unclaim","delete","rename").contains(args[0]))service.list(town.getUUID()).forEach(d->options.add(d.id()));
        else if(args.length==3&&args[0].equals("create"))for(var type:DistrictType.values())options.add(type.id());
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return options.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
