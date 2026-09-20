package ru.neverland.townyachievements.gui;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import java.util.*;
import ru.neverland.townyachievements.model.*;
import ru.neverland.townyachievements.service.*;

public final class AchievementMenu implements Listener {
    private final Plugin plugin; private final AchievementService service;
    public AchievementMenu(Plugin plugin, AchievementService service) { this.plugin = plugin; this.service = service; }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static final class View implements InventoryHolder {
        final UUID player, town; final Map<Integer, Action> buttons = new HashMap<>(); Inventory inventory;
        View(UUID player, UUID town) { this.player = player; this.town = town; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private View view(Player p, String title) {
        var town = service.requireTown(p); var v = new View(p.getUniqueId(), town.getUUID()); v.inventory = Bukkit.createInventory(v, 54, title);
        for (int i = 45; i < 54; i++) button(v, i, "GRAY_STAINED_GLASS_PANE", " ", List.of(), null);
        return v;
    }
    private void button(View v, int slot, String icon, String title, List<String> lore, Action action) {
        Material material = Material.matchMaterial(icon); if (material == null || material.isAir()) material = Material.BOOK;
        var item = new ItemStack(material); var meta = item.getItemMeta(); meta.setDisplayName("§b" + title);
        meta.setLore(lore.stream().map(line -> "§7" + line).toList()); item.setItemMeta(meta); v.inventory.setItem(slot, item);
        if (action != null) v.buttons.put(slot, action);
    }
    public void home(Player p) { list(p, false, 0); }
    public void rewards(Player p) { list(p, true, 0); }
    private void list(Player p, boolean rewards, int requested) {
        var v = view(p, rewards ? "Награды города" : "Достижения города"); var city = service.state(v.town);
        var entries = service.catalogue(v.town).values().stream().filter(a -> !rewards || a.earned()).toList();
        int page = Math.max(0, Math.min(requested, (entries.size() - 1) / 45)); int slot = 0;
        for (var a : entries.stream().skip(page * 45L).limit(45).toList()) {
            var lore = new ArrayList<String>(); lore.add(a.earned() ? "§aОткрыто навсегда" : "Лучший прогресс: " + a.best() + "/" + a.definition().target());
            lore.add(a.definition().description()); lore.add("Титул: " + a.definition().reward().title());
            lore.add("Нажмите для условий и наград");
            button(v, slot++, a.definition().icon(), a.definition().name(), lore, () -> achievement(p, a.definition().id()));
        }
        if (rewards && entries.isEmpty()) button(v, 22, "PAPER", "Награды ещё не открыты", List.of("Выполняйте общие цели города", "Прогресс считается автоматически"), null);
        if (page > 0) button(v, 45, "ARROW", "Предыдущая страница", List.of(), () -> list(p, rewards, page - 1));
        button(v, 47, "BOOK", "Все достижения", List.of("Общий прогресс города"), () -> home(p));
        button(v, 49, "CHEST", "Открытые награды", List.of("Титулы, частицы и знамёна", "Титул города: " + (service.title(v.town).isEmpty() ? "не выбран" : service.title(v.town))), () -> rewards(p));
        button(v, 51, "BARRIER", "Отключить свои частицы", List.of("Открытая косметика сохраняется"), () -> { service.selectCosmetic(p, "none"); p.sendMessage("§aЧастицы отключены."); });
        if ((page + 1) * 45 < entries.size()) button(v, 53, "ARROW", "Следующая страница", List.of(), () -> list(p, rewards, page + 1));
        p.openInventory(v.inventory);
    }
    public void achievement(Player p, String id) {
        var v = view(p, "Достижение и награды"); var progress = service.catalogue(v.town).get(id);
        if (progress == null) throw new IllegalArgumentException("Достижение отсутствует"); var a = progress.definition();
        button(v, 4, a.icon(), a.name(), List.of(a.description(), progress.earned() ? "§aОткрыто навсегда" : "Лучший прогресс: " + progress.best() + "/" + a.target(), service.detail(v.town, id)), null);
        var conditions = new ArrayList<String>();
        conditions.add(switch (a.kind()) {
            case BALANCE -> "Баланс казны одновременно: " + a.target();
            case POPULATION -> "Население Population: " + a.target();
            case RAID_VICTORIES -> "Завершённые победы над набегами: " + a.target();
            case ALL_BUILDINGS -> "Все " + a.projects().size() + " зданий одновременно уровня V";
            case FIRST_WONDER -> "Любое чудо из каталога: уровень I или выше";
        });
        if (!a.projects().isEmpty()) {
            conditions.add("Каталог: " + a.projects().size() + " построек");
            conditions.add(a.kind() == Achievement.Kind.ALL_BUILDINGS ? "Чудеса в эту цель не входят" : "Учитывается завершённое строительство");
        }
        conditions.add("Переименование города не сбрасывает прогресс");
        button(v, 13, "SPYGLASS", "Условия", conditions, null);
        String lock = progress.earned() ? "§aДоступно" : "§cСначала откройте достижение";
        button(v, 28, "NAME_TAG", "Титул: " + a.reward().title(), List.of(lock, "Один выбранный титул на весь город", "Выбирает мэр или уполномоченный"), progress.earned() ? () -> { service.selectTitle(p, id); achievement(p, id); } : null);
        if (!a.reward().cosmetic().isEmpty()) button(v, 30, "FIREWORK_STAR", "Личный эффект частиц", List.of(lock, "Доступен жителям этого города", "Виден вам; включается по желанию"), progress.earned() ? () -> { service.selectCosmetic(p, id); p.sendMessage("§aЭффект включён."); } : null);
        if (!a.reward().banner().isEmpty()) button(v, 32, "WHITE_BANNER", "Памятное знамя", List.of(lock, "В основной руке: одно чистое белое знамя", "Нажмите, чтобы нанести городской узор", "Копии разрешены, предмет не даёт бонусов"), progress.earned() ? () -> BannerRewards.apply(plugin, service, p, id) : null);
        if (a.reward().happiness() > 0) button(v, 34, "SUNFLOWER", "Бонус довольства", List.of(lock, "+" + a.reward().happiness() + " к довольству Population", "Применяется автоматически после открытия"), null);
        button(v, 45, "ARROW", "К достижениям", List.of(), () -> home(p));
        button(v, 49, "COMPASS", "Обновить отображение", List.of("Проверки условий идут автоматически"), () -> achievement(p, id));
        button(v, 53, "BARRIER", "Снять титул города", List.of("Для мэра или уполномоченного"), () -> { service.selectTitle(p, "none"); achievement(p, id); });
        p.openInventory(v.inventory);
    }
    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof View v)) return; e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !v.player.equals(p.getUniqueId())) return;
        var town = service.town(p);
        if (town == null || !v.town.equals(town.getUUID()) || !p.hasPermission("neverlandtownyachievements.use")) { p.closeInventory(); return; }
        var action = v.buttons.get(e.getRawSlot()); if (action != null) try { action.run(); } catch (Exception ex) { p.sendMessage("§c" + ex.getMessage()); }
    }
    @EventHandler public void drag(InventoryDragEvent e) { if (e.getView().getTopInventory().getHolder() instanceof View) e.setCancelled(true); }
    public void close() { for (var p : Bukkit.getOnlinePlayers()) if (p.getOpenInventory().getTopInventory().getHolder() instanceof View) p.closeInventory(); }
}
