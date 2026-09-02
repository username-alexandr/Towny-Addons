package ru.neverland.mintespionage.gui;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintespionage.integration.ItemsAdderHook;
import ru.neverland.mintespionage.integration.TownyHook;
import ru.neverland.mintespionage.model.IntelReport;
import ru.neverland.mintespionage.model.OperationDefinition;
import ru.neverland.mintespionage.model.SpyOperation;
import ru.neverland.mintespionage.service.EspionageService;
import ru.neverland.mintespionage.service.MessageService;
import ru.neverland.mintespionage.util.ColorUtil;
import ru.neverland.mintespionage.util.TimeUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EspionageMenuManager implements Listener {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
    private final JavaPlugin plugin;private final TownyHook towny;private final EspionageService service;private final ItemsAdderHook itemsAdder;private final MessageService messages;
    private final NamespacedKey actionKey,targetKey,operationKey,reportKey;
    public EspionageMenuManager(JavaPlugin plugin,TownyHook towny,EspionageService service,ItemsAdderHook itemsAdder,MessageService messages){
        this.plugin=plugin;this.towny=towny;this.service=service;this.itemsAdder=itemsAdder;this.messages=messages;
        actionKey=new NamespacedKey(plugin,"action");targetKey=new NamespacedKey(plugin,"target");operationKey=new NamespacedKey(plugin,"operation");reportKey=new NamespacedKey(plugin,"report");
    }
    public void open(Player player){
        Town town=towny.town(player);if(town==null){messages.send(player,"no-town");return;}EspionageMenuHolder holder=new EspionageMenuHolder(town.getUUID(),null,EspionageMenuHolder.Type.MAIN);
        Inventory inventory=create(holder,54,title("gui.main-title","Городская разведка",null));fill(inventory);var data=service.data(town);int active=service.repository().active(town.getUUID()).size();
        inventory.setItem(10,action("targets",Material.SPYGLASS,"&#63E6BE&lНачать операцию",List.of("&7Выбрать город и тип разведки.","","&#55FF55Нажмите, чтобы выбрать цель")));
        inventory.setItem(12,action("reports",Material.WRITTEN_BOOK,"&#65B8FF&lРазведывательные отчёты",List.of("&7Непрочитанных: &f"+service.unread(town.getUUID()),"&7Всего: &f"+service.repository().reports(town.getUUID()).size(),"","&#55FF55Нажмите, чтобы открыть")));
        inventory.setItem(14,action("active",Material.CLOCK,"&#FFD45A&lАктивные операции",List.of("&7Агенты заняты: &f"+active+"&7/&f"+service.activeLimit(town),"","&#55FF55Нажмите, чтобы открыть")));
        inventory.setItem(28,action("upgrade-network",Material.ENDER_EYE,"&#C58CFF&lРазведывательная сеть",List.of("&7Уровень: &f"+data.networkLevel()+"&7/&f"+plugin.getConfig().getInt("network.maximum-level",5),"&7Увеличивает шанс успеха,","&7ускоряет агентов и расширяет лимит.","","&#FFFF55Нажмите для улучшения")));
        inventory.setItem(34,action("upgrade-defense",Material.SHIELD,"&#FF7777&lКонтрразведка",List.of("&7Уровень: &f"+data.defenseLevel()+"&7/&f"+plugin.getConfig().getInt("counterintelligence.maximum-level",5),"&7Общая защита: &f"+Math.round(service.defenseStrength(town)*100)+"%","&7Снижает успех и повышает","&7обнаружение чужих агентов.","","&#FFFF55Нажмите для улучшения")));
        inventory.setItem(49,item(Material.PAPER,"&#AAAAAAСостояние города",List.of("&7Город: &f"+town.getName(),"&7Казна: &#FFD45A"+service.economy().format(service.economy().balance(town)),"&7Сеть: &f"+data.networkLevel(),"&7Контрразведка: &f"+data.defenseLevel())));player.openInventory(inventory);
    }
    public void openTargets(Player player){
        Town own=towny.town(player);if(own==null){messages.send(player,"no-town");return;}EspionageMenuHolder holder=new EspionageMenuHolder(own.getUUID(),null,EspionageMenuHolder.Type.TARGETS);Inventory inventory=create(holder,54,title("gui.targets-title","Выбор цели",null));fill(inventory);int slot=0;
        List<Town> targets=towny.towns().stream().filter(t->!t.getUUID().equals(own.getUUID())).sorted(Comparator.comparing(Town::getName,String.CASE_INSENSITIVE_ORDER)).toList();
        for(Town target:targets){if(slot>=45)break;ItemStack stack=item(Material.PLAYER_HEAD,"&#FFFFFF"+target.getName(),List.of("&7Жителей: &f"+target.getResidents().size(),"&7Участков: &f"+target.getTownBlocks().size(),"&7Нация: &f"+towny.nation(target),"","&#55FF55Нажмите, чтобы выбрать"));inventory.setItem(slot++,keyed(stack,targetKey,target.getUUID().toString()));}
        inventory.setItem(49,action("back",Material.ARROW,"&fНазад",List.of()));player.openInventory(inventory);
    }
    public void openOperations(Player player,Town target){
        Town own=towny.town(player);if(own==null||target==null){messages.send(player,"town-not-found",Map.of("town","?"));return;}EspionageMenuHolder holder=new EspionageMenuHolder(own.getUUID(),target.getUUID(),EspionageMenuHolder.Type.OPERATIONS);Inventory inventory=create(holder,45,title("gui.operations-title","Операция против %town%",target.getName()));fill(inventory);
        for(OperationDefinition definition:service.registry().all()){EspionageService.Preview preview=service.preview(own,target,definition);List<String> lore=new ArrayList<>();definition.description().forEach(line->lore.add("&7"+line));lore.add("");lore.add("&7Стоимость: &#FFD45A"+service.economy().format(definition.cost()));lore.add("&7Время: &f"+TimeUtil.format(preview.duration()));lore.add("&7Шанс успеха: &#55FF55"+Math.round(preview.successChance()*100)+"%");lore.add("&7Риск обнаружения: &#FF7777"+Math.round(preview.detectionChance()*100)+"%");if(preview.cooldown()>0)lore.add("&7Перезарядка: &#FFFF55"+TimeUtil.format(preview.cooldown()));lore.add("");lore.add(preview.cooldown()>0?"&#FF7777Операция недоступна":"&#55FF55Нажмите, чтобы начать");ItemStack stack=itemsAdder.item(definition.itemsAdderIcon());if(stack==null)stack=new ItemStack(definition.icon());stack=decorate(stack,definition.name(),lore);inventory.setItem(Math.max(0,Math.min(35,definition.slot())),keyed(stack,operationKey,definition.id()));}
        inventory.setItem(40,action("targets",Material.ARROW,"&fНазад к городам",List.of()));player.openInventory(inventory);
    }
    public void openReports(Player player){
        Town town=towny.town(player);if(town==null){messages.send(player,"no-town");return;}EspionageMenuHolder holder=new EspionageMenuHolder(town.getUUID(),null,EspionageMenuHolder.Type.REPORTS);Inventory inventory=create(holder,54,title("gui.reports-title","Разведывательные отчёты",null));fill(inventory);int slot=0;long now=System.currentTimeMillis();
        for(IntelReport report:service.repository().reports(town.getUUID())){if(slot>=45)break;OperationDefinition definition=service.registry().get(report.type());List<String> lore=new ArrayList<>();lore.add("&7Цель: &f"+report.targetName());lore.add("&7Дата: &f"+DATE.format(Instant.ofEpochMilli(report.createdAt())));lore.add("&7Действует ещё: &f"+TimeUtil.format(report.expiresAt()-now));lore.add("");lore.addAll(report.lines());lore.add("");lore.add(report.read()?"&8Прочитано":"&#55FF55Новое — нажмите, чтобы прочитать");Material icon=report.read()?Material.PAPER:Material.ENCHANTED_BOOK;ItemStack stack=item(icon,definition==null?report.type():definition.name(),lore);inventory.setItem(slot++,keyed(stack,reportKey,report.id().toString()));}
        inventory.setItem(49,action("back",Material.ARROW,"&fНазад",List.of()));player.openInventory(inventory);
    }
    public void openActive(Player player){
        Town town=towny.town(player);if(town==null){messages.send(player,"no-town");return;}EspionageMenuHolder holder=new EspionageMenuHolder(town.getUUID(),null,EspionageMenuHolder.Type.ACTIVE);Inventory inventory=create(holder,45,title("gui.active-title","Активные операции",null));fill(inventory);int slot=0;
        for(SpyOperation operation:service.repository().active(town.getUUID())){OperationDefinition definition=service.registry().get(operation.type());inventory.setItem(slot++,item(definition==null?Material.CLOCK:definition.icon(),definition==null?operation.type():definition.name(),List.of("&7Цель: &f"+operation.targetName(),"&7Осталось: &f"+TimeUtil.format(operation.completesAt()-System.currentTimeMillis()),"&7Шанс успеха: &#55FF55"+Math.round(operation.successChance()*100)+"%","&7ID: &8"+operation.shortId())));if(slot>=36)break;}
        inventory.setItem(40,action("back",Material.ARROW,"&fНазад",List.of()));player.openInventory(inventory);
    }
    @EventHandler public void onClick(InventoryClickEvent event){
        if(!(event.getInventory().getHolder() instanceof EspionageMenuHolder holder))return;event.setCancelled(true);if(!(event.getWhoClicked() instanceof Player player))return;Town own=towny.town(player);if(own==null||!own.getUUID().equals(holder.townId())){player.closeInventory();return;}ItemStack clicked=event.getCurrentItem();if(clicked==null||!clicked.hasItemMeta())return;ItemMeta meta=clicked.getItemMeta();String action=meta.getPersistentDataContainer().get(actionKey,PersistentDataType.STRING);
        if("back".equals(action)){open(player);return;}if("targets".equals(action)){openTargets(player);return;}if("reports".equals(action)){openReports(player);return;}if("active".equals(action)){openActive(player);return;}
        if(action!=null&&action.startsWith("upgrade-")){if(!canManage(player,own))return;boolean network=action.endsWith("network");sendUpgrade(player,service.upgrade(own,network),network);open(player);return;}
        String targetRaw=meta.getPersistentDataContainer().get(targetKey,PersistentDataType.STRING);if(targetRaw!=null){try{openOperations(player,towny.town(UUID.fromString(targetRaw)));}catch(IllegalArgumentException ignored){}return;}
        String operation=meta.getPersistentDataContainer().get(operationKey,PersistentDataType.STRING);if(operation!=null){if(!canManage(player,own))return;Town target=towny.town(holder.targetId());OperationDefinition definition=service.registry().get(operation);if(target!=null&&definition!=null){sendStart(player,service.start(player,target,definition),definition,target);openOperations(player,target);}return;}
        String reportRaw=meta.getPersistentDataContainer().get(reportKey,PersistentDataType.STRING);if(reportRaw!=null)for(IntelReport report:service.repository().reports(own.getUUID()))if(report.id().toString().equals(reportRaw)){service.read(report);openReports(player);break;}
    }
    public void sendStart(Player player,EspionageService.StartOutcome outcome,OperationDefinition definition,Town target){
        switch(outcome.status()){
            case SUCCESS->messages.send(player,"operation-started",Map.of("operation",ColorUtil.strip(definition.name()),"target",target.getName(),"time",TimeUtil.format(outcome.remaining())));
            case SELF_TARGET->messages.send(player,"self-target");case LIMIT->messages.send(player,"operation-limit",Map.of("active",service.repository().active(towny.town(player).getUUID()).size(),"limit",service.activeLimit(towny.town(player))));
            case DUPLICATE->messages.send(player,"operation-duplicate");case COOLDOWN->messages.send(player,"operation-cooldown",Map.of("time",TimeUtil.format(outcome.remaining())));
            case NO_MONEY->messages.send(player,"not-enough-treasury",Map.of("amount",service.economy().format(outcome.required())));case ECONOMY_ERROR->messages.send(player,"economy-error");case NO_TOWN->messages.send(player,"no-town");
        }
    }
    public void sendUpgrade(Player player,EspionageService.UpgradeOutcome result,boolean network){switch(result.status()){case SUCCESS->messages.send(player,"upgrade-success",Map.of("type",network?"Разведывательная сеть":"Контрразведка","level",result.level(),"cost",service.economy().format(result.cost())));case MAXIMUM->messages.send(player,"upgrade-maximum");case NO_MONEY->messages.send(player,"not-enough-treasury",Map.of("amount",service.economy().format(result.cost())));case ECONOMY_ERROR->messages.send(player,"economy-error");}}
    private boolean canManage(Player player,Town town){if(!player.hasPermission("mintespionage.manage")||!towny.isManager(player,town)){messages.send(player,"only-manager");return false;}return true;}
    private Inventory create(EspionageMenuHolder holder,int size,String title){Inventory inventory=Bukkit.createInventory(holder,size,ColorUtil.color(title));holder.inventory(inventory);return inventory;}
    private String title(String path,String fallback,String town){String value=plugin.getConfig().getString(path,fallback);return town==null?value:value.replace("%town%",town);}
    private void fill(Inventory inventory){Material material=Material.matchMaterial(plugin.getConfig().getString("gui.filler","BLACK_STAINED_GLASS_PANE"));ItemStack filler=item(material==null?Material.BLACK_STAINED_GLASS_PANE:material," ",List.of());for(int i=0;i<inventory.getSize();i++)inventory.setItem(i,filler);}
    private ItemStack action(String action,Material material,String name,List<String> lore){return keyed(item(material,name,lore),actionKey,action);}
    private ItemStack keyed(ItemStack stack,NamespacedKey key,String value){ItemMeta meta=stack.getItemMeta();meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,value);stack.setItemMeta(meta);return stack;}
    private ItemStack item(Material material,String name,List<String> lore){return decorate(new ItemStack(material==null?Material.PAPER:material),name,lore);}
    private ItemStack decorate(ItemStack stack,String name,List<String> lore){ItemMeta meta=stack.getItemMeta();meta.setDisplayName(ColorUtil.color(name));meta.setLore(lore.stream().map(ColorUtil::color).toList());meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);stack.setItemMeta(meta);return stack;}
}
