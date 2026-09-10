package ru.neverland.minttrade.gui;
import ru.neverland.localization.MaterialNameConfig;

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
import ru.neverland.minttrade.integration.TownyHook;
import ru.neverland.minttrade.model.Caravan;
import ru.neverland.minttrade.model.CaravanStatus;
import ru.neverland.minttrade.model.ExportDefinition;
import ru.neverland.minttrade.model.TradeHistory;
import ru.neverland.minttrade.model.TradeOffer;
import ru.neverland.minttrade.service.MessageService;
import ru.neverland.minttrade.service.TradeService;
import ru.neverland.minttrade.util.ColorUtil;
import ru.neverland.minttrade.util.TimeUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TradeMenuManager implements Listener {
    private static final int[] INCOMING = {10, 11, 12};
    private static final int[] OUTGOING = {14, 15, 16};
    private static final int[] CARAVANS = {46, 47, 48, 50, 51, 52};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
    private final JavaPlugin plugin; private final TownyHook towny; private final TradeService trade; private final MessageService messages;
    private final NamespacedKey offerKey, actionKey;
    private final ru.neverland.minttrade.contract.SupplyMenus supplies;
    public TradeMenuManager(JavaPlugin plugin, TownyHook towny, TradeService trade, MessageService messages,ru.neverland.minttrade.contract.SupplyMenus supplies) {
        this.supplies=supplies;
        this.plugin = plugin; this.towny = towny; this.trade = trade; this.messages = messages;
        offerKey = new NamespacedKey(plugin, "offer"); actionKey = new NamespacedKey(plugin, "action");
    }
    public void open(Player player) {
        Town town = towny.town(player); if (town == null) { messages.send(player, "no-town"); return; }
        TradeMenuHolder holder = new TradeMenuHolder(town.getUUID(), TradeMenuHolder.Type.BOARD);
        Inventory inventory = Bukkit.createInventory(holder, 54, ColorUtil.color(plugin.getConfig().getString("gui.title", "Городская торговля")));
        holder.inventory(inventory); fill(inventory);
        listOffers(inventory, trade.incoming(town), INCOMING, true);
        listOffers(inventory, trade.outgoing(town), OUTGOING, false);
        for (ExportDefinition definition : trade.registry().all()) {
            int slot = Math.max(27, Math.min(44, definition.slot()));
            if (slot == 36 || slot == 44) slot = 35;
            inventory.setItem(slot, exportItem(definition));
        }
        List<Caravan> caravans = trade.caravans(town);
        for (int index = 0; index < CARAVANS.length; index++) inventory.setItem(CARAVANS[index], index < caravans.size()
                ? caravanItem(caravans.get(index), town) : item(Material.GRAY_STAINED_GLASS_PANE, "&#555555Свободный маршрут", List.of()));
        inventory.setItem(36, item(Material.EMERALD, "&#63E6BEТорговый центр", List.of(
                "&7Уровень Рынка: &#FFFFFF" + trade.marketLevel(town),
                "&7Маршруты: &#FFFFFF" + trade.activeCount(town) + "&7/&#FFFFFF" + trade.routeLimit(town),
                "&7Казна: &#FFD45A" + trade.economy().format(trade.economy().balance(town)),
                "&7Транзитная пошлина: &#FFFFFF" + trade.tariff(town) + "%")));
        inventory.setItem(44, actionItem("history", Material.WRITABLE_BOOK, "&#65B8FFИстория торговли", List.of("&7Завершённые караваны и сделки.")));
        if(Bukkit.getPluginManager().isPluginEnabled("NeverLandTownyMarket"))inventory.setItem(7,actionItem("markets",Material.CHEST,"&#63E6BEМеждународный рынок",List.of("&7Товарные предложения городов.")));
        inventory.setItem(8, actionItem("contracts",Material.CLOCK,"&#63E6BEРегулярные договоры",List.of("&7Поставки между складами по расписанию.")));
        player.openInventory(inventory);
    }
    public void openHistory(Player player, Town town) {
        TradeMenuHolder holder = new TradeMenuHolder(town.getUUID(), TradeMenuHolder.Type.HISTORY);
        Inventory inventory = Bukkit.createInventory(holder, 54, ColorUtil.color(plugin.getConfig().getString("gui.history-title", "История торговли")));
        holder.inventory(inventory); fill(inventory); int slot = 0;
        for (TradeHistory entry : trade.history(town)) {
            ExportDefinition definition = trade.registry().get(entry.exportId());
            Town seller = towny.town(entry.sellerId()), buyer = towny.town(entry.buyerId());
            inventory.setItem(slot++, item(entry.status() == CaravanStatus.COMPLETED ? Material.LIME_DYE : Material.RED_DYE,
                    definition == null ? entry.exportId() : definition.name(), List.of(
                            "&7Маршрут: &f" + name(seller) + " &7→ &f" + name(buyer),
                            "&7Статус: " + (entry.status() == CaravanStatus.COMPLETED ? "&#55FF55Доставлен" : "&#FF7777Отменён"),
                            "&7Груз: &f" + entry.amount(), "&7Цена: &#FFD45A" + trade.economy().format(entry.price()),
                            "&7Пошлины: &#FFD45A" + trade.economy().format(entry.tariffs()),
                            "&7Дата: &f" + DATE.format(Instant.ofEpochMilli(entry.completedAt())))));
            if (slot >= 45) break;
        }
        inventory.setItem(49, actionItem("back", Material.ARROW, "&fНазад", List.of("&7Вернуться к торговле.")));
        player.openInventory(inventory);
    }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeMenuHolder holder)) return;
        event.setCancelled(true); if (!(event.getWhoClicked() instanceof Player player)) return;
        Town town = towny.town(player); if (town == null || !town.getUUID().equals(holder.townId())) { player.closeInventory(); return; }
        if(event.getRawSlot()<0||event.getRawSlot()>=event.getView().getTopInventory().getSize()||!player.hasPermission("minttrade.use"))return;
        ItemStack clicked = event.getCurrentItem(); if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (holder.type() == TradeMenuHolder.Type.HISTORY) { if ("back".equals(action)) open(player); return; }
        if ("markets".equals(action)) {player.performCommand("townymarket global");return;}
        if ("contracts".equals(action)) {if(supplies.access(player))supplies.open(player,0);return;}
        if ("history".equals(action)) { openHistory(player, town); return; }
        String offerId = clicked.getItemMeta().getPersistentDataContainer().get(offerKey, PersistentDataType.STRING);
        if (offerId == null || !towny.isManager(player, town) || !player.hasPermission("minttrade.manage")) {
            if (offerId != null) messages.send(player, "only-manager"); return;
        }
        TradeOffer offer = trade.offer(offerId);
        if ("incoming".equals(action)) {
            if (event.isRightClick()) { trade.reject(town, offer); messages.send(player, "proposal-rejected"); }
            else sendAccept(player, trade.accept(town, offer));
        } else if ("outgoing".equals(action)) {
            trade.cancelOffer(town, offer); messages.send(player, "proposal-cancelled");
        }
        open(player);
    }
    @EventHandler public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof TradeMenuHolder)event.setCancelled(true);}
    public void sendAccept(Player player, TradeService.AcceptOutcome outcome) {
        switch (outcome.result()) {
            case SUCCESS -> messages.send(player, "caravan-departed", Map.of("export", ColorUtil.strip(trade.definitionOf(outcome.caravan()).name()),
                    "time", TimeUtil.format(outcome.caravan().arrivesAt() - System.currentTimeMillis())));
            case NOT_FOUND -> messages.send(player, "offer-not-found", Map.of("offer", "?"));
            case NOT_BUYER -> messages.send(player, "not-offer-party"); case MARKET_REQUIRED -> messages.send(player, "market-required");
            case ROUTE_LIMIT -> messages.send(player, "route-limit"); case ROUTE_UNAVAILABLE -> messages.send(player, "route-unavailable");
            case SANCTIONED -> messages.send(player, "trade-blocked");
            case IMPORT_RESTRICTED -> messages.send(player,"policy-import-blocked");
            case NO_MONEY -> messages.send(player, "not-enough-treasury", Map.of("amount", trade.economy().format(outcome.required())));
            case STOCK_LOW -> messages.send(player, "seller-stock-low", Map.of("amount", "полный объём"));
            case WAREHOUSE_BUSY -> messages.send(player, "warehouse-busy"); case WAREHOUSE_UNAVAILABLE -> messages.send(player, "warehouse-unavailable");
            case ECONOMY_ERROR, SAVE_ERROR -> messages.send(player, "economy-error");
        }
    }
    private void listOffers(Inventory inventory, List<TradeOffer> offers, int[] slots, boolean incoming) {
        for (int index = 0; index < slots.length; index++) {
            if (index >= offers.size()) { inventory.setItem(slots[index], item(Material.GRAY_STAINED_GLASS_PANE,
                    incoming ? "&#555555Нет входящего договора" : "&#555555Нет исходящего договора", List.of())); continue; }
            TradeOffer offer = offers.get(index); ExportDefinition definition = trade.definition(offer);
            Town other = towny.town(incoming ? offer.sellerId() : offer.buyerId());
            List<String> lore = new ArrayList<>(); lore.add("&7Город: &f" + name(other));
            if (definition != null) { lore.add("&7Груз: &f" + definition.amount()); lore.add("&7Цена: &#FFD45A" + trade.economy().format(definition.price())); }
            lore.add("&7ID: &f" + offer.shortId()); lore.add("");
            lore.add(incoming ? "&#55FF55ЛКМ — принять" : "&#FFFF55ЛКМ — отменить"); if (incoming) lore.add("&#FF7777ПКМ — отклонить");
            ItemStack stack = item(definition == null ? Material.BARRIER : definition.icon(), definition == null ? offer.exportId() : definition.name(), lore);
            stack = keyed(stack, offerKey, offer.id().toString()); inventory.setItem(slots[index], keyed(stack, actionKey, incoming ? "incoming" : "outgoing"));
        }
    }
    private ItemStack exportItem(ExportDefinition definition) {
        List<String> lore = new ArrayList<>(); definition.description().forEach(line -> lore.add("&7" + line));
        lore.add(""); lore.add("&7Партия: &#FFFFFF" + definition.amount()); lore.add("&7Цена: &#FFD45A" + trade.economy().format(definition.price()));
        lore.add(""); lore.add("&#AAAAAA/t trade propose <город> " + definition.id()); return item(definition.icon(), definition.name(), lore);
    }
    private ItemStack caravanItem(Caravan caravan, Town viewer) {
        ExportDefinition definition = trade.definitionOf(caravan); Town from = towny.town(caravan.sellerId()), to = towny.town(caravan.buyerId());
        return item(definition.icon(), definition.name(), List.of("&7Маршрут: &f" + name(from) + " &7→ &f" + name(to),
                "&7Статус: " + (caravan.status() == CaravanStatus.WAITING_WAREHOUSE ? "&#FFFF55Ожидает склад" : "&#55FF55В пути"),
                "&7Прогресс: &#63E6BE" + Math.round(caravan.progress(System.currentTimeMillis()) * 100) + "%",
                "&7До прибытия: &#65B8FF" + TimeUtil.format(caravan.arrivesAt() - System.currentTimeMillis()),
                "&7Перевалочных лагерей: &f" + caravan.campStops(), "&7Пошлины: &#FFD45A" + trade.economy().format(caravan.escrow() - caravan.basePrice()),
                "&7ID: &f" + caravan.shortId()));
    }
    private String name(Town town) { return town == null ? "Удалённый город" : town.getName(); }
    private void fill(Inventory inventory) { Material material = MaterialNameConfig.matchMaterial(plugin.getConfig().getString("gui.filler", "BLACK_STAINED_GLASS_PANE")); ItemStack filler = item(material == null ? Material.BLACK_STAINED_GLASS_PANE : material, " ", List.of()); for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler); }
    private ItemStack actionItem(String action, Material material, String name, List<String> lore) { return keyed(item(material, name, lore), actionKey, action); }
    private ItemStack keyed(ItemStack stack, NamespacedKey key, String value) { ItemMeta meta = stack.getItemMeta(); meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value); stack.setItemMeta(meta); return stack; }
    private ItemStack item(Material material, String name, List<String> lore) { ItemStack stack = new ItemStack(material == null ? Material.PAPER : material); ItemMeta meta = stack.getItemMeta(); meta.setDisplayName(ColorUtil.color(name)); meta.setLore(lore.stream().map(ColorUtil::color).toList()); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); stack.setItemMeta(meta); return stack; }
}
