package ru.neverland.townypopulation.command;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.neverland.townypopulation.NeverLandTownyPopulation;
import ru.neverland.townypopulation.gui.PopulationMenu;
import ru.neverland.townypopulation.service.*;
import java.util.*;

public final class PopulationCommand implements CommandExecutor, TabCompleter {
    private final NeverLandTownyPopulation plugin;
    private final PopulationService service;
    private final PopulationMenu menu;
    private final Messages messages;
    public PopulationCommand(NeverLandTownyPopulation plugin, PopulationService service, PopulationMenu menu, Messages messages) {
        this.plugin=plugin; this.service=service; this.menu=menu; this.messages=messages;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
        if(action.equals("reload") || action.equals("set")) {
            if(!sender.hasPermission("neverlandtownypopulation.admin")) { messages.send(sender,"no-permission"); return true; }
            if(action.equals("reload")) { messages.send(sender,plugin.reloadPopulation()?"reloaded":"reload-failed"); return true; }
            if(args.length!=3) { messages.tell(sender,"/townypopulation set <город> <количество>"); return true; }
            Town town=TownyAPI.getInstance().getTown(args[1]);
            if(town==null) { messages.send(sender,"unknown-town"); return true; }
            try {
                int amount=Integer.parseInt(args[2]);
                if(!service.setPopulation(town.getUUID(),amount)) throw new NumberFormatException();
                messages.send(sender,"set-success",Map.of("town",town.getName(),"amount",String.valueOf(amount)));
                plugin.getLogger().info(sender.getName()+" установил население "+town.getName()+": "+amount);
            } catch(NumberFormatException ex) { messages.tell(sender,"Укажите целое число от 0 до "+service.settings().rules().maximum()+"."); }
            return true;
        }
        if(!sender.hasPermission("neverlandtownypopulation.use")) { messages.send(sender,"no-permission"); return true; }
        if(!Set.of("menu","info","buildings").contains(action) || args.length>2) {
            messages.tell(sender,"/t population — меню; /t population info — сводка; /t population buildings — вклад зданий."); return true;
        }
        Town town=null;
        if(args.length==2) {
            if(!sender.hasPermission("neverlandtownypopulation.admin")) { messages.send(sender,"no-permission"); return true; }
            town=TownyAPI.getInstance().getTown(args[1]);
            if(town==null) { messages.send(sender,"unknown-town"); return true; }
        } else if(sender instanceof Player player) {
            var resident=TownyAPI.getInstance().getResident(player);
            town=resident==null?null:resident.getTownOrNull();
        }
        if(town==null) { messages.send(sender,sender instanceof Player?"no-town":"players-only"); return true; }
        service.refreshView();
        var snapshot=service.population(town.getUUID()).orElse(null);
        if(snapshot==null) { messages.send(sender,"paused"); return true; }
        if(action.equals("info") || !(sender instanceof Player)) {
            var m=snapshot.metrics(); var c=snapshot.capacity();
            messages.tell(sender,"&a"+town.getName()+": &fнаселение "+snapshot.population()+" / "+c.housing()
                    +", довольство "+Messages.number(m.happiness())+"%, прирост "+Messages.number(snapshot.paused()?0:m.change())+" за цикл.");
            messages.tell(sender,"Работа: "+m.employed()+" / "+m.workforce()+"; безработица "+Messages.number(m.unemployment()*100)
                    +"%; еда "+Messages.number(m.foodCoverage()*100)+"%; вода "+Messages.number(m.waterCoverage()*100)+"%.");
            messages.tell(sender,String.join("; ",m.reasons())+".");
            if(snapshot.paused()) messages.send(sender,"paused");
        } else menu.open((Player)sender,town.getUUID(),action.equals("buildings"),0);
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options=new ArrayList<>();
        if(args.length==1) {
            if(sender.hasPermission("neverlandtownypopulation.use")) options.addAll(List.of("info","buildings"));
            if(sender.hasPermission("neverlandtownypopulation.admin")) options.addAll(List.of("reload","set"));
        } else if(args.length==2 && sender.hasPermission("neverlandtownypopulation.admin")
                && Set.of("info","buildings","set").contains(args[0].toLowerCase(Locale.ROOT)))
            TownyAPI.getInstance().getTowns().forEach(t->options.add(t.getName()));
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
