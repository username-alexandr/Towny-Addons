package ru.neverland.townyjustice;
import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.MenuStyle;
public final class JusticeCommand implements CommandExecutor,TabCompleter {
    private final NeverLandTownyJustice plugin;private final JusticeService service;private final JusticeMenu menu;
    public JusticeCommand(NeverLandTownyJustice p,JusticeService s,JusticeMenu m){plugin=p;service=s;menu=m;}
    private static void length(String[] args,int count,String syntax){if(args.length!=count)throw new IllegalArgumentException(syntax);}
    private boolean manager(Player p){try{return service.manager(p,service.ownTown(p).getUUID());}catch(IllegalArgumentException e){return false;}}
    private UUID caseId(Player p,String key){var matches=service.repository().state().cases().values().stream().filter(c->service.readable(p,c)&&key.length()>=8&&c.id().toString().startsWith(key.toLowerCase(Locale.ROOT))).map(JusticeCase::id).toList();if(matches.size()!=1)throw new IllegalArgumentException("Укажите полный ID дела или однозначные первые 8 символов");return matches.get(0);}
    private UUID resident(String name){var r=TownyAPI.getInstance().getResident(name);if(r==null)throw new IllegalArgumentException("Игрок не найден в Towny");return r.getUUID();}
    static void say(CommandSender s,String text){s.sendMessage(MenuStyle.decode(text));}
    @Override public boolean onCommand(CommandSender s,Command command,String label,String[] args){try{
        String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
        if(Set.of("reload","payments","resolve").contains(action)){
            if(!s.hasPermission("neverlandtownyjustice.admin"))throw new IllegalArgumentException("Недостаточно прав");
            if(action.equals("reload")){length(args,1,"/justice reload");plugin.reloadJustice();say(s,"&aНастройки суда обновлены.");}
            else if(action.equals("payments")){length(args,1,"/justice payments");say(s,"&eНезавершённые платежи — ID, назначение, сумма, состояние:");service.repository().state().payments().values().stream().filter(p->p.step()!=JusticePayment.Step.COMPLETE&&p.step()!=JusticePayment.Step.CANCELLED).forEach(p->say(s,"&f"+p.id()+" | "+p.kind()+" | "+JusticeMenu.money(p.amount())+" | "+p.step()+" | город "+p.town()+" | игрок "+p.resident()));say(s,"&7Сверьте журнал банка по [justice:ID] перед resolve.");}
            else{length(args,3,"/justice resolve <ID платежа> <решение после сверки банка>");service.resolve(UUID.fromString(args[1]),args[2]);say(s,"&aРезультат сверки сохранён.");}return true;
        }
        if(!s.hasPermission("neverlandtownyjustice.use"))throw new IllegalArgumentException("Недостаточно прав");
        if(action.equals("help")){say(s,"&eГородской суд и розыск");say(s,"&b/justice fines &f— мои штрафы и оплата");say(s,"&b/justice wanted &f— действующий розыск и награды");say(s,"&b/justice case <ID> &f— дело; &bpay <ID> &f— оплатить свой штраф");say(s,"&b/justice capture <ID> &f— задержать рядом, в городе-инициаторе");if(s instanceof Player p&&manager(p)){say(s,"&b/justice fine <игрок> <сумма> <причина> &f— штраф");say(s,"&b/justice warrant <игрок> <награда> <часы> <причина> &f— розыск");say(s,"&b/justice cancel <ID> &f— отмена; &brelease <ID> &f— освобождение");say(s,"&b/justice prison <create|bind|unbind> <имя> &f— тюрьма");say(s,"&b/justice cases &f— дела города");}return true;}
        if(!(s instanceof Player p))throw new IllegalArgumentException("Команда доступна в игре");
        switch(action){
            case "menu","fines","wanted","cases"->{length(args,args.length==0?0:1,"/justice "+action);menu.open(p,action,0,MenuStyle.previousMenu(p));}
            case "case"->{length(args,2,"/justice case <ID>");menu.detail(p,caseId(p,args[1]),MenuStyle.previousMenu(p));}
            case "fine","warrant"->{int start=action.equals("fine")?3:4;if(args.length<=start)throw new IllegalArgumentException(action.equals("fine")?"/justice fine <игрок> <сумма> <причина>":"/justice warrant <игрок> <награда> <часы> <причина>");UUID id=service.issue(p,action.equals("fine")?JusticeCase.Kind.FINE:JusticeCase.Kind.WARRANT,resident(args[1]),JusticeSettings.cents(args[2]),action.equals("fine")?0:Integer.parseInt(args[3]),String.join(" ",Arrays.copyOfRange(args,start,args.length)));say(s,"&aДело сохранено: &f"+id);menu.detail(p,id,MenuStyle.previousMenu(p));}
            case "pay","cancel","capture","release"->{length(args,2,"/justice "+action+" <ID>");UUID id=caseId(p,args[1]);switch(action){case "pay"->service.pay(p,id);case "cancel"->service.cancel(p,id);case "capture"->service.capture(p,id);case "release"->service.release(p,id);}say(s,"&eСостояние дела: &f"+JusticeMenu.phase(service.get(id).phase()));}
            case "prison"->{length(args,3,"/justice prison <create|bind|unbind> <имя>");switch(args[1]){case "create","bind"->{UUID id=service.createPrison(p,args[2],args[1].equals("bind"));say(s,"&aТюрьма подключена: &f"+id);}case "unbind"->{service.unbind(p,args[2]);say(s,"&aТюрьма отключена от суда. Участок Towny сохранён.");}default->throw new IllegalArgumentException("/justice prison <create|bind|unbind> <имя>");}}
            default->throw new IllegalArgumentException("Справка: /justice help");
        }
    }catch(Exception ex){say(s,"&c"+(ex instanceof IllegalArgumentException?ex.getMessage():"Операция ожидает проверки; откройте дело или обратитесь к администратору"));if(!(ex instanceof IllegalArgumentException))plugin.getLogger().log(java.util.logging.Level.WARNING,"Команда суда",ex);}return true;}
    @Override public List<String> onTabComplete(CommandSender s,Command command,String alias,String[] args){var choices=new ArrayList<String>();if(args.length==1){if(s.hasPermission("neverlandtownyjustice.use")){choices.addAll(List.of("menu","fines","wanted","case","pay","help"));if(s.hasPermission("neverlandtownyjustice.capture"))choices.add("capture");if(s instanceof Player p&&manager(p))choices.addAll(List.of("cases","fine","warrant","cancel","release","prison"));}if(s.hasPermission("neverlandtownyjustice.admin"))choices.addAll(List.of("reload","payments","resolve"));}
        else if(args.length==2&&s instanceof Player p&&s.hasPermission("neverlandtownyjustice.use")){if(Set.of("case","pay","capture","cancel","release").contains(args[0]))service.repository().state().cases().values().stream().filter(c->service.readable(p,c)).forEach(c->choices.add(c.id().toString()));if(Set.of("fine","warrant").contains(args[0])&&manager(p))TownyAPI.getInstance().getResidents().forEach(r->choices.add(r.getName()));if(args[0].equals("prison")&&manager(p))choices.addAll(List.of("create","bind","unbind"));}
        else if(args.length==3&&args[0].equals("resolve")&&s.hasPermission("neverlandtownyjustice.admin"))choices.addAll(List.of("debit-applied","debit-not-applied","credit-applied","credit-not-applied"));String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return choices.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();}
}
