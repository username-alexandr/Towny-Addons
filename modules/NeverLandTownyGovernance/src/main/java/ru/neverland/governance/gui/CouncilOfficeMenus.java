package ru.neverland.governance.gui;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.MenuStyle;
import ru.neverland.core.SuiteScheduler;
import ru.neverland.core.SuiteState;
import ru.neverland.governance.integration.ItemsAdderHook;
import ru.neverland.governance.integration.TownyHook;
import ru.neverland.governance.model.OfficeDefinition;
import ru.neverland.governance.model.OfficeHolder;
import ru.neverland.governance.service.GovernanceService;
import ru.neverland.governance.service.MessageService;
import ru.neverland.governance.util.ColorUtil;
import java.util.*;
import java.util.function.Consumer;
import static ru.neverland.governance.gui.GovernanceMenuHolder.Type.*;
import static ru.neverland.governance.service.GovernanceService.Result.*;

/** Standard inventory actions also work through CraftoUI's existing native-menu bridge. */
public final class CouncilOfficeMenus {
    private static final int PAGE_SIZE=28;
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final GovernanceService governance;
    private final ItemsAdderHook itemsAdder;
    private final MessageService messages;
    private final Consumer<Player> main;

    public CouncilOfficeMenus(JavaPlugin plugin,TownyHook towny,GovernanceService governance,ItemsAdderHook itemsAdder,MessageService messages,Consumer<Player> main){
        this.plugin=plugin;this.towny=towny;this.governance=governance;this.itemsAdder=itemsAdder;this.messages=messages;this.main=main;
    }
    public boolean handles(GovernanceMenuHolder holder){return holder.viewer()!=null&&Set.of(COUNCIL,OFFICE,RESIDENTS,OFFICE_CONFIRM,OFFICE_HOLDER).contains(holder.type());}

    public void click(InventoryClickEvent event,Player player,GovernanceMenuHolder holder){
        if(event.getClick()!=ClickType.LEFT||!player.getUniqueId().equals(holder.viewer()))return;
        Town town=access(player);if(town==null)return;
        if(!town.getUUID().equals(holder.townId())){player.sendMessage("§eГород изменился. Откройте совет заново.");return;}
        Consumer<Player> action=holder.takeAction(event.getRawSlot());if(action==null)return;
        Inventory expected=event.getView().getTopInventory();
        SuiteScheduler.entity(plugin,player,()->{
            // This callback runs on the player's owning thread and under the suite ledger lock.
            if(!plugin.isEnabled()||SuiteState.frozen(plugin.getName())||!player.isOnline()||player.getOpenInventory().getTopInventory()!=expected)return;
            Town fresh=access(player);if(fresh==null)return;
            if(!fresh.getUUID().equals(holder.townId())){player.sendMessage("§eГород изменился. Откройте совет заново.");openCouncil(player,0);return;}
            action.accept(player);
        });
    }

