package ru.neverland.townysieges;

import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.MenuStyle;

public final class SiegeCommand implements TabExecutor {
    private final NeverLandTownySiegesPlus plugin;private final SiegeService service;private final SiegeMenu menu;
    public SiegeCommand(NeverLandTownySiegesPlus plugin,SiegeService service,SiegeMenu menu){this.plugin=plugin;this.service=service;this.menu=menu;}
    static void tell(CommandSender p,String text){p.sendMessage(MenuStyle.decode("&6[Осады] &f"+text));}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownySiegesPlus")){return true;}
        try{
            if(!sender.hasPermission("neverlandtownysiegesplus.use"))throw new IllegalArgumentException("Недостаточно прав");
            String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
            switch(action){
                case "menu"->{if(!(sender instanceof Player p))throw new IllegalArgumentException("Меню доступно в игре");if(args.length>2)throw new IllegalArgumentException("/sieges menu [город]");var town=args.length==2?TownyAPI.getInstance().getTown(args[1]):SiegeService.town(p.getUniqueId());if(town==null)throw new IllegalArgumentException("Укажите город: /sieges menu <город>");menu.open(p,town.getUUID(),MenuStyle.previousMenu(p));}
                case "status"->{if(!sender.hasPermission("neverlandtownysiegesplus.admin"))throw new IllegalArgumentException("Диагностика доступна администратору");var s=service.warStatus();tell(sender,s.get("state")+": "+s.get("detail"));if(!s.get("dependencyError").isBlank())tell(sender,"&e"+s.get("dependencyError"));}
                case "reload"->{if(!sender.hasPermission("neverlandtownysiegesplus.admin"))throw new IllegalArgumentException("Недостаточно прав");if(args.length!=1)throw new IllegalArgumentException("/sieges reload");plugin.reloadSieges();tell(sender,"Настройки осад обновлены.");}
                case "raid"->{
                    if(args.length==1||args[1].equalsIgnoreCase("status")){plugin.raids().status(sender);break;}
                    if(args[1].equalsIgnoreCase("cancel")||args[1].equalsIgnoreCase("history")){
                        if(!sender.hasPermission("neverlandtownysiegesplus.admin"))throw new IllegalArgumentException("Доступно администратору");
                        if(args.length!=3)throw new IllegalArgumentException("/sieges raid "+args[1]+" <город>");
                        var target=TownyAPI.getInstance().getTown(args[2]);if(target==null)throw new IllegalArgumentException("Город не найден");
                        if(args[1].equalsIgnoreCase("cancel"))plugin.raids().cancel(sender,target.getUUID());else plugin.raids().history(sender,target.getUUID());break;
                    }
                    if(!(sender instanceof Player p)||args.length!=2)throw new IllegalArgumentException("/sieges raid <город>");
                    plugin.raids().declare(p,args[1]);
                }
                case "repair"->{
                    if(args.length==2){if(!(sender instanceof Player p))throw new IllegalArgumentException("Ремонт здания доступен в игре");plugin.repairs().open(p,args[1]);break;}
                    if(!sender.isOp()&&!sender.hasPermission("neverlandtownysiegesplus.admin"))throw new IllegalArgumentException("Квитанции ремонта доступны администратору");
                    if(args.length==1||args.length==3&&args[1].equalsIgnoreCase("admin")&&args[2].equalsIgnoreCase("list")){plugin.repairs().list(sender);break;}
                    if(args.length==4&&args[1].equalsIgnoreCase("admin")&&args[2].equalsIgnoreCase("show")){plugin.repairs().show(sender,UUID.fromString(args[3]));break;}
                    if(args.length>=7&&args[1].equalsIgnoreCase("admin")&&args[2].equalsIgnoreCase("resolve")&&args[5].equalsIgnoreCase("confirm")){
                        plugin.repairs().resolve(sender,UUID.fromString(args[3]),args[4].toLowerCase(Locale.ROOT),String.join(" ",Arrays.copyOfRange(args,6,args.length)));break;
                    }
                    throw new IllegalArgumentException("/sieges repair <здание> | /sieges repair admin list|show|resolve");
                }
                case "hp"->{if(!(sender instanceof Player p))throw new IllegalArgumentException("Посмотрите на блок в игре и выполните /sieges hp");plugin.tnt().health(p);}
                case "camp"->{if(!(sender instanceof Player p))throw new IllegalArgumentException("Осадный лагерь доступен в игре");if(args.length==1)plugin.camps().menu(p);else if(args.length==2)plugin.camps().register(p,args[1]);else throw new IllegalArgumentException("/sieges camp [город]");}
                case "loot"->{
                    if(!sender.isOp()&&!sender.hasPermission("neverlandtownysiegesplus.admin"))throw new IllegalArgumentException("Журнал добычи доступен администратору");
                    if(args.length==1){plugin.loot().list(sender);break;}
                    if(args.length==3&&args[1].equalsIgnoreCase("show")){plugin.loot().status(sender,UUID.fromString(args[2]));break;}
                    if(args.length>=7&&args[1].equalsIgnoreCase("resolve")){
                        if(!Set.of("yes","no").contains(args[4].toLowerCase(Locale.ROOT))||!args[5].equalsIgnoreCase("confirm"))throw new IllegalArgumentException("/sieges loot resolve <ID> <core|debit|credit> <yes|no> confirm <основание>");
                        plugin.loot().resolve(sender,UUID.fromString(args[2]),args[3].toLowerCase(Locale.ROOT),args[4].equalsIgnoreCase("yes"),String.join(" ",Arrays.copyOfRange(args,6,args.length)));break;
                    }
                    throw new IllegalArgumentException("/sieges loot | /sieges loot show <ID> | /sieges loot resolve <ID> <core|debit|credit> <yes|no> confirm <основание>");
                }
                default->{tell(sender,"&e/sieges repair <здание> &f— прочность и ремонт из городской казны.");tell(sender,"&e/sieges camp <город> &f— зарегистрировать лагерь у чужого города; &e/sieges camp &f— меню лагеря.");tell(sender,"&e/sieges menu [город] &f— оборонительные постройки и ограничения.");tell(sender,"&e/t sieges &f— открыть меню своего города.");tell(sender,"&e/sieges raid <город> &f— объявить армейский рейд; &e/sieges raid status &f— текущие рейды.");tell(sender,"&e/sieges hp &f— прочность двери или блока под прицелом.");if(sender.hasPermission("neverlandtownysiegesplus.admin"))tell(sender,"&e/sieges raid cancel|history <город> &f— остановка и журнал; &e/sieges reload &f— настройки.");}
            }
        }catch(Exception|LinkageError e){tell(sender,"&c"+e.getMessage());}
        return true;
    } }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if(!sender.hasPermission("neverlandtownysiegesplus.use"))return List.of();
        var out=new ArrayList<String>();if(args.length==1){out.addAll(List.of("menu","help","camp","raid","hp","repair"));if(sender.hasPermission("neverlandtownysiegesplus.admin")||sender.isOp())out.addAll(List.of("status","reload","loot"));}
        if(args.length==2&&(args[0].equalsIgnoreCase("menu")||args[0].equalsIgnoreCase("raid")||args[0].equalsIgnoreCase("camp")))TownyAPI.getInstance().getTowns().forEach(t->out.add(t.getName()));
        if(args.length==2&&args[0].equalsIgnoreCase("raid")){out.add("status");if(sender.hasPermission("neverlandtownysiegesplus.admin"))out.addAll(List.of("cancel","history"));}
        if(args.length==3&&args[0].equalsIgnoreCase("raid")&&Set.of("cancel","history").contains(args[1].toLowerCase(Locale.ROOT))&&sender.hasPermission("neverlandtownysiegesplus.admin"))TownyAPI.getInstance().getTowns().forEach(t->out.add(t.getName()));
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return out.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().limit(60).toList();
    } }
}
