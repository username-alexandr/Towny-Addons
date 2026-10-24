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
        final UUID viewer,town,mayor;final long revision;final Map<Integer,Click> buttons=new HashMap<>();Inventory inventory;
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
    private void back(View v,Click click){button(v,45,Material.ARROW,"&fНазад",List.of(),click);}
    public void open(Player p,Town town,boolean roles,int page){ synchronized(SuiteState.lock()) {
        var v=view(p,town,roles?"Доступ должностей":"Доступ жителей");
        button(v,4,Material.BOOK,"&eДоступ в городе",List.of("&7Личное правило важнее должности.","&7По умолчанию — обычные правила.","&7Настройки меняет только мэр."),null);
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
    public void actions(Player p,Town town,String role,UUID resident,String category,int page){ synchronized(SuiteState.lock()) {
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
                Boolean value=overrides.get(a.id());String label=value==null?"По умолчанию":value?"Разрешено":"Запрещено";
                String next=value==null?"allow":value?"deny":"default";
                var lore=new ArrayList<>(List.of("&7"+a.description(),"", "&fСейчас: "+label,"&7ЛКМ — изменить; ПКМ — по умолчанию"));
                if(resident!=null){String result=service.decision(town.getUUID(),resident,a.id());lore.add("&7Итог: "+switch(result){case "ALLOW"->"разрешено";case "DENY"->"запрещено";default->"обычные правила города";});}
                int slot=10+(n/7)*9+n%7;n++;
                button(v,slot,value==null?Material.GRAY_DYE:value?Material.LIME_DYE:Material.RED_DYE,"&f"+a.title(),lore,who->{
                    service.changeAccess(who,town,v.mayor,v.revision,role,resident,a.id(),next);actions(who,town,role,resident,category,page);
                });
                v.buttons.put(-slot-1,who->{service.changeAccess(who,town,v.mayor,v.revision,role,resident,a.id(),"default");actions(who,town,role,resident,category,page);});
            }
            back(v,who->actions(who,town,role,resident,null,0));
        }
        p.openInventory(v.inventory);
    } }
    @EventHandler public void click(InventoryClickEvent e){ synchronized(SuiteState.lock()) {
        if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);
        if(SuiteState.frozen(plugin.getName())||!(e.getWhoClicked() instanceof Player p)||!v.viewer.equals(p.getUniqueId()))return;
        if(e.getRawSlot()<0||e.getRawSlot()>=54||!(e.getClick()==ClickType.LEFT||e.getClick()==ClickType.RIGHT))return;
        var click=v.buttons.get(e.getClick()==ClickType.RIGHT?-e.getRawSlot()-1:e.getRawSlot());if(click==null)return;
        SuiteScheduler.entity(plugin,p,()->{try{
            var town=TownyAPI.getInstance().getTown(v.town);if(!service.mayEditAccess(p,town))throw new IllegalArgumentException("Доступ доступен только действующему мэру");
            var now=service.rules(town);if(!now.mayor().equals(v.mayor)||now.revision()!=v.revision)throw new IllegalArgumentException("Настройки изменились. Откройте меню заново");
            click.run(p);
        }catch(Exception ex){CouncilCommand.tell(p,"&c"+ex.getMessage());}});
    } }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
}
