package ru.neverland.townylogistics.command;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.neverland.townylogistics.NeverLandTownyLogistics;
import ru.neverland.townylogistics.gui.LogisticsMenu;
import ru.neverland.townylogistics.integration.BuildsStorage;
import ru.neverland.townylogistics.model.*;
import ru.neverland.townylogistics.model.Network.*;
import ru.neverland.townylogistics.service.*;
import java.util.*;
import java.io.IOException;
public final class LogisticsCommand implements CommandExecutor,TabCompleter {
    private final NeverLandTownyLogistics plugin;private final LogisticsService service;private LogisticsMenu menu;
    public LogisticsCommand(NeverLandTownyLogistics plugin,LogisticsService service){this.plugin=plugin;this.service=service;}
    public void menu(LogisticsMenu menu){this.menu=menu;}
    public static void tell(CommandSender sender,String text){sender.sendMessage(ChatColor.translateAlternateColorCodes('&',"&8[&aNeverLand &8• &fЛогистика&8] &r"+text));}
    @Override public boolean onCommand(CommandSender sender,Command c,String label,String[] args){
        try{execute(sender,args);}catch(NumberFormatException ex){tell(sender,"&cОжидается целое число.");}catch(IllegalArgumentException|IllegalStateException ex){tell(sender,"&c"+(ex.getMessage()==null?"Проверьте аргументы команды":ex.getMessage()));}
        catch(IOException ex){plugin.getLogger().warning("Операция логистики не завершена: "+ex.getMessage());tell(sender,"&cОшибка доступа к данным. Груз остаётся в журнале; проверьте консоль.");}return true;
    }
    private void count(String[] args,int n,String usage){if(args.length!=n)throw new IllegalArgumentException(usage);}
    private void execute(CommandSender sender,String[] args)throws IOException{
        String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
        if(action.equals("reload")||action.equals("recover")){
            if(!sender.hasPermission("neverlandtownylogistics.admin"))throw new IllegalArgumentException("Недостаточно прав");
            if(action.equals("reload")){tell(sender,plugin.reloadLogistics()?"Настройки логистики обновлены.":"Настройки не применены; проверьте консоль.");return;}
            count(args,3,"/townylogistics recover <UUID города> <UUID груза>");UUID town=UUID.fromString(args[1]),id=UUID.fromString(args[2]);
            boolean done=service.recover(town,id);tell(sender,done?"Груз возвращён на склад отправителя.":"Возврат невозможен: склад открыт, заполнен или груз не найден.");
            if(done)plugin.getLogger().info("Административный возврат груза "+id+" города "+town+" пользователем "+sender.getName());return;
        }
        if(!(sender instanceof Player p))throw new IllegalArgumentException("Эта команда выполняется в игре");
        if(!p.hasPermission("neverlandtownylogistics.use"))throw new IllegalArgumentException("Недостаточно прав");
        var town=service.town(p);if(town==null)throw new IllegalArgumentException("Вы не состоите в городе");UUID id=town.getUUID();Network n=service.network(id);
        if(Set.of("menu","routes","nodes","couriers","cargo","depots").contains(action)){menu.open(p,action,null,args.length>1?Integer.parseInt(args[1]):0);return;}
        if(action.equals("info")){count(args,2,"/t logistics info <маршрут>");n.route(args[1]);menu.open(p,"info",args[1],0);return;}
        if(action.equals("storage")){count(args,2,"/t logistics storage <здание>");service.builds().open(p,args[1]);return;}
        if(action.equals("map")){showMap(p,n);tell(p,"Ближайшие узлы и связи показаны частицами.");return;}
        if(action.equals("help")){
            tell(p,"&e1. /t logistics node <id> <здание> &7— точка погрузки под вами.");
            tell(p,"&e2. /t logistics waypoint <id> road|rail|port &7— промежуточный узел.");
            tell(p,"&e3. /t logistics link <узел1> <узел2> &7— соединить соседние точки.");
            tell(p,"&e4. /t logistics route <id> <база> <отправитель> <получатель>");
            tell(p,"filter <id> hand|all|<материал>; limit <id> <количество>; keep <id> <остаток>; pause/resume <id>.");return;
        }
        if(!service.manager(p,town))throw new IllegalArgumentException("Логистикой управляет мэр, помощник или уполномоченный житель");
        if(action.equals("node")||action.equals("waypoint")){
            count(args,3,"/t logistics "+action+" <id> <здание или тип узла>");String nodeId=Network.id(args[1].toLowerCase(Locale.ROOT));
            if(n.nodes().containsKey(nodeId))throw new IllegalArgumentException("ID узла уже занят");if(n.nodes().size()>=service.settings().nodes())throw new IllegalArgumentException("Достигнут предел узлов");
            Kind kind=action.equals("node")?Kind.BUILDING:Kind.valueOf(args[2].toUpperCase(Locale.ROOT));if(action.equals("waypoint")&&kind==Kind.BUILDING)throw new IllegalArgumentException("Тип: road, rail или port");
            String building=action.equals("node")?args[2].toLowerCase(Locale.ROOT):"";
            if(!building.isBlank()&&n.nodes().values().stream().anyMatch(v->v.project().equals(building)))throw new IllegalArgumentException("У здания уже есть точка погрузки");
            var point=CourierNpcs.position(p.getLocation());var node=new Node(nodeId,building,kind,point);service.validateNode(id,node);
            if(kind==Kind.RAIL&&!Tag.RAILS.isTagged(p.getLocation().getBlock().getType())&&!Tag.RAILS.isTagged(p.getLocation().getBlock().getRelative(0,-1,0).getType()))throw new IllegalArgumentException("Железнодорожный узел задаётся на рельсах");
            var nodes=new HashMap<>(n.nodes());nodes.put(nodeId,node);service.change(new Network(id,nodes,n.links(),n.routes()),true);tell(p,"&aУзел создан: "+nodeId);return;
        }
        if(action.equals("link")||action.equals("unlink")){
            count(args,3,"/t logistics "+action+" <узел1> <узел2>");var a=n.node(args[1]);var z=n.node(args[2]);var link=new Link(a.id(),z.id());var links=new HashSet<>(n.links());
            if(action.equals("link")){service.validateLink(id,a,z);links.add(link);}else links.remove(link);service.change(new Network(id,n.nodes(),links,n.routes()),true);tell(p,"&aСвязи обновлены.");return;
        }
        if(action.equals("remove")){
            count(args,2,"/t logistics remove <узел>");n.node(args[1]);if(n.routes().values().stream().anyMatch(v->List.of(v.hub(),v.source(),v.target()).contains(args[1])))throw new IllegalArgumentException("Узел используется маршрутом");
            var nodes=new HashMap<>(n.nodes());nodes.remove(args[1]);var links=new HashSet<>(n.links());links.removeIf(l->l.a().equals(args[1])||l.b().equals(args[1]));service.change(new Network(id,nodes,links,n.routes()),true);tell(p,"Узел удалён.");return;
        }
        if(action.equals("route")){
            count(args,5,"/t logistics route <id> <база> <отправитель> <получатель>");String routeId=Network.id(args[1].toLowerCase(Locale.ROOT));if(n.routes().containsKey(routeId))throw new IllegalArgumentException("ID маршрута уже занят");
            if(n.routes().size()>=service.settings().routes())throw new IllegalArgumentException("Достигнут предел маршрутов");
            var route=new Route(routeId,args[2],args[3],args[4],"",64,0,false);service.job(n,route);var routes=new HashMap<>(n.routes());routes.put(routeId,route);service.change(new Network(id,n.nodes(),n.links(),routes),false);tell(p,"&aМаршрут создан: "+routeId+". Настройте фильтр и нажмите «Включить».");menu.open(p,"info",routeId,0);return;
        }
        if(args.length<2)throw new IllegalArgumentException("Справка: /t logistics help");var route=n.route(args[1]);var routes=new HashMap<>(n.routes());boolean topology=false;
        switch(action){
            case "pause","resume"->{count(args,2,"/t logistics "+action+" <маршрут>");if(action.equals("resume"))service.job(n,route);routes.put(route.id(),route.enabled(action.equals("resume")));}
            case "dismiss"->{count(args,2,"/t logistics dismiss <маршрут>");if(route.enabled())throw new IllegalArgumentException("Сначала приостановите маршрут");service.dismiss(id,route.id());tell(p,"Свободные курьеры сняты с маршрута.");return;}
            case "delete"->{count(args,2,"/t logistics delete <маршрут>");if(service.jobs(id).stream().anyMatch(j->j.route().equals(route.id()))||service.builds().shipments(id).values().stream().anyMatch(s->s.route().equals(route.id())&&s.transit()))throw new IllegalArgumentException("В маршруте остались курьеры или груз");routes.remove(route.id());}
            case "filter"->{count(args,3,"/t logistics filter <маршрут> hand|all|<материал>");String encoded="";
                if(!args[2].equalsIgnoreCase("all")){ItemStack item;if(args[2].equalsIgnoreCase("hand"))item=p.getInventory().getItemInMainHand();else{Material m=Material.matchMaterial(ru.neverland.localization.MaterialLabels.canonicalKey(args[2]));if(m==null||!m.isItem())throw new IllegalArgumentException("Материал не найден");item=new ItemStack(m);}
                    if(item.getType().isAir())throw new IllegalArgumentException("Возьмите предмет в основную руку");encoded=BuildsStorage.encode(item);}
                routes.put(route.id(),new Route(route.id(),route.hub(),route.source(),route.target(),encoded,route.limit(),route.keep(),route.enabled()));topology=false;}
            case "limit","keep"->{count(args,3,"/t logistics "+action+" <маршрут> <число>");int value=Integer.parseInt(args[2]);routes.put(route.id(),new Route(route.id(),route.hub(),route.source(),route.target(),route.filter(),action.equals("limit")?value:route.limit(),action.equals("keep")?value:route.keep(),route.enabled()));topology=false;}
            default->throw new IllegalArgumentException("Неизвестная команда. /t logistics help");
        }
        service.change(new Network(id,n.nodes(),n.links(),routes),topology);tell(p,"&aМаршрут обновлён.");menu.open(p,"routes",null,0);
    }
    private void showMap(Player p,Network network){int points=0;for(var link:network.links()){
        var a=network.node(link.a()).point();var b=network.node(link.b()).point();if(!a.world().equals(p.getWorld().getUID()))continue;
        int count=Math.max(1,(int)Math.ceil(a.distance(b)));for(int i=0;i<=count&&points<300;i+=2){double f=(double)i/count;var v=new Position(a.world(),a.x()+(b.x()-a.x())*f,a.y()+(b.y()-a.y())*f+0.2,a.z()+(b.z()-a.z())*f);
            if(v.distance(CourierNpcs.position(p.getLocation()))<=64){p.spawnParticle(Particle.DUST,v.x(),v.y(),v.z(),1,0,0,0,0,new Particle.DustOptions(Color.AQUA,1.4f));points++;}}
        if(points>=300)break;
    }}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(!(sender instanceof Player p)||!p.hasPermission("neverlandtownylogistics.use"))return List.of();var town=service.town(p);if(town==null)return List.of();var n=service.network(town.getUUID());List<String> values=new ArrayList<>();
        try{
            if(args.length==1){values.addAll(List.of("routes","nodes","depots","couriers","cargo","info","storage","map","help"));if(service.manager(p,town))values.addAll(List.of("node","waypoint","link","unlink","remove","route","filter","limit","keep","pause","resume","dismiss","delete"));if(p.hasPermission("neverlandtownylogistics.admin"))values.addAll(List.of("reload","recover"));}
            else if(args.length==2&&Set.of("info","filter","limit","keep","pause","resume","dismiss","delete").contains(args[0]))values.addAll(n.routes().keySet());
            else if((args.length==2&&args[0].equals("storage"))||(args.length==3&&args[0].equals("node")))values.addAll(service.depots(town.getUUID()).keySet());
            else if(args.length==3&&args[0].equals("waypoint"))values.addAll(List.of("road","rail","port"));
            else if((Set.of("link","unlink").contains(args[0])&&args.length<=3)||(args[0].equals("remove")&&args.length==2)||(args[0].equals("route")&&args.length>=3&&args.length<=5))values.addAll(n.nodes().keySet());
            else if(args[0].equals("filter")&&args.length==3)values.addAll(List.of("hand","all"));
        }catch(IOException ignored){}
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return values.stream().filter(v->v.startsWith(prefix)).sorted().toList();
    }
}
