package ru.neverland.townyideologies.command;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.townyideologies.NeverLandTownyIdeologies;
import ru.neverland.townyideologies.integration.TownyHook;
import ru.neverland.townyideologies.model.IdeologyDefinition;
import ru.neverland.townyideologies.model.TownIdeology;
import ru.neverland.townyideologies.service.IdeologyRegistry;
import ru.neverland.townyideologies.service.IdeologyService;
import ru.neverland.townyideologies.service.MessageService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final NeverLandTownyIdeologies plugin;
    private final TownyHook towny;
    private final IdeologyRegistry registry;
    private final IdeologyService ideologies;
    private final MessageService messages;

    public AdminCommand(NeverLandTownyIdeologies plugin, TownyHook towny, IdeologyRegistry registry,
                        IdeologyService ideologies, MessageService messages) {
        this.plugin = plugin;
        this.towny = towny;
        this.registry = registry;
        this.ideologies = ideologies;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyIdeologies"))return true;
        if (!sender.hasPermission("neverlandtownyideologies.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if(args.length==2&&args[0].equalsIgnoreCase("reform")){
            var r=ru.neverland.townyideologies.reforms.ReformBook.require(ideologies.data().state(),java.util.UUID.fromString(args[1]));sender.sendMessage("§e"+r.id()+" · "+r.target().townName()+" · "+ideologies.status(r));sender.sendMessage("§7Казна: "+r.cents()/100.0+"; цель "+r.target().ideologyId()+" "+r.target().level());
            for(var attempt:r.attempts())sender.sendMessage("§7[ideology-reform:"+attempt.id()+"] "+attempt.result()+"; actor="+attempt.actor());return true;
        }
        if(args.length>=5&&args[0].equalsIgnoreCase("reconcile")){
            if(!java.util.Set.of("applied","not-applied").contains(args[2])||!args[3].equals("confirm"))throw new IllegalArgumentException("reconcile <ID> <applied|not-applied> confirm <причина>");
            String reason=String.join(" ",java.util.Arrays.copyOfRange(args,4,args.length));if(reason.trim().length()<8)throw new IllegalArgumentException("Укажите основание сверки не короче 8 символов");
            ideologies.resolve(java.util.UUID.fromString(args[1]),args[2].equals("applied"),sender.getName()+": "+reason).whenComplete((text,error)->reply(sender,error==null?text:"Результат не сохранён; проверьте журнал"));return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPlugin();
            messages.send(sender, "reload");
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("info")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) return notFound(sender);
            Town town = towny.town(target);
            if (town == null) {
                messages.send(sender, "town-required");
                return true;
            }
            TownIdeology current = ideologies.get(town).orElse(null);
            if (current == null) {
                messages.send(sender, "admin-empty", Map.of("town", town.getName()));
            } else {
                String name = registry.find(current.ideologyId()).map(IdeologyDefinition::name).orElse(current.ideologyId());
                messages.send(sender, "admin-info", Map.of("town", town.getName(), "ideology", name,
                        "level", Integer.toString(current.level())));
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("reset")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) return notFound(sender);
            Town town = towny.town(target);
            if (town == null) {
                messages.send(sender, "town-required");
                return true;
            }
            String townName=town.getName();ideologies.adminReset(town).whenComplete((saved,error)->reply(sender,error==null?"Идеология города сброшена: "+townName:"Сброс не сохранён; прежнее состояние сохранено"));
            return true;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("set")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) return notFound(sender);
            IdeologyDefinition definition = registry.find(args[2]).orElse(null);
            if (definition == null) {
                messages.send(sender, "definition-missing");
                return true;
            }
            int level;
            try {
                level = Integer.parseInt(args[3]);
            } catch (NumberFormatException exception) {
                messages.send(sender, "admin-usage");
                return true;
            }
            Town town = towny.town(target);
            if (town == null) {
                messages.send(sender, "town-required");
                return true;
            }
            String townName=town.getName();ideologies.adminSet(town, definition.id(), level).whenComplete((saved,error)->reply(sender,error==null?"Идеология города сохранена: "+townName:"Изменение не сохранено; прежнее состояние сохранено"));
            return true;
        }
        messages.send(sender, "admin-usage");
        return true;
    } catch(Exception error){sender.sendMessage("§c"+(error.getMessage()==null?"Действие не выполнено":error.getMessage()));return true;} }
    private void reply(CommandSender sender,String text){if(plugin.isEnabled())ru.neverland.core.SuiteScheduler.reply(plugin,sender,()->sender.sendMessage("§a"+text));}

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if(!sender.hasPermission("neverlandtownyideologies.admin"))return List.of();
        if (args.length == 1) return filter(List.of("reload", "info", "set", "reset", "reform", "reconcile"), args[0]);
        if(args.length==2&&java.util.Set.of("reform","reconcile").contains(args[0]))return filter(ideologies.data().state().reforms().keySet().stream().map(java.util.UUID::toString).toList(),args[1]);
        if(args.length==3&&args[0].equals("reconcile"))return filter(List.of("applied","not-applied"),args[2]);
        if(args.length==4&&args[0].equals("reconcile"))return filter(List.of("confirm"),args[3]);
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return filter(registry.all().stream().map(IdeologyDefinition::id).toList(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("set")) return filter(List.of("1", "2", "3", "4", "5"), args[3]);
        return List.of();
    } }

    private boolean notFound(CommandSender sender) {
        messages.send(sender, "player-not-found");
        return true;
    }

    private List<String> filter(List<String> values, String prefix) {
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.regionMatches(true, 0, prefix, 0, prefix.length())) result.add(value);
        return result;
    }
}
