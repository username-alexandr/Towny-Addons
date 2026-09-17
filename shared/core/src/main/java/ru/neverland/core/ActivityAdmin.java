package ru.neverland.core;

import java.util.*;
import java.util.function.Supplier;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;

/** An additive /existing-command admin namespace; original commands keep their semantics. */
public final class ActivityAdmin implements TabExecutor {
    @FunctionalInterface public interface Action { String run(String action, long minutes) throws Exception; }
    public record Target(String id, String name, Set<String> actions, Action action) {
        public Target { Objects.requireNonNull(id); Objects.requireNonNull(name); actions=Set.copyOf(actions); Objects.requireNonNull(action); }
    }
    public static final Set<String> TIMED = Set.of("status", "pause", "resume", "restart", "extend", "cancel");
    private final JavaPlugin plugin;
    private final String permission;
    private final Supplier<List<Target>> targets;
    private final CommandExecutor original;
    private final TabCompleter completions;
    private ActivityAdmin(JavaPlugin plugin, String permission, Supplier<List<Target>> targets, CommandExecutor original, TabCompleter completions) {
        this.plugin=plugin; this.permission=permission; this.targets=targets; this.original=original; this.completions=completions;
    }
    public static void attach(JavaPlugin plugin, String command, String permission, Supplier<List<Target>> targets) {
        var direct=Objects.requireNonNull(plugin.getCommand(command), "Нет команды " + command);
        if (direct.getExecutor() instanceof ActivityAdmin) throw new IllegalStateException("Администрирование уже подключено");
        var control=new ActivityAdmin(plugin,permission,targets,direct.getExecutor(),direct.getTabCompleter());
        direct.setExecutor(control); direct.setTabCompleter(control);
    }
    private boolean allowed(CommandSender sender) { return sender.hasPermission(permission); }
    public static Target find(List<Target> targets, String id) {
        var exact=targets.stream().filter(t->t.id().equalsIgnoreCase(id)).toList();
        var found=exact.isEmpty()?targets.stream().filter(t->t.id().toLowerCase(Locale.ROOT).startsWith(id.toLowerCase(Locale.ROOT))).toList():exact;
        if (id.isBlank() || found.isEmpty()) throw new IllegalArgumentException("Активная задача не найдена. Используйте admin list");
        if (found.size()!=1) throw new IllegalArgumentException("Неоднозначный ID: укажите полный идентификатор");
        return found.get(0);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length==0 || !args[0].equalsIgnoreCase("admin")) return original.onCommand(sender,command,label,args);
        if (!allowed(sender)) { sender.sendMessage("§cНет разрешения " + permission); return true; }
        try {
            ApiServices.primaryThread();
            String action=args.length==1?"help":args[1].toLowerCase(Locale.ROOT);
            if (action.equals("help")) {
                sender.sendMessage("§e/"+label+" admin list | status <ID> | pause|resume|restart|cancel <ID> | extend <минуты> <ID>");
                sender.sendMessage("§7Доступные действия каждой задачи показаны в list. Расчёты и завершённые задачи не перезапускаются."); return true;
            }
            var entries=List.copyOf(targets.get());
            if (action.equals("list") && args.length==2) {
                sender.sendMessage("§bАктивные задачи: " + entries.size());
                entries.stream().limit(100).forEach(t->sender.sendMessage("§f"+t.id()+" §7| "+t.name()+" | "+String.join(", ",new TreeSet<>(t.actions()))));
                if(entries.size()>100)sender.sendMessage("§7Показаны первые 100; status принимает полный ID любой задачи."); return true;
            }
            boolean extend=action.equals("extend");
            if (args.length!=(extend?4:3)) throw new IllegalArgumentException("Неверные аргументы. /"+label+" admin help");
            long minutes=extend?Long.parseLong(args[2]):0;
            if(extend&&(minutes<1||minutes>10080))throw new IllegalArgumentException("Продление: 1–10080 минут");
            var target=find(entries,args[extend?3:2]);
            if (!target.actions().contains(action)) throw new IllegalArgumentException("Для этой задачи доступны: " + String.join(", ",new TreeSet<>(target.actions())));
            String result=target.action().run(action,minutes);
            sender.sendMessage("§a"+result);
            if(!action.equals("status"))plugin.getLogger().info("Администратор "+sender.getName()+": "+action+" "+target.id()+(extend?" +"+minutes+" мин.":"")+" — "+result);
        } catch(Exception ex) { sender.sendMessage("§cОперация остановлена: "+ex.getMessage()); }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!allowed(sender)) return args.length>0&&args[0].equalsIgnoreCase("admin")?List.of():completions==null?List.of():completions.onTabComplete(sender,command,alias,args);
        if(args.length==1) {
            var values=new ArrayList<String>(); if(completions!=null){var old=completions.onTabComplete(sender,command,alias,args);if(old!=null)values.addAll(old);}values.add("admin");return filter(values,args[0]);
        }
        if(args.length==0||!args[0].equalsIgnoreCase("admin"))return completions==null?List.of():completions.onTabComplete(sender,command,alias,args);
        if(args.length==2)return filter(List.of("help","list","status","pause","resume","restart","extend","cancel"),args[1]);
        try {
            if(args.length==3&&args[1].equalsIgnoreCase("extend"))return filter(List.of("5","30","60","1440"),args[2]);
            if(args.length==(args[1].equalsIgnoreCase("extend")?4:3))return filter(targets.get().stream().filter(t->t.actions().contains(args[1].toLowerCase(Locale.ROOT))).map(Target::id).toList(),args[args.length-1]);
        }catch(Exception ignored){}
        return List.of();
    }
    private static List<String> filter(Collection<String> values,String prefix) {return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).distinct().sorted().toList();}
}
