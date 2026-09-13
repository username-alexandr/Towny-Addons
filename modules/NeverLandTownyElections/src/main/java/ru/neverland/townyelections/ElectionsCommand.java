package ru.neverland.townyelections;
import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.TownyAPI;

public final class ElectionsCommand implements TabExecutor {
    private final NeverLandTownyElections plugin;private final ElectionsService service;private final ElectionsMenus menus;
    public ElectionsCommand(NeverLandTownyElections plugin,ElectionsService service,ElectionsMenus menus){this.plugin=plugin;this.service=service;this.menus=menus;}
    public static void help(CommandSender s){
        s.sendMessage("§6Выборы города §8• §f/t elections");
        s.sendMessage("§e/t elections candidate <должность> §7— выдвинуть себя");
        s.sendMessage("§e/t elections withdraw §7— снять кандидатуру до голосования");
        s.sendMessage("§e/t elections vote <должность> <игроки через запятую> §7— сохранить выбор");
        s.sendMessage("§e/t elections results §7— итоги; history — последние кампании");
        if(s.hasPermission("townyelections.admin")){
            s.sendMessage("§c/elections start <город> §7— досрочный старт; reload — настройки");
            s.sendMessage("§c/elections resume <город> <UUID кампании> §7— применить сохранённые итоги после проверки");
            s.sendMessage("§c/elections abort <город> <UUID кампании> §7— закрыть кампанию, сохранив текущую власть");
        }
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        try {
            if(!sender.hasPermission("townyelections.use"))throw new IllegalArgumentException("Нет разрешения townyelections.use.");
            String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
            if(action.equals("help")){help(sender);return true;}
            if(Set.of("start","resume","abort","reload").contains(action)){
                if(!sender.hasPermission("townyelections.admin"))throw new IllegalArgumentException("Команда доступна администратору.");
                if(action.equals("reload")){if(args.length!=1)throw new IllegalArgumentException("/elections reload");plugin.reloadSettings();}
                else {
                    if(args.length!=(action.equals("start")?2:3))throw new IllegalArgumentException("/elections "+action+" <город>"+(action.equals("start")?"":" <UUID кампании>"));
                    var town=TownyAPI.getInstance().getTown(args[1]);if(town==null)throw new IllegalArgumentException("Город не найден.");
                    if(action.equals("start"))service.start(town,System.currentTimeMillis());
                    else if(action.equals("resume"))service.resume(town,UUID.fromString(args[2]));
                    else service.abort(town,UUID.fromString(args[2]),sender.getName());
                    plugin.getLogger().info("Администратор "+sender.getName()+": "+String.join(" ",args));
                }
                sender.sendMessage("§aИзменение сохранено.");return true;
            }
            if(!(sender instanceof Player p))throw new IllegalArgumentException("Откройте меню из игры; /elections help.");
            var town=ElectionsService.requireTown(p);
            switch(action){
                case "menu" -> menus.open(p,null,0,null,null);
                case "candidate" -> {if(args.length!=2)throw new IllegalArgumentException("Укажите ID должности из меню.");service.nominate(p,args[1]);p.sendMessage("§aКандидатура сохранена.");}
                case "withdraw" -> {if(args.length!=1)throw new IllegalArgumentException("/t elections withdraw");service.withdraw(p);p.sendMessage("§aКандидатура снята.");}
                case "vote" -> {
                    if(args.length!=3)throw new IllegalArgumentException("/t elections vote <должность> <игрок1,игрок2>");
                    var choices=new ArrayList<UUID>();for(String name:args[2].split(",",-1)){var r=TownyAPI.getInstance().getResident(name);if(r==null)throw new IllegalArgumentException("Игрок не найден: "+name);choices.add(r.getUUID());}
                    service.vote(p,args[1],choices);p.sendMessage("§aГолос сохранён. Повторное голосование заменяет предыдущий выбор.");
                }
                case "results" -> {
                    var e=service.state(town);p.sendMessage("§6Кампания §f"+e.id+" §7• "+ElectionsMenus.phase(e.phase));
                    if(e.results.isEmpty())p.sendMessage("§7Подсчёт ещё не проводился.");
                    for(var entry:e.results.entrySet())p.sendMessage("§e"+menus.name(entry.getKey())+" §7— "+entry.getValue()+" §f"+e.winners.getOrDefault(entry.getKey(),List.of()).stream().map(ElectionsMenus::residentName).toList());
                    if(!e.detail.isEmpty())p.sendMessage("§7"+e.detail);
                }
                case "history" -> {var e=service.state(town);if(e.history.isEmpty())p.sendMessage("§7История пока пуста.");else e.history.stream().limit(5).forEach(v->p.sendMessage("§7"+v));}
                default -> help(sender);
            }
        }catch(Exception ex){sender.sendMessage("§cВыборы: "+(ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage()));}
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args){
        var values=new ArrayList<String>();
        if(args.length==1){values.addAll(List.of("menu","candidate","withdraw","vote","results","history","help"));if(sender.hasPermission("townyelections.admin"))values.addAll(List.of("start","resume","abort","reload"));}
        else if(args.length==2 && Set.of("candidate","vote").contains(args[0]))values.addAll(service.settings().seats().keySet());
        else if(args.length==2 && sender.hasPermission("townyelections.admin") && Set.of("start","resume","abort").contains(args[0]))TownyAPI.getInstance().getTowns().forEach(t->values.add(t.getName()));
        else if(args.length==3 && args[0].equals("vote") && sender instanceof Player p)try{var town=ElectionsService.requireTown(p);service.state(town).candidates.forEach((id,r)->{if(r.equals(args[1]))values.add(ElectionsMenus.residentName(id));});}catch(Exception ignored){}
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return values.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
