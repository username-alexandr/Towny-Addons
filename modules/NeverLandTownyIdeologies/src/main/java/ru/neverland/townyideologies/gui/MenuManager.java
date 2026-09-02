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
import ru.neverland.townyideologies.service.PurchaseResult;
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

    public void openList(Player player) {
        Town town = towny.town(player);
        if (town == null) {
            messages.send(player, "town-required");
            return;
        }
        String title = plugin.getConfig().getString("settings.gui.list-title", "&8Идеологии города");
        Inventory inventory = Bukkit.createInventory(new IdeologyListHolder(), 27, ColorUtil.component(title));
        fill(inventory);
        TownIdeology current = ideologies.get(town).orElse(null);
        for (IdeologyDefinition definition : registry.all()) {
            inventory.setItem(definition.slot(), ideologyIcon(definition, current));
        }
        player.openInventory(inventory);
    }

    public void openDetails(Player player, String ideologyId) {
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
        Inventory inventory = Bukkit.createInventory(new DetailsHolder(definition.id()), 27, ColorUtil.component(title));
        fill(inventory);
        inventory.setItem(11, ideologyIcon(definition, current));
        inventory.setItem(15, purchaseButton(definition, current));
        inventory.setItem(18, actionItem(backItem(), "back", "&fНазад", List.of("&7Вернуться к списку идеологий.")));
        player.openInventory(inventory);
    }

    private void openConfirm(Player player, IdeologyDefinition definition) {
        TownIdeology current = ideologies.get(player).orElse(null);
        String title = plugin.getConfig().getString("settings.gui.confirm-title", "&8Подтверждение");
        Inventory inventory = Bukkit.createInventory(new ConfirmHolder(definition.id()), 27, ColorUtil.component(title));
        fill(inventory);
        int nextLevel = current != null && current.ideologyId().equals(definition.id()) ? current.level() + 1 : 1;
        double price = definition.priceForLevel(nextLevel);
        String label = current == null ? messages.raw("gui.confirm-select")
                : current.ideologyId().equals(definition.id()) ? messages.raw("gui.confirm-upgrade")
                : messages.raw("gui.confirm-change");
        label = label.replace("{level}", Integer.toString(nextLevel));
        inventory.setItem(11, actionItem(confirmItem(), "confirm", label,
                List.of("&7Стоимость: &e" + economy.format(price) + " " + economy.currencyName(),
                        messages.raw("gui.purchase-hint"))));
        inventory.setItem(15, actionItem(backItem(), "cancel", messages.raw("gui.cancel"), List.of()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(holder instanceof MenuHolder) || !(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ItemStack clicked = event.getCurrentItem();
        if (holder instanceof IdeologyListHolder) {
            String id = ideologyFrom(clicked);
            if (id != null) openDetails(player, id);
            return;
        }
        if (holder instanceof DetailsHolder details) {
            String action = actionFrom(clicked);
            if ("back".equals(action)) openList(player);
            if ("purchase".equals(action)) {
                IdeologyDefinition definition = registry.find(details.ideologyId()).orElse(null);
                if (definition == null) {
                    messages.send(player, "definition-missing");
                } else if (plugin.getConfig().getBoolean("settings.selection.require-confirmation", true)) {
                    openConfirm(player, definition);
                } else {
                    purchase(player, definition);
                }
            }
            return;
        }
        if (holder instanceof ConfirmHolder confirm) {
            String action = actionFrom(clicked);
            if ("cancel".equals(action)) openDetails(player, confirm.ideologyId());
            if ("confirm".equals(action)) {
                IdeologyDefinition definition = registry.find(confirm.ideologyId()).orElse(null);
                if (definition == null) messages.send(player, "definition-missing");
                else purchase(player, definition);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof MenuHolder) event.setCancelled(true);
    }

    private void purchase(Player player, IdeologyDefinition definition) {
        Town town = towny.town(player);
        TownIdeology previous = town == null ? null : ideologies.get(town).orElse(null);
        PurchaseResult result = ideologies.purchase(player, definition.id());
        Map<String, String> replacements = Map.of(
                "town", town == null ? "—" : town.getName(),
                "ideology", definition.name(),
                "current", previous == null ? "—" : registry.find(previous.ideologyId()).map(IdeologyDefinition::name).orElse(previous.ideologyId()),
                "level", Integer.toString(result.level()),
                "price", economy.format(result.price()),
                "currency", economy.currencyName()
        );
        switch (result.status()) {
            case SELECTED -> messages.send(player, "selected", replacements);
            case CHANGED -> messages.send(player, "changed", replacements);
            case UPGRADED -> messages.send(player, "upgraded", replacements);
            case NO_TOWN -> messages.send(player, "town-required");
            case NOT_MAYOR -> messages.send(player, "mayor-only");
            case NO_PERMISSION -> messages.send(player, "no-permission");
            case OTHER_SELECTED -> messages.send(player, "other-selected", replacements);
            case MAX_LEVEL -> messages.send(player, "max-level");
            case INSUFFICIENT_FUNDS -> messages.send(player, "not-enough-money", replacements);
            case ECONOMY_ERROR -> messages.send(player, "economy-error");
            case DEFINITION_MISSING -> messages.send(player, "definition-missing");
        }
        if (result.success()) openDetails(player, definition.id());
    }

    private ItemStack ideologyIcon(IdeologyDefinition definition, TownIdeology current) {
        ItemStack icon = itemsAdder.item(definition.itemsAdderIcon(), 1);
        if (icon == null) icon = new ItemStack(definition.material());
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(ColorUtil.component(definition.name()));
        List<Component> lore = new ArrayList<>();
        definition.description().forEach(line -> lore.add(ColorUtil.component(line)));
        lore.add(Component.empty());
        boolean selected = current != null && current.ideologyId().equals(definition.id());
        int level = selected ? current.level() : 0;
        lore.add(ColorUtil.component(messages.replace(messages.raw("gui.level"),
                Map.of("level", Integer.toString(level), "max_level", Integer.toString(IdeologyDefinition.MAX_LEVEL)))));
        lore.add(ColorUtil.component(messages.replace(messages.raw("gui.progress"), Map.of("progress", progress(level)))));
        int displayLevel = Math.max(1, level);
        Map<String, String> bonus = registry.bonusPlaceholders(definition, displayLevel);
        for (String line : definition.bonusDescription()) lore.add(ColorUtil.component(messages.replace(line, bonus)));
        lore.add(Component.empty());
        if (selected) lore.add(ColorUtil.component(messages.raw("gui.selected")));
        else if (current == null || plugin.getConfig().getBoolean("settings.selection.change-allowed", false)) {
            lore.add(ColorUtil.component(messages.replace(messages.raw("gui.select-price"),
                    Map.of("price", economy.format(definition.selectionPrice()), "currency", economy.currencyName()))));
            lore.add(ColorUtil.component(messages.raw("gui.available")));
        } else lore.add(ColorUtil.component(messages.raw("gui.unavailable")));
        lore.add(ColorUtil.component(messages.raw("gui.left-click")));
        meta.lore(lore);
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
        meta.displayName(ColorUtil.component(name));
        meta.lore(lore.stream().map(ColorUtil::component).toList());
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
            meta.displayName(Component.text(" "));
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
    private record ConfirmHolder(String ideologyId) implements MenuHolder { }
}
