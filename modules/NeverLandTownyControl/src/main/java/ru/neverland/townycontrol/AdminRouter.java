package ru.neverland.townycontrol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.SafeYaml;

/** A discoverable entry point that retains the owning plugin's permissions and executor. */
final class AdminRouter implements TabExecutor {
    record Entry(String module,String command,String permission,boolean activities) { String shortName(){return module.substring("NeverLandTowny".length());} }
    private final JavaPlugin plugin;
    private final ModuleGraph graph;
    private final Map<String,Entry> entries=new TreeMap<>();
    private AdminMenu menu;
    AdminRouter(JavaPlugin plugin,ModuleGraph graph)throws Exception {
        this.plugin=plugin;this.graph=graph;
        try(var stream=plugin.getResource("admin-modules.yml")) {
            if(stream==null)throw new IOException("Нет справочника аддонов");
            var yaml=new YamlConfiguration();yaml.load(new InputStreamReader(stream,StandardCharsets.UTF_8));
            var root=SafeYaml.section(yaml,"modules");
            for(String name:root.getKeys(false)) {
                var v=SafeYaml.section(root,name);String module=graph.resolve(name);
                entries.put(name.toLowerCase(Locale.ROOT),new Entry(module,SafeYaml.text(v,"command"),SafeYaml.text(v,"permission"),SafeYaml.booleanValue(v,"activities")));
            }
            if(entries.size()!=graph.modules().size())throw new IOException("Неполный справочник аддонов");
        }
    }
    void register(AdminMenu menu){this.menu=menu;var command=Objects.requireNonNull(plugin.getCommand("nltadmin"));command.setExecutor(this);command.setTabCompleter(this);}
    private boolean allowed(CommandSender sender){return AdminAccess.admin(sender);}
    Collection<Entry> entries(){return List.copyOf(entries.values());}
    Entry entry(String name){String full=graph.resolve(name);return entries.values().stream().filter(e->e.module().equals(full)).findFirst().orElseThrow();}
    PluginCommand nativeCommand(Entry e) {
        var owner=plugin.getServer().getPluginManager().getPlugin(e.module());
        if(!(owner instanceof JavaPlugin java)||!owner.isEnabled())throw new IllegalArgumentException("Аддон недоступен. /nltmodules status "+e.module());
        return Objects.requireNonNull(java.getCommand(e.command()),"Команда аддона недоступна");
    }
    void describe(CommandSender sender,Entry e) {
        String name=e.module().substring("NeverLandTowny".length());
        sender.sendMessage("§b"+name+" §7— /"+e.command()+"; право: "+e.permission());
        sender.sendMessage("§f/nltadmin "+name+" module <status|plan|disable|enable>");
        sender.sendMessage("§f/nltadmin "+name+" native <штатные аргументы команды>");
        if(e.activities())sender.sendMessage("§f/nltadmin "+name+" list | status|pause|resume|restart|cancel <ID> | extend <минуты> <ID> §7— list показывает поддерживаемые действия");
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!allowed(sender)){sender.sendMessage("§cНет прав.");return true;}
        try {
            if((args.length==0||args.length==1&&Set.of("menu","logs").contains(args[0].toLowerCase(Locale.ROOT)))&&sender instanceof org.bukkit.entity.Player player){
                if(args.length==1&&args[0].equalsIgnoreCase("logs"))menu.logs(player);else menu.home(player);return true;
            }
            if(args.length==0||args.length==1&&args[0].equalsIgnoreCase("list")) {
                sender.sendMessage("§bАддоны — /nltadmin <модуль> для команд:");
                for(var e:entries.values())sender.sendMessage("§f"+e.module().substring("NeverLandTowny".length())+" §7| /"+e.command()+(e.activities()?" | управление задачами":" | штатное управление")+" | module disable/enable");
                return true;
            }
            var e=entry(args[0]);if(args.length==1){describe(sender,e);return true;}
            String action=args[1].toLowerCase(Locale.ROOT);
            if(action.equals("module")) {
                if(args.length!=3||!Set.of("status","plan","disable","enable").contains(args[2].toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("module <status|plan|disable|enable>");
                plugin.getCommand("nltmodules").execute(sender,"nltmodules",new String[]{args[2],e.module()});return true;
            }
            if(!AdminAccess.has(sender,e.permission()))throw new IllegalArgumentException("Нет разрешения "+e.permission());
            var nativeCommand=nativeCommand(e);
            if(action.equals("native"))nativeCommand.execute(sender,e.command(),Arrays.copyOfRange(args,2,args.length));
            else if(e.activities()) {
                String[] nested=Arrays.copyOfRange(args,0,args.length);nested[0]="admin";
                nativeCommand.execute(sender,e.command(),nested);
            } else describe(sender,e);
        }catch(Exception ex){sender.sendMessage("§cОперация остановлена: "+ex.getMessage());}
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if(!allowed(sender))return List.of();
        try {
            if(args.length<=1){var names=new ArrayList<>(entries.keySet());names.addAll(List.of("list","menu","logs"));return filter(names,args.length==0?"":args[0]);}
            var e=entry(args[0]);
            if(args.length==2){var actions=new ArrayList<>(List.of("module"));if(AdminAccess.has(sender,e.permission())){actions.add("native");if(e.activities())actions.addAll(List.of("list","help","status","pause","resume","restart","extend","cancel"));}return filter(actions,args[1]);}
            if(args[1].equalsIgnoreCase("module"))return args.length==3?filter(List.of("status","plan","disable","enable"),args[2]):List.of();
            if(!AdminAccess.has(sender,e.permission()))return List.of();
            var direct=nativeCommand(e);String[] nested;
            if(args[1].equalsIgnoreCase("native"))nested=Arrays.copyOfRange(args,2,args.length);
            else if(e.activities()){nested=Arrays.copyOfRange(args,0,args.length);nested[0]="admin";}
            else return List.of();
            return direct.tabComplete(sender,e.command(),nested);
        }catch(Exception ignored){return List.of();}
    }
    private static List<String> filter(Collection<String> values,String prefix){return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).sorted().toList();}
}
