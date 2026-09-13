package ru.neverland.townydiplomacy;

import java.util.*;
import java.time.Instant;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import static ru.neverland.townydiplomacy.Treaty.*;

public final class DiplomacyCommand implements TabExecutor {
    private final NeverLandTownyDiplomacy plugin;private final DiplomacyService service;private final DiplomacyMenus menus;
    public DiplomacyCommand(NeverLandTownyDiplomacy plugin,DiplomacyService service,DiplomacyMenus menus) { this.plugin=plugin;this.service=service;this.menus=menus; }
    public static void tell(CommandSender sender,String text) { sender.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',"&aNeverLand &f• Дипломатия &7» &f"+text)); }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] original) {
        if(!sender.hasPermission("neverlandtownydiplomacy.use")){tell(sender,"&cНет права на просмотр дипломатии");return true;}
        try {
            String[] args=original;Town own=sender instanceof Player p?service.ownTown(p.getUniqueId()):null;
            if(args.length>0&&args[0].equalsIgnoreCase("as")) {
                if(!sender.hasPermission("neverlandtownydiplomacy.admin"))throw new IllegalArgumentException("Команда as доступна только администратору");
                if(args.length<3)throw new IllegalArgumentException("/diplomacy as <город> <команда> ...");
                own=TownyAPI.getInstance().getTown(args[1]);args=Arrays.copyOfRange(args,2,args.length);
            }
            String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
            if(action.equals("help")){help(sender);return true;}
            if(action.equals("reload")) {
                if(!sender.hasPermission("neverlandtownydiplomacy.admin"))throw new IllegalArgumentException("Нет административного права");
                plugin.reloadSettings();tell(sender,"&aНастройки проверены. Условия уже предложенных договоров сохранены");return true;
            }
            if(action.equals("menu")||action.equals("list")) {
                Town town=args.length>1?TownyAPI.getInstance().getTown(args[1]):own;if(town==null)throw new IllegalArgumentException("Укажите город: /diplomacy list <город> [страница]");
                int page=args.length>2?Integer.parseInt(args[2])-1:0;if(page<0)throw new IllegalArgumentException("Страница начинается с 1");
                if(sender instanceof Player p)menus.open(p,town,page,null);
                else {
                    tell(sender,"&e"+town.getName()+" • Договоры");
                    service.treaties(town.getUUID()).stream().skip(page*7L).limit(7).forEach(t->tell(sender,t.get("id")+" • "+t.get("type")+" • "+t.get("phase")));
                }
                return true;
            }
            if(own==null)throw new IllegalArgumentException("Нужен свой город; для консоли: /diplomacy as <город> <команда>");
            switch(action) {
                case "offer" -> {
                    if(args.length<3)throw new IllegalArgumentException("/diplomacy offer <тип> <город> [дни] [причина]");
                    var type=TreatyType.parse(args[1]);if(!type.bilateral())throw new IllegalArgumentException("Для ограничений используйте embargo или sanctions");
                    var t=service.offer(sender,own,TownyAPI.getInstance().getTown(args[2]),type,days(args,3),SanctionScope.NONE,reason(args,4));
                    announce(sender,t,"Предложение сохранено. Ожидает согласия второго города");
                }
                case "embargo" -> {
                    if(args.length<2)throw new IllegalArgumentException("/diplomacy embargo <город> [дни] [причина]");
                    var t=service.offer(sender,own,TownyAPI.getInstance().getTown(args[1]),TreatyType.EMBARGO,days(args,2),SanctionScope.NONE,reason(args,3));announce(sender,t,"Эмбарго введено");
                }
                case "sanctions" -> {
                    if(args.length<3)throw new IllegalArgumentException("/diplomacy sanctions <город> <trade|diplomatic|all> [дни] [причина]");
                    SanctionScope scope;try{scope=SanctionScope.valueOf(args[2].toUpperCase(Locale.ROOT));}catch(RuntimeException ex){throw new IllegalArgumentException("Ограничения: trade, diplomatic или all");}
                    var t=service.offer(sender,own,TownyAPI.getInstance().getTown(args[1]),TreatyType.SANCTIONS,days(args,3),scope,reason(args,4));announce(sender,t,"Санкции введены");
                }
                case "accept","reject","end" -> {
                    if(args.length!=2)throw new IllegalArgumentException("/diplomacy "+action+" <полный-ID>");
                    var t=service.change(sender,own,UUID.fromString(args[1]),action);announce(sender,t,"Решение сохранено: "+DiplomacyMenus.phase(t));
                }
                case "history" -> {
                    requireView(sender,own);var town=own.getUUID();int page=args.length>1?Integer.parseInt(args[1])-1:0;if(page<0)throw new IllegalArgumentException("Страница начинается с 1");
                    var rows=service.repository().history().stream().filter(a->a.first().equals(town)||a.second().equals(town)).sorted(Comparator.comparingLong(DiplomacyRepository.Audit::at).reversed()).toList();
                    tell(sender,"&eЖурнал дипломатии • "+own.getName());rows.stream().skip(page*6L).limit(6).forEach(a->{tell(sender,"&7"+Instant.ofEpochMilli(a.at())+" &f"+a.action()+" • "+a.treaty());tell(sender,"&7Инициатор: &f"+a.actor());});
                }
                case "incidents" -> {
                    requireView(sender,own);var town=own.getUUID();int page=args.length>1?Integer.parseInt(args[1])-1:0;if(page<0)throw new IllegalArgumentException("Страница начинается с 1");
                    tell(sender,"&eОборонные инциденты • "+own.getName());
                    service.repository().incidents().stream().filter(i->i.victim().equals(town)||i.defenders().contains(town)).sorted(Comparator.comparingLong(DiplomacyRepository.Incident::at).reversed()).skip(page*6L).limit(6)
                            .forEach(i->{tell(sender,"&7"+Instant.ofEpochMilli(i.at())+" &f"+DiplomacyMenus.name(i.attacker())+" → "+DiplomacyMenus.name(i.victim()));tell(sender,"&7Место: &f"+i.location());});
                }
                default -> help(sender);
            }
        } catch(IllegalArgumentException ex) { tell(sender,"&c"+(ex instanceof NumberFormatException?"Дни и страница должны быть целыми числами":ex.getMessage())); }
        catch(Exception ex) { tell(sender,"&cДействие не выполнено; реестр требует проверки администратором");plugin.failure("Операция дипломатии не выполнена",ex); }
        return true;
    }
    private void requireView(CommandSender sender,Town town) { if(!service.manages(sender,town,TreatyType.ALLIANCE))throw new IllegalArgumentException("Журнал доступен руководству своего города"); }
    private int days(String[] args,int at) { return args.length>at?Integer.parseInt(args[at]):service.settings().defaultDays(); }
    private String reason(String[] args,int at) { return args.length>at?String.join(" ",Arrays.copyOfRange(args,at,args.length)):"Решение городской администрации"; }
    private void announce(CommandSender sender,Treaty t,String message) {
        tell(sender,"&a"+message+" • &f"+t.id());plugin.announce(t,"&e"+t.type().title()+" &7• &f"+DiplomacyMenus.name(t.first())+" ↔ "+DiplomacyMenus.name(t.second())+" &7• "+DiplomacyMenus.phase(t)+". /diplomacy");
    }
    public static void help(CommandSender sender) {
        tell(sender,"&e/diplomacy &7— ваши договоры; &e/diplomacy list <город>");
        tell(sender,"&e/diplomacy offer <тип> <город> [дни] [причина]");
        tell(sender,"&7Типы: &falliance, trade, nonaggression, vassalage, guarantee");
        tell(sender,"&7Для vassalage вы — сюзерен; для guarantee — гарант.");
        tell(sender,"&e/diplomacy accept|reject|end <полный-ID>");
        tell(sender,"&e/diplomacy embargo <город> [дни] [причина]");
        tell(sender,"&e/diplomacy sanctions <город> <trade|diplomatic|all> [дни]");
        tell(sender,"&e/diplomacy history|incidents [страница] &7— для руководства.");
        if(sender.hasPermission("neverlandtownydiplomacy.admin")){tell(sender,"&e/diplomacy as <город> <команда> ...");tell(sender,"&e/diplomacy reload &7— новые настройки.");}
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if(!sender.hasPermission("neverlandtownydiplomacy.use")||args.length==0)return List.of();
        var own=sender instanceof Player p?service.ownTown(p.getUniqueId()):null;boolean manager=sender.hasPermission("neverlandtownydiplomacy.admin")||service.manages(sender,own,TreatyType.ALLIANCE);
        var values=new ArrayList<String>();String action=args[0].toLowerCase(Locale.ROOT);
        if(args.length==1){values.addAll(List.of("menu","list","help"));if(manager)values.addAll(List.of("offer","accept","reject","end","embargo","sanctions","history","incidents"));if(sender.hasPermission("neverlandtownydiplomacy.admin"))values.addAll(List.of("as","reload"));}
        else if(args.length==2&&action.equals("offer")&&manager)Arrays.stream(TreatyType.values()).filter(TreatyType::bilateral).forEach(t->values.add(t.id()));
        else if(args.length==3&&action.equals("sanctions")&&manager)values.addAll(List.of("trade","diplomatic","all"));
        else if(args.length==2&&Set.of("accept","reject","end").contains(action)&&manager&&own!=null){var id=own.getUUID();service.repository().all().values().stream().filter(t->t.party(id)&&t.open(System.currentTimeMillis())).forEach(t->values.add(t.id().toString()));}
        else if(args.length==2&&Set.of("menu","list","embargo","sanctions","as").contains(action)||args.length==3&&action.equals("offer"))TownyAPI.getInstance().getTowns().forEach(t->values.add(t.getName()));
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().limit(100).toList();
    }
}
