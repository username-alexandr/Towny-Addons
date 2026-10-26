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

/** Buttons contain IDs, never commands built from display names. Every click checks the live mayor and revision. */
public final class AccessMenus implements Listener {
    private interface Click {void run(Player player)throws Exception;}
    public static final class View implements InventoryHolder {
        final UUID viewer,town,mayor;final long revision;final Map<Integer,Click> buttons=new HashMap<>();Inventory inventory;boolean queued;
        View(Player p,Town t,AccessRules r){viewer=p.getUniqueId();town=t.getUUID();mayor=r.mayor();revision=r.revision();}
        public Inventory getInventory(){return inventory;}
    }
    private final NeverLandTownyCouncil plugin;private final CouncilService service;private final CouncilMenus council;
    public AccessMenus(NeverLandTownyCouncil plugin,CouncilService service,CouncilMenus council){this.plugin=plugin;this.service=service;this.council=council;}
    private View view(Player p,Town t,String title){
        if(SuiteState.frozen(plugin.getName())||!service.mayEditAccess(p,t))throw new IllegalArgumentException("Менять доступ может только мэр своего города");
        var v=new View(p,t,service.rules(t));v.inventory=MenuStyle.inventory(plugin,v,54,title);return v;
    }
    private void button(View v,int slot,Material icon,String title,List<String> lore,Click click){
        var stack=new ItemStack(icon);var meta=stack.getItemMeta();meta.setDisplayName(MenuStyle.nameLegacy(title));meta.setLore(MenuStyle.loreStrings(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);stack.setItemMeta(meta);v.inventory.setItem(slot,stack);if(click!=null)v.buttons.put(slot,click);
    }
    private void back(View v,Click click){button(v,49,Material.ARROW,"&fНазад",List.of(),click);}
    public void open(Player p,Town town,boolean roles,int page){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        var v=view(p,town,roles?"Права министров":"Права жителей");
        button(v,4,Material.BOOK,"&eДоступ в городе",List.of("&7Личное правило важнее должности.","&7Сброс возвращает права жителя и его должностей.","&7Настройки меняет только мэр."),null);
        if(roles){int slot=19;for(var role:MinisterRole.values()){
            button(v,slot,Material.WRITABLE_BOOK,"&f"+role.title(),List.of("&7Выбрать действия этой должности"),who->actions(who,town,role.id(),null,null,0));slot+=2;
        }}else{
            var people=town.getResidents().stream().filter(r->!r.getUUID().equals(town.getMayor().getUUID())).sorted(Comparator.comparing(r->r.getName().toLowerCase(Locale.ROOT))).toList();
            int current=Math.max(0,Math.min(page,Math.max(0,(people.size()-1)/28)));int n=0;
            for(var resident:people.stream().skip((long)current*28).limit(28).toList()){
                int slot=10+(n/7)*9+n%7;n++;UUID id=resident.getUUID();
                button(v,slot,Material.PLAYER_HEAD,"&f"+resident.getName(),List.of("&7Личные разрешения и запреты"),who->actions(who,town,"",id,null,0));
            }
            if(current>0)button(v,48,Material.ARROW,"&fПредыдущая страница",List.of(),who->open(who,town,false,current-1));
            if((current+1)*28<people.size())button(v,50,Material.ARROW,"&fСледующая страница",List.of(),who->open(who,town,false,current+1));
        }
        back(v,who->council.open(who,town));p.openInventory(v.inventory);
    } }
    public void actions(Player p,Town town,String role,UUID resident,String category,int page){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if(resident!=null&&(!town.equals(service.ownTown(resident))||resident.equals(town.getMayor().getUUID())))throw new IllegalArgumentException("Житель больше недоступен");
        if(resident==null)MinisterRole.parse(role);
        var v=view(p,town,resident==null?"Права • "+MinisterRole.parse(role).title():"Права • "+CouncilMenus.name(resident));
        if(category==null){
            int slot=19;for(String group:Arrays.stream(TownAction.values()).map(TownAction::category).distinct().toList()){
                button(v,slot++,Material.BOOK,"&f"+group,List.of("&7Открыть список действий"),who->actions(who,town,role,resident,group,0));
            }
            back(v,who->open(who,town,resident==null,0));
        }else{
            var choices=Arrays.stream(TownAction.values()).filter(a->a.category().equals(category)).toList();
            var rules=service.rules(town);var overrides=resident==null?rules.roles().getOrDefault(role,Map.of()):rules.residents().getOrDefault(resident,Map.of());
            int n=0;for(var a:choices){
                Boolean value=overrides.get(a.id());String label=value==null?"Без отдельного правила":value?"Разрешено":"Запрещено";
                var lore=new ArrayList<>(List.of("&7"+a.description(),"", "&fНастройка: "+label,
                    "&e"+(resident==null?service.roleExplanation(town,role,a):service.residentExplanation(town,resident,a)),
                    "&7Нажмите, чтобы выбрать разрешение."));
                if(resident==null)lore.add("&7Личное правило жителя имеет приоритет.");
                int slot=10+(n/7)*9+n%7;n++;
                button(v,slot,value==null?Material.GRAY_DYE:value?Material.LIME_DYE:Material.RED_DYE,"&f"+a.title(),lore,
                    who->choose(who,town,role,resident,a,category,page));
            }
            back(v,who->actions(who,town,role,resident,null,0));
        }
        p.openInventory(v.inventory);
    } }
    private void choose(Player p,Town town,String role,UUID resident,TownAction action,String category,int page){
        var v=view(p,town,"Изменить разрешение");
        button(v,13,Material.BOOK,"&f"+action.title(),List.of("&7"+action.description(),
            "&7Для: &f"+(resident==null?MinisterRole.parse(role).title():CouncilMenus.name(resident)),
            "&e"+(resident==null?service.roleExplanation(town,role,action):service.residentExplanation(town,resident,action))),null);
        String[] modes={"allow","deny","default"};String[] titles={"&aРазрешить","&cЗапретить","&eСбросить отдельное правило"};
        Material[] icons={Material.LIME_DYE,Material.RED_DYE,Material.GRAY_DYE};
        for(int i=0;i<modes.length;i++){
            String mode=modes[i];String hint=i==2?(resident==null?"Вернуть стандартные права должности.":"Снова учитывать должность и обычные права жителя."):
                (resident==null?"Изменить право для всех назначенных на эту должность.":"Установить личное правило, важнее прав должности.");
            button(v,29+i*2,icons[i],titles[i],List.of("&7"+hint,"&7Изменение сохранится сразу после нажатия."),who->{
                service.changeAccess(who,town,v.mayor,v.revision,role,resident,action.id(),mode);
                CouncilCommand.tell(who,"&aСохранено: "+action.title()+" — "+(mode.equals("allow")?"разрешено":mode.equals("deny")?"запрещено":"отдельное правило снято"));
                actions(who,town,role,resident,category,page);
            });
        }
        back(v,who->actions(who,town,role,resident,category,page));p.openInventory(v.inventory);
    }
    @EventHandler public void click(InventoryClickEvent e){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);
        if(SuiteState.frozen(plugin.getName())||!(e.getWhoClicked() instanceof Player p)||!v.viewer.equals(p.getUniqueId()))return;
        if(e.getRawSlot()<0||e.getRawSlot()>=54||e.getClick()!=ClickType.LEFT||v.queued)return;
        var click=v.buttons.get(e.getRawSlot());if(click==null)return;v.queued=true;
        SuiteScheduler.entity(plugin,p,()->{if(!plugin.isEnabled()||SuiteState.frozen(plugin.getName())||!p.isOnline()||p.getOpenInventory().getTopInventory()!=v.inventory)return;try{
            var town=TownyAPI.getInstance().getTown(v.town);if(!service.mayEditAccess(p,town))throw new IllegalArgumentException("Изменять права может только действующий мэр");
            var now=service.rules(town);if(!now.mayor().equals(v.mayor)||now.revision()!=v.revision)throw new IllegalArgumentException("Настройки изменились. Откройте меню заново");
            click.run(p);
        }catch(Exception ex){CouncilCommand.tell(p,"&c"+ex.getMessage());v.queued=false;}});
    } }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
}