    public void openCouncil(Player player,int requestedPage){
        Town town=access(player);if(town==null)return;
        List<OfficeDefinition> offices=List.copyOf(governance.offices());
        boolean ministers=Bukkit.getPluginManager().isPluginEnabled("NeverLandTownyCouncil")&&player.hasPermission("neverlandtownycouncil.use");
        String[] roleIds={"economy","defense","construction","foreign"};String[] roleNames={"Экономика","Оборона","Строительство","Внешняя политика"};
        Material[] roleIcons={Material.GOLD_INGOT,Material.SHIELD,Material.BRICKS,Material.WRITABLE_BOOK};
        int count=offices.size()+(ministers?roleIds.length:0),page=page(requestedPage,count);
        Inventory menu=create(player,town,COUNCIL,plugin.getConfig().getString("gui.offices-title","Должности города"));
        for(int i=page*PAGE_SIZE;i<Math.min(offices.size(),(page+1)*PAGE_SIZE);i++){
            OfficeDefinition office=offices.get(i);List<OfficeHolder> holders=holders(town,office.id());List<String> lore=description(office,holders);
            lore.add("");lore.add("&eОткрыть должность и её возможности");
            button(menu,slot(i%PAGE_SIZE),icon(office),office.name(),lore,p->openOffice(p,office.id(),page,0));
        }
        if(ministers)for(int i=0;i<roleIds.length;i++){
            int index=offices.size()+i;if(index/PAGE_SIZE!=page)continue;String id=roleIds[i];
            button(menu,slot(index%PAGE_SIZE),roleIcons[i],"&eМинистр: "+roleNames[i],List.of("&7Городские службы", "&7Получает права на действия в городе.","&7Назначается отдельно от должностей совета.","&eОткрыть назначение и права"),p->p.performCommand("townycouncil office "+id));
        }
        if(count==0)menu.setItem(22,item(Material.PAPER,"&7Должности пока недоступны",List.of("&7Сообщите администрации сервера.")));
        menu.setItem(4,item(Material.BOOK,"&eКак устроено управление",List.of("&7Совет предлагает законы и голосует.","&7Министры управляют городскими службами.","&7Выберите должность — увидите её возможности.")));
        List<String> members=governance.council(town).stream().map(towny::resident).filter(Objects::nonNull).map(Resident::getName).sorted().toList();
        menu.setItem(48,item(Material.BELL,"&eКто входит в совет",members.isEmpty()?List.of("&7Совет не сформирован"):members.stream().map(name->"&7• &f"+name).toList()));
        if(ministers&&town.getMayor()!=null&&town.getMayor().getUUID().equals(player.getUniqueId()))
            button(menu,46,Material.PLAYER_HEAD,"&eПрава жителей",List.of("&7Лично разрешить или запретить действие жителю.","&7Личное правило важнее прав должности."),p->p.performCommand("townycouncil access"));
        button(menu,49,Material.ARROW,"&7Назад",List.of("&7Законы и совет"),main);
        pages(menu,page,count,45,53,p->openCouncil(p,page-1),p->openCouncil(p,page+1));player.openInventory(menu);
    }

    private void openOffice(Player player,String officeId,int councilPage,int requestedPage){
        Town town=access(player);if(town==null)return;OfficeDefinition office=governance.office(officeId);if(office==null){openCouncil(player,councilPage);return;}
        List<OfficeHolder> holders=holders(town,officeId);int page=page(requestedPage,holders.size());
        Inventory menu=create(player,town,OFFICE,ColorUtil.strip(office.name()));menu.setItem(4,item(icon(office),office.name(),description(office,holders)));
        var allowed=governance.officeAccess(player,officeId);
        for(int i=page*PAGE_SIZE;i<Math.min(holders.size(),(page+1)*PAGE_SIZE);i++){
            OfficeHolder holder=holders.get(i);List<String> lore=List.of("&7Занимает эту должность","&eОткрыть действия с жителем");
            button(menu,slot(i%PAGE_SIZE),Material.PLAYER_HEAD,"&f"+holder.residentName(),lore,p->openHolder(p,officeId,holder.residentId(),councilPage,page));
        }
        if(holders.isEmpty())menu.setItem(22,item(Material.PAPER,"&7Пока никто не назначен",List.of(allowed==SUCCESS?"&7Выберите жителя кнопкой ниже.":"&7"+governance.officeAccessReason(player,officeId))));
        var assign=allowed==SUCCESS&&holders.size()>=office.maxHolders()?OFFICE_FULL:allowed;
        button(menu,45,assign==SUCCESS?Material.LIME_DYE:Material.GRAY_DYE,"&aНазначить жителя",List.of(assign==SUCCESS?"&7Выбрать жителя своего города, в том числе офлайн.":"&c"+(assign==NO_PERMISSION?governance.officeAccessReason(player,officeId):reason(assign))),assign==SUCCESS?p->openResidents(p,officeId,councilPage,0):null);
        button(menu,49,Material.ARROW,"&7Назад",List.of("&7Все должности города"),p->openCouncil(p,councilPage));
        pages(menu,page,holders.size(),46,53,p->openOffice(p,officeId,councilPage,page-1),p->openOffice(p,officeId,councilPage,page+1));player.openInventory(menu);
    }

