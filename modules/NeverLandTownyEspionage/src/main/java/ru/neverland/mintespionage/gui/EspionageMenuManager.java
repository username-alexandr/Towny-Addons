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
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'МСК'").withZone(ZoneId.of("Europe/Moscow"));
    private final JavaPlugin plugin;private final TownyHook towny;private final EspionageService service;private final ItemsAdderHook itemsAdder;private final MessageService messages;
    private final NamespacedKey actionKey,targetKey,operationKey,reportKey;
    public EspionageMenuManager(JavaPlugin plugin,TownyHook towny,EspionageService service,ItemsAdderHook itemsAdder,MessageService messages){
        this.plugin=plugin;this.towny=towny;this.service=service;this.itemsAdder=itemsAdder;this.messages=messages;
        actionKey=new NamespacedKey(plugin,"action");targetKey=new NamespacedKey(plugin,"target");operationKey=new NamespacedKey(plugin,"operation");reportKey=new NamespacedKey(plugin,"report");
    }
    public void open(Player player){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        Town town=viewTown(player);if(town==null){messages.send(player,"no-town");return;}EspionageMenuHolder holder=new EspionageMenuHolder(town.getUUID(),null,EspionageMenuHolder.Type.MAIN);
        Inventory inventory=create(holder,54,title("gui.main-title","Городская разведка",null));fill(inventory);var data=service.data(town);int active=service.repository().active(town.getUUID()).size();
        inventory.setItem(10,action("targets",Material.SPYGLASS,"&#63E6BE&lНачать операцию",List.of("&7Выбрать город и тип разведки.","","&#55FF55Нажмите, чтобы выбрать цель")));
        inventory.setItem(12,action("reports",Material.WRITTEN_BOOK,"&#65B8FF&lРазведывательные отчёты",List.of("&7Непрочитанных: &f"+service.unread(town.getUUID()),"&7Всего: &f"+service.repository().reports(town.getUUID()).size(),"","&#55FF55Нажмите, чтобы открыть")));
        inventory.setItem(14,action("active",Material.CLOCK,"&#FFD45A&lАктивные операции",List.of("&7Агенты заняты: &f"+active+"&7/&f"+service.activeLimit(town),"","&#55FF55Нажмите, чтобы открыть")));
        inventory.setItem(28,action("upgrade-network",Material.ENDER_EYE,"&#C58CFF&lРазведывательная сеть",List.of("&7Уровень: &f"+data.networkLevel()+"&7/&f"+plugin.getConfig().getInt("network.maximum-level",5),"&7Увеличивает шанс успеха,","&7ускоряет агентов и расширяет лимит.","","&#FFFF55Нажмите для улучшения")));
        inventory.setItem(34,action("upgrade-defense",Material.SHIELD,"&#FF7777&lКонтрразведка",List.of("&7Уровень: &f"+data.defenseLevel()+"&7/&f"+plugin.getConfig().getInt("counterintelligence.maximum-level",5),"&7Общая защита: &f"+defenseText(town),"&7Снижает успех и повышает","&7обнаружение чужих агентов.","","&#FFFF55Нажмите для улучшения")));
        inventory.setItem(32,action("defense",Material.IRON_BARS,"&#65B8FF&lОбнаруженные агенты",List.of("&7Только подтверждённые обнаружения.","&7Тайные операции не раскрываются.","&7Защита также делает оценки врага грубее.","","&#55FF55Открыть историю контрразведки")));
        inventory.setItem(30,action("purchases",Material.GOLD_INGOT,"&eОплаты разведки",List.of("&7Подтверждение миссий и улучшений.","&7Состояние и история покупок города.","&7"+service.purchases().diagnostic())));
        inventory.setItem(49,item(Material.PAPER,"&#AAAAAAСостояние города",List.of("&7Город: &f"+town.getName(),"&7Казна: &#FFD45A"+service.economy().balanceText(town),"&7Сеть: &f"+data.networkLevel(),"&7Контрразведка: &f"+data.defenseLevel())));player.openInventory(inventory);
    } }
    public void openTargets(Player player){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        Town own=viewTown(player);if(own==null){messages.send(player,"no-town");return;}EspionageMenuHolder holder=new EspionageMenuHolder(own.getUUID(),null,EspionageMenuHolder.Type.TARGETS);Inventory inventory=create(holder,54,title("gui.targets-title","Выбор цели",null));fill(inventory);int slot=0;
        List<Town> targets=towny.towns().stream().filter(t->!t.getUUID().equals(own.getUUID())).sorted(Comparator.comparing(Town::getName,String.CASE_INSENSITIVE_ORDER)).toList();
        for(Town target:targets){if(slot>=45)break;ItemStack stack=item(Material.PLAYER_HEAD,"&#FFFFFF"+target.getName(),List.of("&7Жителей: &f"+target.getResidents().size(),"&7Участков: &f"+target.getTownBlocks().size(),"&7Нация: &f"+towny.nation(target),"","&#55FF55Нажмите, чтобы выбрать"));inventory.setItem(slot++,keyed(stack,targetKey,target.getUUID().toString()));}
        inventory.setItem(49,action("back",Material.ARROW,"&fНазад",List.of()));player.openInventory(inventory);
    } }
    public void openOperations(Player player,Town target){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        Town own=viewTown(player);if(own==null||target==null){messages.send(player,"town-not-found",Map.of("town","?"));return;}EspionageMenuHolder holder=new EspionageMenuHolder(own.getUUID(),target.getUUID(),EspionageMenuHolder.Type.OPERATIONS);Inventory inventory=create(holder,45,title("gui.operations-title","Операция против %town%",target.getName()));fill(inventory);
        for(OperationDefinition definition:service.registry().all()){EspionageService.Preview preview=service.preview(own,target,definition);List<String> lore=new ArrayList<>();definition.description().forEach(line->lore.add("&7"+line));lore.add("");lore.add("&7Стоимость: &#FFD45A"+service.economy().format(definition.cost()));lore.add("&7Время: &f"+TimeUtil.format(preview.duration()));lore.add("&7Шанс успеха: &#55FF55"+Math.round(preview.successChance()*100)+"%");lore.add("&7Риск обнаружения: &#FF7777"+Math.round(preview.detectionChance()*100)+"%");if(preview.cooldown()>0)lore.add("&7Перезарядка: &#FFFF55"+TimeUtil.format(preview.cooldown()));lore.add("");lore.add(preview.cooldown()>0?"&#FF7777Операция недоступна":"&#55FF55Нажмите, чтобы начать");ItemStack stack=itemsAdder.item(definition.itemsAdderIcon());if(stack==null)stack=new ItemStack(definition.icon());stack=decorate(stack,definition.name(),lore);inventory.setItem(Math.max(0,Math.min(35,definition.slot())),keyed(stack,operationKey,definition.id()));}
        inventory.setItem(40,action("targets",Material.ARROW,"&fНазад к городам",List.of()));player.openInventory(inventory);
    } }
    public void openReports(Player player){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        Town town=viewTown(player);if(town==null){messages.send(player,"no-town");return;}EspionageMenuHolder holder=new EspionageMenuHolder(town.getUUID(),null,EspionageMenuHolder.Type.REPORTS);Inventory inventory=create(holder,54,title("gui.reports-title","Разведывательные отчёты",null));fill(inventory);int slot=0;long now=System.currentTimeMillis();
        for(IntelReport report:service.repository().reports(town.getUUID())){if(slot>=45)break;OperationDefinition definition=service.registry().get(report.type());List<String> lore=new ArrayList<>();lore.add("&7Цель: &f"+report.targetName());lore.add("&7Дата: &f"+DATE.format(Instant.ofEpochMilli(report.createdAt())));lore.add("&7Действует ещё: &f"+TimeUtil.format(report.expiresAt()-now));lore.add("");lore.addAll(report.lines().stream().filter(line->!line.contains("Достоверность операции")).toList());if(service.intelligence().report(town.getUUID(),report.id()).isEmpty())lore.add(service.intelligence().archiveReadable()?"&8Старый отчёт: время и источник отдельных сведений не записаны.":"&eЖурнал источников временно недоступен; сам отчёт сохранён.");lore.add("");lore.add(report.read()?"&#55FF55Открыть источники и сверку":"&#55FF55Новое — открыть источники и сверку");Material icon=report.read()?Material.PAPER:Material.ENCHANTED_BOOK;ItemStack stack=item(icon,definition==null?report.type():definition.name(),lore);inventory.setItem(slot++,keyed(stack,reportKey,report.id().toString()));}
        inventory.setItem(49,action("back",Material.ARROW,"&fНазад",List.of()));player.openInventory(inventory);
    } }
    public void openActive(Player player){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        Town town=viewTown(player);if(town==null){messages.send(player,"no-town");return;}EspionageMenuHolder holder=new EspionageMenuHolder(town.getUUID(),null,EspionageMenuHolder.Type.ACTIVE);Inventory inventory=create(holder,45,title("gui.active-title","Активные операции",null));fill(inventory);int slot=0;
        for(SpyOperation operation:service.repository().active(town.getUUID())){OperationDefinition definition=service.registry().get(operation.type());inventory.setItem(slot++,item(definition==null?Material.CLOCK:definition.icon(),definition==null?operation.type():definition.name(),List.of("&7Цель: &f"+operation.targetName(),"&7Осталось: &f"+(operation.timer().paused()?"Пауза • ":"")+TimeUtil.format(operation.timer().remaining(System.currentTimeMillis())),"&7Шанс успеха: &#55FF55"+Math.round(operation.successChance()*100)+"%","&7ID: &8"+operation.shortId())));if(slot>=36)break;}
        inventory.setItem(40,action("back",Material.ARROW,"&fНазад",List.of()));player.openInventory(inventory);
    } }
    public void openReport(Player player,UUID id,boolean compare){try(var guard=ru.neverland.core.SuiteState.guard()){
        Town own=viewTown(player);if(own==null)return;
        IntelReport report=service.repository().reports(own.getUUID()).stream().filter(r->r.id().equals(id)).findFirst().orElse(null);
        if(report==null){player.sendMessage("§eОтчёт уже удалён из архива. Откройте список заново.");openReports(player);return;}
        service.read(report);var holder=new EspionageMenuHolder(own.getUUID(),id,EspionageMenuHolder.Type.REPORT_DETAIL);var inventory=create(holder,54,"Разведка: "+report.targetName());fill(inventory);
        var receipt=service.intelligence().report(own.getUUID(),id).orElse(null);
        if(receipt==null){inventory.setItem(13,item(Material.PAPER,"&eИсторический отчёт",service.intelligence().archiveReadable()?List.of("&7Результат сохранён до обновления разведки.","&7Источник и точное время отдельных сведений","&7тогда не записывались. Сверка недоступна."):List.of("&eЖурнал источников временно недоступен.","&7Основной отчёт сохранён; источники и сверка","&7станут доступны после восстановления журнала.")));inventory.setItem(22,item(Material.WRITTEN_BOOK,"&fСохранённые сведения",report.lines().stream().filter(line->!line.contains("Достоверность операции")).toList()));}
        else{
            var checks=compare?service.intelligence().compare(receipt):List.<ru.neverland.mintespionage.intelligence.IntelEvidence.Check>of();int slot=0;
            for(var fact:receipt.facts()){
                var lore=new ArrayList<String>();lore.add("&7Сведения: &f"+fact.display());lore.add("&7Наблюдение: &f"+DATE.format(Instant.ofEpochMilli(fact.sampledAt())));lore.add("&7Источник: &f"+fact.source());lore.add("&7"+fact.explanation());
                for(var check:checks)if(check.key().equals(fact.key())){lore.add("");lore.add("&eСверка: "+switch(check.result()){case OVERLAPPING->"совместимые сведения";case CHANGED->"сведения отличаются";case UNKNOWN->"недостаточно данных";case NO_LATER_REPORT->"нет более позднего отчёта";});lore.add("&7"+check.explanation());if(check.laterReport()!=null){lore.add("&7Сравнили с: &f"+DATE.format(Instant.ofEpochMilli(check.laterAt())));lore.add("&8Отчёт: "+check.laterReport());}}
                inventory.setItem(slot++,item(fact.availability()==ru.neverland.mintespionage.intelligence.IntelEvidence.Availability.KNOWN?Material.MAP:Material.GRAY_DYE,"&f"+fact.label(),lore));
            }
            inventory.setItem(49,item(Material.CLOCK,"&fКогда и как получены сведения",List.of("&7Город: &f"+report.targetName(),"&7Возраст: &f"+TimeUtil.format(Math.max(0,System.currentTimeMillis()-receipt.sampledAt())),receipt.expired(System.currentTimeMillis())?"&eСрок актуальности истёк":"&7Срок: &f"+DATE.format(Instant.ofEpochMilli(receipt.expiresAt())),"&7Сеть при наблюдении: &f"+receipt.profile().network(),"&7Числа могут быть округлены.","&7Успех миссии не означает точность всех данных.","&8Миссия: "+receipt.mission())));
            inventory.setItem(50,action("verify",Material.SPYGLASS,"&aСверить с более поздними отчётами",List.of("&7Сравниваются только уже полученные сведения","&7вашего города об этой же цели.","&7Бесплатного повторного наблюдения не происходит.","&7Различие может означать изменения в городе.")));
        }
        inventory.setItem(45,action("reports",Material.ARROW,"&fНазад к отчётам",List.of()));player.openInventory(inventory);
    }}
    public void openDefense(Player player){try(var guard=ru.neverland.core.SuiteState.guard()){
        Town own=viewTown(player);if(own==null)return;var holder=new EspionageMenuHolder(own.getUUID(),null,EspionageMenuHolder.Type.ALERTS);var inventory=create(holder,54,"История контрразведки");fill(inventory);int slot=0;
        for(var receipt:service.intelligence().alerts(own.getUUID())){if(slot>=45)break;inventory.setItem(slot++,item(Material.SHIELD,"&eОбнаружена чужая разведка",List.of("&7Когда: &f"+DATE.format(Instant.ofEpochMilli(receipt.sampledAt())),"&7Операция: &f"+service.operationName(receipt.missionType()),"&7Город отправителя: &f"+(receipt.revealAttacker()?receipt.attackerName():"не установлен"),"&7Это подтверждённое обнаружение,","&7а не догадка о чужих действиях.","&8Миссия: "+receipt.mission())));}
        if(slot==0)inventory.setItem(22,item(Material.GRAY_DYE,service.intelligence().archiveReadable()?"&7Сохранённых обнаружений нет":"&eИстория временно недоступна",List.of("&7Это не подтверждает отсутствие чужой разведки.","&7История ведётся для новых завершённых миссий.")));
        inventory.setItem(49,action("back",Material.ARROW,"&fНазад",List.of()));player.openInventory(inventory);
    }}
    public void openPurchases(Player player){try(var guard=ru.neverland.core.SuiteState.guard()){Town own=viewTown(player);if(own==null||!canManage(player,own))return;var holder=new EspionageMenuHolder(own.getUUID(),null,EspionageMenuHolder.Type.PURCHASES);var inv=create(holder,54,"Оплаты разведки");fill(inv);int slot=0;for(var p:service.purchases().history(own.getUUID())){var t=p.terms();var name=switch(t.kind()){case MISSION->"Миссия: "+service.operationName(t.type());case NETWORK->"Улучшение сети";case DEFENSE->"Улучшение контрразведки";};inv.setItem(slot++,item(p.pending()?Material.CLOCK:Material.PAPER,"&f"+name,List.of("&7"+ru.neverland.mintespionage.service.SpyPurchaseController.label(p.phase()),"&7Стоимость: &f"+service.economy().format(t.cents()/100.0),"&7Заказал: &f"+t.actorName(),"&7Дата: &f"+DATE.format(Instant.ofEpochMilli(t.created())),"&7Условия сохранены при заказе.","&7Оплаченную ожидающую покупку можно отменить:","&7/espionage refund <UUID> confirm","&8Квитанция: "+t.id(),"&8Возврат: "+(p.phase()==ru.neverland.mintespionage.service.SpyPurchases.Phase.REFUND_PENDING||p.phase()==ru.neverland.mintespionage.service.SpyPurchases.Phase.REFUNDED?p.refundId():"ещё не запрошен"))));}inv.setItem(48,action("purchases",Material.CLOCK,"&eОбновить",List.of("&7"+service.purchases().diagnostic())));inv.setItem(49,action("back",Material.ARROW,"&fНазад",List.of()));player.openInventory(inv);}}
    @EventHandler public void onClick(InventoryClickEvent event){try(var guard=ru.neverland.core.SuiteState.guard()){
        var top=event.getView().getTopInventory();if(!(top.getHolder() instanceof EspionageMenuHolder holder))return;event.setCancelled(true);
        if(ru.neverland.core.SuiteState.frozen(plugin.getName())||!(event.getWhoClicked() instanceof Player player)||event.getRawSlot()<0||event.getRawSlot()>=top.getSize())return;
        var current=event.getCurrentItem();if(current==null||!current.hasItemMeta())return;var clicked=current.clone();
        ru.neverland.core.SuiteScheduler.entity(plugin,player,()->{if(!player.isOnline()||player.getOpenInventory().getTopInventory()!=top||ru.neverland.core.SuiteState.frozen(plugin.getName()))return;
            Town own=viewTown(player);if(own==null||!own.getUUID().equals(holder.townId())){player.closeInventory();return;}
            try{handleClick(player,own,holder,clicked.getItemMeta());}catch(IllegalStateException unavailable){player.sendMessage("§e"+unavailable.getMessage());}
        });
    }}
    @EventHandler public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof EspionageMenuHolder)event.setCancelled(true);}
    private void handleClick(Player player,Town own,EspionageMenuHolder holder,ItemMeta meta){
        String action=meta.getPersistentDataContainer().get(actionKey,PersistentDataType.STRING);
        if("purchases".equals(action)){openPurchases(player);return;}if("back".equals(action)){open(player);return;}if("targets".equals(action)){openTargets(player);return;}if("reports".equals(action)){openReports(player);return;}if("active".equals(action)){openActive(player);return;}if("defense".equals(action)){openDefense(player);return;}
        if("verify".equals(action)&&holder.type()==EspionageMenuHolder.Type.REPORT_DETAIL){openReport(player,holder.targetId(),true);return;}
        if(action!=null&&action.startsWith("upgrade-")){if(!canManage(player,own))return;boolean network=action.endsWith("network");sendUpgrade(player,service.upgrade(player,own,network),network);open(player);return;}
        String targetRaw=meta.getPersistentDataContainer().get(targetKey,PersistentDataType.STRING);if(targetRaw!=null){try{openOperations(player,towny.town(UUID.fromString(targetRaw)));}catch(IllegalArgumentException ignored){}return;}
        String operation=meta.getPersistentDataContainer().get(operationKey,PersistentDataType.STRING);if(operation!=null){if(!canManage(player,own))return;Town target=towny.town(holder.targetId());OperationDefinition definition=service.registry().get(operation);if(target!=null&&definition!=null){sendStart(player,service.start(player,target,definition),definition,target);openOperations(player,target);}return;}
        String reportRaw=meta.getPersistentDataContainer().get(reportKey,PersistentDataType.STRING);if(reportRaw!=null)try{openReport(player,UUID.fromString(reportRaw),false);}catch(IllegalArgumentException ignored){}
    }
    private Town viewTown(Player player){if(!player.hasPermission("mintespionage.use")){messages.send(player,"no-permission");return null;}return towny.town(player);}
    private String defenseText(Town town){try{return Math.round(service.defenseStrength(town)*100)+"%";}catch(RuntimeException unavailable){return "временно неизвестна";}}
    public void sendStart(Player player,EspionageService.StartOutcome outcome,OperationDefinition definition,Town target){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        switch(outcome.status()){
            case PENDING->player.sendMessage("§eЗапрос сохраняется. Миссия начнётся после подтверждения оплаты; состояние — в разделе «Оплаты разведки».");
            case PAYMENT_UNAVAILABLE->player.sendMessage("§eПлатные миссии недоступны: казна не подключена или приостановлена. Списание не начиналось.");
            case STORAGE_BUSY->player.sendMessage("§eРазведка сохраняет результат или временно приостановлена. Новый заказ не принят; проверьте раздел оплат.");
            case SUCCESS->messages.send(player,"operation-started",Map.of("operation",ColorUtil.strip(definition.name()),"target",target.getName(),"time",TimeUtil.format(outcome.remaining())));
            case NEWCOMER_PROTECTED->player.sendMessage("§bОперация недоступна, пока у одного из городов действует щит новичка: "+TimeUtil.format(outcome.remaining()));
            case PROTECTION_UNAVAILABLE->player.sendMessage("§eРеестр защиты новичков недоступен. Средства не списаны.");
            case DIPLOMACY_BLOCKED->player.sendMessage(ru.neverland.mintespionage.util.ColorUtil.color("&cОперация запрещена дипломатическим договором или реестр дипломатии недоступен."));case SELF_TARGET->messages.send(player,"self-target");case LIMIT->messages.send(player,"operation-limit",Map.of("active",service.repository().active(towny.town(player).getUUID()).size(),"limit",service.activeLimit(towny.town(player))));
            case DUPLICATE->messages.send(player,"operation-duplicate");case COOLDOWN->messages.send(player,"operation-cooldown",Map.of("time",TimeUtil.format(outcome.remaining())));
            case NO_MONEY->messages.send(player,"not-enough-treasury",Map.of("amount",service.economy().format(outcome.required())));case ECONOMY_ERROR->messages.send(player,"economy-error");case NO_TOWN->messages.send(player,"no-town");
        }
    } }
    public void sendUpgrade(Player player,EspionageService.UpgradeOutcome result,boolean network){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {switch(result.status()){case PENDING->player.sendMessage("§eЗапрос сохраняется. Улучшение появится после подтверждения оплаты; состояние — в разделе «Оплаты разведки».");case PAYMENT_UNAVAILABLE->player.sendMessage("§eПлатные улучшения недоступны: казна не подключена или приостановлена. Списание не начиналось.");case STORAGE_BUSY->player.sendMessage("§eРазведка сохраняет результат или временно приостановлена. Новый заказ не принят; проверьте раздел оплат.");case SUCCESS->messages.send(player,"upgrade-success",Map.of("type",network?"Разведывательная сеть":"Контрразведка","level",result.level(),"cost",service.economy().format(result.cost())));case MAXIMUM->messages.send(player,"upgrade-maximum");case NO_MONEY->messages.send(player,"not-enough-treasury",Map.of("amount",service.economy().format(result.cost())));case ECONOMY_ERROR->messages.send(player,"economy-error");}} }
    private boolean canManage(Player player,Town town){if(!player.hasPermission("mintespionage.manage")||!towny.isManager(player,town)){messages.send(player,"only-manager");return false;}return true;}
    private Inventory create(EspionageMenuHolder holder,int size,String title){Inventory inventory=ru.neverland.core.MenuStyle.inventory(plugin, holder,size,ColorUtil.color(title));holder.inventory(inventory);return inventory;}
    private String title(String path,String fallback,String town){String value=plugin.getConfig().getString(path,fallback);return town==null?value:value.replace("%town%",town);}
    private void fill(Inventory inventory){Material material=Material.matchMaterial(plugin.getConfig().getString("gui.filler","BLACK_STAINED_GLASS_PANE"));ItemStack filler=item(material==null?Material.BLACK_STAINED_GLASS_PANE:material," ",List.of());for(int i=0;i<inventory.getSize();i++)inventory.setItem(i,filler);}
    private ItemStack action(String action,Material material,String name,List<String> lore){return keyed(item(material,name,lore),actionKey,action);}
    private ItemStack keyed(ItemStack stack,NamespacedKey key,String value){ItemMeta meta=stack.getItemMeta();meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,value);stack.setItemMeta(meta);return stack;}
    private ItemStack item(Material material,String name,List<String> lore){return decorate(new ItemStack(material==null?Material.PAPER:material),name,lore);}
    private ItemStack decorate(ItemStack stack,String name,List<String> lore){ItemMeta meta=stack.getItemMeta();meta.setDisplayName(ru.neverland.core.MenuStyle.nameLegacy(ColorUtil.color(name)));meta.setLore(ru.neverland.core.MenuStyle.loreStrings(lore.stream().map(ColorUtil::color).toList()));meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);stack.setItemMeta(meta);return stack;}
}
