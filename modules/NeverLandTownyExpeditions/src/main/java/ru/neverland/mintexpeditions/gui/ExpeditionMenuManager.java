package ru.neverland.mintexpeditions.gui;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.mintexpeditions.MintTownyExpeditions;
import ru.neverland.mintexpeditions.journey.Journey;
import ru.neverland.mintexpeditions.model.*;
import ru.neverland.mintexpeditions.service.*;
import ru.neverland.mintexpeditions.util.ColorUtil;

public final class ExpeditionMenuManager implements Listener {
    private final JavaPlugin plugin;private final ExpeditionService service;private final RussianItemNames itemNames;
    public ExpeditionMenuManager(JavaPlugin plugin,ExpeditionService service,RussianItemNames itemNames){this.plugin=plugin;this.service=service;this.itemNames=itemNames;}
    public void open(Player player){try(var guard=SuiteState.guard()){
        if(!player.hasPermission("mintexpeditions.use"))return;
        var active=service.participantExpedition(player.getUniqueId());if(active!=null&&active.planned()){active(player,active);return;}
        var offers=new HashMap<Integer,ExpeditionDefinition>();for(var d:service.registry().all())offers.put(Math.max(0,Math.min(44,d.slot())),d);
        var holder=new ExpeditionMenuHolder(player.getUniqueId(),ExpeditionMenuHolder.Page.BOARD,offers,null,null,null);var inventory=inventory(holder,"Экспедиции NeverLand");
        offers.forEach((slot,d)->{var lore=conditions(d,false);lore.add("&aВыбрать порядок прохождения");inventory.setItem(slot,item(d.icon(),d.name(),lore));});
        if(service.canReturn(player)&&service.hasReturnTarget(player))inventory.setItem(49,item(Material.ENDER_PEARL,"&dВернуться в лагерь",List.of("&7Телепортация к лагерю вашей экспедиции")));
        player.openInventory(inventory);
    }}
    public void choose(Player player,ExpeditionDefinition definition){
        var holder=new ExpeditionMenuHolder(player.getUniqueId(),ExpeditionMenuHolder.Page.CHOOSE,Map.of(),definition,null,null);var inventory=inventory(holder,"Порядок прохождения");
        inventory.setItem(11,item(Material.MAP,"&fОбычная экспедиция",List.of("&7Цели и враги доступны одновременно.","&7Прежние условия наград сохраняются.","&aПосмотреть условия перед стартом")));
        inventory.setItem(13,item(Material.SPYGLASS,"&bРазведка → зачистка",List.of("&7Сначала найти все цели, затем победить врагов.","&7До конца разведки враги не участвуют в бою.","&7Награда — участникам с подтверждённым вкладом.","&aПосмотреть условия перед стартом")));
        inventory.setItem(15,item(Material.IRON_SWORD,"&cЗачистка → поиск",List.of("&7Сначала победить всех врагов, затем найти цели.","&7До зачистки поиск целей недоступен.","&7Награда — участникам с подтверждённым вкладом.","&aПосмотреть условия перед стартом")));
        back(inventory);player.openInventory(inventory);
    }
    private void confirmation(Player player,ExpeditionDefinition definition,Journey.Branch branch){
        ExpeditionDefinition frozen;try{frozen=branch==null?definition:Journey.freeze(definition);}catch(RuntimeException invalid){player.sendMessage("Этот шаблон пока доступен только как обычная экспедиция.");return;}
        var holder=new ExpeditionMenuHolder(player.getUniqueId(),ExpeditionMenuHolder.Page.CONFIRM,Map.of(),frozen,branch,null);var inventory=inventory(holder,"Подтверждение экспедиции");
        var lore=conditions(frozen,branch!=null);lore.add("&eПорядок: "+(branch==null?"цели и враги одновременно":branch.title()));lore.add("&7Припасы спишутся из схрона вашего лагеря.");lore.add("&aНачать на показанных условиях");inventory.setItem(13,item(Material.LIME_DYE,"&aПодтвердить начало",lore));back(inventory);player.openInventory(inventory);
    }
    private void active(Player player,ActiveExpedition expedition){
        var holder=new ExpeditionMenuHolder(player.getUniqueId(),ExpeditionMenuHolder.Page.ACTIVE,Map.of(),null,null,expedition.id());var inventory=inventory(holder,"Ваш поход");var journey=service.journey(expedition);
        var lore=new ArrayList<String>();lore.add("&f"+service.journeyStatus(expedition));if(journey!=null){lore.add("&7Порядок: "+journey.branch().title());lore.add("&7Вклад: "+journey.contributions().values().stream().filter(c->c.player().equals(player.getUniqueId())).count());var role=journey.roles().get(player.getUniqueId());lore.add("&7Ваша роль: "+(role==null?"не выбрана":role.title()));}lore.add("&7Награда требует хотя бы одного действия:");lore.add("&7найти цель или победить врага этого похода.");lore.add("&7Роль добровольная и не ограничивает действия.");
        inventory.setItem(13,item(Material.WRITABLE_BOOK,"&eЭтап и вклад",lore));inventory.setItem(20,item(Material.SPYGLASS,"&bСтать разведчиком",List.of("&7Ваш выбор будет сохранён в истории вклада")));inventory.setItem(22,item(Material.SHIELD,"&cСтать защитником",List.of("&7Ваш выбор будет сохранён в истории вклада")));inventory.setItem(24,item(Material.PAPER,"&fБез роли",List.of("&7Можно выполнять любые доступные действия")));inventory.setItem(40,item(Material.COMPASS,"&aОбновить сведения",List.of()));if(service.canReturn(player)&&service.hasReturnTarget(player))inventory.setItem(49,item(Material.ENDER_PEARL,"&dВернуться в лагерь",List.of()));player.openInventory(inventory);
    }
    private List<String> conditions(ExpeditionDefinition d,boolean planned){var lore=new ArrayList<String>();lore.add("&f"+ColorUtil.strip(d.name()));lore.add("&7Уровень лагеря: "+d.minCampLevel());lore.add("&7Время: "+d.durationSeconds()/60+" мин");lore.add("&7Целей: "+d.goal()+"; врагов: "+d.mobCount());lore.add("&eПрипасы из схрона:");d.costs().forEach(i->lore.add("&7• "+itemNames.name(i.item())+" × "+i.amount()));lore.add("&eЛичная награда: "+d.moneyPerParticipant()+" монет");d.rewards().forEach(i->lore.add("&7• "+itemNames.name(i.item())+" × "+i.stack().getAmount()));if(planned){lore.add("&7Нужен хотя бы один подтверждённый вклад.");lore.add("&7Присутствие и ожидание не дают награду.");lore.add("&7Условия этого похода сохраняются до конца.");}lore.add("&7До точки нужно добраться самостоятельно.");return lore;}
    private Inventory inventory(ExpeditionMenuHolder holder,String title){var inventory=MenuStyle.inventory(plugin,holder,54,ColorUtil.color("&8"+title));var filler=item(Material.BLACK_STAINED_GLASS_PANE," ",List.of());for(int i=0;i<54;i++)inventory.setItem(i,filler);return inventory;}
    private void back(Inventory inventory){inventory.setItem(45,item(Material.ARROW,"&fНазад",List.of()));}
    private ItemStack item(Material material,String name,List<String> lore){var item=new ItemStack(material);item.editMeta(meta->{meta.setDisplayName(MenuStyle.nameLegacy(ColorUtil.color(name)));meta.setLore(MenuStyle.loreStrings(lore.stream().map(ColorUtil::color).toList()));meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);});return item;}
    @EventHandler public void click(InventoryClickEvent event){try(var guard=SuiteState.guard()){
        var top=event.getView().getTopInventory();if(!(top.getHolder() instanceof ExpeditionMenuHolder holder))return;event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player)||event.getClick()!=ClickType.LEFT||event.getRawSlot()<0||event.getRawSlot()>=top.getSize()||!player.getUniqueId().equals(holder.owner))return;
        int slot=event.getRawSlot();SuiteScheduler.entity(plugin,player,()->{
            if(!player.isOnline()||player.getOpenInventory().getTopInventory()!=top||top.getHolder()!=holder||!player.hasPermission("mintexpeditions.use")||SuiteState.frozen(plugin.getName()))return;
            if(slot==49&&(holder.page==ExpeditionMenuHolder.Page.BOARD||holder.page==ExpeditionMenuHolder.Page.ACTIVE)){service.returnToCamp(player);return;}
            if(slot==45){open(player);return;}
            switch(holder.page){
                case BOARD->{var offer=holder.offers.get(slot);if(offer!=null)choose(player,offer);}
                case CHOOSE->{if(slot==11||slot==13||slot==15)confirmation(player,holder.definition,slot==11?null:slot==13?Journey.Branch.SCOUT_FIRST:Journey.Branch.FIGHT_FIRST);}
                case CONFIRM->{if(slot==13){player.closeInventory();start(player,holder.definition,holder.branch);}}
                case ACTIVE->{var current=service.participantExpedition(player.getUniqueId());if(current==null||!current.id().equals(holder.expedition)){open(player);return;}if(slot==40){open(player);return;}if(slot==20||slot==22||slot==24)service.role(player,slot==20?Journey.Role.EXPLORER:slot==22?Journey.Role.GUARD:null).whenComplete((v,e)->{if(plugin.isEnabled())SuiteScheduler.entity(plugin,player,()->{if(e!=null)player.sendMessage("Роль пока не сохранена. "+service.journeyStatus(current));if(player.getOpenInventory().getTopInventory()==top)open(player);});});}
            }
        });
    }}
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof ExpeditionMenuHolder)event.setCancelled(true);}
    /** Existing command keeps the classic mode; the new ordered mode is an explicit menu choice. */
    public void start(Player player,ExpeditionDefinition definition){start(player,definition,null);}
    private void start(Player player,ExpeditionDefinition definition,Journey.Branch branch){var result=service.start(player,definition,branch);switch(result){case NO_CAMP->message(player,"no-camp");case INACTIVE->message(player,"camp-inactive");case TOO_FAR->message(player,"not-at-camp");case STASH_OPEN->message(player,"stash-open");case LOW_LEVEL->((MintTownyExpeditions)plugin).messages().send(player,"camp-level",Map.of("level",definition.minCampLevel()));case ACTIVE->message(player,"active-exists");case WORLD_BLOCKED->message(player,"world-blocked");case UNAVAILABLE->player.sendMessage("Начало пока недоступно: проверьте право на старт или дождитесь сохранения журнала.");default->{}}}
    private void message(Player player,String key){((MintTownyExpeditions)plugin).messages().send(player,key);}
}
