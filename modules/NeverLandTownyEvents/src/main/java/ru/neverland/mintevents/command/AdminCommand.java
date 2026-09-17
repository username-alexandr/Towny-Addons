package ru.neverland.mintevents.command;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.*;
import ru.neverland.mintevents.MintTownyEvents;
import ru.neverland.mintevents.integration.TownyHook;
import ru.neverland.mintevents.model.ActiveEvent;
import ru.neverland.mintevents.model.EventDefinition;
import ru.neverland.mintevents.service.EventService;
import ru.neverland.mintevents.service.MessageService;
import ru.neverland.mintevents.util.ColorUtil;
import ru.neverland.mintevents.util.TimeUtil;
import java.util.*;

/** Town names may contain spaces; numeric parameters precede the complete town name. */
public final class AdminCommand implements CommandExecutor, TabCompleter {
    public static final Set<String> ACTIONS = Set.of("start", "stop", "cancel", "restart", "pause", "resume", "success", "fail", "extend", "shield", "status", "list", "raidwave", "reload");
    private final MintTownyEvents plugin;
    private final TownyHook towny;
    private final EventService events;
    private final MessageService messages;
    public AdminCommand(MintTownyEvents plugin, TownyHook towny, EventService events, MessageService messages) {
        this.plugin=plugin; this.towny=towny; this.events=events; this.messages=messages;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!sender.hasPermission("mintevents.admin")) { messages.send(sender,"no-permission"); return true; }
        if(args.length == 0) { help(sender); return true; }
        String action=args[0].toLowerCase(Locale.ROOT);
        try {
            if(action.equals("reload") && args.length==1) { plugin.reloadPlugin(); messages.send(sender,"reload"); return true; }
            if(action.equals("list") && args.length==1) {
                sender.sendMessage("§bГородские события:");
                for(Town town : towny.towns()) if(events.active(town.getUUID()) != null) status(sender,town);
                return true;
            }
            if(!ACTIONS.contains(action) || args.length<2) { help(sender); return true; }
            if(action.equals("start")) {
                if(args.length<3) { help(sender); return true; }
                EventDefinition definition=events.registry().get(args[1]);
                if(definition==null) throw new IllegalArgumentException("Неизвестное событие: "+args[1]);
                boolean force=args[args.length-1].equalsIgnoreCase("--force");
                Town town=city(args,2,args.length-(force?1:0));
                if(!events.startEvent(town,definition,force)) throw new IllegalArgumentException("Запуск отклонён: активное событие, щит новичка или незавершённый ремонт пожара. Статус: /townyevents status "+town.getName());
                sender.sendMessage("§aСобытие запущено: §f"+ColorUtil.strip(definition.name())+" §7· "+town.getName());
            } else if(action.equals("extend") || action.equals("shield")) {
                if(args.length<3) { help(sender); return true; }
                long amount=Long.parseLong(args[1]); Town town=city(args,2,args.length);
                if(action.equals("shield")) events.shield(town,amount);
                else if(!events.extend(town,amount)) throw new IllegalArgumentException("Нет активного события");
                status(sender,town);
            } else {
                int end=args.length;
                if(action.equals("stop") && end>2 && Set.of("success","fail","cancel").contains(args[end-1].toLowerCase(Locale.ROOT))) action=args[--end].toLowerCase(Locale.ROOT);
                Town town=city(args,1,end);
                if(action.equals("status")) { status(sender,town); return true; }
                if(action.equals("raidwave")) {
                    int count=events.forceRaidWave(town);
                    if(count<=0) throw new IllegalArgumentException("Волна недоступна: проверьте тип события, паузу и территорию города");
                    sender.sendMessage("§aСоздано врагов: "+count);
                } else {
                    boolean changed=switch(action) {
                        case "stop", "cancel" -> events.cancel(town);
                        case "restart" -> events.restart(town);
                        case "pause" -> events.pause(town,true);
                        case "resume" -> events.pause(town,false);
                        case "success" -> events.resolve(town,true);
                        case "fail" -> events.resolve(town,false);
                        default -> false;
                    };
                    if(!changed) throw new IllegalArgumentException("Нет подходящего события или оно уже в этом состоянии");
                    sender.sendMessage("§aВыполнено: §f"+action+" §7· "+town.getName()+
                        (action.equals("stop")||action.equals("cancel") ? " §eБез поражения и штрафов." : ""));
                }
            }
            plugin.getLogger().info("Администратор "+sender.getName()+": "+String.join(" ",args));
        } catch(IllegalArgumentException e) { sender.sendMessage("§e"+e.getMessage()); }
        catch(RuntimeException e) {
            sender.sendMessage("§cОперация не завершена: проверьте журнал сервера и доступность хранилища.");
            plugin.getLogger().log(java.util.logging.Level.SEVERE,"Административная команда события",e);
        }
        return true;
    }
    private Town city(String[] args,int from,int end) {
        if(from>=end) throw new IllegalArgumentException("Укажите город");
        String name=String.join(" ",Arrays.copyOfRange(args,from,end));
        Town town=towny.town(name); if(town==null) throw new IllegalArgumentException("Город не найден: "+name); return town;
    }
    private void status(CommandSender sender,Town town) {
        ActiveEvent event=events.active(town.getUUID());
        sender.sendMessage("§b"+town.getName()+" §7· Щит новичка: §f"+TimeUtil.format((events.shieldRemainingMillis(town.getUUID())+999)/1000));
        sender.sendMessage(event==null ? "§7Активного события нет." : "§f"+event.eventId()+" §7· "+(event.paused()?"§eПауза":"§aАктивно")+" §7· "+event.progress()+"/"+event.goal()+" · осталось "+TimeUtil.format(event.secondsLeft(System.currentTimeMillis())));
    }
    private void help(CommandSender sender) {
        // Code-owned usage also upgrades servers with an existing messages.yml.
        sender.sendMessage("§bСобытия — команды администратора (/t events или /townyevents)");
        sender.sendMessage("§fstart <событие> <город> [--force] §7— запуск; force обходит щит");
        sender.sendMessage("§fstop / cancel <город> §7— отмена без поражения и штрафов");
        sender.sendMessage("§frestart <город> §7— новый таймер с сохранением прогресса");
        sender.sendMessage("§fpause / resume <город> §7— заморозить / продолжить");
        sender.sendMessage("§fsuccess / fail <город> §7— явно завершить победой / поражением");
        sender.sendMessage("§fextend <минуты> <город> §7— продлить на 1–10080 минут");
        sender.sendMessage("§fshield <часы> <город> §7— 0 снять; 1–720 выдать щит");
        sender.sendMessage("§fstatus <город> §7· §flist §7· §fraidwave <город> §7· §freload");
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if(!sender.hasPermission("mintevents.admin") || args.length==0) return List.of();
        List<String> options=List.of(); String action=args[0].toLowerCase(Locale.ROOT);
        if(args.length==1) options=ACTIONS.stream().sorted().toList();
        else if(args.length==2 && action.equals("start")) options=events.registry().all().stream().map(EventDefinition::id).toList();
        else if(args.length==2 && (action.equals("extend") || action.equals("shield"))) options=action.equals("shield")?List.of("0","24","48"):List.of("15","30","60");
        else if(args.length==2 && !Set.of("start","reload","list").contains(action) || args.length==3 && Set.of("start","extend","shield").contains(action)) options=towny.towns().stream().map(Town::getName).toList();
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);
        return options.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
