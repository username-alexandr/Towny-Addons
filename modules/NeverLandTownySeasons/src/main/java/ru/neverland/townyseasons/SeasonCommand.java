package ru.neverland.townyseasons;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.MenuStyle;

public final class SeasonCommand implements TabExecutor {
    private final NeverLandTownySeasons plugin;private final SeasonService service;private final SeasonMenu menu;
    public SeasonCommand(NeverLandTownySeasons plugin,SeasonService service,SeasonMenu menu) { this.plugin=plugin;this.service=service;this.menu=menu; }
    private static void tell(CommandSender p,String text) { p.sendMessage(MenuStyle.decode("&6[Сезоны] &f"+text)); }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        try {
            if(!sender.hasPermission("neverlandtownyseasons.use"))throw new IllegalArgumentException("Недостаточно прав");
            String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
            if(Set.of("set","resume","reload","status").contains(action)&&!sender.hasPermission("neverlandtownyseasons.admin"))throw new IllegalArgumentException("Недостаточно прав администратора");
            switch(action) {
                case "menu" -> {
                    if(!(sender instanceof Player player))throw new IllegalArgumentException("Меню доступно в игре");
                    if(args.length>2)throw new IllegalArgumentException("/tseasons menu [мир]");
                    UUID world;
                    if(args.length==2) { var w=Bukkit.getWorld(args[1]);if(w==null)throw new IllegalArgumentException("Мир не найден");world=w.getUID(); }
                    else {var r=TownyAPI.getInstance().getResident(player);var town=r==null?null:r.getTownOrNull();world=town==null?player.getWorld().getUID():service.townWorld(town.getUUID());}
                    menu.open(player,world,MenuStyle.previousMenu(player));
                }
                case "set","resume" -> {
                    if(args.length!=(action.equals("set")?3:2))throw new IllegalArgumentException("/tseasons "+action+" <мир>"+(action.equals("set")?" <spring|summer|autumn|winter>":""));
                    var w=Bukkit.getWorld(args[1]);if(w==null)throw new IllegalArgumentException("Мир не найден");
                    service.override(w.getUID(),action.equals("set")?Season.parse(args[2]):null);
                    tell(sender,action.equals("set")?"Сезон зафиксирован и сохранён. Автоматический календарь: /tseasons resume "+w.getName():"Ручная фиксация снята; используется текущая дата источника календаря.");
                }
                case "reload" -> {if(args.length!=1)throw new IllegalArgumentException("/tseasons reload");plugin.reloadSeasons();tell(sender,"Настройки обновлены.");}
                case "status" -> {
                    tell(sender,"Хранилище: "+(service.healthy()?"доступно":"ошибка")+"; источник: "+service.settings().mode());
                    for(var w:Bukkit.getWorlds())try {var v=service.calendar(w.getUID());tell(sender,w.getName()+": "+v.get("title")+" • "+v.get("source"));}catch(RuntimeException e){tell(sender,"&c"+w.getName()+": "+e.getMessage());}
                    if(service.settings().mode()==SeasonSettings.Mode.REALISTIC_SEASONS)tell(sender,"RealisticSeasons: "+service.providerStatus());
                }
                default -> {
                    tell(sender,"&e/t seasons &f— календарь и экономика мира вашего города.");
                    tell(sender,"&e/tseasons menu [мир] &f— посмотреть другой мир.");
                    if(sender.hasPermission("neverlandtownyseasons.admin")) {
                        tell(sender,"&e/tseasons set <мир> <сезон> &f— зафиксировать сезон.");
                        tell(sender,"&e/tseasons resume <мир> &f— вернуть календарь.");
                        tell(sender,"&e/tseasons status &f— диагностика; &e/tseasons reload &f— настройки.");
                    }
                }
            }
        } catch(Exception|LinkageError e) { tell(sender,"&c"+e.getMessage()); }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if(!sender.hasPermission("neverlandtownyseasons.use"))return List.of();var out=new ArrayList<String>();boolean admin=sender.hasPermission("neverlandtownyseasons.admin");
        if(args.length==1){out.addAll(List.of("menu","help"));if(admin)out.addAll(List.of("set","resume","status","reload"));}
        if(args.length==2&&(args[0].equalsIgnoreCase("menu")||admin&&Set.of("set","resume").contains(args[0].toLowerCase(Locale.ROOT))))Bukkit.getWorlds().forEach(w->out.add(w.getName()));
        if(args.length==3&&admin&&args[0].equalsIgnoreCase("set"))out.addAll(List.of("spring","summer","autumn","winter"));
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return out.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
