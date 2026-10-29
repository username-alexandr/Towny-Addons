package ru.neverland.townyideologies.gui;

import com.palmergames.bukkit.towny.object.Town;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townyideologies.integration.ItemsAdderHook;
import ru.neverland.townyideologies.integration.TownyHook;
import ru.neverland.townyideologies.model.IdeologyDefinition;
import ru.neverland.townyideologies.model.TownIdeology;
import ru.neverland.townyideologies.service.EconomyService;
import ru.neverland.townyideologies.service.IdeologyRegistry;
import ru.neverland.townyideologies.service.IdeologyService;
import ru.neverland.townyideologies.service.MessageService;
import ru.neverland.townyideologies.reforms.ReformBook;
import ru.neverland.townyideologies.util.ColorUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MenuManager implements Listener {
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final IdeologyRegistry registry;
    private final IdeologyService ideologies;
    private final EconomyService economy;
    private final ItemsAdderHook itemsAdder;
    private final MessageService messages;
    private final NamespacedKey ideologyKey;
    private final NamespacedKey actionKey;

    public MenuManager(JavaPlugin plugin, TownyHook towny, IdeologyRegistry registry,
                       IdeologyService ideologies, EconomyService economy,
                       ItemsAdderHook itemsAdder, MessageService messages) {
        this.plugin = plugin;
        this.towny = towny;
        this.registry = registry;
        this.ideologies = ideologies;
        this.economy = economy;
        this.itemsAdder = itemsAdder;
        this.messages = messages;
        this.ideologyKey = new NamespacedKey(plugin, "ideology");
        this.actionKey = new NamespacedKey(plugin, "action");
    }

    public void openList(Player player) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        Town town = towny.town(player);
        if (town == null) {
            messages.send(player, "town-required");
            return;
        }
        String title = plugin.getConfig().getString("settings.gui.list-title", "&8Идеологии города");
        Inventory inventory = ru.neverland.core.MenuStyle.inventory(plugin, new IdeologyListHolder(), 27, ColorUtil.component(title));
        fill(inventory);
        TownIdeology current = ideologies.get(town).orElse(null);
        for (IdeologyDefinition definition : registry.all()) {
            inventory.setItem(definition.slot(), ideologyIcon(definition, current));
        }
        if(registry.all().stream().noneMatch(d->d.slot()==4)){
            var reform=ideologies.reform(town.getUUID()).orElse(null);var info=new ArrayList<String>();
            if(reform!=null){info.add("&7"+ideologies.status(reform));info.add("&7Деньги списываются только при завершении.");}else info.add("&7Подготовка → обсуждение → принятие → подтверждение.");
            if(!ideologies.availability().isBlank())info.add("&e"+ideologies.availability());
            inventory.setItem(4,actionItem(new ItemStack(Material.CLOCK),"reform","&fРеформа города",info));
        }
        player.openInventory(inventory);
    } }

    public void openDetails(Player player, String ideologyId) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        Town town = towny.town(player);
        IdeologyDefinition definition = registry.find(ideologyId).orElse(null);
        if (town == null || definition == null) {
            messages.send(player, town == null ? "town-required" : "definition-missing");
            return;
        }
        TownIdeology current = ideologies.get(town).orElse(null);
        int shownLevel = current != null && current.ideologyId().equals(definition.id()) ? current.level() : 1;
        String title = plugin.getConfig().getString("settings.gui.details-title", "&8Идеология: {ideology}")
                .replace("{ideology}", ColorUtil.strip(definition.name()));
        Inventory inventory = ru.neverland.core.MenuStyle.inventory(plugin, new DetailsHolder(definition.id()), 27, ColorUtil.component(title));
        fill(inventory);
        inventory.setItem(11, ideologyIcon(definition, current));
        var reform=ideologies.reform(town.getUUID()).orElse(null);
        if(reform==null)inventory.setItem(15,purchaseButton(definition,current));
        else{
            boolean ready=reform.target().ideologyId().equals(definition.id())&&reform.phase()==ReformBook.Phase.READY&&!reform.paused();
            inventory.setItem(15,actionItem(ready?confirmItem():new ItemStack(Material.CLOCK),ready?"purchase":"locked",ready?"&aПодтвердить завершение":"&fРеформа уже идёт",List.of("&7"+ideologies.status(reform),"&7Сохранённая цена: &e"+economy.format(reform.cents()/100.0)+" "+economy.currencyName(),"&7До подтверждения действуют прежние бонусы.")));
            var lines=new ArrayList<String>();lines.add("&7Сейчас: "+name(reform.previous()));lines.addAll(effectLines(reform.previous()));lines.add("&7После: "+name(reform.target()));lines.addAll(effectLines(reform.target()));
            inventory.setItem(4,actionItem(new ItemStack(Material.BOOK),"locked","&fУсловия начатой реформы",lines));
        }
        if(reform==null&&!ideologies.availability().isBlank())inventory.setItem(4,actionItem(lockedItem(),"locked","&eДоступность реформ",List.of("&7"+ideologies.availability())));
        inventory.setItem(18, actionItem(backItem(), "back", "&fНазад", List.of("&7Вернуться к списку идеологий.")));
        player.openInventory(inventory);
    } }

    private String name(TownIdeology current){return current==null?"идеология не выбрана":ColorUtil.strip(current.terms()==null?registry.find(current.ideologyId()).map(IdeologyDefinition::name).orElse(current.ideologyId()):current.terms().name())+" · уровень "+current.level();}
    private List<String> effectLines(TownIdeology current){
        if(current==null)return List.of("&7Бонусов выбранной идеологии нет.");
        String line=switch(current.ideologyId()){
            case "agriculture"->"Рост растений ×"+registry.value(current,"growth-multiplier",1);
            case "industry"->"Скорость переплавки ×"+registry.value(current,"smelt-multiplier",1);
            case "healthcare"->"Дополнительные сердца: "+registry.value(current,"health-hearts",0);
            case "infrastructure"->"Скорость вагонетки ×"+registry.value(current,"minecart-multiplier",1)+"; задержка установки: "+(registry.integer(current,"placement-cooldown-ticks",0)/20.0)+" сек.";
            case "trade"->"Результат торговли ×"+registry.value(current,"trade-output-multiplier",1);
            case "defense"->"Боевые эффекты: "+String.join(", ",registry.effects(current));
            default->"Сохранённая идеология города";
        };
        return List.of("&7"+line);
    }
    private void openConfirm(Player player,IdeologyDefinition definition){
        var offer=ideologies.offer(player,definition.id());var reform=offer.reform();
        String title=offer.payment()?"&8Завершение реформы":"&8Начало реформы";
        var inventory=ru.neverland.core.MenuStyle.inventory(plugin,new ConfirmHolder(definition.id(),offer.token(),player.getUniqueId()),27,ColorUtil.component(title));fill(inventory);
        var lines=new ArrayList<String>();lines.add("&7Сейчас: "+name(reform.previous()));lines.addAll(effectLines(reform.previous()));lines.add("&7После: "+name(reform.target()));lines.addAll(effectLines(reform.target()));lines.add("&7Стоимость из городской казны: &e"+economy.format(reform.cents()/100.0)+" "+economy.currencyName());
        lines.add(offer.payment()?"&eПодтверждение спишет сохранённую сумму один раз.":"&aСейчас деньги не списываются.");
        if(!offer.payment()){lines.add("&7Подготовка: "+reform.stages().get(0)/1000+" сек.");lines.add("&7Обсуждение: "+reform.stages().get(1)/1000+" сек.");lines.add("&7Принятие: "+reform.stages().get(2)/1000+" сек.");lines.add("&7Затем мэр отдельно подтвердит оплату.");}
        inventory.setItem(11,actionItem(confirmItem(),"confirm",offer.payment()?"&aОплатить и завершить":"&aНачать подготовку",lines));inventory.setItem(15,actionItem(backItem(),"cancel","&fНазад",List.of("&7Без списания денег")));player.openInventory(inventory);
    }
    @EventHandler public void onClick(InventoryClickEvent event){try(var guard=ru.neverland.core.SuiteState.guard()){
        if(ru.neverland.core.SuiteState.frozen("NeverLandTownyIdeologies")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        var holder=event.getView().getTopInventory().getHolder(false);if(!(holder instanceof MenuHolder)||!(event.getWhoClicked() instanceof Player player))return;event.setCancelled(true);
        if(event.getRawSlot()<0||event.getRawSlot()>=event.getView().getTopInventory().getSize())return;
        try{
            if(!player.hasPermission("neverlandtownyideologies.use"))throw new IllegalArgumentException("Нет доступа к меню идеологий");
            var clicked=event.getCurrentItem();String action=actionFrom(clicked);
            if(holder instanceof IdeologyListHolder){String id=ideologyFrom(clicked);if(id!=null)openDetails(player,id);else if("reform".equals(action)){var town=towny.town(player);if(town!=null)ideologies.reform(town.getUUID()).ifPresent(r->openDetails(player,r.target().ideologyId()));}return;}
            if(holder instanceof DetailsHolder details){if("back".equals(action))openList(player);else if("purchase".equals(action))openConfirm(player,registry.find(details.ideologyId()).orElseThrow(()->new IllegalArgumentException("Идеология отсутствует")));return;}
            if(holder instanceof ConfirmHolder confirm){if(!confirm.player().equals(player.getUniqueId()))return;if("cancel".equals(action))openDetails(player,confirm.ideologyId());else if("confirm".equals(action)){
                var result=ideologies.confirm(player,confirm.token());player.closeInventory();player.sendMessage("§7Сохраняем решение. Дождитесь результата…");
                result.whenComplete((text,error)->{if(plugin.isEnabled())ru.neverland.core.SuiteScheduler.reply(plugin,player,()->{if(player.isOnline())player.sendMessage(error==null?"§a"+text:"§cСохранение не подтверждено. Сообщите администрации; повторять оплату автоматически нельзя.");});});
            }}
        }catch(Exception error){player.sendMessage("§c"+(error.getMessage()==null?"Действие не выполнено":error.getMessage()));}
    }}

    @EventHandler
    public void onDrag(InventoryDragEvent event) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyIdeologies")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        if (event.getView().getTopInventory().getHolder(false) instanceof MenuHolder) event.setCancelled(true);
    } }

    private ItemStack ideologyIcon(IdeologyDefinition definition, TownIdeology current) {
        ItemStack icon = itemsAdder.item(definition.itemsAdderIcon(), 1);
        if (icon == null) icon = new ItemStack(definition.material());
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(ru.neverland.core.MenuStyle.nameComponent(ColorUtil.component(definition.name())));
        List<Component> lore = new ArrayList<>();
        definition.description().forEach(line -> lore.add(ColorUtil.component(line)));
        lore.add(Component.empty());
        boolean selected = current != null && current.ideologyId().equals(definition.id());
        int level = selected ? current.level() : 0;
        lore.add(ColorUtil.component(messages.replace(messages.raw("gui.level"),
                Map.of("level", Integer.toString(level), "max_level", Integer.toString(IdeologyDefinition.MAX_LEVEL)))));
        lore.add(ColorUtil.component(messages.replace(messages.raw("gui.progress"), Map.of("progress", progress(level)))));
        int displayLevel = Math.max(1, level);
        if (selected && current.terms() != null) effectLines(current).forEach(line -> lore.add(ColorUtil.component(line)));
        else {
            Map<String, String> bonus = registry.bonusPlaceholders(definition, displayLevel);
            for (String line : definition.bonusDescription()) lore.add(ColorUtil.component(messages.replace(line, bonus)));
        }
        lore.add(Component.empty());
        if (selected) lore.add(ColorUtil.component(messages.raw("gui.selected")));
        else if (current == null || plugin.getConfig().getBoolean("settings.selection.change-allowed", false)) {
            lore.add(ColorUtil.component(messages.replace(messages.raw("gui.select-price"),
                    Map.of("price", economy.format(definition.selectionPrice()), "currency", economy.currencyName()))));
            lore.add(ColorUtil.component(messages.raw("gui.available")));
        } else lore.add(ColorUtil.component(messages.raw("gui.unavailable")));
        lore.add(ColorUtil.component(messages.raw("gui.left-click")));
        meta.lore(ru.neverland.core.MenuStyle.loreComponents(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(ideologyKey, PersistentDataType.STRING, definition.id());
        icon.setItemMeta(meta);
        icon.setAmount(1);
        return icon;
    }

    private ItemStack purchaseButton(IdeologyDefinition definition, TownIdeology current) {
        boolean selected = current != null && current.ideologyId().equals(definition.id());
        if (selected && current.level() >= IdeologyDefinition.MAX_LEVEL) {
            return actionItem(lockedItem(), "locked", messages.raw("gui.max-level"), List.of());
        }
        if (current != null && !selected && !plugin.getConfig().getBoolean("settings.selection.change-allowed", false)) {
            return actionItem(lockedItem(), "locked", messages.raw("gui.unavailable"), List.of());
        }
        int nextLevel = selected ? current.level() + 1 : 1;
        double price = definition.priceForLevel(nextLevel);
        String name = current == null ? messages.raw("gui.confirm-select")
                : selected ? messages.raw("gui.confirm-upgrade").replace("{level}", Integer.toString(nextLevel))
                : messages.raw("gui.confirm-change");
        return actionItem(confirmItem(), "purchase", name,
                List.of("&7Стоимость: &e" + economy.format(price) + " " + economy.currencyName(),
                        messages.raw("gui.purchase-hint")));
    }

    private ItemStack actionItem(ItemStack base, String action, String name, List<String> lore) {
        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ru.neverland.core.MenuStyle.nameComponent(ColorUtil.component(name)));
        meta.lore(ru.neverland.core.MenuStyle.loreComponents(lore.stream().map(ColorUtil::component).toList()));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        item.setAmount(1);
        return item;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = itemsAdder.item(plugin.getConfig().getString("settings.itemsadder.filler-item", ""), 1);
        if (filler == null) {
            Material material = Material.matchMaterial(plugin.getConfig().getString("settings.gui.filler-material", "BLACK_STAINED_GLASS_PANE"));
            filler = new ItemStack(material == null ? Material.BLACK_STAINED_GLASS_PANE : material);
            ItemMeta meta = filler.getItemMeta();
            meta.displayName(ru.neverland.core.MenuStyle.nameComponent(Component.text(" ")));
            filler.setItemMeta(meta);
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private ItemStack confirmItem() {
        ItemStack item = itemsAdder.item(plugin.getConfig().getString("settings.itemsadder.confirm-item", ""), 1);
        return item == null ? new ItemStack(Material.LIME_DYE) : item;
    }

    private ItemStack backItem() {
        ItemStack item = itemsAdder.item(plugin.getConfig().getString("settings.itemsadder.back-item", ""), 1);
        return item == null ? new ItemStack(Material.ARROW) : item;
    }

    private ItemStack lockedItem() {
        ItemStack item = itemsAdder.item(plugin.getConfig().getString("settings.itemsadder.locked-item", ""), 1);
        return item == null ? new ItemStack(Material.BARRIER) : item;
    }

    private String progress(int level) {
        String filled = plugin.getConfig().getString("settings.gui.progress-filled", "&a■");
        String empty = plugin.getConfig().getString("settings.gui.progress-empty", "&7□");
        StringBuilder bar = new StringBuilder("&8[");
        for (int index = 1; index <= IdeologyDefinition.MAX_LEVEL; index++) bar.append(index <= level ? filled : empty);
        return bar.append("&8]").toString();
    }

    private String ideologyFrom(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(ideologyKey, PersistentDataType.STRING);
    }

    private String actionFrom(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private interface MenuHolder extends InventoryHolder {
        @Override
        default Inventory getInventory() {
            throw new UnsupportedOperationException("Marker holder");
        }
    }

    private record IdeologyListHolder() implements MenuHolder { }
    private record DetailsHolder(String ideologyId) implements MenuHolder { }
    private record ConfirmHolder(String ideologyId,java.util.UUID token,java.util.UUID player) implements MenuHolder { }
}