    private void openHolder(Player player,String officeId,UUID residentId,int councilPage,int officePage){
        Town town=access(player);if(town==null)return;OfficeDefinition office=governance.office(officeId);
        OfficeHolder holder=holders(town,officeId).stream().filter(h->h.residentId().equals(residentId)).findFirst().orElse(null);
        if(office==null||holder==null){openCouncil(player,councilPage);return;}
        Inventory menu=create(player,town,OFFICE_HOLDER,"Житель • "+holder.residentName());
        menu.setItem(13,item(Material.PLAYER_HEAD,"&f"+holder.residentName(),description(office,List.of(holder))));
        String reason=governance.officeAccessReason(player,officeId);
        button(menu,29,reason.isEmpty()?Material.RED_DYE:Material.GRAY_DYE,"&cСнять с должности",List.of(reason.isEmpty()?"&7Показать подтверждение снятия.":"&c"+reason),reason.isEmpty()?p->review(p,officeId,residentId,true,councilPage,officePage):null);
        if(Bukkit.getPluginManager().isPluginEnabled("NeverLandTownyCouncil")&&player.hasPermission("neverlandtownycouncil.use")&&town.getMayor()!=null&&town.getMayor().getUUID().equals(player.getUniqueId())&&!residentId.equals(town.getMayor().getUUID()))
            button(menu,33,Material.WRITABLE_BOOK,"&eНастроить права жителя",List.of("&7Изменить личные разрешения этого жителя.","&7Другие назначенные жители сохранят свои права."),p->p.performCommand("townycouncil access resident "+residentId));
        button(menu,49,Material.ARROW,"&7Назад",List.of("&7К должности"),p->openOffice(p,officeId,councilPage,officePage));player.openInventory(menu);
    }

    private void openResidents(Player player,String officeId,int councilPage,int requestedPage){
        Town town=access(player);if(town==null)return;var allowed=governance.officeAccess(player,officeId);
        if(allowed!=SUCCESS){player.sendMessage("§e"+(allowed==NO_PERMISSION?governance.officeAccessReason(player,officeId):reason(allowed)));openOffice(player,officeId,councilPage,0);return;}
        OfficeDefinition office=governance.office(officeId);
        List<Resident> residents=town.getResidents().stream().sorted(Comparator.comparing(Resident::getName,String.CASE_INSENSITIVE_ORDER).thenComparing(r->r.getUUID().toString())).toList();
        int page=page(requestedPage,residents.size());Inventory menu=create(player,town,RESIDENTS,"Выбор жителя");
        menu.setItem(4,item(icon(office),office.name(),List.of("&7Выберите жителя для назначения.")));
        for(int i=page*PAGE_SIZE;i<Math.min(residents.size(),(page+1)*PAGE_SIZE);i++){
            Resident resident=residents.get(i);UUID id=resident.getUUID();var result=governance.appointmentStatus(player,officeId,id);
            button(menu,slot(i%PAGE_SIZE),Material.PLAYER_HEAD,"&f"+resident.getName(),List.of(result==SUCCESS?"&eВыбрать жителя":"&c"+reason(result)),result==SUCCESS?p->review(p,officeId,id,false,councilPage,page):null);
        }
        button(menu,49,Material.ARROW,"&7Назад",List.of("&7К должности"),p->openOffice(p,officeId,councilPage,0));
        pages(menu,page,residents.size(),45,53,p->openResidents(p,officeId,councilPage,page-1),p->openResidents(p,officeId,councilPage,page+1));player.openInventory(menu);
    }

