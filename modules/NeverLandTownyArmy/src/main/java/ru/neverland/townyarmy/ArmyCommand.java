package ru.neverland.townyarmy;

import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.MenuStyle;
import static ru.neverland.townyarmy.ArmyModel.*;

public final class ArmyCommand implements CommandExecutor, TabCompleter {
    private final NeverLandTownyArmy plugin; private final ArmyService service; private final ArmyMenu menu;
    public ArmyCommand(NeverLandTownyArmy p, ArmyService s, ArmyMenu m) { plugin = p; service = s; menu = m; }
    static void say(CommandSender s, String text) { s.sendMessage(MenuStyle.decode("&a[Армия] &f" + text)); }
    private static void count(String[] a, int size, String syntax) { if (a.length != size) throw new IllegalArgumentException(syntax); }
    private static boolean toggle(String arg) { if (arg.equalsIgnoreCase("on")) return true; if (arg.equalsIgnoreCase("off")) return false; throw new IllegalArgumentException("Укажите on или off"); }
    private static String reason(String[] args, int start) { return ArmyModel.reason(String.join(" ", Arrays.copyOfRange(args, Math.min(start, args.length), args.length))); }
    private UUID resident(String name) {
        try { UUID id = UUID.fromString(name); if (TownyAPI.getInstance().getResident(id) != null) return id; } catch (IllegalArgumentException ignored) { }
        var r = TownyAPI.getInstance().getResident(name); if (r == null) throw new IllegalArgumentException("Игрок не найден в Towny"); return r.getUUID();
    }
    private boolean officer(Player p, Rank rank) { try { return service.officer(p, service.ownTown(p).getUUID(), rank); } catch (IllegalArgumentException e) { return false; } }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            String action = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
            if (action.equals("list")) action = "roster";
            if (action.equals("age")) {
                // The old provider remains the sole authority for verified character age.
                plugin.getServer().getPluginCommand("townyarmy").execute(sender, "townyarmy", args); return true;
            }
            if (action.equals("reload") || action.equals("transfers")) {
                if (!sender.hasPermission("neverlandtownyarmy.admin")) throw new IllegalArgumentException("Недостаточно прав");
                count(args, 1, "/army " + action);
                if (action.equals("reload")) { plugin.reloadArmy(); say(sender, "&aНастройки армии обновлены."); }
                else { say(sender, "&eНезавершённые поставки:"); service.repository().state().transfers().values().stream().filter(t -> t.phase() != Phase.CLOSED && t.phase() != Phase.CANCELLED).forEach(t -> say(sender, "&f" + t.id() + " | " + t.phase() + " | город " + t.town())); }
                return true;
            }
            if (!sender.hasPermission("neverlandtownyarmy.use")) throw new IllegalArgumentException("Недостаточно прав");
            if (action.equals("help")) { help(sender); return true; }
            if (!(sender instanceof Player p)) throw new IllegalArgumentException("Команда доступна в игре");
            switch (action) {
                case "menu", "roster", "stock", "history", "units", "ranks" -> { count(args, args.length == 0 ? 0 : 1, "/army " + action); menu.open(p, action, 0, MenuStyle.previousMenu(p)); }
                case "record" -> { count(args, 2, "/army record <игрок>"); menu.record(p, resident(args[1]), MenuStyle.previousMenu(p)); }
                case "apply" -> { count(args, 2, "/army apply <infantry|cavalry|navy|air>"); service.apply(p, Unit.parse(args[1])); say(p, "&aЗаявка сохранена. Ожидайте решения командования."); }
                case "oath" -> { count(args, 1, "/army oath"); service.oath(p); say(p, "&aПрисяга принята. Вы зачислены в резерв в звании рядового."); }
                case "approve", "mobilize", "release", "equip" -> {
                    count(args, 2, "/army " + action + " <игрок>"); UUID id = resident(args[1]);
                    switch (action) { case "approve" -> service.approve(p, id); case "mobilize" -> service.mobilize(p, id, true); case "release" -> service.mobilize(p, id, false); case "equip" -> service.equip(p, id); }
                    say(p, "&aПриказ выполнен и сохранён.");
                }
                case "commission", "promote", "demote", "dismiss", "warn", "pardon" -> {
                    if (args.length < 3) throw new IllegalArgumentException("/army " + action + " <игрок> <причина>"); UUID id = resident(args[1]); String reason = reason(args, 2);
                    switch (action) { case "commission" -> service.commission(p, id, reason); case "promote" -> service.promote(p, id, reason); default -> service.personnel(p, id, action, reason); }
                    say(p, "&aКадровый приказ сохранён в журнале.");
                }
                case "assign" -> { if (args.length < 4) throw new IllegalArgumentException("/army assign <игрок> <подразделение> <причина>"); service.assign(p, resident(args[1]), Unit.parse(args[2]), reason(args, 3)); say(p, "&aПеревод выполнен. Новое подразделение требует снабжения."); }
                case "alert" -> { count(args, 2, "/army alert <on|off>"); int n = service.alert(p, toggle(args[1])); say(p, "&aРежим мобилизации сохранён. Изменён статус бойцов: " + n); }
                case "base" -> { count(args, 2, "/army base set"); if (!args[1].equals("set")) throw new IllegalArgumentException("/army base set"); service.base(p); say(p, "&aБаза назначена в текущей точке."); }
                case "supply" -> { count(args, 3, "/army supply <ресурс> <количество>"); UUID id = service.supply(p, args[1].toLowerCase(Locale.ROOT), ArmySettings.amount(args[2])); var tx = service.repository().transfer(id); say(p, "&eПоставка " + id + ": &f" + ArmyMenu.phase(tx.phase())); }
                case "duty" -> { count(args, 2, "/army duty <on|off>"); boolean on = toggle(args[1]); service.duty(p, on); say(p, on ? "&aВы заступили на дежурство." : "&eДежурство завершено."); }
                case "train" -> { count(args, 1, "/army train"); service.train(p); say(p, "&eТренировка началась. Оставайтесь на базе и двигайтесь: " + service.settings().trainingDuration() / 1000 + " сек., не менее " + service.settings().trainingMovement() + " блоков."); }
                case "radio" -> {
                    if (args.length < 2) throw new IllegalArgumentException("/army radio <сообщение>");
                    UUID town = service.ownTown(p).getUUID(); var s = service.repository().state().soldiers().get(p.getUniqueId());
                    if (s == null || !s.serving() || !service.officer(p, town, Rank.RECRUIT)) throw new IllegalArgumentException("Радиосвязь доступна военнослужащим после присяги");
                    String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length)); if (message.length() > 200 || message.chars().anyMatch(c -> Character.isISOControl(c) || c == '§' || c == '&')) throw new IllegalArgumentException("Сообщение: до 200 символов без цветовых кодов");
                    for (var receiver : plugin.getServer().getOnlinePlayers()) if (service.officer(receiver, town, Rank.RECRUIT)) say(receiver, "&b[Рация] " + p.getName() + "&f: " + message);
                }
                default -> throw new IllegalArgumentException("Справка: /army help");
            }
        } catch (Exception ex) {
            say(sender, "&c" + (ex instanceof IllegalArgumentException ? ex.getMessage() : "Действие приостановлено. Проверьте состояние армии; поставка восстановится по сохранённому ID."));
            if (!(ex instanceof IllegalArgumentException)) plugin.getLogger().log(java.util.logging.Level.WARNING, "Команда армии", ex);
        }
        return true;
    }
    private void help(CommandSender p) {
        say(p, "&eВоенная служба • /army — меню");
        say(p, "&b/army apply <подразделение> &f— заявка на службу");
        say(p, "&b/army oath &f— присяга на базе после приёма");
        say(p, "&b/army duty <on|off> &f— дежурство; &b/army train &f— подготовка");
        say(p, "&b/army roster &f— состав; &bunits &f— подразделения; &branks &f— звания");
        say(p, "&b/army stock &f— снабжение; &brecord <игрок> &f— служебная карточка");
        say(p, "&b/army radio <сообщение> &f— рация армии своего города");
        if (p instanceof Player player && officer(player, Rank.SERGEANT)) {
            say(p, "&b/army supply <ресурс> <количество> &f— перевод со склада города");
            say(p, "&b/army equip <игрок> &f— выделить снабжение");
        }
        if (p instanceof Player player && officer(player, Rank.LIEUTENANT)) {
            say(p, "&b/army approve <игрок> &f— приём; &bmobilize/release <игрок> &f— призыв/резерв");
            say(p, "&b/army history &f— журнал командования");
        }
        if (p instanceof Player player && officer(player, Rank.CAPTAIN)) {
            say(p, "&b/army promote/demote <игрок> <причина> &f— изменение звания");
            say(p, "&b/army assign <игрок> <подразделение> <причина> &f— перевод");
            say(p, "&b/army warn/pardon/dismiss <игрок> <причина> &f— дисциплина и увольнение");
        }
        if (p instanceof Player player && officer(player, Rank.GENERAL)) {
            say(p, "&b/army base set &f— назначить базу; &balert <on|off> &f— общая мобилизация");
            if (service.executive(player, service.ownTown(player).getUUID())) say(p, "&b/army commission <игрок> <причина> &f— назначить генерала");
        }
    }
    @Override public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] a) {
        var choices = new ArrayList<String>();
        if (a.length == 1) {
            if (s.hasPermission("neverlandtownyarmy.use")) choices.addAll(List.of("menu", "help", "apply", "oath", "duty", "train", "roster", "record", "stock", "units", "ranks", "radio"));
            if (s instanceof Player p) {
                if (officer(p, Rank.SERGEANT)) choices.addAll(List.of("supply", "equip"));
                if (officer(p, Rank.LIEUTENANT)) choices.addAll(List.of("approve", "mobilize", "release", "history"));
                if (officer(p, Rank.CAPTAIN)) choices.addAll(List.of("promote", "demote", "assign", "warn", "pardon", "dismiss"));
                if (officer(p, Rank.GENERAL)) choices.addAll(List.of("base", "alert"));
                try { if (service.executive(p, service.ownTown(p).getUUID())) choices.add("commission"); } catch (IllegalArgumentException ignored) { }
            }
            if (s.hasPermission("neverlandtownyarmy.admin")) choices.addAll(List.of("reload", "transfers"));
            if (s.hasPermission("neverlandtownybuilds.army.age")) choices.add("age");
        } else if (s instanceof Player p && s.hasPermission("neverlandtownyarmy.use")) {
            if (a.length == 2 && a[0].equals("apply") || a.length == 3 && a[0].equals("assign") && officer(p, Rank.CAPTAIN)) for (Unit u : Unit.values()) choices.add(u.id());
            if (a.length == 2 && (a[0].equals("duty") || a[0].equals("alert") && officer(p, Rank.GENERAL))) choices.addAll(List.of("on", "off"));
            if (a.length == 2 && a[0].equals("base") && officer(p, Rank.GENERAL)) choices.add("set");
            if (a.length == 2 && a[0].equals("supply") && officer(p, Rank.SERGEANT)) choices.addAll(RESOURCES);
            if (a.length == 2 && (a[0].equals("record") || Set.of("approve", "mobilize", "release", "equip", "promote", "demote", "assign", "warn", "pardon", "dismiss", "commission").contains(a[0]) && officer(p, Rank.SERGEANT))) {
                try { UUID town = service.ownTown(p).getUUID(); for (var member : service.repository().state().soldiers().values()) if (member.town().equals(town)) choices.add(ArmyMenu.name(member.resident())); } catch (IllegalArgumentException ignored) { }
            }
        }
        String prefix = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().distinct().filter(c -> c.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
