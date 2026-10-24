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
    public boolean handles(GovernanceMenuHolder holder){return holder.viewer()!=null&&Set.of(COUNCIL,OFFICE,RESIDENTS,OFFICE_CONFIRM).contains(holder.type());}

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
        List<OfficeDefinition> offices=List.copyOf(governance.offices());int page=page(requestedPage,offices.size());
        Inventory menu=create(player,town,COUNCIL,plugin.getConfig().getString("gui.council-title","Городской совет"));
        for(int i=page*PAGE_SIZE;i<Math.min(offices.size(),(page+1)*PAGE_SIZE);i++){
            OfficeDefinition office=offices.get(i);List<OfficeHolder> holders=holders(town,office.id());List<String> lore=description(office,holders);
            lore.add("");lore.add("&eНажмите — участники и назначения");
            button(menu,slot(i%PAGE_SIZE),icon(office),office.name(),lore,p->openOffice(p,office.id(),page,0));
        }
        if(offices.isEmpty())menu.setItem(22,item(Material.PAPER,"&7Должности не настроены",List.of("&7Добавьте должности в offices.yml.")));
        List<String> members=governance.council(town).stream().map(towny::resident).filter(Objects::nonNull).map(Resident::getName).sorted().toList();
        menu.setItem(48,item(Material.BELL,"&eСостав совета",members.isEmpty()?List.of("&7Совет не сформирован"):members.stream().map(name->"&7• &f"+name).toList()));
        if(Bukkit.getPluginManager().isPluginEnabled("NeverLandTownyCouncil")&&player.hasPermission("neverlandtownycouncil.use"))
            button(menu,46,Material.WRITABLE_BOOK,"&eМинистры города",List.of("&7Экономика, оборона, строительство и внешняя политика."),p->p.performCommand("townycouncil"));
        button(menu,49,Material.ARROW,"&7Назад",List.of("&7Законы и совет"),main);
        pages(menu,page,offices.size(),45,53,p->openCouncil(p,page-1),p->openCouncil(p,page+1));player.openInventory(menu);
    }

    private void openOffice(Player player,String officeId,int councilPage,int requestedPage){
        Town town=access(player);if(town==null)return;OfficeDefinition office=governance.office(officeId);if(office==null){openCouncil(player,councilPage);return;}
        List<OfficeHolder> holders=holders(town,officeId);int page=page(requestedPage,holders.size());
        Inventory menu=create(player,town,OFFICE,ColorUtil.strip(office.name()));menu.setItem(4,item(icon(office),office.name(),description(office,holders)));
        var allowed=governance.officeAccess(player,officeId);
        for(int i=page*PAGE_SIZE;i<Math.min(holders.size(),(page+1)*PAGE_SIZE);i++){
            OfficeHolder holder=holders.get(i);List<String> lore=List.of("&7Занимает эту должность",allowed==SUCCESS?"&eНажмите — снять с подтверждением":"&c"+reason(allowed));
            button(menu,slot(i%PAGE_SIZE),Material.PLAYER_HEAD,"&f"+holder.residentName(),lore,allowed==SUCCESS?p->review(p,officeId,holder.residentId(),true,councilPage,page):null);
        }
        if(holders.isEmpty())menu.setItem(22,item(Material.PAPER,"&7Пока никто не назначен",List.of("&7Выберите жителя кнопкой ниже.")));
        var assign=allowed==SUCCESS&&holders.size()>=office.maxHolders()?OFFICE_FULL:allowed;
        button(menu,45,Material.LIME_DYE,"&aНазначить жителя",List.of(assign==SUCCESS?"&7Выбрать жителя своего города, в том числе офлайн.":"&c"+reason(assign)),assign==SUCCESS?p->openResidents(p,officeId,councilPage,0):null);
        button(menu,49,Material.ARROW,"&7Назад",List.of("&7Городской совет"),p->openCouncil(p,councilPage));
        pages(menu,page,holders.size(),46,53,p->openOffice(p,officeId,councilPage,page-1),p->openOffice(p,officeId,councilPage,page+1));player.openInventory(menu);
    }

    private void openResidents(Player player,String officeId,int councilPage,int requestedPage){
        Town town=access(player);if(town==null)return;var allowed=governance.officeAccess(player,officeId);
        if(allowed!=SUCCESS){player.sendMessage("§e"+reason(allowed));openOffice(player,officeId,councilPage,0);return;}
        OfficeDefinition office=governance.office(officeId);
        List<Resident> residents=town.getResidents().stream().sorted(Comparator.comparing(Resident::getName,String.CASE_INSENSITIVE_ORDER).thenComparing(r->r.getUUID().toString())).toList();
        int page=page(requestedPage,residents.size());Inventory menu=create(player,town,RESIDENTS,"Выбор жителя");
        menu.setItem(4,item(icon(office),office.name(),List.of("&7Выберите жителя для назначения.")));
        for(int i=page*PAGE_SIZE;i<Math.min(residents.size(),(page+1)*PAGE_SIZE);i++){
            Resident resident=residents.get(i);UUID id=resident.getUUID();var result=governance.appointmentStatus(player,officeId,id);
            button(menu,slot(i%PAGE_SIZE),Material.PLAYER_HEAD,"&f"+resident.getName(),List.of(result==SUCCESS?"&eНажмите — проверить назначение":"&c"+reason(result)),result==SUCCESS?p->review(p,officeId,id,false,councilPage,page):null);
        }
        button(menu,49,Material.ARROW,"&7Назад",List.of("&7К должности"),p->openOffice(p,officeId,councilPage,0));
        pages(menu,page,residents.size(),45,53,p->openResidents(p,officeId,councilPage,page-1),p->openResidents(p,officeId,councilPage,page+1));player.openInventory(menu);
    }

    private void review(Player player,String officeId,UUID residentId,boolean dismiss,int councilPage,int returnPage){
        Town town=access(player);if(town==null)return;
        var result=dismiss?governance.dismissalStatus(player,officeId,residentId):governance.appointmentStatus(player,officeId,residentId);
        if(result!=SUCCESS){player.sendMessage("§e"+reason(result));openOffice(player,officeId,councilPage,0);return;}
        OfficeDefinition office=governance.office(officeId);List<OfficeHolder> holders=holders(town,officeId);
        String name=dismiss?holders.stream().filter(h->h.residentId().equals(residentId)).findFirst().orElseThrow().residentName():towny.resident(residentId).getName();
        OfficeConfirmation quote=new OfficeConfirmation(town.getUUID(),player.getUniqueId(),residentId,dismiss,office,holders,System.nanoTime());
        Inventory menu=create(player,town,OFFICE_CONFIRM,dismiss?"Снятие с должности":"Назначение на должность");
        menu.setItem(13,item(icon(office),office.name(),List.of("&7Житель: &f"+name,"&7Действие: &f"+(dismiss?"снять с должности":"назначить на должность"),"&7Мест занято: &f"+holders.size()+" / "+office.maxHolders(),"&7Должность входит в совет: &f"+(office.councilMember()?"да":"нет"),"&7Изменение будет записано в историю решений.","&7Подтверждение действительно одну минуту.")));
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
        else player.sendMessage("§e"+reason(result));
        openOffice(player,id,councilPage,0);
    }

    private Town access(Player player){
        if(!player.hasPermission("townygovernance.use")){messages.send(player,"no-permission");return null;}
        Town town=towny.town(player);if(town==null)messages.send(player,"no-town");return town;
    }
    private List<OfficeHolder> holders(Town town,String office){return List.copyOf(governance.data(town).offices().getOrDefault(office,List.of()));}
    private static int page(int request,int count){return Math.max(0,Math.min(request,Math.max(0,(count-1)/PAGE_SIZE)));}
    private static int slot(int index){return 10+(index/7)*9+index%7;}
    private List<String> description(OfficeDefinition office,List<OfficeHolder> holders){List<String> lines=new ArrayList<>(office.description());lines.add("&7Мест: &f"+holders.size()+" / "+office.maxHolders());lines.add("&7Член совета: &f"+(office.councilMember()?"Да":"Нет"));for(var holder:holders)lines.add("&7• &f"+holder.residentName());return lines;}
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
        case NO_TOWN->"Сначала вступите в город.";case NO_PERMISSION->"Нужны полномочия мэра/заместителя, право управления должностями и гражданство.";
        case UNKNOWN_OFFICE->"Должность больше не существует.";case UNKNOWN_PLAYER->"Житель не найден.";case NOT_RESIDENT->"Игрок больше не состоит в вашем городе.";
        case NOT_ELIGIBLE->"Гражданство не позволяет занимать должность.";case OFFICE_FULL->"Все места заняты. Сначала снимите одного из назначенных жителей.";
        case ALREADY_APPOINTED->"Уже занимает эту должность.";case NOT_APPOINTED->"Уже не занимает эту должность.";case MULTIPLE_OFFICES->"Совмещение должностей отключено.";
        case CHANGE_DISABLED->"Выборная должность: изменить состав можно через выборы.";default->"Действие сейчас недоступно.";
    };}
}
