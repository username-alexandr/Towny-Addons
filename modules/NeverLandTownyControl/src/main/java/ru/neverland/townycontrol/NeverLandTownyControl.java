package ru.neverland.townycontrol;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.event.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class NeverLandTownyControl extends JavaPlugin implements CommandExecutor,TabCompleter,Listener {
    private ModuleGraph graph;
    private Path stateFile;
    private boolean applying;
    @Override public void onEnable() {
        try(var stream=getResource("module-dependencies.yml")) {
            if(stream==null)throw new IOException("Нет схемы зависимостей");
            var y=new YamlConfiguration();y.load(new InputStreamReader(stream,StandardCharsets.UTF_8));
            if(SafeYaml.intValue(y,"schema")!=1)throw new IOException("Неверная схема зависимостей");
            var map=new HashMap<String,Set<String>>();var root=SafeYaml.section(y,"modules");
            for(String name:root.getKeys(false))map.put(name,Set.copyOf(SafeYaml.strings(root,name)));
            graph=new ModuleGraph(map);graph.modules().forEach(ModuleTimers::rules);
            stateFile=ModulePauseStore.file(getDataFolder().toPath().getParent());
            var state=ModulePauseStore.load(stateFile);
            if(!graph.modules().containsAll(state.modules().keySet()))throw new IOException("В состоянии есть неизвестные модули");
            var blocked=graph.closure(state.requests());
            ModulePauseStore.save(stateFile,ModulePauseStore.request(state,state.requests(),blocked,System.currentTimeMillis()));
            getCommand("nltmodules").setExecutor(this);getCommand("nltmodules").setTabCompleter(this);
            new AdminRouter(this,graph).register();
            getServer().getPluginManager().registerEvents(this,this);
            getLogger().info("Управление модулями: "+graph.modules().size()+"; отключено с зависимостями: "+blocked.size());
        } catch(Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE,"Управление аддонами не загружено; проверьте modules.yml",e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!sender.hasPermission("neverlandtownycontrol.admin")){sender.sendMessage("§cНет прав.");return true;}
        try {
            if(graph==null)throw new IllegalStateException("Управление недоступно");
            var state=ModulePauseStore.load(stateFile);
            String action=args.length==0?"list":args[0].toLowerCase(Locale.ROOT);
            if(action.equals("list")&&args.length<=1) {
                sender.sendMessage("§bАддоны — состояние:");
                graph.modules().stream().sorted().forEach(name->sender.sendMessage(describe(name,state)));return true;
            }
            if(args.length!=2||!Set.of("status","plan","disable","enable").contains(action)) {
                sender.sendMessage("§f/nltmodules list | status <модуль> | plan <модуль> | disable <модуль|all> | enable <модуль|all>");return true;
            }
            Set<String> selected=args[1].equalsIgnoreCase("all")?graph.modules():Set.of(graph.resolve(args[1]));
            if(action.equals("status")){selected.stream().sorted().forEach(n->sender.sendMessage(describe(n,state)));return true;}
            if(action.equals("plan")){sender.sendMessage("§eОстановятся вместе с зависимыми аддонами: §f"+shortNames(graph.closure(selected)));return true;}
            var requested=new HashSet<>(state.requests());
            if(action.equals("disable"))requested.addAll(selected);else requested.removeAll(selected);
            Set<String> blocked=graph.closure(requested);
            // Persist the whole group before any listener, task or service is unregistered.
            ModulePauseStore.save(stateFile,ModulePauseStore.request(state,requested,blocked,System.currentTimeMillis()));
            if(action.equals("disable")) {
                stop(blocked);
                sender.sendMessage("§eОтключены с зависимостями: §f"+shortNames(blocked));
                sender.sendMessage("§7Прогресс, денежные журналы и оставшееся время сохранены. Провалы за время отключения не начисляются.");
            } else {
                sender.sendMessage("§aЗапуск разрешён после полного перезапуска сервера.");
                if(!blocked.isEmpty())sender.sendMessage("§eОстаются отключёнными: §f"+shortNames(blocked)+" §7(их зависимость ещё выключена)");
            }
            getLogger().warning(sender.getName()+": "+String.join(" ",args)+"; disabled="+shortNames(blocked));
        } catch(Exception e) {sender.sendMessage("§cОперация остановлена: "+e.getMessage());getLogger().log(java.util.logging.Level.WARNING,"Управление модулями",e);}
        return true;
    }
    private void stop(Set<String> names) { stop(names, ""); }
    private void stop(Set<String> names, String originating) {
        applying=true;
        try {
            // Close open UI/storage while its original listeners can still persist the contents.
            for(var player:List.copyOf(Bukkit.getOnlinePlayers()))player.closeInventory();
            List<Plugin> plugins=new ArrayList<>();for(Plugin p:getServer().getPluginManager().getPlugins())if(names.contains(p.getName())&&!p.getName().equals(originating)&&p.isEnabled())plugins.add(p);
            Collections.reverse(plugins);
            for(Plugin p:plugins)getServer().getPluginManager().disablePlugin(p);
        } finally {applying=false;}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void onDisable(PluginDisableEvent event) {
        if(applying||graph==null||!isEnabled()||!graph.modules().contains(event.getPlugin().getName())||Bukkit.isStopping())return;
        String name=event.getPlugin().getName();
        try {
            var state=ModulePauseStore.load(stateFile);var entry=state.modules().get(name);
            if(entry!=null&&entry.disabled())return;
            var requested=new HashSet<>(state.requests());requested.add(name);var blocked=graph.closure(requested);
            ModulePauseStore.save(stateFile,ModulePauseStore.request(state,requested,blocked,System.currentTimeMillis()));
            // Disable is synchronous: no dependent economy tick is allowed between these transitions.
            stop(blocked,name);
            getLogger().warning("Внешнее отключение "+name+": зависимые аддоны приостановлены без списаний за простой.");
        } catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"Не удалось сохранить защитную паузу зависимостей",e);stop(graph.closure(Set.of(name)),name);}
    }
    private boolean dormant(org.bukkit.entity.Entity entity) {
        if(entity instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof org.bukkit.entity.Entity owner) entity=owner;
        if(entity==null)return false;
        for(var key:entity.getPersistentDataContainer().getKeys()) {
            String module=switch(key.getNamespace()) {
                case "neverlandtownyevents","minttownyevents" -> "NeverLandTownyEvents";
                case "neverlandtownyexpeditions","minttownyexpeditions" -> "NeverLandTownyExpeditions";
                default -> "";
            };
            if(!module.isEmpty()&&!getServer().getPluginManager().isPluginEnabled(module))return true;
        }
        return false;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(org.bukkit.event.entity.EntityDamageEvent e) {
        if(dormant(e.getEntity()) || e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent hit && dormant(hit.getDamager()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void target(org.bukkit.event.entity.EntityTargetLivingEntityEvent e) {if(dormant(e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explode(org.bukkit.event.entity.EntityExplodeEvent e) {if(dormant(e.getEntity()))e.setCancelled(true);}
    @EventHandler public void loaded(org.bukkit.event.world.EntitiesLoadEvent e) {
        if(getServer().getPluginManager().isPluginEnabled("NeverLandTownyEvents"))return;
        for(var entity:e.getEntities())for(var key:entity.getPersistentDataContainer().getKeys())if(key.getKey().equals("raid_town")&&Set.of("neverlandtownyevents","minttownyevents").contains(key.getNamespace())){entity.remove();break;}
    }
    private String describe(String name,ModulePauseStore.State state) {
        var p=getServer().getPluginManager().getPlugin(name);var e=state.modules().get(name);
        String status=e!=null&&e.disabled()?"§eОтключён":p!=null&&p.isEnabled()?"§aРаботает":e!=null&&e.pausedAt()>0?"§bЖдёт перезапуска":p==null?"§7Не установлен":"§cНе запущен";
        return "§f"+name.substring("NeverLandTowny".length())+" §7— "+status+(state.requests().contains(name)?" §7(по запросу)":"");
    }
    private static String shortNames(Set<String> names){return String.join(", ",names.stream().map(n->n.substring("NeverLandTowny".length())).sorted().toList());}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if(!sender.hasPermission("neverlandtownycontrol.admin")||graph==null)return List.of();
        List<String> options=args.length==1?List.of("list","status","plan","disable","enable"):args.length==2?java.util.stream.Stream.concat(java.util.stream.Stream.of("all"),graph.modules().stream().map(n->n.substring("NeverLandTowny".length()))).sorted().toList():List.of();
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return options.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
