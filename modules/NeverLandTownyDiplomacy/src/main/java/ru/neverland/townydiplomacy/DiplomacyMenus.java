package ru.neverland.townydiplomacy;

import java.util.*;
import java.time.Instant;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import ru.neverland.core.MenuStyle;
import static ru.neverland.townydiplomacy.Treaty.*;

public final class DiplomacyMenus implements Listener {
    record Holder(UUID viewer,UUID town,int page,List<UUID> entries,UUID detail,String confirm,Inventory back) implements InventoryHolder { public Inventory getInventory(){return null;} }
    private final NeverLandTownyDiplomacy plugin;private final DiplomacyService service;
    public DiplomacyMenus(NeverLandTownyDiplomacy plugin,DiplomacyService service){this.plugin=plugin;this.service=service;}
    public static String name(UUID id){var t=TownyAPI.getInstance().getTown(id);return t==null?"Удалённый город":t.getName();}
    public static String phase(Treaty t){long now=System.currentTimeMillis();if(t.phase()==Phase.ENDED)return "Завершён";if(!t.open(now))return "Срок истёк";return switch(t.phase()){case PENDING->"Ожидает принятия";case ACTIVE->"Действует";case TERMINATING->"Расторгается; условия ещё действуют";case ENDED->"Завершён";};}
    private static Material icon(TreatyType type){return switch(type){case ALLIANCE->Material.SHIELD;case TRADE->Material.GOLD_INGOT;case NONAGGRESSION->Material.WHITE_BANNER;case VASSALAGE->Material.GOLDEN_HELMET;case GUARANTEE->Material.TOTEM_OF_UNDYING;case EMBARGO->Material.BARRIER;case SANCTIONS->Material.IRON_BARS;};}
    private Inventory previous(Player p){var top=p.getOpenInventory().getTopInventory();return top.getHolder() instanceof Holder h?h.back():MenuStyle.previousMenu(p);}
    public void open(Player player,Town town,int page,Inventory back){
        if(!player.hasPermission("neverlandtownydiplomacy.use"))return;
        var values=service.repository().all().values().stream().filter(t->t.party(town.getUUID())).sorted(Comparator.comparing((Treaty t)->!t.open(System.currentTimeMillis())).thenComparing(Comparator.comparingLong(Treaty::created).reversed()).thenComparing(Treaty::id)).toList();
        int pages=Math.max(1,(values.size()+27)/28);page=Math.max(0,Math.min(page,pages-1));var slice=values.stream().skip(page*28L).limit(28).toList();
        var holder=new Holder(player.getUniqueId(),town.getUUID(),page,slice.stream().map(Treaty::id).toList(),null,null,back==null?previous(player):back);
        var inv=MenuStyle.inventory(plugin,holder,54,"Дипломатия • "+town.getName());
        inv.setItem(4,item(Material.WRITABLE_BOOK,"&eДоговоры города",List.of("&7Город: &f"+town.getName(),"&7Страница: &f"+(page+1)+" / "+pages,"",service.healthy()?"&a✓ Реестр доступен":"&c× Действия временно закрыты","&fНажмите договор — условия и решения.")));
        int i=0;for(var t:slice){int slot=10+(i/7)*9+i%7;i++;inv.setItem(slot,item(icon(t.type()),"&f"+t.type().title(),List.of("&7Сторона: &f"+name(t.other(town.getUUID())),"&e"+phase(t),"", "&fНажмите — открыть условия.")));}
        if(slice.isEmpty())inv.setItem(22,item(Material.PAPER,"&fДоговоров пока нет",List.of("&7Предложите соглашение другому городу.","&e/diplomacy help")));
        inv.setItem(45,item(Material.ARROW,holder.back()==null?"&fЗакрыть":"&fНазад",List.of("&7Предыдущее меню.")));
        if(page>0)inv.setItem(48,item(Material.PAPER,"&f← Предыдущая страница",List.of()));
        inv.setItem(49,item(Material.BOOK,"&eПомощь",List.of("&7Типы договоров и команды.")));
        if(page+1<pages)inv.setItem(50,item(Material.PAPER,"&fСледующая страница →",List.of()));
        inv.setItem(53,item(Material.SUNFLOWER,"&fОбновить",List.of("&7Актуальные решения и сроки.")));player.openInventory(inv);
    }
    private void detail(Player player,Holder old,UUID id,String confirm){
        var t=service.repository().all().get(id);if(t==null){DiplomacyCommand.tell(player,"&cДоговор уже отсутствует");return;}
        var h=new Holder(player.getUniqueId(),old.town(),old.page(),List.of(),id,confirm,old.back());var inv=MenuStyle.inventory(plugin,h,54,"Условия • "+t.type().title());
        var lore=new ArrayList<>(List.of("&7Инициатор: &f"+name(t.first()),"&7Вторая сторона: &f"+name(t.second()),"", "&e"+phase(t),"&7"+t.type().description(),"&7Срок после принятия: &f"+(t.duration()/86_400_000)+" дн.","&7Уведомление о расторжении: &f"+(t.noticePeriod()/3_600_000)+" ч."));
        if(t.phase()==Phase.PENDING)lore.add("&7Принять до: &f"+Instant.ofEpochMilli(t.offerUntil()));
        if(t.expires()>0)lore.add("&7Окончание: &f"+Instant.ofEpochMilli(t.expires()));if(t.noticeUntil()>0)lore.add("&7Прекращение условий: &f"+Instant.ofEpochMilli(t.noticeUntil()));
        if(t.type()==TreatyType.TRADE)lore.add("&7Скидка на пошлины сторон: &f"+t.discountBasisPoints()/100.0+"%");
        if(t.type()==TreatyType.SANCTIONS)lore.add("&7Ограничение: &f"+switch(t.sanction()){case TRADE->"торговля";case DIPLOMATIC->"новые соглашения";case ALL->"торговля и новые соглашения";case NONE->"нет";});
        lore.add("");lore.add("&7Причина: &f"+t.reason());lore.add("&7ID: &f"+t.id());
        inv.setItem(13,item(icon(t.type()),"&f"+t.type().title(),lore));
        var town=TownyAPI.getInstance().getTown(h.town());boolean manages=service.manages(player,town,t.type());long now=System.currentTimeMillis();
        if(confirm!=null&&manages)inv.setItem(31,item(Material.LIME_DYE,"&aПодтвердить решение",List.of("&7"+actionName(confirm),"&7"+name(t.first())+" ↔ "+name(t.second()),"", "&fНажмите — сохранить решение.")));
        else if(manages){
            if(t.pending(now)&&t.second().equals(h.town())){inv.setItem(30,item(Material.LIME_DYE,"&aПринять",List.of("&7Согласиться с указанными условиями.")));inv.setItem(32,item(Material.RED_DYE,"&cОтклонить",List.of("&7Отклонить входящее предложение.")));}
            if(t.pending(now)&&t.first().equals(h.town())||t.active(now)&&t.phase()!=Phase.TERMINATING&&(t.type().bilateral()||t.first().equals(h.town())))
                inv.setItem(34,item(Material.BARRIER,"&eЗавершить",List.of(t.pending(now)?"&7Отозвать предложение.":t.type().bilateral()?"&7Начать срок уведомления о расторжении.":"&7Снять введённое вашим городом ограничение.")));
        }
        inv.setItem(45,item(Material.ARROW,"&fНазад",List.of(confirm==null?"&7К списку договоров.":"&7Отменить подтверждение.")));player.openInventory(inv);
    }
    private static String actionName(String action){return switch(action){case "accept"->"Принять договор";case "reject"->"Отклонить предложение";default->"Завершить договор или ограничение";};}
    private static ItemStack item(Material type,String name,List<String> lore){var item=new ItemStack(type);var meta=item.getItemMeta();meta.setDisplayName(MenuStyle.nameLegacy(name));meta.setLore(MenuStyle.loreStrings(lore));item.setItemMeta(meta);return item;}
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof Holder h))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||!h.viewer().equals(p.getUniqueId())||!p.hasPermission("neverlandtownydiplomacy.use"))return;
        int slot=e.getRawSlot();var town=TownyAPI.getInstance().getTown(h.town());
        plugin.getServer().getScheduler().runTask(plugin,()->{
            if(!p.hasPermission("neverlandtownydiplomacy.use"))return;
            try{
                if(h.detail()!=null){
                    if(slot==45){if(h.confirm()!=null)detail(p,h,h.detail(),null);else if(town!=null)open(p,town,h.page(),h.back());else p.closeInventory();}
                    else if(slot==31&&h.confirm()!=null&&town!=null){var t=service.change(p,town,h.detail(),h.confirm());plugin.announce(t,t.type().title()+" • "+name(t.first())+" ↔ "+name(t.second())+" • "+phase(t));detail(p,h,h.detail(),null);}
                    else if(h.confirm()==null){String action=slot==30?"accept":slot==32?"reject":slot==34?"end":null;if(action!=null)detail(p,h,h.detail(),action);}
                }else if(slot==45)MenuStyle.returnTo(p,h.back());else if(slot==49)DiplomacyCommand.help(p);
                else if(town!=null&&(slot==48||slot==50||slot==53))open(p,town,h.page()+(slot==48?-1:slot==50?1:0),h.back());
                else if(slot>=10&&slot<=43&&slot%9>=1&&slot%9<=7){int i=((slot/9)-1)*7+(slot%9)-1;if(i<h.entries().size())detail(p,h,h.entries().get(i),null);}
            }catch(IllegalArgumentException ex){DiplomacyCommand.tell(p,"&c"+ex.getMessage());}catch(Exception ex){DiplomacyCommand.tell(p,"&cРешение не сохранено; обратитесь к администратору");plugin.failure("Ошибка меню дипломатии",ex);}
        });
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true);}
}
