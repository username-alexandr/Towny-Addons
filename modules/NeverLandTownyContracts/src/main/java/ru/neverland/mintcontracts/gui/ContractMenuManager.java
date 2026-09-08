package ru.neverland.mintcontracts.gui;
import ru.neverland.localization.MaterialNameConfig;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
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
import ru.neverland.mintcontracts.integration.TownyHook;
import ru.neverland.mintcontracts.integration.WarehouseBridge;
import ru.neverland.mintcontracts.model.ActiveContract;
import ru.neverland.mintcontracts.model.ContractDefinition;
import ru.neverland.mintcontracts.model.ContractHistory;
import ru.neverland.mintcontracts.model.ContractStatus;
import ru.neverland.mintcontracts.model.ContractType;
import ru.neverland.mintcontracts.service.ContractService;
import ru.neverland.mintcontracts.service.MessageService;
import ru.neverland.mintcontracts.service.RussianNames;
import ru.neverland.mintcontracts.util.ColorUtil;
import ru.neverland.mintcontracts.util.TimeUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ContractMenuManager implements Listener {
    private static final int[] ACTIVE_SLOTS = {10, 13, 16};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final ContractService contracts;
    private final MessageService messages;
    private final RussianNames names;
    private final NamespacedKey contractKey;
    private final NamespacedKey templateKey;
    private final NamespacedKey actionKey;

    public ContractMenuManager(JavaPlugin plugin, TownyHook towny, ContractService contracts, MessageService messages, RussianNames names) {
        this.plugin = plugin; this.towny = towny; this.contracts = contracts; this.messages = messages; this.names = names;
        contractKey = new NamespacedKey(plugin, "contract"); templateKey = new NamespacedKey(plugin, "template");
        actionKey = new NamespacedKey(plugin, "action");
    }

    public void open(Player player) {
        Town town = towny.town(player);
        if (town == null) { messages.send(player, "no-town"); return; }
        ContractMenuHolder holder = new ContractMenuHolder(town.getUUID(), ContractMenuHolder.Type.BOARD);
        Inventory inventory = Bukkit.createInventory(holder, 54, ColorUtil.color(plugin.getConfig().getString("gui.title", "Доска заказов")));
        holder.inventory(inventory); fill(inventory);
        List<ActiveContract> active = contracts.active(town.getUUID());
        for (int index = 0; index < ACTIVE_SLOTS.length; index++) {
            if (index < active.size()) inventory.setItem(ACTIVE_SLOTS[index], activeItem(active.get(index)));
            else inventory.setItem(ACTIVE_SLOTS[index], item(Material.GRAY_STAINED_GLASS_PANE, "&#777777Свободное место",
                    List.of("&7Мэр или заместитель может", "&7опубликовать новый заказ ниже.")));
        }
        for (ContractDefinition definition : contracts.registry().all()) {
            int slot = Math.max(0, Math.min(53, definition.slot()));
            inventory.setItem(slot, templateItem(town, definition));
        }
        inventory.setItem(45, actionItem("treasury", Material.GOLD_INGOT, "&#FFD45AКазна города",
                List.of("&7Баланс: &#FFFFFF" + contracts.economy().balance(town), "&7Награда резервируется при публикации.")));
        double pending = contracts.pending(player.getUniqueId());
        inventory.setItem(47, actionItem("claim", pending > 0 ? Material.EMERALD : Material.GRAY_DYE,
                "&#63E6BEПолучить награду", List.of("&7Ожидает выплаты: &#FFFFFF" + contracts.economy().format(pending), "", "&#63E6BEНажмите для получения")));
        inventory.setItem(49, actionItem("history", Material.WRITABLE_BOOK, "&#65B8FFИстория заказов",
                List.of("&7Завершённые и просроченные задания.")));
        player.openInventory(inventory);
    }

    public void openHistory(Player player, Town town) {
        ContractMenuHolder holder = new ContractMenuHolder(town.getUUID(), ContractMenuHolder.Type.HISTORY);
        Inventory inventory = Bukkit.createInventory(holder, 54, ColorUtil.color(plugin.getConfig().getString("gui.history-title", "История")));
        holder.inventory(inventory); fill(inventory);
        int slot = 0;
        for (ContractHistory entry : contracts.repository().history(town.getUUID())) {
            ContractDefinition definition = contracts.registry().get(entry.templateId());
            String name = definition == null ? entry.templateId() : definition.name();
            Material icon = entry.status() == ContractStatus.SUCCESS ? Material.LIME_DYE :
                    entry.status() == ContractStatus.EXPIRED ? Material.YELLOW_DYE : Material.RED_DYE;
            inventory.setItem(slot++, item(icon, name, List.of(
                    "&7Статус: " + status(entry.status()), "&7Прогресс: &f" + entry.progress() + "/" + entry.goal(),
                    "&7Выплачено: &#63E6BE" + contracts.economy().format(entry.paid()),
                    "&7Возвращено в казну: &#FFD45A" + contracts.economy().format(entry.refunded()),
                    "&7Завершение: &f" + DATE.format(Instant.ofEpochMilli(entry.endedAt())))));
            if (slot >= 45) break;
        }
        inventory.setItem(49, actionItem("back", Material.ARROW, "&fНазад", List.of("&7Вернуться к доске.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ContractMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Town town = towny.town(player);
        if (town == null || !town.getUUID().equals(holder.townId())) { player.closeInventory(); return; }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (holder.type() == ContractMenuHolder.Type.HISTORY) {
            if ("back".equals(action)) open(player);
            return;
        }
        if ("history".equals(action)) { openHistory(player, town); return; }
        if ("claim".equals(action)) { claim(player); return; }
        String templateId = clicked.getItemMeta().getPersistentDataContainer().get(templateKey, PersistentDataType.STRING);
        if (templateId != null) { activate(player, town, contracts.registry().get(templateId)); return; }
        String contractId = clicked.getItemMeta().getPersistentDataContainer().get(contractKey, PersistentDataType.STRING);
        if (contractId == null) return;
        ActiveContract contract = contracts.find(town.getUUID(), contractId);
        ContractDefinition definition = contracts.definition(contract);
        if (contract == null || definition == null || definition.type() != ContractType.DELIVERY) return;
        if (!player.hasPermission("mintcontracts.contribute")) { messages.send(player, "no-permission"); return; }
        ContractService.DeliveryResult result = contracts.deliver(player, contract, event.isShiftClick());
        switch (result.status()) {
            case BUSY -> messages.send(player, "warehouse-busy");
            case FULL -> messages.send(player, "warehouse-full");
            case UNAVAILABLE -> messages.send(player, "warehouse-unavailable");
            case SUCCESS -> {
                if (result.amount() <= 0) messages.send(player, "no-items");
                else messages.send(player, "contributed", Map.of("amount", result.amount(),
                        "item", itemName(definition.deliveryItem()), "progress", result.progress(), "goal", definition.goal()));
            }
        }
        open(player);
    }

    private void activate(Player player, Town town, ContractDefinition definition) {
        if (!player.hasPermission("mintcontracts.manage") || !towny.isManager(player, town)) { messages.send(player, "only-manager"); return; }
        ContractService.ActivateResult result = contracts.activate(town, definition);
        switch (result) {
            case SUCCESS -> messages.send(player, "activated", Map.of("contract", ColorUtil.strip(definition.name()), "reward", contracts.economy().format(definition.reward())));
            case MAX_ACTIVE -> messages.send(player, "max-active");
            case DUPLICATE -> messages.send(player, "duplicate-template");
            case NO_MONEY -> messages.send(player, "not-enough-treasury", Map.of("reward", contracts.economy().format(definition.reward())));
            case ECONOMY_ERROR -> messages.send(player, "economy-error");
            case WAREHOUSE_UNAVAILABLE -> messages.send(player, "warehouse-unavailable");
            case SAVE_ERROR -> messages.send(player, "economy-error");
        }
        open(player);
    }

    private void claim(Player player) {
        double amount = contracts.claim(player);
        if (amount > 0) messages.send(player, "claim-success", Map.of("amount", contracts.economy().format(amount)));
        else if (amount == 0) messages.send(player, "no-pending-reward");
        else messages.send(player, "economy-error");
        open(player);
    }

    private ItemStack activeItem(ActiveContract contract) {
        ContractDefinition definition = contracts.definition(contract);
        if (definition == null) return item(Material.BARRIER, "&#FF5555Повреждённый заказ", List.of(contract.templateId()));
        List<String> lore = new ArrayList<>();
        definition.description().forEach(line -> lore.add("&7" + line));
        lore.add(""); lore.add("&#FFFFFFТип: &#B65CFF" + type(definition.type()));
        lore.add("&#FFFFFFЦель: &#FFD45A" + target(definition));
        lore.add("&#FFFFFFПрогресс: &#63E6BE" + contract.progress() + "&7/&#FFFFFF" + contract.goal());
        lore.add(progress(contract.ratio()));
        lore.add("&#FFFFFFНаграда: &#FFD45A" + contracts.economy().format(contract.escrow()));
        lore.add("&#FFFFFFОсталось: &#65B8FF" + TimeUtil.format((contract.expiresAt() - System.currentTimeMillis()) / 1000));
        lore.add("&#FFFFFFID: &7" + contract.shortId());
        List<Map.Entry<java.util.UUID, Integer>> leaders = contract.contributions().entrySet().stream()
                .sorted(Map.Entry.<java.util.UUID, Integer>comparingByValue(Comparator.reverseOrder())).limit(3).toList();
        if (!leaders.isEmpty()) { lore.add(""); lore.add("&#C9A7FFЛучшие участники:"); }
        for (Map.Entry<java.util.UUID, Integer> entry : leaders) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            lore.add("&8• &f" + (player.getName() == null ? entry.getKey().toString().substring(0, 8) : player.getName()) + " &7— " + entry.getValue());
        }
        if (definition.type() == ContractType.DELIVERY) {
            lore.add(""); lore.add("&#55FF55ЛКМ — передать один стак"); lore.add("&#FFD45AShift + ЛКМ — передать всё");
        }
        return keyed(item(definition.icon(), definition.name(), lore), contractKey, contract.id().toString());
    }

    private ItemStack templateItem(Town town, ContractDefinition definition) {
        boolean active = contracts.active(town.getUUID()).stream().anyMatch(c -> c.templateId().equals(definition.id()));
        List<String> lore = new ArrayList<>(); definition.description().forEach(line -> lore.add("&7" + line));
        lore.add(""); lore.add("&#FFFFFFТип: &#B65CFF" + type(definition.type()));
        lore.add("&#FFFFFFЦель: &#FFD45A" + target(definition) + " x" + definition.goal());
        lore.add("&#FFFFFFНаграда из казны: &#FFD45A" + contracts.economy().format(definition.reward()));
        lore.add("&#FFFFFFСрок: &#65B8FF" + TimeUtil.format(definition.durationSeconds()));
        lore.add(""); lore.add(active ? "&#FF5555Уже опубликован" : "&#63E6BEНажмите, чтобы опубликовать");
        return keyed(item(definition.icon(), definition.name(), lore), templateKey, definition.id());
    }

    private String target(ContractDefinition definition) {
        if (definition.type() == ContractType.DELIVERY) return itemName(definition.deliveryItem());
        return definition.target().equalsIgnoreCase("ANY") ? "Любая подходящая цель" : names.value(definition.target());
    }
    private String type(ContractType type) { return switch (type) {
        case DELIVERY -> "Поставка"; case MOB_KILL -> "Охота"; case BLOCK_BREAK -> "Добыча"; case FISH -> "Рыбалка"; }; }
    private String status(ContractStatus status) { return switch (status) {
        case SUCCESS -> "&#55FF55Выполнен"; case EXPIRED -> "&#FFFF55Истёк срок"; case CANCELLED -> "&#FF7777Отменён"; }; }
    private String progress(double ratio) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * 20);
        return "&#B65CFF" + "■".repeat(filled) + "&#3A3146" + "■".repeat(20 - filled);
    }
    private String itemName(ItemStack stack) {
        return names.item(stack);
    }
    private void fill(Inventory inventory) {
        Material material = MaterialNameConfig.matchMaterial(plugin.getConfig().getString("gui.filler", "BLACK_STAINED_GLASS_PANE"));
        ItemStack filler = item(material == null ? Material.BLACK_STAINED_GLASS_PANE : material, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }
    private ItemStack actionItem(String action, Material material, String name, List<String> lore) {
        return keyed(item(material, name, lore), actionKey, action);
    }
    private ItemStack keyed(ItemStack stack, NamespacedKey key, String value) {
        ItemMeta meta = stack.getItemMeta(); meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value); stack.setItemMeta(meta); return stack;
    }
    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = stack.getItemMeta(); meta.setDisplayName(ColorUtil.color(name));
        meta.setLore(lore.stream().map(ColorUtil::color).toList()); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); stack.setItemMeta(meta); return stack;
    }
}
