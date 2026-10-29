package ru.neverland.townyquests.gui;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import java.util.*;
import ru.neverland.townyquests.model.*;
import ru.neverland.townyquests.service.QuestService;

public final class QuestMenu implements Listener {
    private ru.neverland.townyquests.cooperative.CooperativeMenu cooperative;
    public void cooperative(ru.neverland.townyquests.cooperative.CooperativeMenu value){this.cooperative=value;}
    private final QuestService service;private final org.bukkit.plugin.Plugin plugin;
    public QuestMenu(org.bukkit.plugin.Plugin plugin,QuestService service) { this.service = service;this.plugin=plugin; }
    private static final class View implements InventoryHolder {
        final UUID player, town; final Map<Integer, Runnable> buttons = new HashMap<>(); Inventory inventory;
        View(UUID player, UUID town) { this.player = player; this.town = town; }
        @Override public Inventory getInventory() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return inventory; } }
    }
    private View view(Player player, String title) {
        var town = service.town(player);
        if (town == null || !player.hasPermission("neverlandtownyquests.use")) throw new IllegalArgumentException("Нужен свой город и доступ к проектам");
        var v = new View(player.getUniqueId(), town.getUUID()); v.inventory = Bukkit.createInventory(v, 54, title); return v;
    }
    private void button(View v, int slot, String icon, String title, List<String> lore, Runnable action) {
        Material material = Material.matchMaterial(icon); if (material == null || material.isAir()) material = Material.BOOK;
        var item = new ItemStack(material); var meta = item.getItemMeta(); meta.setDisplayName("§b" + title);
        meta.setLore(lore.stream().map(s -> "§7" + s).toList()); item.setItemMeta(meta); v.inventory.setItem(slot, item);
        if (action != null) v.buttons.put(slot, action);
    }
    public void home(Player player) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { home(player, 0); } }
    private void home(Player player, int requestedPage) {
        var v = view(player, "Городские проекты"); var state = service.state(v.town);
        Map<String, Project> definitions = new LinkedHashMap<>(service.settings().projects());
        state.forEach((id, run) -> definitions.put(id, run.definition()));
        int page = Math.max(0, Math.min(requestedPage, (definitions.size() - 1) / 45));
        int slot = 0;
        for (var p : definitions.values().stream().skip(page * 45L).limit(45).toList()) {
            var run = state.get(p.id()); var lore = new ArrayList<String>();
            lore.add(run == null ? "Не начат" : QuestService.status(run.status()));
            lore.add("Общий проект города; этапов: " + p.stages().size());
            if (run != null) lore.add("Завершено этапов: " + run.stage() + "/" + p.stages().size());
            if (!p.requires().isEmpty()) lore.add("После проектов: " + String.join(", ", p.requires().stream()
                    .map(id -> definitions.containsKey(id) ? definitions.get(id).name() : id).toList()));
            if(!p.choiceGroup().isEmpty()){String reason=service.branchReason(v.town,p);lore.add(reason.isEmpty()?"Альтернативное продолжение: выбор закрепится за городом":reason);}
            lore.add(p.reward()); lore.add("Нажмите, чтобы увидеть этапы");
            button(v, slot++, p.icon(), p.name(), lore, () -> project(player, p.id()));
        }
        if(cooperative!=null)button(v,47,"BRICKS","Общие стройки",List.of("Городские и серверные сметы", "Подтверждённые поставки из /orders", "История вкладов и резерв материалов"),()->cooperative.home(player,1));
        button(v, 49, "BELL", "Проекты всего города", List.of("Начинает мэр или уполномоченный", "Прогресс сохраняется за городом", "Отключение аддона не создаёт провалов"), null);
        if (page > 0) button(v, 45, "ARROW", "Предыдущая страница", List.of(), () -> home(player, page - 1));
        if ((page + 1) * 45 < definitions.size()) button(v, 53, "ARROW", "Следующая страница", List.of(), () -> home(player, page + 1));
        player.openInventory(v.inventory);
    }
    public void project(Player player, String id) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        var v = view(player, "Этапы городского проекта"); var run = service.state(v.town).get(id);
        var p = run == null ? service.settings().projects().get(id) : run.definition();
        if (p == null) throw new IllegalArgumentException("Проект отсутствует. Обновите меню");
        int index = 0;
        for (var stage : p.stages()) {
            int at = index++; boolean completed = run != null && at < run.stage(), current = run != null && at == run.stage();
            var lore = new ArrayList<String>(); lore.add(completed ? "Завершён" : current ? "Текущий этап" : "Предстоит выполнить");
            lore.add(stage.kind() == Project.Kind.BUILDING ? "Работающее здание: " + stage.building() + ", уровень " + stage.target()
                    : "Районов с водой одновременно: " + stage.target());
            if (stage.holdSeconds() > 0) lore.add("Непрерывное снабжение: " + (current ? run.heldSeconds() : completed ? stage.holdSeconds() : 0) + "/" + stage.holdSeconds() + " сек.");
            if (stage.kind() == Project.Kind.WATER_DISTRICTS) { lore.add("Связь с акведуком по территории города"); lore.add("Рабочая сеть и полное снабжение водой"); }
            if (current) lore.add(service.detail(v.town));
            button(v, at, completed ? "LIME_DYE" : current ? "CLOCK" : "PAPER", (at + 1) + ". " + stage.name(), lore, null);
        }
        button(v, 40, "KNOWLEDGE_BOOK", "Результат: " + p.name(), List.of(p.reward(), "Реформу принимает город в /t policies"), null);
        button(v, 45, "ARROW", "Все проекты", List.of(), () -> home(player));
        button(v, 47, "COMPASS", "Обновить прогресс", List.of(), () -> project(player, id));
        if (run == null || run.status() == CityProject.Status.CANCELLED || run.status() == CityProject.Status.PAUSED)
            button(v, 49, "EMERALD", run == null ? "Начать городской проект" : "Продолжить проект", List.of("Для мэра или уполномоченного", "Один текущий проект на город", "Выполненные этапы сохраняются"), () -> {
                try { if(run==null&&!p.choiceGroup().isEmpty()){confirmBranch(player,p);return;}service.begin(player, id); project(player, id); } catch (Exception ex) { player.sendMessage("§c" + ex.getMessage()); }
            });
        if (run != null && run.status() == CityProject.Status.COMPLETED)
            button(v, 51, "WRITABLE_BOOK", "Открыть городские политики", List.of("Результат проекта разблокирован", "Выбор и издержки указаны в политиках"), () -> { player.closeInventory(); player.performCommand("town policies"); });
        player.openInventory(v.inventory);
    } }
    private void confirmBranch(Player player,Project project){
        var v=view(player,"Выбор продолжения города");String reason=service.branchReason(v.town,project);
        button(v,13,project.icon(),project.name(),List.of(project.reward(),reason.isEmpty()?"Выбор сохранится за городом":reason,"Другая ветвь этой группы будет закрыта","Отмена проекта не меняет выбранное направление"),null);
        if(reason.isEmpty())button(v,20,"LIME_DYE","Подтвердить направление",List.of("Для мэра или уполномоченного"),()->{try{service.begin(player,project.id(),true);project(player,project.id());}catch(Exception ex){player.sendMessage("§e"+ex.getMessage());}});
        button(v,24,"ARROW","Вернуться",List.of("Продолжение пока не выбрано"),()->project(player,project.id()));player.openInventory(v.inventory);
    }
    @EventHandler public void click(InventoryClickEvent event) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyQuests")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        if (!(event.getView().getTopInventory().getHolder() instanceof View v)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !v.player.equals(player.getUniqueId())) return;
        var town = service.town(player);
        if (town == null || !v.town.equals(town.getUUID()) || !player.hasPermission("neverlandtownyquests.use")) { player.closeInventory(); return; }
        var action = v.buttons.get(event.getRawSlot());
        if (action != null) try { action.run(); } catch (Exception ex) { player.sendMessage("§c" + ex.getMessage()); }
    } }
    @EventHandler public void drag(InventoryDragEvent event) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyQuests")){ru.neverland.core.ModuleGate.stopEvent(event);return;} if (event.getView().getTopInventory().getHolder() instanceof View) event.setCancelled(true); } }
    public void close() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { ru.neverland.core.SuiteScheduler.cleanupPlayers(plugin,p->{if(p.getOpenInventory().getTopInventory().getHolder() instanceof View)p.closeInventory();}); } }
}
