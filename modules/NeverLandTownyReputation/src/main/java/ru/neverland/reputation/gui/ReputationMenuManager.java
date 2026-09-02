package ru.neverland.reputation.gui;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.reputation.integration.ItemsAdderHook;
import ru.neverland.reputation.integration.TownyHook;
import ru.neverland.reputation.model.RelationKey;
import ru.neverland.reputation.model.ReputationHistory;
import ru.neverland.reputation.model.ReputationRecord;
import ru.neverland.reputation.model.ReputationScope;
import ru.neverland.reputation.model.ReputationTier;
import ru.neverland.reputation.service.MessageService;
import ru.neverland.reputation.service.ReputationService;
import ru.neverland.reputation.util.ColorUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ReputationMenuManager implements Listener {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withLocale(new Locale("ru")).withZone(ZoneId.systemDefault());
    private final JavaPlugin plugin; private final TownyHook towny; private final ReputationService reputation; private final ItemsAdderHook itemsAdder; private final MessageService messages;
    private final NamespacedKey actionKey; private final NamespacedKey valueKey;
    public ReputationMenuManager(JavaPlugin plugin, TownyHook towny, ReputationService reputation, ItemsAdderHook itemsAdder, MessageService messages) {
        this.plugin = plugin; this.towny = towny; this.reputation = reputation; this.itemsAdder = itemsAdder; this.messages = messages;
        actionKey = new NamespacedKey(plugin, "menu_action"); valueKey = new NamespacedKey(plugin, "menu_value");
    }

    public void openMain(Player player) {
        Inventory menu = menu(ReputationMenuHolder.Type.MAIN, null, player.getUniqueId(), 45, config("gui.main-title")); fill(menu);
        menu.setItem(10, tagged(Material.PLAYER_HEAD, "scope", "PLAYER", "&#74C0FCИгроки", List.of("&7Ваши личные отношения", "&7Связей: &f" + reputation.involving(ReputationScope.PLAYER, player.getUniqueId()).size(), "", "&#FFD166Нажмите, чтобы открыть")));
        Town town = towny.town(player); menu.setItem(12, tagged(Material.BELL, "scope", "TOWN", "&#63E6BEГорода", List.of(town == null ? "&#FF6B6BВы не состоите в городе" : "&7Город: &f" + town.getName(), "&7Дипломатические отношения", "", "&#FFD166Нажмите, чтобы открыть")));
        Nation nation = towny.nation(player); menu.setItem(14, tagged(Material.BEACON, "scope", "NATION", "&#C77DFFНации", List.of(nation == null ? "&#FF6B6BУ вас нет нации" : "&7Нация: &f" + nation.getName(), "&7Международная репутация", "", "&#FFD166Нажмите, чтобы открыть")));
        menu.setItem(16, tagged(Material.EXPERIENCE_BOTTLE, "levels", "", "&#FFD166Уровни доверия", List.of("&7Пороги, бонусы и возможности", "", "&#FFD166Нажмите, чтобы открыть")));
        menu.setItem(31, item(Material.WRITABLE_BOOK, "&#ADB5BDОтзывы игроков", List.of("&f/rep endorse <игрок>", "&f/rep denounce <игрок>", "", "&7Отзывы ограничены задержкой", "&7и суточным лимитом.")));
        player.openInventory(menu);
    }

    public void openRelations(Player player, ReputationScope scope) {
        Owner owner = owner(player, scope); if (owner == null) return;
        String title = switch (scope) { case PLAYER -> config("gui.player-title"); case TOWN -> config("gui.town-title"); case NATION -> config("gui.nation-title"); };
        Inventory menu = menu(ReputationMenuHolder.Type.RELATIONS, scope, owner.id(), 54, title); fill(menu); int slot = 10;
        List<ReputationRecord> records = reputation.involving(scope, owner.id());
        for (ReputationRecord record : records) {
            while (slot < 44 && isBorder(slot)) slot++; if (slot >= 44) break;
            UUID other = record.key().other(owner.id()); String otherName = record.nameOf(other); ReputationTier tier = reputation.tier(record.score());
            String direction = scope == ReputationScope.PLAYER ? (record.key().first().equals(owner.id()) ? "&7Вы → &f" + otherName : "&f" + otherName + " &7→ Вы") : "&f" + owner.name() + " &7↔ &f" + otherName;
            List<String> lore = new ArrayList<>(); lore.add(direction); lore.add("&7Очки: " + scoreColor(record.score()) + signed(record.score())); lore.add("&7Уровень: " + tier.name());
            lore.add("&7История: &f" + record.history().size()); lore.add(""); lore.add("&#FFD166Нажмите, чтобы открыть историю");
            menu.setItem(slot++, tagged(icon(tier), "history", encode(record.key()), tier.name() + " &8— &f" + otherName, lore));
        }
        if (records.isEmpty()) menu.setItem(22, item(Material.BARRIER, "&#ADB5BDСвязей пока нет", List.of("&7Репутация появится после отзывов", "&7и событий других аддонов.")));
        menu.setItem(45, back()); menu.setItem(49, item(Material.NAME_TAG, "&#FFFFFF" + owner.name(), List.of("&7Найдено связей: &f" + records.size()))); player.openInventory(menu);
    }

    public void openHistory(Player player, RelationKey key) {
        ReputationRecord record = reputation.record(key.scope(), key.first(), key.second()); if (record == null) { messages.send(player, "relation-not-found"); return; }
        Inventory menu = menu(ReputationMenuHolder.Type.HISTORY, key.scope(), key.first(), 54, config("gui.history-title")); fill(menu); ReputationTier tier = reputation.tier(record.score());
        menu.setItem(4, item(icon(tier), tier.name(), List.of("&f" + record.firstName() + (key.scope() == ReputationScope.PLAYER ? " &7→ &f" : " &7↔ &f") + record.secondName(), "&7Текущее значение: " + scoreColor(record.score()) + signed(record.score()))));
        int slot = 10; for (ReputationHistory history : record.history()) { while (slot < 44 && isBorder(slot)) slot++; if (slot >= 44) break; List<String> lore = new ArrayList<>(); lore.add("&7" + DATE.format(Instant.ofEpochMilli(history.timestamp()))); lore.add("&7Изменение: " + scoreColor(history.delta()) + signed(history.delta())); lore.add("&7Было: &f" + history.oldScore() + " &7→ стало: &f" + history.newScore()); lore.add("&7Источник: &f" + sourceName(history.source())); if (!history.reason().isBlank()) lore.add("&7Причина: &f" + history.reason()); lore.add("&7Инициатор: &f" + history.actorName()); menu.setItem(slot++, item(history.delta() >= 0 ? Material.LIME_DYE : Material.RED_DYE, history.delta() >= 0 ? "&#63E6BEПовышение" : "&#FF6B6BПонижение", lore)); }
        menu.setItem(45, back()); player.openInventory(menu);
    }

    public void openLevels(Player player) {
        Inventory menu = menu(ReputationMenuHolder.Type.LEVELS, null, player.getUniqueId(), 45, config("gui.main-title") + " &8— уровни"); fill(menu); int slot = 10;
        for (ReputationTier tier : reputation.tiers()) { List<String> lore = new ArrayList<>(tier.description()); lore.add(""); lore.add("&7От: &f" + tier.minimumScore() + " очков"); lore.add("&7Скидка торговли: &f" + number(tier.tradeDiscountPercent()) + "%"); lore.add("&7Награды: &fx" + number(tier.rewardMultiplier())); if (!tier.privileges().isEmpty()) { lore.add(""); lore.add("&7Возможности:"); tier.privileges().forEach(value -> lore.add("&#63E6BE• &f" + value)); } menu.setItem(slot++, item(icon(tier), tier.name(), lore)); }
        menu.setItem(36, back()); player.openInventory(menu);
    }

    public void openTop(Player player, ReputationScope scope) {
        Inventory menu = menu(ReputationMenuHolder.Type.TOP, scope, player.getUniqueId(), 54, "&#18243AТоп репутации: &f" + scopeName(scope)); fill(menu); int slot = 10; int rank = 1;
        for (ReputationRecord record : reputation.top(scope, 28)) { while (slot < 44 && isBorder(slot)) slot++; if (slot >= 44) break; ReputationTier tier = reputation.tier(record.score()); menu.setItem(slot++, item(icon(tier), "&#FFD166#" + rank++ + " &f" + record.firstName() + (scope == ReputationScope.PLAYER ? " → " : " ↔ ") + record.secondName(), List.of("&7Очки: " + scoreColor(record.score()) + signed(record.score()), "&7Уровень: " + tier.name()))); }
        menu.setItem(45, back()); player.openInventory(menu);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ReputationMenuHolder holder)) return; event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack stack = event.getCurrentItem(); if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return;
        String action = stack.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING); String value = stack.getItemMeta().getPersistentDataContainer().get(valueKey, PersistentDataType.STRING); if (action == null) return;
        switch (action) { case "back" -> openMain(player); case "levels" -> openLevels(player); case "scope" -> { ReputationScope scope = ReputationScope.parse(value); if (scope != null) openRelations(player, scope); } case "history" -> { RelationKey key = decode(value); if (key != null) openHistory(player, key); } default -> { } }
    }
    @EventHandler public void onDrag(InventoryDragEvent event) { if (event.getInventory().getHolder() instanceof ReputationMenuHolder) event.setCancelled(true); }

    private Owner owner(Player player, ReputationScope scope) { if (scope == ReputationScope.PLAYER) return new Owner(player.getUniqueId(), player.getName()); if (scope == ReputationScope.TOWN) { Town town = towny.town(player); if (town == null) { messages.send(player, "no-town"); return null; } return new Owner(town.getUUID(), town.getName()); } Nation nation = towny.nation(player); if (nation == null) { messages.send(player, "no-nation"); return null; } return new Owner(nation.getUUID(), nation.getName()); }
    private Inventory menu(ReputationMenuHolder.Type type, ReputationScope scope, UUID owner, int size, String title) { return Bukkit.createInventory(new ReputationMenuHolder(type, scope, owner), size, ColorUtil.color(title)); }
    private void fill(Inventory menu) { Material material = Material.matchMaterial(plugin.getConfig().getString("gui.filler", "BLACK_STAINED_GLASS_PANE")); ItemStack filler = item(material == null ? Material.BLACK_STAINED_GLASS_PANE : material, " ", List.of()); for (int i = 0; i < menu.getSize(); i++) menu.setItem(i, filler); }
    private ItemStack back() { return tagged(Material.ARROW, "back", "", "&#ADB5BDНазад", List.of("&7Вернуться в главное меню")); }
    private ItemStack icon(ReputationTier tier) { ItemStack custom = plugin.getConfig().getBoolean("itemsadder.enabled", true) ? itemsAdder.item(tier.itemsAdderIcon()) : null; return custom == null ? new ItemStack(tier.material()) : custom; }
    private ItemStack item(Material material, String name, List<String> lore) { return item(new ItemStack(material), name, lore); }
    @SuppressWarnings("deprecation") private ItemStack item(ItemStack stack, String name, List<String> lore) { ItemMeta meta = stack.getItemMeta(); meta.setDisplayName(ColorUtil.color(name)); meta.setLore(lore.stream().map(ColorUtil::color).toList()); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); stack.setItemMeta(meta); return stack; }
    private ItemStack tagged(Material material, String action, String value, String name, List<String> lore) { return tagged(new ItemStack(material), action, value, name, lore); }
    private ItemStack tagged(ItemStack stack, String action, String value, String name, List<String> lore) { stack = item(stack, name, lore); ItemMeta meta = stack.getItemMeta(); meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action); meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value); stack.setItemMeta(meta); return stack; }
    private String encode(RelationKey key) { return key.scope() + ";" + key.first() + ";" + key.second(); }
    private RelationKey decode(String value) { try { String[] parts = value.split(";"); return RelationKey.of(ReputationScope.parse(parts[0]), UUID.fromString(parts[1]), UUID.fromString(parts[2])); } catch (RuntimeException ignored) { return null; } }
    private boolean isBorder(int slot) { return slot % 9 == 0 || slot % 9 == 8; }
    private String scoreColor(int score) { return score > 0 ? "&#63E6BE" : score < 0 ? "&#FF6B6B" : "&#ADB5BD"; }
    private String signed(int value) { return value > 0 ? "+" + value : String.valueOf(value); }
    private String number(double value) { return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.US, "%.2f", value); }
    private String scopeName(ReputationScope scope) { return switch (scope) { case PLAYER -> "игроки"; case TOWN -> "города"; case NATION -> "нации"; }; }
    private String sourceName(String source) { if (source == null) return "неизвестно"; return switch (source.toLowerCase(Locale.ROOT)) { case "player_feedback" -> "отзыв игрока"; case "trade" -> "торговля"; case "contract" -> "городской заказ"; case "expedition" -> "экспедиция"; case "governance" -> "решение совета"; case "quest" -> "задание"; case "alliance" -> "союз"; case "admin" -> "администратор"; case "decay" -> "естественное угасание"; default -> source; }; }
    private String config(String path) { return plugin.getConfig().getString(path, path); }
    private record Owner(UUID id, String name) { }
}
