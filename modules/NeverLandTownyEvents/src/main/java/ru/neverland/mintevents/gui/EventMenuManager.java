package ru.neverland.mintevents.gui;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import ru.neverland.mintevents.integration.TownyHook;
import ru.neverland.mintevents.model.ActiveEvent;
import ru.neverland.mintevents.model.ContributionRule;
import ru.neverland.mintevents.model.EventDefinition;
import ru.neverland.mintevents.model.HistoryEntry;
import ru.neverland.mintevents.service.ContributionService;
import ru.neverland.mintevents.service.EventService;
import ru.neverland.mintevents.service.MessageService;
import ru.neverland.mintevents.util.ColorUtil;
import ru.neverland.mintevents.util.TimeUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EventMenuManager implements Listener {
    private static final int[] CONTRIBUTION_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withLocale(new Locale("ru")).withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final EventService events;
    private final MessageService messages;
    private final ContributionService contributions = new ContributionService();

    public EventMenuManager(JavaPlugin plugin, TownyHook towny, EventService events, MessageService messages) {
        this.plugin = plugin;
        this.towny = towny;
        this.events = events;
        this.messages = messages;
    }

    public void open(Player player) {
        Town town = towny.town(player);
        if (town == null) {
            messages.send(player, "no-town");
            return;
        }
        EventMenuHolder holder = new EventMenuHolder(town.getUUID(), EventMenuHolder.Type.MAIN);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                ColorUtil.color(plugin.getConfig().getString("gui.title", "Городское событие")));
        holder.inventory(inventory);
        fill(inventory);
        ActiveEvent active = events.active(town.getUUID());
        EventDefinition definition = events.definition(active);
        if (active == null || definition == null) {
            inventory.setItem(22, item(Material.CLOCK, "&#AAAAAAСейчас спокойно",
                    List.of("&7Активных событий в городе нет.", "&7Следите за городскими объявлениями.")));
        } else {
            inventory.setItem(4, item(definition.icon(), definition.name(), List.of(
                    "&7" + definition.description(), "",
                    "&#FFFFFFПрогресс: &#B65CFF" + active.progress() + "&7/&#FFFFFF" + active.goal(),
                    progressBar(active.progressRatio()),
                    "&#FFFFFFОсталось: &#FFD45A" + TimeUtil.format(active.secondsLeft(System.currentTimeMillis())),
                    "&#FFFFFFЗащита города: &#63E6BE" + Math.round(active.protection() * 100) + "%")));
            int index = 0;
            for (ContributionRule rule : definition.contributions()) {
                if (index >= CONTRIBUTION_SLOTS.length) break;
                int slot = CONTRIBUTION_SLOTS[index++];
                holder.rules().put(slot, rule);
                inventory.setItem(slot, contributionItem(rule, "&#63E6BE" + rule.name(), List.of(
                        "&7Очков за предмет: &#FFFFFF" + rule.points(),
                        "&7Бонус защиты к очкам: &#63E6BE+" + Math.round(active.protection() * 50) + "%", "",
                        "&#55FF55ЛКМ — внести 1 предмет",
                        "&#FFD45AShift + ЛКМ — внести всё")));
            }
            inventory.setItem(40, protectionItem(town, definition, active));
        }
        inventory.setItem(49, item(Material.BOOK, "&#65B8FFИстория событий", List.of("&7Показать последние результаты города.")));
        player.openInventory(inventory);
    }

    public void openHistory(Player player, Town town) {
        EventMenuHolder holder = new EventMenuHolder(town.getUUID(), EventMenuHolder.Type.HISTORY);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                ColorUtil.color(plugin.getConfig().getString("gui.history-title", "История событий")));
        holder.inventory(inventory);
        fill(inventory);
        int slot = 0;
        for (HistoryEntry entry : events.history(town.getUUID())) {
            EventDefinition definition = events.registry().get(entry.eventId());
            String name = definition == null ? entry.eventId() : definition.name();
            Material icon = entry.success() ? Material.LIME_DYE : Material.RED_DYE;
            inventory.setItem(slot++, item(icon, name, List.of(
                    entry.success() ? "&#55FF55Успешно завершено" : "&#FF5555Цель не выполнена",
                    "&7Прогресс: &f" + entry.progress() + "/" + entry.goal(),
                    "&7Начало: &f" + DATE.format(Instant.ofEpochMilli(entry.startedAt())),
                    "&7Завершение: &f" + DATE.format(Instant.ofEpochMilli(entry.endedAt())))));
            if (slot >= 45) break;
        }
        inventory.setItem(49, item(Material.ARROW, "&#FFFFFFНазад", List.of("&7Вернуться к событию.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof EventMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Town town = towny.town(player);
        if (town == null || !town.getUUID().equals(holder.townId())) {
            player.closeInventory();
            messages.send(player, "no-town");
            return;
        }
        int slot = event.getRawSlot();
        if (holder.type() == EventMenuHolder.Type.HISTORY) {
            if (slot == 49) open(player);
            return;
        }
        if (slot == 49) {
            openHistory(player, town);
            return;
        }
        ContributionRule rule = holder.rules().get(slot);
        if (rule == null) return;
        if (!player.hasPermission("mintevents.contribute")) {
            messages.send(player, "no-permission");
            return;
        }
        ActiveEvent active = events.active(town.getUUID());
        if (active == null) {
            messages.send(player, "no-event");
            open(player);
            return;
        }
        ContributionService.Result result = contributions.contribute(player, rule, event.isShiftClick(), active);
        if (result.amount() <= 0) {
            messages.send(player, "no-items");
            return;
        }
        boolean completesGoal = active.progress() + result.points() >= active.goal();
        events.contribute(town, result.points());
        messages.send(player, "contributed", Map.of("amount", result.amount(), "item", rule.name(), "points", result.points()));
        if (completesGoal) messages.send(player, "goal-complete");
        open(player);
    }

    private ItemStack protectionItem(Town town, EventDefinition definition, ActiveEvent active) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Постройки и идеологии ослабляют событие.");
        lore.add("");
        events.development().buildingLevels(town.getUUID(), definition).forEach((id, level) -> {
            double value = definition.buildingModifiers().getOrDefault(id, 0.0) * level * 100;
            lore.add("&#FFFFFF" + buildingName(id) + " " + level + " ур. &8— &#63E6BE" + Math.round(value) + "%");
        });
        events.development().ideologyLevels(town.getUUID(), definition).forEach((id, level) -> {
            double value = definition.ideologyModifiers().getOrDefault(id, 0.0) * level * 100;
            lore.add("&#FFFFFF" + ideologyName(id) + " " + level + " ур. &8— &#63E6BE" + Math.round(value) + "%");
        });
        lore.add("");
        lore.add("&#FFFFFFИтоговая защита: &#63E6BE" + Math.round(active.protection() * 100) + "%");
        return item(Material.SHIELD, "&#63E6BEЗащита города", lore);
    }

    private String buildingName(String id) {
        return switch (id) {
            case "temple" -> "Священный Храм";
            case "great_library" -> "Великая Библиотека";
            case "agrarian_complex" -> "Аграрный Комплекс";
            case "town_hall" -> "Ратуша";
            case "forge" -> "Кузница";
            case "barracks" -> "Казармы";
            case "market" -> "Рынок";
            default -> id;
        };
    }

    private String ideologyName(String id) {
        return switch (id) {
            case "healthcare" -> "Здравоохранение";
            case "agriculture" -> "Сельское хозяйство";
            case "industry" -> "Промышленность";
            case "infrastructure" -> "Инфраструктура";
            case "military" -> "Военное дело";
            case "culture" -> "Культура";
            default -> id;
        };
    }

    private String progressBar(double ratio) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * 20);
        return "&#B65CFF" + "■".repeat(filled) + "&#3A3146" + "■".repeat(20 - filled);
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.matchMaterial(plugin.getConfig().getString("gui.filler", "BLACK_STAINED_GLASS_PANE")), " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ColorUtil.color(name));
        meta.setLore(lore.stream().map(ColorUtil::color).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack contributionItem(ContributionRule rule, String name, List<String> lore) {
        ItemStack stack = item(rule.material(), name, lore);
        if (rule.key().equalsIgnoreCase("FIRE_RESISTANCE") && stack.getItemMeta() instanceof PotionMeta potion) {
            potion.setBasePotionType(PotionType.FIRE_RESISTANCE);
            stack.setItemMeta(potion);
        }
        return stack;
    }
}
