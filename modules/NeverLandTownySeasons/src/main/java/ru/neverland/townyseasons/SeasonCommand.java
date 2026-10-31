package ru.neverland.townyseasons;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.MenuStyle;

public final class SeasonCommand implements TabExecutor {
    private ru.neverland.townyseasons.markets.SeasonMarketsService markets;
    private ru.neverland.townyseasons.markets.SeasonMarketsMenu stockMenu;
    public void markets(ru.neverland.townyseasons.markets.SeasonMarketsService service,ru.neverland.townyseasons.markets.SeasonMarketsMenu menu){markets=service;stockMenu=menu;}
    private final NeverLandTownySeasons plugin;private final SeasonService service;private final SeasonMenu menu;
    public SeasonCommand(NeverLandTownySeasons plugin,SeasonService service,SeasonMenu menu) { this.plugin=plugin;this.service=service;this.menu=menu; }
    private static void tell(CommandSender p,String text) { p.sendMessage(MenuStyle.decode(ru.neverland.core.EditableMessages.decorate(SeasonCommand.class,"&6[Сезоны] &f",text))); }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownySeasons")){return true;}
        try {
            if(!sender.hasPermission("neverlandtownyseasons.use"))throw new IllegalArgumentException("Недостаточно прав");
            String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
            if(Set.of("set","resume","reload","status").contains(action)&&!sender.hasPermission("neverlandtownyseasons.admin"))throw new IllegalArgumentException("Недостаточно прав администратора");
            if(action.equals("admin")){admin(sender,args);return true;}
            switch(action) {
                case "stock","fair" -> {
                    if(!(sender instanceof Player player))throw new IllegalArgumentException("Меню доступно в игре");
                    if(args.length!=1||stockMenu==null)throw new IllegalArgumentException("Сезонные запасы недоступны; /tseasons stock или fair");
                    if(action.equals("stock"))stockMenu.openStock(player,MenuStyle.previousMenu(player),1);else stockMenu.openEntries(player,MenuStyle.previousMenu(player),1);
                }
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
                    tell(sender,"Запасы и ярмарка: "+(markets==null?"не подключены":markets.problem().isBlank()?"доступны":markets.problem()));
                    if(service.settings().mode()==SeasonSettings.Mode.REALISTIC_SEASONS)tell(sender,"RealisticSeasons: "+service.providerStatus());
                }
                default -> {
                    tell(sender,"&e/t seasons &f— календарь и экономика мира вашего города.");
                    tell(sender,"&e/tseasons stock &f— запас города; &e/tseasons fair &f— ярмарка и история.");
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
    } }
    private void authorized(CommandSender sender){if(!plugin.isEnabled()||(!sender.isOp()&&!sender.hasPermission("neverlandtownyseasons.admin")))throw new IllegalArgumentException("Нет доступа к сезонным резервам");if(markets==null)throw new IllegalArgumentException("Сезонные резервы недоступны");}
    public int adminMenuVersion(){return 1;}
    private static String targetName(Map<String,Object> row){return row.get("townName")+" • "+row.get("title")+" • до "+java.time.Instant.ofEpochMilli(((Number)row.get("expires")).longValue());}
    public List<Map<String,Object>> adminMenuTargets(CommandSender sender){try(var guard=ru.neverland.core.SuiteState.guard()){authorized(sender);return markets.active().stream().map(row->Map.<String,Object>of("id",row.get("id"),"name",targetName(row),"actions",List.of("status","cancel"))).toList();}}
    public String adminMenuAction(CommandSender sender,String id,String expectedName,String action,long minutes){try(var guard=ru.neverland.core.SuiteState.guard()){
        authorized(sender);if(minutes!=0||!Set.of("status","cancel").contains(action))throw new IllegalArgumentException("Доступны просмотр и отмена без потерь");
        var row=markets.active().stream().filter(r->id.equals(r.get("id"))).findFirst().orElseThrow(()->new IllegalArgumentException("Резерв уже завершён; обновите меню"));
        if(!targetName(row).equals(expectedName))throw new IllegalArgumentException("Резерв изменился; откройте его заново");
        if(action.equals("status"))return expectedName+"; передаёт: "+row.get("give")+"; получает: "+row.get("receive");
        markets.adminCancel(sender,UUID.fromString(id)).whenComplete((value,error)->{String message=error==null?"Резерв отменён, партии полностью возвращены городу.":"Отмена не подтверждена; обновите меню и проверьте журнал: "+error.getMessage();if(!plugin.isEnabled())return;if(sender instanceof Player p)ru.neverland.core.SuiteScheduler.entityLater(plugin,p,()->tell(p,message),()->{},1);else ru.neverland.core.SuiteScheduler.get().runTask(plugin,()->tell(sender,message));});
        return "Запрос отмены отправлен. Дождитесь сохранения и обновите список; полный возврат появится в журнале.";
    }}
    private void admin(CommandSender sender,String[] args){authorized(sender);if(args.length==2&&args[1].equalsIgnoreCase("list")){var rows=adminMenuTargets(sender);tell(sender,"Активных резервов: "+rows.size());rows.stream().limit(100).forEach(row->tell(sender,row.get("id")+" • "+row.get("name")));return;}if(args.length!=3||!Set.of("status","cancel").contains(args[1].toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("/tseasons admin list | status <UUID> | cancel <UUID>");var row=adminMenuTargets(sender).stream().filter(r->args[2].equals(r.get("id"))).findFirst().orElseThrow(()->new IllegalArgumentException("Резерв не найден; обновите список"));tell(sender,adminMenuAction(sender,args[2],(String)row.get("name"),args[1].toLowerCase(Locale.ROOT),0));}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if(!sender.hasPermission("neverlandtownyseasons.use"))return List.of();var out=new ArrayList<String>();boolean admin=sender.hasPermission("neverlandtownyseasons.admin");
        if(args.length==1){out.addAll(List.of("menu","stock","fair","help"));if(admin)out.addAll(List.of("set","resume","status","reload","admin"));}
        if(args.length==2&&admin&&args[0].equalsIgnoreCase("admin"))out.addAll(List.of("list","status","cancel"));
        if(args.length==3&&admin&&args[0].equalsIgnoreCase("admin")&&markets!=null)markets.active().forEach(row->out.add((String)row.get("id")));
        if(args.length==2&&(args[0].equalsIgnoreCase("menu")||admin&&Set.of("set","resume").contains(args[0].toLowerCase(Locale.ROOT))))Bukkit.getWorlds().forEach(w->out.add(w.getName()));
        if(args.length==3&&admin&&args[0].equalsIgnoreCase("set"))out.addAll(List.of("spring","summer","autumn","winter"));
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return out.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    } }
}
