package ru.neverland.townycouncil;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import ru.neverland.core.*;

/** Inventory-only appointments; all authority and persistence remain in CouncilService. */
public final class MinisterMenus implements Listener {
    @FunctionalInterface private interface Action { void run(Player player,Town town) throws Exception; }
    public static final class View implements InventoryHolder {
        final UUID viewer,town,mayor;final Map<Integer,Action> actions=new HashMap<>();
        Inventory inventory;boolean queued;
        View(Player p,Town t){viewer=p.getUniqueId();town=t.getUUID();mayor=t.getMayor().getUUID();}
        public Inventory getInventory(){return inventory;}
    }
    private final NeverLandTownyCouncil plugin;private final CouncilService service;private final CouncilMenus menus;private final AccessMenus access;
    public MinisterMenus(NeverLandTownyCouncil plugin,CouncilService service,CouncilMenus menus,AccessMenus access){this.plugin=plugin;this.service=service;this.menus=menus;this.access=access;}
    private View view(Player p,Town town,String title){
        if(!p.hasPermission("neverlandtownycouncil.use")||town==null||town.getMayor()==null)throw new IllegalArgumentException("Должности сейчас недоступны.");
        var v=new View(p,town);v.inventory=MenuStyle.inventory(plugin,v,54,title);return v;
    }
    private void button(View v,int slot,Material material,String title,List<String> lore,Action action){
        var item=new ItemStack(material);var meta=item.getItemMeta();meta.setDisplayName(MenuStyle.nameLegacy(title));meta.setLore(MenuStyle.loreStrings(lore));meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);item.setItemMeta(meta);v.inventory.setItem(slot,item);if(action!=null)v.actions.put(slot,action);
    }
    private static Material icon(MinisterRole role){return switch(role){case ECONOMY->Material.GOLD_INGOT;case DEFENSE->Material.SHIELD;case CONSTRUCTION->Material.BRICKS;case FOREIGN->Material.WRITABLE_BOOK;};}
    public void open(Player p,Town town,MinisterRole role){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        var v=view(p,town,"Министр • "+role.title());var holder=service.appointment(town.getUUID(),role);
        var lore=new ArrayList<String>(List.of("&7Управление городскими службами", "&7"+role.description(),holder==null?"&eДолжность свободна":"&7Назначен: &f"+CouncilMenus.name(holder.resident()),"&7Министр работает до смены мэра."));
        if(holder!=null)lore.add(service.active(holder)?"&aПолномочия действуют":"&cПолномочия приостановлены");
        button(v,13,icon(role),"&fМинистр: "+role.title(),lore,null);
        String assign=service.appointmentProblem(p,town,role,null),dismiss=service.dismissalProblem(p,town,role);
        button(v,29,assign.isEmpty()?Material.LIME_DYE:Material.GRAY_DYE,"&aНазначить жителя",List.of(assign.isEmpty()?"&7Выберите жителя, затем подтвердите назначение.":"&c"+assign),assign.isEmpty()?(who,t)->residents(who,t,role,0):null);
        button(v,31,dismiss.isEmpty()?Material.RED_DYE:Material.GRAY_DYE,"&cСнять с должности",List.of(dismiss.isEmpty()?"&7Сначала покажем, кого вы снимаете.":"&c"+dismiss),dismiss.isEmpty()?(who,t)->review(who,t,role,holder.resident(),true):null);
        button(v,33,Material.BOOK,"&eВозможности должности",List.of("&7Посмотреть доступные действия."),(who,t)->rights(who,t,role));
        if(service.mayEditAccess(p,town))button(v,40,Material.WRITABLE_BOOK,"&eНастроить права",List.of("&7Выбрать разрешения для этой должности."),(who,t)->access.actions(who,t,role.id(),null,null,0));
        button(v,49,Material.ARROW,"&fНазад",List.of("&7Все должности города"),(who,t)->menus.open(who,t));p.openInventory(v.inventory);
    } }
    private void rights(Player p,Town town,MinisterRole role){
        var v=view(p,town,"Возможности • "+role.title());int n=0;
        for(var action:TownAction.values()){
            int slot=9+n++;button(v,slot,Material.PAPER,"&f"+action.title(),List.of("&7"+action.description(),"&e"+service.roleExplanation(town,role.id(),action),"&7Личное правило жителя может изменить доступ."),null);
        }
        button(v,49,Material.ARROW,"&fНазад",List.of("&7К должности"),(who,t)->open(who,t,role));p.openInventory(v.inventory);
    }
    private void residents(Player p,Town town,MinisterRole role,int requested){
        String problem=service.appointmentProblem(p,town,role,null);if(!problem.isEmpty())throw new IllegalArgumentException(problem);
        var residents=town.getResidents().stream().sorted(Comparator.comparing(r->r.getName().toLowerCase(Locale.ROOT))).toList();
        int page=Math.max(0,Math.min(requested,Math.max(0,(residents.size()-1)/28)));var v=view(p,town,"Выбрать министра • "+role.title());
        int n=0;for(var resident:residents.stream().skip(page*28L).limit(28).toList()){
            UUID id=resident.getUUID();String reason=service.appointmentProblem(p,town,role,id);int slot=10+n/7*9+n%7;n++;
            button(v,slot,Material.PLAYER_HEAD,"&f"+resident.getName(),List.of(reason.isEmpty()?"&eВыбрать жителя":"&c"+reason),reason.isEmpty()?(who,t)->review(who,t,role,id,false):null);
        }
        if(page>0)button(v,45,Material.ARROW,"&fПредыдущая страница",List.of(),(who,t)->residents(who,t,role,page-1));
        if((page+1)*28<residents.size())button(v,53,Material.ARROW,"&fСледующая страница",List.of(),(who,t)->residents(who,t,role,page+1));
        button(v,50,Material.PAPER,"&7Страница "+(page+1)+" / "+Math.max(1,(residents.size()+27)/28),List.of(),null);
        button(v,49,Material.ARROW,"&fНазад",List.of("&7К должности"),(who,t)->open(who,t,role));p.openInventory(v.inventory);
    }
    private void review(Player p,Town town,MinisterRole role,UUID resident,boolean dismiss){
        String problem=dismiss?service.dismissalProblem(p,town,role):service.appointmentProblem(p,town,role,resident);
        if(!problem.isEmpty())throw new IllegalArgumentException(problem);
        Appointment expected=service.appointment(town.getUUID(),role);long revision=service.rules(town).revision(),created=System.nanoTime();
        Set<String> permissions=Set.copyOf(service.permissions(role.id()));
        var v=view(p,town,dismiss?"Снять министра":"Назначить министра");String name=CouncilMenus.name(resident);
        var lore=new ArrayList<>(List.of("&7Житель: &f"+name,"&7Должность: &f"+role.title()));
        if(dismiss)lore.addAll(List.of("&7Житель потеряет права этой должности.","&7Его личные разрешения останутся."));
        else {
            var rights=service.appointmentRights(town,role,resident);
            lore.addAll(rights.stream().limit(7).toList());
            if(rights.size()>7)lore.add("&7Полный список — кнопка «Проверить все права».");
        }
        lore.add("&7Решение будет записано в историю.");
        button(v,13,icon(role),"&f"+(dismiss?"Снятие: ":"Назначение: ")+name,lore,null);
        if(!dismiss)button(v,22,Material.BOOK,"&eПроверить все права",List.of("&7Доступ жителя после назначения."),(who,t)->reviewRights(who,t,role,resident));
        button(v,29,dismiss?Material.RED_DYE:Material.LIME_DYE,dismiss?"&cПодтвердить снятие":"&aПодтвердить назначение",List.of("&7"+name+" • "+role.title()),(who,t)->{
            if(System.nanoTime()-created>60_000_000_000L||!Objects.equals(expected,service.appointment(t.getUUID(),role))||service.rules(t).revision()!=revision||!permissions.equals(service.permissions(role.id())))
                throw new IllegalArgumentException("Состав или права изменились, либо подтверждение устарело. Откройте должность заново.");
            if(dismiss)service.dismiss(who,t,role);else service.appoint(who,t,role,resident);
            CouncilCommand.tell(who,"&a"+name+(dismiss?" снят с должности: ":" назначен: ")+role.title());open(who,t,role);
        });
        button(v,33,Material.ARROW,"&fОтмена",List.of("&7Вернуться без изменений"),(who,t)->open(who,t,role));p.openInventory(v.inventory);
    }
    private void reviewRights(Player p,Town town,MinisterRole role,UUID resident){
        var v=view(p,town,"Права после назначения");var rules=service.rules(town);int slot=9;
        for(var action:TownAction.values()){
            Boolean personal=rules.residents().getOrDefault(resident,Map.of()).get(action.id());
            String explanation=personal==null?service.roleExplanation(town,role.id(),action):personal?"Лично разрешено мэром":"Лично запрещено мэром";
            button(v,slot++,Material.PAPER,"&f"+action.title(),List.of("&7Житель: &f"+CouncilMenus.name(resident),"&e"+explanation),null);
        }
        button(v,49,Material.ARROW,"&fНазад",List.of("&7К подтверждению назначения"),(who,t)->review(who,t,role,resident,false));p.openInventory(v.inventory);
    }
    @EventHandler public void click(InventoryClickEvent event){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if(!(event.getView().getTopInventory().getHolder() instanceof View v))return;event.setCancelled(true);
        if(SuiteState.frozen(plugin.getName())||event.getClick()!=ClickType.LEFT||!(event.getWhoClicked() instanceof Player p)||!v.viewer.equals(p.getUniqueId())||v.queued)return;
        var action=v.actions.get(event.getRawSlot());if(action==null)return;v.queued=true;
        SuiteScheduler.entity(plugin,p,()->{
            if(!plugin.isEnabled()||SuiteState.frozen(plugin.getName())||!p.isOnline()||p.getOpenInventory().getTopInventory()!=v.inventory)return;
            try{
                var town=TownyAPI.getInstance().getTown(v.town);
                if(!p.hasPermission("neverlandtownycouncil.use")||town==null||town.getMayor()==null||!v.mayor.equals(town.getMayor().getUUID()))throw new IllegalArgumentException("Власть в городе изменилась. Откройте меню заново.");
                action.run(p,town);
            }catch(IllegalArgumentException ex){CouncilCommand.tell(p,"&c"+ex.getMessage());v.queued=false;}
            catch(Exception ex){CouncilCommand.tell(p,"&cИзменение не удалось сохранить. Сообщите администрации сервера.");plugin.getLogger().log(java.util.logging.Level.SEVERE,"Minister menu action failed",ex);v.queued=false;}
        });
    } }
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof View)event.setCancelled(true);}
}