    private void review(Player player,String officeId,UUID residentId,boolean dismiss,int councilPage,int returnPage){
        Town town=access(player);if(town==null)return;
        var result=dismiss?governance.dismissalStatus(player,officeId,residentId):governance.appointmentStatus(player,officeId,residentId);
        if(result!=SUCCESS){player.sendMessage("§e"+(result==NO_PERMISSION?governance.officeAccessReason(player,officeId):reason(result)));openOffice(player,officeId,councilPage,0);return;}
        OfficeDefinition office=governance.office(officeId);List<OfficeHolder> holders=holders(town,officeId);
        String name=dismiss?holders.stream().filter(h->h.residentId().equals(residentId)).findFirst().orElseThrow().residentName():towny.resident(residentId).getName();
        OfficeConfirmation quote=new OfficeConfirmation(town.getUUID(),player.getUniqueId(),residentId,dismiss,office,holders,System.nanoTime());
        Inventory menu=create(player,town,OFFICE_CONFIRM,dismiss?"Снятие с должности":"Назначение на должность");
        var preview=new ArrayList<String>(List.of("&7Житель: &f"+name,"&7Действие: &f"+(dismiss?"снять с должности":"назначить на должность")));
        preview.addAll(description(office,holders));
        preview.add(dismiss?"&7Личные разрешения жителя сохранятся.":"&7Личные ограничения могут изменить доступ.");
        preview.add("&7Решение сохранится в истории города.");
        menu.setItem(13,item(icon(office),office.name(),preview));
        button(menu,29,dismiss?Material.RED_DYE:Material.LIME_DYE,dismiss?"&cПодтвердить снятие":"&aПодтвердить назначение",List.of("&7"+name+" → "+ColorUtil.strip(office.name())),p->confirm(p,quote,name,councilPage));
        button(menu,33,Material.ARROW,"&7Назад",List.of("&7Вернуться без изменений"),p->{if(dismiss)openOffice(p,officeId,councilPage,returnPage);else openResidents(p,officeId,councilPage,returnPage);});player.openInventory(menu);
    }

    private void confirm(Player player,OfficeConfirmation quote,String reviewedName,int councilPage){
        Town town=access(player);if(town==null)return;String id=quote.office().id();
        if(!quote.current(town.getUUID(),player.getUniqueId(),governance.office(id),holders(town,id),System.nanoTime())){
            player.sendMessage("§eСостав должности изменился или подтверждение истекло. Проверьте назначение заново.");openOffice(player,id,councilPage,0);return;
        }
        var result=quote.dismiss()?governance.dismiss(player,id,quote.resident()):governance.appoint(player,id,quote.resident());
        if(result==SUCCESS)messages.send(player,quote.dismiss()?"dismissed":"appointed",Map.of("player",reviewedName,"office",quote.office().name()));
        else player.sendMessage("§e"+(result==NO_PERMISSION?governance.officeAccessReason(player,id):reason(result)));
        openOffice(player,id,councilPage,0);
    }

