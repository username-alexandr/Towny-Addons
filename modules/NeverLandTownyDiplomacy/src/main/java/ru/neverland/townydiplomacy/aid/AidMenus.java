package ru.neverland.townydiplomacy.aid;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.core.*;
import ru.neverland.townydiplomacy.*;

public final class AidMenus implements Listener {
    public record Holder(UUID viewer,UUID town,int page,List<UUID> entries,UUID pact,UUID call,AidPact expected,String confirm,Inventory back)implements InventoryHolder{public Inventory getInventory(){return null;}}
    private final NeverLandTownyDiplomacy plugin;private final AidService aid;
    public AidMenus(NeverLandTownyDiplomacy plugin,AidService aid){this.plugin=plugin;this.aid=aid;}
    private static ItemStack item(Material material,String name,String... lore){var s=new ItemStack(material);var m=s.getItemMeta();m.setDisplayName(MenuStyle.nameLegacy(name));m.setLore(MenuStyle.loreStrings(Arrays.asList(lore)));m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);s.setItemMeta(m);return s;}
    private boolean allowed(Player p,UUID town){var own=plugin.diplomacy().ownTown(p.getUniqueId());return p.hasPermission("neverlandtownydiplomacy.use")&&(p.hasPermission("neverlandtownydiplomacy.admin")||own!=null&&own.getUUID().equals(town));}
    public void open(Player p,Town town,int page,Inventory back){try(var guard=SuiteState.guard()){
        if(town==null||!allowed(p,town.getUUID()))throw new IllegalArgumentException("Помощь и квитанции доступны жителям своего города");var list=aid.visibleState().pacts().values().stream().filter(v->v.party(town.getUUID())).sorted(Comparator.comparingLong(AidPact::created).reversed()).toList();int pages=Math.max(1,(list.size()+27)/28);page=Math.max(0,Math.min(page,pages-1));var entries=list.stream().skip(page*28L).limit(28).toList();
        var holder=new Holder(p.getUniqueId(),town.getUUID(),page,entries.stream().map(AidPact::id).toList(),null,null,null,null,back==null?MenuStyle.previousMenu(p):back);var inv=MenuStyle.inventory(plugin,holder,54,"Взаимопомощь • "+town.getName());
        inv.setItem(4,item(Material.SHIELD,"&eВзаимопомощь союзников","&7"+aid.status(),"&7Страница "+(page+1)+" / "+pages,"&7Обещание без денег и предметов.","&7Не объявляет войну и не включает PvP.","&e/diplomacy aid offer <город>"));
        for(int i=0;i<entries.size();i++){var pact=entries.get(i);inv.setItem(10+i/7*9+i%7,item(pact.phase()==AidPact.Phase.ACTIVE?Material.LIME_BANNER:Material.PAPER,"&f"+DiplomacyMenus.name(pact.other(town.getUUID())),"&7"+AidCommands.phase(pact),"&7До "+AidCommands.when(pact.endsAt()),"&fНажмите — условия, подписи и помощь"));}
        inv.setItem(45,item(Material.ARROW,"&fНазад"));if(page>0)inv.setItem(48,item(Material.PAPER,"&fПредыдущая страница"));if(page+1<pages)inv.setItem(50,item(Material.PAPER,"&fСледующая страница"));inv.setItem(53,item(Material.SUNFLOWER,"&fОбновить"));p.openInventory(inv);
    }}
    private void detail(Player player,Holder old,UUID id,String confirmation){
        var pact=aid.visibleState().pacts().get(id);if(pact==null||!pact.party(old.town()))throw new IllegalArgumentException("Соглашение отсутствует");var calls=aid.visibleState().calls().values().stream().filter(c->c.pact().equals(id)).sorted(Comparator.comparingLong(AidCall::opened).reversed()).limit(14).toList();
        var h=new Holder(player.getUniqueId(),old.town(),old.page(),calls.stream().map(AidCall::id).toList(),id,null,pact,confirmation,old.back());var inv=MenuStyle.inventory(plugin,h,54,"Условия взаимопомощи");
        inv.setItem(4,item(Material.SHIELD,"&e"+AidCommands.phase(pact),"&7"+DiplomacyMenus.name(pact.first())+" ↔ "+DiplomacyMenus.name(pact.second()),"&7Действует до: "+AidCommands.when(pact.endsAt()),"&7Ответить на нападение за "+pact.responseMillis()/60_000+" мин.","&7Помощь: "+pact.requiredMilliDamage()/1000+" ед. урона нападающим.","&7Учитываются разрешённые удары после брони", "&7только на земле защищаемого города.","&7Две единицы урона = одно сердце.","&7Нет денег, депозита и автоматических наказаний.","&7"+pact.note()));
        inv.setItem(13,item(Material.WRITABLE_BOOK,"&fПодписи и сроки","&7Первый подписант: "+pact.proposer(),"&7Второй: "+(pact.accepter().isEmpty()?"ещё не подписано":pact.accepter()),"&7Предложение до "+AidCommands.when(pact.offerUntil()),"&7ID: "+pact.id()));
        if(!aid.canManage(player,TownyAPI.getInstance().getTown(old.town())))inv.setItem(22,item(Material.GRAY_DYE,"&7Решение доступно мэру города","&7Жители могут читать условия и квитанции."));
        else if(confirmation!=null)inv.setItem(22,item(Material.LIME_DYE,"&aПодтвердить решение","&7"+confirmation,"&7Условия и полномочия проверятся повторно."));
        else {if(pact.phase()==AidPact.Phase.OFFERED&&pact.second().equals(old.town())){inv.setItem(20,item(Material.LIME_DYE,"&aПринять условия"));inv.setItem(22,item(Material.RED_DYE,"&eОтклонить предложение"));}if(pact.phase()!=AidPact.Phase.ENDED)inv.setItem(24,item(Material.BARRIER,"&eПрекратить соглашение","&7Открытая помощь получит «не оценено».","&7Автоматических последствий нет."));}
        for(int i=0;i<calls.size();i++){var c=calls.get(i);inv.setItem(28+i/7*9+i%7,item(c.status()==AidCall.Status.FULFILLED?Material.EMERALD:Material.PAPER,"&f"+AidCommands.status(c),"&7Помощник: "+DiplomacyMenus.name(c.helper()),"&7Защитить: "+DiplomacyMenus.name(c.beneficiary()),"&7Подтверждено: "+c.confirmedMilliDamage()/1000.0+" / "+c.requiredMilliDamage()/1000.0,"&7До "+AidCommands.when(c.deadline()),"&fНажмите — квитанции и место"));}
        inv.setItem(45,item(Material.ARROW,"&fНазад"));inv.setItem(49,item(Material.BOOK,"&fВся история","&e/diplomacy aid calls [страница]","&e/diplomacy aid history [страница]"));inv.setItem(53,item(Material.SUNFLOWER,"&fОбновить"));player.openInventory(inv);
    }
    private void call(Player player,Holder old,UUID id,int page){
        var c=aid.visibleState().calls().get(id);if(c==null||!c.pact().equals(old.pact()))throw new IllegalArgumentException("Вызов не найден");int pages=Math.max(1,(c.proof().size()+27)/28);page=Math.max(0,Math.min(page,pages-1));
        var h=new Holder(player.getUniqueId(),old.town(),page,List.of(),c.pact(),c.id(),null,null,old.back());var inv=MenuStyle.inventory(plugin,h,54,"Квитанции помощи");var a=c.attack();
        inv.setItem(4,item(Material.SHIELD,"&e"+AidCommands.status(c),"&7"+c.note(),"&7Нужно: "+c.confirmedMilliDamage()/1000.0+" / "+c.requiredMilliDamage()/1000.0,"&7Нападение: "+AidCommands.when(c.opened()),"&7Срок: "+AidCommands.when(c.deadline()),"&7Нападавший: "+a.actor(),"&7Пострадавший: "+a.victim(),"&7Место: "+a.world()+" "+a.x()+" "+a.y()+" "+a.z()));
        var facts=c.proof().stream().skip(page*28L).limit(28).toList();for(int i=0;i<facts.size();i++){var f=facts.get(i);inv.setItem(10+i/7*9+i%7,item(Material.PAPER,"&fПодтверждено "+f.milliDamage()/1000.0+" ед. урона","&7"+AidCommands.when(f.at()),"&7Помог: "+f.actor(),"&7Противник: "+f.victim(),"&7Место: "+f.x()+" "+f.y()+" "+f.z(),"&7Квитанция: "+f.id()));}
        inv.setItem(45,item(Material.ARROW,"&fНазад"));if(page>0)inv.setItem(48,item(Material.PAPER,"&fПредыдущая страница"));if(page+1<pages)inv.setItem(50,item(Material.PAPER,"&fСледующая страница"));player.openInventory(inv);
    }
    @EventHandler public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Holder h))return;event.setCancelled(true);if(event.getClick()!=ClickType.LEFT)return;var expectedWindow=event.getView().getTopInventory();if(!(event.getWhoClicked() instanceof Player player)||!h.viewer().equals(player.getUniqueId()))return;int slot=event.getRawSlot();
        SuiteScheduler.entity(plugin,player,()->{try{if(player.getOpenInventory().getTopInventory()!=expectedWindow||player.getOpenInventory().getTopInventory().getHolder()!=h)return;if(!allowed(player,h.town()))throw new IllegalArgumentException("Нет доступа к этому городу");var town=TownyAPI.getInstance().getTown(h.town());if(town==null)throw new IllegalArgumentException("Город больше не существует");
            if(h.call()!=null){if(slot==45)detail(player,h,h.pact(),null);else if(slot==48||slot==50)call(player,h,h.call(),h.page()+(slot==48?-1:1));return;}
            if(h.pact()!=null){if(slot==45){if(h.confirm()!=null)detail(player,h,h.pact(),null);else open(player,town,0,h.back());}else if(slot==53)detail(player,h,h.pact(),null);
                else if(slot==22&&h.confirm()!=null){if(!Objects.equals(h.expected(),aid.visibleState().pacts().get(h.pact())))throw new IllegalArgumentException("Условия изменились; откройте их заново");aid.decide(player,town,h.pact(),h.confirm()).whenComplete((value,error)->{if(plugin.isEnabled())SuiteScheduler.entity(plugin,player,()->{DiplomacyCommand.tell(player,error==null?AidCommands.phase(value):"Решение не подтверждено; проверьте состояние помощи");if(player.getOpenInventory().getTopInventory().getHolder()==h)detail(player,h,h.pact(),null);});});DiplomacyCommand.tell(player,"Решение сохраняется");}
                else if(h.confirm()==null&&(slot==20||slot==22||slot==24))detail(player,h,h.pact(),slot==20?"accept":slot==22?"reject":"end");
                else if(slot>=28&&slot<=43&&slot%9>=1&&slot%9<=7){int i=(slot/9-3)*7+slot%9-1;if(i<h.entries().size())call(player,h,h.entries().get(i),0);}return;}
            if(slot==45)MenuStyle.returnTo(player,h.back());else if(slot==48||slot==50||slot==53)open(player,town,h.page()+(slot==48?-1:slot==50?1:0),h.back());else if(slot>=10&&slot<=43&&slot%9>=1&&slot%9<=7){int i=(slot/9-1)*7+slot%9-1;if(i<h.entries().size())detail(player,h,h.entries().get(i),null);}
        }catch(Exception error){DiplomacyCommand.tell(player,"&c"+error.getMessage());}});
    }
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Holder)event.setCancelled(true);}
}
