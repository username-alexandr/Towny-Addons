package ru.neverland.townyenvironment.gui;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import ru.neverland.townyenvironment.service.EnvironmentService;

public final class EnvironmentMenu implements Listener {
    private final EnvironmentService service;
    public EnvironmentMenu(EnvironmentService service) { this.service = service; }
    private static final class View implements InventoryHolder {
        final UUID player, town; final Map<Integer, Runnable> buttons = new HashMap<>(); Inventory inventory;
        View(UUID player, UUID town) { this.player = player; this.town = town; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private View view(Player player, String title) {
        var town = service.requireTown(player); var view = new View(player.getUniqueId(), town.getUUID()); view.inventory = Bukkit.createInventory(view, 54, title);
        for (int i = 45; i < 54; i++) button(view, i, "GRAY_STAINED_GLASS_PANE", " ", List.of(), null); return view;
    }
    private void button(View view, int slot, String icon, String title, List<String> lore, Runnable action) {
        Material material = Material.matchMaterial(icon); if (material == null || material.isAir()) material = Material.BOOK;
        var item = new ItemStack(material); var meta = item.getItemMeta(); meta.setDisplayName("§b" + title); meta.setLore(lore.stream().map(s -> "§7" + s).toList()); item.setItemMeta(meta);
        view.inventory.setItem(slot, item); if (action != null) view.buttons.put(slot, action);
    }
    private static String f(double value) { return EnvironmentService.format(value); }
    public void home(Player player) {
        var view = view(player, "Экология города"); var state = service.state(view.town); var observation = service.observation(view.town);
        var pressure = observation.pressure(); var settings = service.settings(); var snapshot = service.environment(view.town);
        button(view, 4, "GRASS_BLOCK", "Загрязнение: " + f(state.pollution()) + " / 100", List.of(observation.status(),
                "Цикл: " + settings.interval() + " секунд работы сервера", "До цикла: " + snapshot.get("secondsUntilCycle") + " с", "Во время паузы и простоя начислений нет"), null);
        button(view, 20, "BLAST_FURNACE", "Источники загрязнения", List.of("+" + f(pressure.emissions()) + " за цикл", "Нажмите: здания и уровни", "Нагрузка действующих построек", "Не зависит от заполненности склада"), () -> sources(player, false, 0));
        button(view, 22, "OAK_SAPLING", "Очистка и зелёные зоны", List.of("−" + f(pressure.cleaning()) + " за цикл", "Природное восстановление: " + f(settings.naturalRecovery()), "Нажмите: парки и лесничество"), () -> sources(player, true, 0));
        button(view, 24, "WHEAT", "Влияние на город", List.of("Штрафы выше загрязнения " + f(settings.threshold()), "Довольство: " + f((double)snapshot.get("happiness")),
                "Аграрный выпуск еды: " + f((double)snapshot.get("agriculture") * 100) + "%", "Предел: −" + f(settings.maximumHappinessPenalty()) + " довольства",
                "Предел снижения еды: " + f(settings.maximumAgriculturePenalty() * 100) + "%", "Запасы и другие ресурсы сохраняются"), null);
        button(view, 31, "CLOCK", "Прогноз следующего цикла", List.of(observation.ready() ? "Изменение: " + f(pressure.change()) : "Пауза: изменения и штрафы отключены", "Парки и лесничество очищают город постепенно", "Учитываются завершённые действующие здания"), null);
        button(view, 49, "COMPASS", "Обновить", List.of(), () -> home(player)); player.openInventory(view.inventory);
    }
    public void sources(Player player, boolean cleaning, int requested) {
        var view = view(player, cleaning ? "Экология: очистка" : "Экология: промышленность"); var observation = service.observation(view.town);
        var entries = service.settings().buildings().entrySet().stream().filter(e -> cleaning ? e.getValue().cleaning() > 0 : e.getValue().emission() > 0).toList();
        int page = Math.max(0, Math.min(requested, (entries.size() - 1) / 45)), slot = 0;
        for (var entry : entries.stream().skip(page * 45L).limit(45).toList()) {
            var profile = entry.getValue(); int level = observation.levels().getOrDefault(entry.getKey(), 0); double amount = cleaning ? profile.cleaning() : profile.emission();
            button(view, slot++, profile.icon(), profile.name(), List.of("Действующий уровень: " + level, "За уровень: " + (cleaning ? "−" : "+") + f(amount),
                    "За цикл: " + (cleaning ? "−" : "+") + f(amount * level), "Не работает / не построено: нагрузка 0", observation.ready() ? "Данные актуальны" : "Последние данные; расчёт приостановлен"), null);
        }
        button(view, 49, "ARROW", "К экологии города", List.of(), () -> home(player));
        if (page > 0) button(view, 45, "ARROW", "Назад", List.of(), () -> sources(player, cleaning, page - 1));
        if ((page + 1) * 45 < entries.size()) button(view, 53, "ARROW", "Далее", List.of(), () -> sources(player, cleaning, page + 1));
        player.openInventory(view.inventory);
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof View view)) return; event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !view.player.equals(player.getUniqueId())) return;
        var town = service.town(player);
        if (town == null || !view.town.equals(town.getUUID()) || !player.hasPermission("neverlandtownyenvironment.use")) { player.closeInventory(); return; }
        var action = view.buttons.get(event.getRawSlot()); if (action != null) try { action.run(); } catch (Exception ex) { player.sendMessage("§c" + ex.getMessage()); }
    }
    @EventHandler public void drag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof View) event.setCancelled(true); }
    public void close() { for (var player : Bukkit.getOnlinePlayers()) if (player.getOpenInventory().getTopInventory().getHolder() instanceof View) player.closeInventory(); }
}
