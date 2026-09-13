package ru.neverland.mintevents.gui;
import ru.neverland.localization.MaterialNameConfig;

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
        Inventory inventory = ru.neverland.core.MenuStyle.inventory(plugin, holder, 54,
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
                    active.raid() == null ? "&#FFFFFFПрогресс: &#B65CFF" + active.progress() + "&7/&#FFFFFF" + active.goal()
                            : "&fВолна: &d" + active.raid().wave() + "/10 &7· Врагов: &f" + active.raid().remaining(),
                    active.raid() == null ? "" : "&7Очки защиты: &f" + active.progress() + " &7· Победа после 10 волн",
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
        int damaged = events.fires().damage(town.getUUID()).size();
        inventory.setItem(45, item(Material.ANVIL, "&eРемонт после пожара", List.of(
                "&7Мест для восстановления: &f" + damaged,
                "&7Установите нужные блоки на прежние места.", "&7Затем подтвердите ремонт в этом разделе.",
                "", "&eЛКМ — список материалов и координат")));
        inventory.setItem(53, item(Material.BARRIER, "&fЗакрыть", List.of("&7Вернуться в игру.")));
        if (events.fireActive(town.getUUID())) inventory.setItem(48, item(Material.WATER_BUCKET, "&bТушение пожара", List.of(
                "&7Очагов поблизости жителей: &f" + events.fires().burning(town.getUUID()),
                "&7Разлейте воду рядом с огнём: это даёт очки защиты.",
                "&7Дерево и декор могут уцелеть или исчезнуть.",
                "&7После завершения события восстановите утраченные блоки.",
                "&7Проверить места: &f/t events repairs")));
        player.openInventory(inventory);
    }

    public void openRepairs(Player player, Town town) { openRepairs(player, town, 0); }

    private void openRepairs(Player player, Town town, int requestedPage) {
        List<ru.neverland.mintevents.model.FireDamage> damage = events.fires().damage(town.getUUID());
        int pages = Math.max(1, (damage.size() + 44) / 45);
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        EventMenuHolder holder = new EventMenuHolder(town.getUUID(), EventMenuHolder.Type.REPAIRS);
        holder.page(page);
        Inventory inventory = ru.neverland.core.MenuStyle.inventory(plugin, holder, 54, "Ремонт после пожара · " + (page + 1) + "/" + pages);
        holder.inventory(inventory);
        fill(inventory);
        var labels = new ru.neverland.localization.MaterialLabels();
        for (int index = page * 45; index < Math.min(damage.size(), (page + 1) * 45); index++) {
            var d = damage.get(index);
            var world = Bukkit.getWorld(d.world());
            int slot = index % 45;
            holder.repairs().put(slot, d.id());
            inventory.setItem(slot, item(Material.valueOf(d.material()), "&e" + labels.name(d.material()), List.of(
                    "&7Мир: &f" + (world == null ? d.world().toString() : world.getName()),
                    "&7Координаты: &f" + d.x() + " / " + d.y() + " / " + d.z(), "",
                    events.fires().placed(d) ? "&aНужный материал установлен. Подтвердите ремонт." : "&eУстановите этот материал на указанное место.",
                    "&7Направление блока восстановится при подтверждении.", "", "&bЛКМ — подсветить место рядом с вами")));
        }
        if (damage.isEmpty()) inventory.setItem(22, item(Material.LIME_DYE, "&aРемонт завершён", List.of("&7В городе нет незакрытых повреждений пожара.")));
        inventory.setItem(45, item(Material.ARROW, "&f← Назад", List.of("&7К городскому событию.")));
        if (page > 0) inventory.setItem(46, item(Material.PAPER, "&f← Предыдущая страница", List.of()));
        if (page + 1 < pages) inventory.setItem(52, item(Material.PAPER, "&fСледующая страница →", List.of()));
        if (player.hasPermission("mintevents.repair")) inventory.setItem(49, item(Material.SMITHING_TABLE, "&aПодтвердить ремонт", List.of(
                "&7Проверить установленные блоки в загруженных участках.",
                "&7Сначала завершите пожар и разместите нужные материалы.", "", "&aЛКМ — подтвердить восстановленные места")));
        inventory.setItem(53, item(Material.SPYGLASS, "&bОбновить", List.of("&7Проверить состояние блоков.")));
        player.openInventory(inventory);
    }

    public void confirmRepairs(Player player, Town town) {
        try {
            int count = events.fires().confirmRepairs(player, events.fireActive(town.getUUID()));
            player.sendMessage(count > 0 ? "§aПодтверждено мест ремонта: §f" + count
                    : "§eНет установленных блоков для подтверждения. Проверьте материалы и подойдите к месту ремонта.");
        } catch (IllegalArgumentException error) {
            player.sendMessage("§e" + error.getMessage());
        } catch (java.io.IOException | RuntimeException error) {
            player.sendMessage("§cНе удалось сохранить ремонт. Установленные блоки остались на месте; сообщите администратору.");
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Не удалось подтвердить ремонт после пожара", error);
        }
    }

    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EventMenuHolder) event.setCancelled(true);
    }

    public void openHistory(Player player, Town town) {
        EventMenuHolder holder = new EventMenuHolder(town.getUUID(), EventMenuHolder.Type.HISTORY);
        Inventory inventory = ru.neverland.core.MenuStyle.inventory(plugin, holder, 54,
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
        if (!player.hasPermission("mintevents.use")) { player.closeInventory(); messages.send(player, "no-permission"); return; }
        Town town = towny.town(player);
        if (town == null || !town.getUUID().equals(holder.townId())) {
            player.closeInventory();
            messages.send(player, "no-town");
            return;
        }
        int slot = event.getRawSlot();
        if (holder.type() == EventMenuHolder.Type.REPAIRS) {
            if (slot == 45) open(player);
            else if (slot == 49) { confirmRepairs(player, town); openRepairs(player, town, holder.page()); }
            else if (slot == 46 && holder.page() > 0) openRepairs(player, town, holder.page() - 1);
            else if (slot == 52) openRepairs(player, town, holder.page() + 1);
            else if (slot == 53) openRepairs(player, town, holder.page());
            else if (holder.repairs().containsKey(slot)) { player.closeInventory(); events.fires().highlight(player, holder.repairs().get(slot)); }
            return;
        }
        if (holder.type() == EventMenuHolder.Type.HISTORY) {
            if (slot == 49) open(player);
            return;
        }
        if (slot == 45) { openRepairs(player, town); return; }
        if (slot == 53) { player.closeInventory(); return; }
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
        boolean completesGoal = active.raid() == null && (long) active.progress() + result.points() >= active.goal();
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
        ItemStack filler = item(MaterialNameConfig.matchMaterial(plugin.getConfig().getString("gui.filler", "BLACK_STAINED_GLASS_PANE")), " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ru.neverland.core.MenuStyle.nameLegacy(ColorUtil.color(name)));
        meta.setLore(ru.neverland.core.MenuStyle.loreStrings(lore.stream().map(ColorUtil::color).toList()));
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
