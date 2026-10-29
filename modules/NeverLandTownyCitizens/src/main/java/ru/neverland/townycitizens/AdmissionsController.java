package ru.neverland.townycitizens;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import ru.neverland.core.*;
import ru.neverland.townycitizens.model.*;
import static ru.neverland.townycitizens.model.Admissions.*;

/** All menu actions run on the viewer's entity scheduler and recheck authority at confirmation. */
public final class AdmissionsController implements Listener {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Moscow"));
    private record Page(UUID viewer,Inventory back,Map<Integer,Runnable> actions) implements InventoryHolder { public Inventory getInventory(){return null;} }
    private record Offer(UUID id,long expires,String title,List<String> lines,Supplier<CompletionStage<State>> action){}
    private final NeverLandTownyCitizens plugin;
    private final CitizensService citizens;
    private final AdmissionsService admissions;
    private final Map<UUID,Offer> offers=new HashMap<>();
    public AdmissionsController(NeverLandTownyCitizens plugin,CitizensService citizens,AdmissionsService admissions){this.plugin=plugin;this.citizens=citizens;this.admissions=admissions;}
    public static String date(long time){return time==0?"Бессрочно":DATE.format(Instant.ofEpochMilli(time))+" МСК";}
    public static String phase(Phase phase){return switch(phase){case OPEN->"На рассмотрении";case APPROVED->"Одобрено";case REJECTED->"Отклонено";case WITHDRAWN->"Отозвано автором";};}
    private static void tell(CommandSender sender,String text){CitizensCommand.tell(sender,text);}
    private static String townName(UUID id){var town=TownyAPI.getInstance().getTown(id);return town==null?"Удалённый город":town.getName();}
    private static String text(String[] args,int from,String fallback){return args.length>from?String.join(" ",Arrays.copyOfRange(args,from,args.length)):fallback;}
    private Town town(Player player,String name){var town=name==null?citizens.ownTown(player.getUniqueId()):TownyAPI.getInstance().getTown(name);if(town==null)throw new IllegalArgumentException("Укажите существующий город");return town;}
    private static final Set<String> COMMANDS=Set.of("applications","apply","application","sponsor","unsponsor","approve","reject","withdraw","terms","history","confirm","cancel");
    public boolean handles(String action){return COMMANDS.contains(action);}
    public void command(CommandSender sender,String[] args){
        if(!(sender instanceof Player p))throw new IllegalArgumentException("Заявления и подтверждение доступны игроку; назначить статус можно через set");
        String action=args[0].toLowerCase(Locale.ROOT);
        switch(action){
            case "applications"->openBoard(p,args.length>1?town(p,args[1]).getUUID():null,0);
            case "application"->{if(args.length!=2)throw new IllegalArgumentException("/t citizens application <номер заявления>");openApplication(p,UUID.fromString(args[1]));}
            case "apply"->{if(args.length<5)throw new IllegalArgumentException("/t citizens apply <город> <citizen|temporary|honorary> <дни|0> <обращение>");var t=town(p,args[1]);var a=admissions.proposal(p,t,CitizenshipStatus.parse(args[2]),Integer.parseInt(args[3]),text(args,4,""));var lines=conditions(a.terms());lines.add("&7Город: &f"+t.getName());lines.add("&7Статус: &e"+a.desired().title()+(a.days()>0?" • "+a.days()+" дн.":""));lines.add("&7Вступление в город остаётся отдельным действием.");quote(p,"Подать заявление",lines,()->admissions.submit(p,a));}
            case "sponsor","unsponsor","approve","reject","withdraw"->{if(args.length<2)throw new IllegalArgumentException("Укажите номер заявления");review(p,UUID.fromString(args[1]),action,text(args,2,"Решение по заявлению"));}
            case "terms"->{if(args.length>1&&args[1].equalsIgnoreCase("set")){if(args.length<6)throw new IllegalArgumentException("/t citizens terms set <поручители 0–5> <дни на сервере> <город> <условия>");var t=town(p,args[4]);if(!citizens.manages(p,t))throw new IllegalArgumentException("Условия меняет мэр своего города или администратор");var old=admissions.terms(t.getUUID());var next=new Terms(Integer.parseInt(args[2]),Integer.parseInt(args[3]),text(args,5,""),Math.incrementExact(old.revision()));var lines=conditions(next);lines.add("&7Только для будущих заявлений.");quote(p,"Изменить условия",lines,()->admissions.policy(p,t.getUUID(),next,old.revision()));}else openTerms(p,town(p,args.length>1?args[1]:null));}
            case "history"->{UUID target=p.getUniqueId();if(args.length>1){var r=TownyAPI.getInstance().getResident(args[1]);if(r==null)throw new IllegalArgumentException("Игрок не найден");target=r.getUUID();}Town t=args.length>2?town(p,args[2]):citizens.ownTown(p.getUniqueId());openHistory(p,t==null?null:t.getUUID(),target,0);}
            case "confirm"->{if(args.length!=2)throw new IllegalArgumentException("Подтвердите действие кнопкой в открытом меню");confirm(p,UUID.fromString(args[1]));}
            case "cancel"->{offers.remove(p.getUniqueId());tell(p,"Предварительное действие отменено. Статус не менялся.");}
            default->throw new IllegalArgumentException("Неизвестное действие");
        }
    }
    private List<String> conditions(Terms terms){var lines=new ArrayList<String>();lines.add("&7Нужно поручителей: &f"+terms.sponsors());lines.add("&7На сервере от: &f"+terms.minimumDays()+" дн.");lines.add("&7Условия: &f"+terms.text());return lines;}
    private void review(Player p,UUID id,String action,String reason){
        var a=admissions.application(p,id);if(a.phase()!=Phase.OPEN)throw new IllegalArgumentException("Заявление уже рассмотрено");
        if(action.equals("sponsor")||action.equals("unsponsor")){
            boolean remove=action.equals("unsponsor");
            if(remove?!a.sponsors().containsKey(p.getUniqueId()):!p.hasPermission("neverlandtownycitizens.sponsor")||!admissions.sponsorEligible(p.getUniqueId(),a.town())||a.applicant().equals(p.getUniqueId()))throw new IllegalArgumentException(remove?"Вашего поручительства нет":"Поручиться может другой действующий гражданин города");
            quote(p,remove?"Отозвать поручительство":"Поручиться за игрока",List.of("&7Игрок: &f"+a.name(),"&7Город: &f"+townName(a.town()),"&7Поручительство не выдаёт доступ автоматически."),()->admissions.support(p,id,a.revision(),remove));return;
        }
        Phase phase=switch(action){case "approve"->Phase.APPROVED;case "reject"->Phase.REJECTED;case "withdraw"->Phase.WITHDRAWN;default->throw new IllegalArgumentException("Неизвестное решение");};
        if(phase==Phase.WITHDRAWN?!a.applicant().equals(p.getUniqueId()):!citizens.manages(p,TownyAPI.getInstance().getTown(a.town())))throw new IllegalArgumentException("У вас нет права на это решение");
        var lines=new ArrayList<>(conditions(a.terms()));lines.add("&7Игрок: &f"+a.name());lines.add("&7Новый статус: &e"+a.desired().title());lines.add("&7Решение: &f"+phase(phase));lines.add("&7Причина: &f"+reason);
        if(phase==Phase.APPROVED){var blockers=admissions.blockers(a);if(!blockers.isEmpty())throw new IllegalArgumentException(String.join("; ",blockers));lines.add("&7Срок временного статуса начнётся после одобрения.");}else lines.add("&7Текущий статус и права не меняются.");
        quote(p,phase(phase),lines,()->admissions.decide(p,id,a.revision(),phase,reason));
    }
    private void quote(Player p,String title,List<String> lines,Supplier<CompletionStage<State>> action){
        long now=System.currentTimeMillis();offers.entrySet().removeIf(e->e.getValue().expires()<now);if(offers.size()>=512&&!offers.containsKey(p.getUniqueId()))throw new IllegalArgumentException("Слишком много открытых подтверждений; повторите позже");
        var offer=new Offer(UUID.randomUUID(),now+60_000,title,List.copyOf(lines),action);offers.put(p.getUniqueId(),offer);var actions=new HashMap<Integer,Runnable>();var menu=menu(p,actions,"Подтверждение • Гражданство");
        menu.setItem(13,item(Material.PAPER,"&e"+title,lines));menu.setItem(29,item(Material.LIME_DYE,"&aПодтвердить",List.of("&7Подтверждение действует 60 секунд.","&7Права и условия будут проверены ещё раз.")));actions.put(29,()->confirm(p,offer.id()));
        menu.setItem(33,item(Material.GRAY_DYE,"&fОтмена",List.of("&7Ничего не изменять.")));actions.put(33,()->{offers.remove(p.getUniqueId(),offer);MenuStyle.returnTo(p,((Page)menu.getHolder()).back());});p.openInventory(menu);
    }
    private void confirm(Player p,UUID id){
        var offer=offers.get(p.getUniqueId());if(offer==null||!offer.id().equals(id)||System.currentTimeMillis()>offer.expires())throw new IllegalArgumentException("Подтверждение устарело. Откройте действие заново");
        offers.remove(p.getUniqueId(),offer);var window=p.getOpenInventory().getTopInventory();var result=offer.action().get();tell(p,"Сохраняем решение…");
        result.whenComplete((state,error)->{if(!plugin.isEnabled()||SuiteState.frozen(plugin.getName()))return;SuiteScheduler.player(plugin,p,player->{if(error!=null){tell(player,"&cИзменение не подтверждено: "+message(error)+". Прежние сохранённые статусы действуют.");return;}tell(player,"&aРешение сохранено.");if(player.getOpenInventory().getTopInventory()==window)openBoard(player,null,0);});});
    }
    static String message(Throwable error){while(error.getCause()!=null)error=error.getCause();return error.getMessage()==null?"обратитесь к администратору":error.getMessage();}
    private Inventory menu(Player p,Map<Integer,Runnable> actions,String title){var previous=p.getOpenInventory().getTopInventory();Inventory back=previous.getHolder() instanceof Page old?old.back():MenuStyle.previousMenu(p);var menu=MenuStyle.inventory(plugin,new Page(p.getUniqueId(),back,actions),45,title);menu.setItem(36,item(Material.ARROW,back==null?"&fЗакрыть":"&fНазад",List.of()));actions.put(36,()->MenuStyle.returnTo(p,back));return menu;}
    private static ItemStack item(Material material,String title,List<String> lore){var stack=new ItemStack(material);var meta=stack.getItemMeta();meta.setDisplayName(MenuStyle.nameLegacy(title));meta.setLore(MenuStyle.loreStrings(lore));stack.setItemMeta(meta);return stack;}
    private void suggest(Player p,String command){p.sendMessage(net.kyori.adventure.text.Component.text("Нажмите, чтобы подготовить команду: "+command).clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(command)));}
    public void openBoard(Player p,UUID town,int requested){
        var list=admissions.list(p,town);int pages=Math.max(1,(list.size()+35)/36),page=Math.min(Math.max(0,requested),pages-1);var actions=new HashMap<Integer,Runnable>();var menu=menu(p,actions,"Заявления • "+(page+1)+"/"+pages);
        for(int i=page*36;i<Math.min(list.size(),page*36+36);i++){var a=list.get(i);int slot=i-page*36;menu.setItem(slot,item(a.phase()==Phase.OPEN?Material.WRITABLE_BOOK:Material.BOOK,"&f"+a.name()+" • "+phase(a.phase()),List.of("&7Город: &f"+townName(a.town()),"&7Статус: &e"+a.desired().title(),"&7Подано: &f"+date(a.created()),"&7Нажмите: условия, поручители и решение.")));actions.put(slot,()->openApplication(p,a.id()));}
        if(list.isEmpty())menu.setItem(13,item(Material.PAPER,"&fЗаявлений пока нет",List.of("&7Подать: /t citizens apply <город>","&7<citizen|temporary|honorary> <дни|0> <обращение>")));
        if(page>0){menu.setItem(38,item(Material.ARROW,"&fПредыдущая страница",List.of()));actions.put(38,()->openBoard(p,town,page-1));}
        var own=town==null?citizens.ownTown(p.getUniqueId()):TownyAPI.getInstance().getTown(town);menu.setItem(40,item(Material.LECTERN,"&eУсловия города",List.of(own==null?"&7Укажите город: /t citizens terms <город>":"&7Посмотреть условия и подготовить заявление.")));actions.put(40,()->{if(own==null)tell(p,"/t citizens terms <город>");else openTerms(p,own);});
        menu.setItem(41,item(Material.CLOCK,"&fМоя история статусов",List.of("&7Назначения и решения с причиной.")));actions.put(41,()->openHistory(p,town,p.getUniqueId(),0));
        if(page+1<pages){menu.setItem(42,item(Material.ARROW,"&fСледующая страница",List.of()));actions.put(42,()->openBoard(p,town,page+1));}p.openInventory(menu);
    }
    public void openTerms(Player p,Town town){
        admissions.list(p,town.getUUID());var terms=admissions.terms(town.getUUID());var actions=new HashMap<Integer,Runnable>();var menu=menu(p,actions,"Условия • "+town.getName());var lines=conditions(terms);lines.add("&7Действуют для новых заявлений.");lines.add("&7Прежние статусы не меняются.");menu.setItem(4,item(Material.LECTERN,"&eУсловия гражданства",lines));
        int slot=19;for(var status:List.of(CitizenshipStatus.CITIZEN,CitizenshipStatus.TEMPORARY,CitizenshipStatus.HONORARY)){int at=slot;menu.setItem(at,item(status==CitizenshipStatus.TEMPORARY?Material.CLOCK:Material.EMERALD,"&f"+status.title(),List.of("&7Нажмите: подготовить своё обращение.","&7Перед подачей будет подтверждение.","&7Вступление в город — отдельно через Towny.")));actions.put(at,()->suggest(p,"/t citizens apply "+town.getName()+" "+status.name().toLowerCase(Locale.ROOT)+" "+(status==CitizenshipStatus.TEMPORARY?"7":"0")+" Моё обращение"));slot+=3;}
        if(citizens.manages(p,town)){menu.setItem(31,item(Material.WRITABLE_BOOK,"&fИзменить условия",List.of("&7Поручители: 0–5; стаж на сервере: 0–3650 дней.","&7Открытые заявления сохранят прежние условия.","&7Нажмите: подготовить команду.")));actions.put(31,()->suggest(p,"/t citizens terms set "+terms.sponsors()+" "+terms.minimumDays()+" "+town.getName()+" "+terms.text()));}p.openInventory(menu);
    }
    public void openApplication(Player p,UUID id){
        var a=admissions.application(p,id);var actions=new HashMap<Integer,Runnable>();var menu=menu(p,actions,"Заявление • "+a.name());var detail=new ArrayList<String>();detail.add("&7Город: &f"+townName(a.town()));detail.add("&7Статус: &e"+a.desired().title()+(a.days()>0?" • "+a.days()+" дн.":""));detail.add("&7Состояние: &f"+phase(a.phase()));detail.add("&7Подано: &f"+date(a.created()));detail.add("&7Обращение: &f"+a.message());detail.add("&7Номер: &f"+a.id());if(a.phase()!=Phase.OPEN){detail.add("&7Решение: &f"+a.actorName()+" • "+date(a.decided()));detail.add("&7Причина: &f"+a.reason());}menu.setItem(4,item(Material.PAPER,"&f"+a.name(),detail));menu.setItem(13,item(Material.LECTERN,"&eУсловия при подаче",conditions(a.terms())));
        var eligible=admissions.eligible(a);var sponsors=new ArrayList<String>();sponsors.add("&7Действуют сейчас: &f"+eligible.size()+"/"+a.terms().sponsors());a.sponsors().values().stream().sorted(Comparator.comparing(Sponsor::name)).forEach(s->sponsors.add((eligible.contains(s.resident())?"&a✓ ":"&7× ")+s.name()));menu.setItem(15,item(Material.PLAYER_HEAD,"&fПоручители",sponsors));
        if(a.phase()==Phase.OPEN){var blockers=admissions.blockers(a);menu.setItem(22,item(blockers.isEmpty()?Material.LIME_DYE:Material.GRAY_DYE,blockers.isEmpty()?"&aУсловия выполнены":"&eЧто ещё требуется",blockers.isEmpty()?List.of("&7Мэр может рассмотреть заявление."):blockers.stream().map(s->"&7"+s).toList()));
            button(menu,actions,28,Material.EMERALD,"Поручиться",!a.applicant().equals(p.getUniqueId())&&!a.sponsors().containsKey(p.getUniqueId())&&p.hasPermission("neverlandtownycitizens.sponsor")&&admissions.sponsorEligible(p.getUniqueId(),a.town()),"Нужен другой действующий гражданин города; повтор не требуется.",()->review(p,id,"sponsor",""));
            button(menu,actions,30,Material.GRAY_DYE,"Отозвать поручительство",a.sponsors().containsKey(p.getUniqueId()),"Вы ещё не поручились за этого игрока.",()->review(p,id,"unsponsor",""));
            boolean manages=citizens.manages(p,TownyAPI.getInstance().getTown(a.town()));button(menu,actions,32,Material.LIME_DYE,"Одобрить",manages&&blockers.isEmpty(),manages?String.join("; ",blockers):"Решение доступно мэру города или администратору.",()->review(p,id,"approve","Условия выполнены; одобрено администрацией города"));button(menu,actions,33,Material.RED_DYE,"Отклонить",manages,"Решение доступно мэру города или администратору.",()->review(p,id,"reject","Решение администрации города"));button(menu,actions,34,Material.BARRIER,"Отозвать заявление",a.applicant().equals(p.getUniqueId()),"Доступно только автору заявления.",()->review(p,id,"withdraw","Отозвано автором"));}
        menu.setItem(40,item(Material.BOOK,"&fВсе доступные заявления",List.of()));actions.put(40,()->openBoard(p,null,0));p.openInventory(menu);
    }
    private static void button(Inventory menu,Map<Integer,Runnable> actions,int slot,Material icon,String title,boolean allowed,String why,Runnable action){menu.setItem(slot,item(allowed?icon:Material.GRAY_DYE,(allowed?"&f":"&7")+title,List.of(allowed?"&7Нажмите для отдельного подтверждения.":"&7"+why)));if(allowed)actions.put(slot,action);}
    public void openHistory(Player p,UUID town,UUID resident,int requested){
        if(!p.hasPermission("neverlandtownycitizens.use"))throw new IllegalArgumentException("Нет права просмотра");if(!resident.equals(p.getUniqueId())&&(town==null||!citizens.manages(p,TownyAPI.getInstance().getTown(town))))throw new IllegalArgumentException("Чужую историю просматривает мэр своего города или администратор");
        var all=citizens.repository().state().board().history();var list=new ArrayList<Change>();for(int i=all.size()-1;i>=0;i--){var row=all.get(i);if(row.resident().equals(resident)&&(town==null||row.town().equals(town)))list.add(row);}int pages=Math.max(1,(list.size()+35)/36),page=Math.min(Math.max(0,requested),pages-1);var actions=new HashMap<Integer,Runnable>();var menu=menu(p,actions,"История гражданства • "+(page+1)+"/"+pages);
        for(int i=page*36;i<Math.min(list.size(),page*36+36);i++){var row=list.get(i);String before=row.before().equals("DEFAULT")?"По членству в городе":CitizenshipStatus.valueOf(row.before()).title();menu.setItem(i-page*36,item(Material.PAPER,"&f"+date(row.at()),List.of("&7Город: &f"+townName(row.town()),"&7Было: &f"+before,"&7Стало: &e"+CitizenshipStatus.valueOf(row.after()).title(),"&7Кто: &f"+row.actorName(),"&7Причина: &f"+row.reason(),"&7Номер записи: &f"+row.id())));}
        if(list.isEmpty())menu.setItem(13,item(Material.CLOCK,"&fИзменений пока нет",List.of("&7Прежний статус сохранён.","&7История фиксируется с этого обновления.")));if(page>0){menu.setItem(38,item(Material.ARROW,"&fПредыдущая страница",List.of()));actions.put(38,()->openHistory(p,town,resident,page-1));}if(page+1<pages){menu.setItem(42,item(Material.ARROW,"&fСледующая страница",List.of()));actions.put(42,()->openHistory(p,town,resident,page+1));}p.openInventory(menu);
    }
    @EventHandler public void click(InventoryClickEvent event){try(var guard=SuiteState.guard()){var top=event.getView().getTopInventory();if(!(top.getHolder() instanceof Page page))return;event.setCancelled(true);if(!(event.getWhoClicked() instanceof Player p)||!page.viewer().equals(p.getUniqueId())||SuiteState.frozen(plugin.getName()))return;var action=page.actions().get(event.getRawSlot());if(action!=null)SuiteScheduler.entity(plugin,p,()->{if(p.getOpenInventory().getTopInventory()!=top)return;try{action.run();}catch(Exception error){tell(p,"&c"+message(error));}});}}
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Page)event.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent event){try(var guard=SuiteState.guard()){offers.remove(event.getPlayer().getUniqueId());}}
    public void close(){offers.clear();}
}