    private Town access(Player player){
        if(!player.hasPermission("townygovernance.use")){messages.send(player,"no-permission");return null;}
        Town town=towny.town(player);if(town==null)messages.send(player,"no-town");return town;
    }
    private List<OfficeHolder> holders(Town town,String office){return List.copyOf(governance.data(town).offices().getOrDefault(office,List.of()));}
    private static int page(int request,int count){return Math.max(0,Math.min(request,Math.max(0,(count-1)/PAGE_SIZE)));}
    private static int slot(int index){return 10+(index/7)*9+index%7;}
    private List<String> description(OfficeDefinition office,List<OfficeHolder> holders){
        List<String> lines=new ArrayList<>();lines.add("&7Городская должность");lines.addAll(office.description());
        var categories=Arrays.stream(ru.neverland.governance.model.LawCategory.values()).filter(office::mayPropose).map(ru.neverland.governance.model.LawCategory::display).toList();
        lines.add("&7Может предлагать законы: &f"+(categories.isEmpty()?"нет":String.join(", ",categories)));
        lines.add(office.councilMember()?"&7Входит в совет; участие зависит от правил выборов.":"&7Сама должность не даёт место в совете.");
        lines.add("&7Доступ к городским службам настраивается отдельно.");
        lines.add("&7Занято мест: &f"+holders.size()+" / "+office.maxHolders());for(var holder:holders)lines.add("&7• &f"+holder.residentName());return lines;
    }
    private void pages(Inventory menu,int page,int count,int previous,int next,Consumer<Player> back,Consumer<Player> forward){
        if(count<=PAGE_SIZE)return;menu.setItem(50,item(Material.PAPER,"&7Страница "+(page+1)+" / "+((count+PAGE_SIZE-1)/PAGE_SIZE),List.of()));
        if(page>0)button(menu,previous,Material.ARROW,"&7Предыдущая страница",List.of(),back);
        if((page+1)*PAGE_SIZE<count)button(menu,next,Material.ARROW,"&7Следующая страница",List.of(),forward);
    }
    private Inventory create(Player player,Town town,GovernanceMenuHolder.Type type,String title){
        Inventory menu=MenuStyle.inventory(plugin,new GovernanceMenuHolder(type,town.getUUID(),player.getUniqueId()),54,ColorUtil.color(title));
        Material material=Material.matchMaterial(plugin.getConfig().getString("gui.filler","BLACK_STAINED_GLASS_PANE"));
        ItemStack filler=item(material==null?Material.BLACK_STAINED_GLASS_PANE:material," ",List.of());for(int i=0;i<menu.getSize();i++)menu.setItem(i,filler);return menu;
    }
    private void button(Inventory menu,int slot,Material material,String name,List<String> lore,Consumer<Player> action){button(menu,slot,new ItemStack(material),name,lore,action);}
    private void button(Inventory menu,int slot,ItemStack icon,String name,List<String> lore,Consumer<Player> action){menu.setItem(slot,item(icon,name,lore));if(action!=null)((GovernanceMenuHolder)menu.getHolder()).action(slot,action);}
    private ItemStack icon(OfficeDefinition office){ItemStack custom=plugin.getConfig().getBoolean("itemsadder.enabled",true)?itemsAdder.item(office.itemsAdderIcon()):null;return custom==null?new ItemStack(office.material()):custom;}
    private ItemStack item(Material material,String name,List<String> lore){return item(new ItemStack(material),name,lore);}
    @SuppressWarnings("deprecation") private ItemStack item(ItemStack stack,String name,List<String> lore){var meta=stack.getItemMeta();meta.setDisplayName(MenuStyle.nameLegacy(ColorUtil.color(name)));meta.setLore(MenuStyle.loreStrings(lore.stream().map(ColorUtil::color).toList()));meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);stack.setItemMeta(meta);return stack;}
    public static String reason(GovernanceService.Result result){return switch(result){
        case NO_TOWN->"Сначала вступите в город.";case NO_PERMISSION->"У вас нет права управлять этой должностью.";
        case UNKNOWN_OFFICE->"Должность больше не существует.";case UNKNOWN_PLAYER->"Житель не найден.";case NOT_RESIDENT->"Игрок больше не состоит в вашем городе.";
        case NOT_ELIGIBLE->"Статус жителя не позволяет занимать должность.";case OFFICE_FULL->"Все места заняты. Сначала снимите одного из назначенных жителей.";
        case ALREADY_APPOINTED->"Уже занимает эту должность.";case NOT_APPOINTED->"Уже не занимает эту должность.";case MULTIPLE_OFFICES->"Житель уже занимает другую должность. Сначала снимите его с неё.";
        case CHANGE_DISABLED->"Выборная должность: изменить состав можно через выборы.";default->"Действие сейчас недоступно.";
    };}
}
