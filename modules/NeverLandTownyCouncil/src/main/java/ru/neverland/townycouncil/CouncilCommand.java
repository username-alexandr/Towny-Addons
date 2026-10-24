package ru.neverland.townycouncil;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import java.time.Instant;
import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class CouncilCommand implements TabExecutor {
    private final NeverLandTownyCouncil plugin; private final CouncilService service; private final CouncilMenus menus; private final AccessMenus access;
    public CouncilCommand(NeverLandTownyCouncil plugin, CouncilService service, CouncilMenus menus,AccessMenus access) { this.plugin = plugin; this.service = service; this.menus = menus;this.access=access; }
    public static void tell(CommandSender sender, String text) { synchronized (ru.neverland.core.SuiteState.lock()) { sender.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&aNeverLand &f• Министры &7» &f" + text)); } }
    private Town town(CommandSender sender, String[] args, int index) {
        Town town = args.length > index ? TownyAPI.getInstance().getTown(args[index])
                : sender instanceof Player p ? service.ownTown(p.getUniqueId()) : null;
        if (town == null) throw new IllegalArgumentException("Город не найден. Укажите его последним аргументом команды"); return town;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) { synchronized (ru.neverland.core.SuiteState.lock()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyCouncil")){return true;}
        if (!sender.hasPermission("neverlandtownycouncil.use")) { tell(sender, "&cНет права на просмотр совета"); return true; }
        try {
            String action = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
            switch (action) {
                case "menu", "list" -> {
                    var town = town(sender, args, 1);
                    if (sender instanceof Player p) menus.open(p, town);
                    else for (var role : MinisterRole.values()) {
                        var a = service.appointment(town.getUUID(), role);
                        tell(sender, role.title() + ": " + (a == null ? "свободно" : CouncilMenus.name(a.resident()) + (service.active(a) ? "" : " (приостановлено)")));
                    }
                }
                case "office" -> {
                    if(!(sender instanceof Player p)||args.length!=2)throw new IllegalArgumentException("Откройте должность из меню города.");
                    menus.office(p,town(sender,args,2),MinisterRole.parse(args[1]));
                }
                case "access" -> {
                    if(!(sender instanceof Player p))throw new IllegalArgumentException("Настройки меняет мэр в игре");
                    var own=service.ownTown(p.getUniqueId());if(!service.mayEditAccess(p,own))throw new IllegalArgumentException("Доступно только мэру своего города");
                    if(args.length==1){access.open(p,own,false,0);break;}
                    if(args.length==2&&args[1].equalsIgnoreCase("roles")){access.open(p,own,true,0);break;}
                    if(args.length!=3&&args.length!=5)throw new IllegalArgumentException("/council access <player|role> <житель|должность> [действие allow|deny|default]");
                    String role="";UUID resident=null;
                    if(args[1].equalsIgnoreCase("role"))role=MinisterRole.parse(args[2]).id();
                    else if(args[1].equalsIgnoreCase("player")){var found=TownyAPI.getInstance().getResident(args[2]);if(found==null)throw new IllegalArgumentException("Житель не найден");resident=found.getUUID();}
                    else if(args[1].equalsIgnoreCase("resident"))resident=UUID.fromString(args[2]);
                    else throw new IllegalArgumentException("Выберите player или role");
                    if(args.length==3)access.actions(p,own,role,resident,null,0);
                    else{var rules=service.rules(own);service.changeAccess(p,own,rules.mayor(),rules.revision(),role,resident,args[3],args[4]);tell(p,"&aПравило доступа сохранено");}
                }
                case "appoint" -> {
                    if (args.length < 3 || args.length > 4) throw new IllegalArgumentException("/council appoint <должность> <игрок> [город]");
                    var role = MinisterRole.parse(args[1]); var resident = TownyAPI.getInstance().getResident(args[2]);
                    if (resident == null) throw new IllegalArgumentException("Игрок не найден в Towny");
                    service.appoint(sender, town(sender, args, 3), role, resident.getUUID());
                    tell(sender, "&aНазначение сохранено: &f" + resident.getName() + " • " + role.title());
                }
                case "dismiss" -> {
                    if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("/council dismiss <должность> [город]");
                    var role = MinisterRole.parse(args[1]); service.dismiss(sender, town(sender, args, 2), role);
                    tell(sender, "&aМинистр снят. Должность свободна: &f" + role.title());
                }
                case "permissions" -> {
                    if (args.length != 2) throw new IllegalArgumentException("/council permissions <должность>");
                    var role = MinisterRole.parse(args[1]); var own=sender instanceof Player p?service.ownTown(p.getUniqueId()):null;
                    if(own==null){tell(sender,"&eОбщие права должности из настроек сервера:");service.permissions(role.id()).stream().sorted().forEach(node->tell(sender,"&7• &f"+node));}
                    else {var rules=service.roleActions(own.getUUID(),role.id());tell(sender,"&e"+role.title()+" • Правила города "+own.getName());
                        for(var a:TownAction.values())tell(sender,"&f"+a.title()+": &7"+switch(rules.get(a.id())){case "ALLOW"->"разрешено";case "DENY"->"запрещено";default->"по умолчанию";});}
                }
                case "history" -> {
                    var town = town(sender, args, 1);
                    if (!service.manages(sender, town)) throw new IllegalArgumentException("Журнал доступен мэру своего города и администратору");
                    var entries = service.repository().history().stream().filter(a -> a.town().equals(town.getUUID())).toList();
                    service.accessHistory().stream().filter(h->h.contains(town.getUUID().toString())).skip(Math.max(0,service.accessHistory().stream().filter(h->h.contains(town.getUUID().toString())).count()-8)).forEach(h->tell(sender,"&7"+h));
                    tell(sender, "&e" + town.getName() + " • Последние назначения и снятия:");
                    entries.stream().skip(Math.max(0, entries.size() - 8)).forEach(a -> {
                        tell(sender, "&7" + Instant.ofEpochMilli(a.at()) + " &f" + a.detail()); tell(sender, "&7Кто: &f" + a.actor());
                    });
                }
                case "reload" -> {
                    if (!sender.hasPermission("neverlandtownycouncil.admin")) throw new IllegalArgumentException("Нет административного права");
                    plugin.reloadSettings(); tell(sender, "&aНастройки проверены; права министров обновлены");
                }
                default -> help(sender);
            }
        } catch (IllegalArgumentException ex) { tell(sender, "&c" + ex.getMessage()); }
        catch (Exception ex) { tell(sender, "&cОперация не завершена. Проверьте журнал сервера"); plugin.getLogger().log(java.util.logging.Level.SEVERE, "Операция совета не выполнена", ex); }
        return true;
    } }
    public static void help(CommandSender sender) { synchronized (ru.neverland.core.SuiteState.lock()) {
        tell(sender, "&e/council [menu] [город] &7— состав министерств.");
        tell(sender, "&e/council permissions <должность> &7— права должности.");
        tell(sender,"&e/council access &7— личные права жителей (только мэр).");
        tell(sender,"&e/council access roles &7— права должностей (только мэр).");
        if (sender.hasPermission("neverlandtownycouncil.manage") || sender.hasPermission("neverlandtownycouncil.admin")) {
            tell(sender, "&e/council appoint <должность> <игрок> [город]");
            tell(sender, "&e/council dismiss <должность> [город]");
            tell(sender, "&7Назначает только мэр своего города или администратор.");
            tell(sender, "&e/council history [город] &7— журнал для мэра.");
        }
        tell(sender, "&7Должности: &feconomy, defense, construction, foreign.");
        tell(sender, "&7Также работает &f/t ministers&7. Выборный совет: &f/t council&7.");
        if (sender.hasPermission("neverlandtownycouncil.admin")) tell(sender, "&e/council reload &7— обновить настройки прав.");
    } }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (!sender.hasPermission("neverlandtownycouncil.use") || args.length == 0) return List.of();
        var own = sender instanceof Player p ? service.ownTown(p.getUniqueId()) : null;
        boolean manager = sender.hasPermission("neverlandtownycouncil.admin") || service.manages(sender, own);
        var values = new ArrayList<String>();
        if (args.length == 1) {
            values.addAll(List.of("menu", "permissions", "help"));if(service.mayEditAccess(sender,own))values.add("access"); if (manager) values.addAll(List.of("appoint", "dismiss", "history"));
            if (sender.hasPermission("neverlandtownycouncil.admin")) values.add("reload");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("permissions") || manager && Set.of("appoint", "dismiss").contains(args[0].toLowerCase(Locale.ROOT))))
            Arrays.stream(MinisterRole.values()).forEach(r -> values.add(r.id()));
        else if (args.length == 3 && args[0].equalsIgnoreCase("appoint") && manager && own != null)
            own.getResidents().forEach(r -> values.add(r.getName()));
        if(args[0].equalsIgnoreCase("access")&&service.mayEditAccess(sender,own)){
            if(args.length==2)values.addAll(List.of("player","role","roles"));
            else if(args.length==3&&args[1].equalsIgnoreCase("role"))Arrays.stream(MinisterRole.values()).forEach(v->values.add(v.id()));
            else if(args.length==3&&args[1].equalsIgnoreCase("player"))own.getResidents().stream().filter(r->!r.getUUID().equals(own.getMayor().getUUID())).forEach(r->values.add(r.getName()));
            else if(args.length==4)Arrays.stream(TownAction.values()).forEach(v->values.add(v.id()));
            else if(args.length==5)values.addAll(List.of("allow","deny","default"));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    } }
}
