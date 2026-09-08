package ru.neverland.mintexpeditions.gui;
import ru.neverland.localization.MaterialNameConfig;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintexpeditions.MintTownyExpeditions;
import ru.neverland.mintexpeditions.model.ExpeditionDefinition;
import ru.neverland.mintexpeditions.service.ExpeditionService;
import ru.neverland.mintexpeditions.service.RussianItemNames;
import ru.neverland.mintexpeditions.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ExpeditionMenuManager implements Listener {
    private final JavaPlugin plugin;
    private final ExpeditionService service;
    private final RussianItemNames itemNames;

    public ExpeditionMenuManager(JavaPlugin plugin, ExpeditionService service,
                                 RussianItemNames itemNames) {
        this.plugin = plugin;
        this.service = service;
        this.itemNames = itemNames;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(
                new ExpeditionMenuHolder(), 54,
                ColorUtil.color(plugin.getConfig().getString(
                        "gui.title", "&#18243AЭкспедиции NeverLand")));
        Material filler = MaterialNameConfig.matchMaterial(
                plugin.getConfig().getString("gui.filler", "BLACK_STAINED_GLASS_PANE"));
        ItemStack glass = item(
                filler == null ? Material.BLACK_STAINED_GLASS_PANE : filler,
                " ", List.of());
        for (int slot = 0; slot < 54; slot++) inventory.setItem(slot, glass);

        for (ExpeditionDefinition definition : service.registry().all()) {
            List<String> lore = new ArrayList<>();
            definition.description().forEach(line -> lore.add("&#AAAAAA" + line));
            lore.add("");
            lore.add("&#FFFFFFУровень лагеря: &#C56DFF" + definition.minCampLevel());
            lore.add("&#FFFFFFВремя: &#C56DFF"
                    + (definition.durationSeconds() / 60) + " мин");
            lore.add("&#FFFFFFЦелей: &#C56DFF" + definition.goal()
                    + " &#555555| &#FFFFFFВрагов: &#C56DFF" + definition.mobCount());
            lore.add("");
            lore.add("&#FFD45AПрипасы из схрона:");
            definition.costs().forEach(cost -> lore.add(
                    "&#AAAAAA• " + itemNames.name(cost.item()) + " × " + cost.amount()));
            lore.add("");
            lore.add("&#AAAAAAПосле старта вы получите координаты.");
            lore.add("&#AAAAAAДо точки нужно добраться самостоятельно.");
            lore.add("&#55FF55Нажмите, чтобы начать");
            inventory.setItem(
                    Math.max(0, Math.min(53, definition.slot())),
                    item(definition.icon(), definition.name(), lore));
        }
        player.openInventory(inventory);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ColorUtil.color(name));
        meta.setLore(lore.stream().map(ColorUtil::color).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ExpeditionMenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        for (ExpeditionDefinition definition : service.registry().all()) {
            if (definition.slot() == event.getRawSlot()) {
                player.closeInventory();
                start(player, definition);
                return;
            }
        }
    }

    public void start(Player player, ExpeditionDefinition definition) {
        ExpeditionService.StartResult result = service.start(player, definition);
        switch (result) {
            case NO_CAMP -> message(player, "no-camp");
            case INACTIVE -> message(player, "camp-inactive");
            case TOO_FAR -> message(player, "not-at-camp");
            case STASH_OPEN -> message(player, "stash-open");
            case LOW_LEVEL -> pluginMessage(
                    player, "camp-level", Map.of("level", definition.minCampLevel()));
            case ACTIVE -> message(player, "active-exists");
            case WORLD_BLOCKED -> message(player, "world-blocked");
            default -> {
            }
        }
    }

    private void message(Player player, String key) {
        ((MintTownyExpeditions) plugin).messages().send(player, key);
    }

    private void pluginMessage(Player player, String key, Map<String, ?> variables) {
        ((MintTownyExpeditions) plugin).messages().send(player, key, variables);
    }
}

