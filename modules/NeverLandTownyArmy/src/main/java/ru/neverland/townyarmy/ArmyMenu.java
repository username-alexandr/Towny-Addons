package ru.neverland.townyarmy;

import java.util.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.MenuStyle;
import static ru.neverland.townyarmy.ArmyModel.*;

/** The holder owns actions; inventory contents never authorize an operation. */
public final class ArmyMenu implements Listener {
    private final NeverLandTownyArmy plugin; private final ArmyService service;
    private interface Action { void run(Player p) throws Exception; }
    private static final class Holder implements InventoryHolder {
        final UUID viewer; final Map<Integer, Action> actions = new HashMap<>(); Inventory inventory;
        Holder(UUID viewer) { this.viewer = viewer; }
        @Override public Inventory getInventory() { return inventory; }
    }
    public ArmyMenu(NeverLandTownyArmy p, ArmyService s) { plugin = p; service = s; }
    public static String name(UUID id) { if (id.equals(SYSTEM)) return "Система"; var r = TownyAPI.getInstance().getResident(id); return r == null ? id.toString().substring(0, 8) : r.getName(); }
    private static String resource(String id) { return Map.of("food","Пища","water","Вода","metal","Металл","materials","Материалы","wood","Древесина","stone","Камень","knowledge","Знания","influence","Влияние").getOrDefault(id,id); }
    private static String building(String id) { return Map.of("army","Штаб армии","barracks","Казармы","stables","Конюшни","shipyard","Верфь","workshop","Мастерская","observatory","Обсерватория").getOrDefault(id,id); }
    private static String action(String id) { return switch(id) { case "APPLICATION" -> "Заявка на службу"; case "ADMISSION" -> "Приём рекрута"; case "OATH" -> "Присяга"; case "COMMANDER" -> "Назначение генерала"; case "PROMOTION" -> "Повышение"; case "DEMOTE" -> "Понижение"; case "DISMISS", "LEFT_TOWN" -> "Прекращение службы"; case "WARN" -> "Взыскание"; case "PARDON" -> "Снятие взысканий"; case "TRANSFER" -> "Перевод подразделения"; case "MOBILIZE", "ALERT" -> "Мобилизация"; case "RESERVE" -> "Перевод в резерв"; case "BASE" -> "Военная база"; case "SUPPLY_ORDER" -> "Заказ снабжения"; case "SUPPLY_APPLIED" -> "Поставка принята"; case "EQUIPMENT" -> "Экипировка"; case "TRAINING" -> "Тренировка"; case "UPKEEP" -> "Содержание гарнизона"; case "SHORTAGE" -> "Недостаток снабжения"; default -> id; }; }
    public static String amount(long value) { return BigDecimal.valueOf(value, 3).stripTrailingZeros().toPlainString(); }
    public static String status(Status value) { return switch (value) { case APPLICANT -> "Заявка"; case RECRUIT -> "Рекрут • ожидает присяги"; case RESERVE -> "Военный резерв"; case ACTIVE -> "Мобилизован"; case DISCHARGED -> "Уволен"; }; }
    public static String phase(Phase phase) { return switch (phase) { case PLANNED -> "ожидает обработки"; case APPLIED, CLOSED -> "зачислена"; case DECLINED, CANCELLED -> "отклонена: ресурсов недостаточно"; }; }
    private Holder create(Player p, String title) { var h = new Holder(p.getUniqueId()); h.inventory = MenuStyle.inventory(plugin, h, 54, title); return h; }
    private void item(Holder h, int slot, Material icon, String title, List<String> lore, Action action) {
        var item = new ItemStack(icon); var meta = item.getItemMeta(); meta.displayName(MenuStyle.nameComponent(MenuStyle.decode(title)));
        meta.lore(MenuStyle.loreComponents(lore.stream().map(MenuStyle::decode).toList())); item.setItemMeta(meta); h.inventory.setItem(slot, item); if (action != null) h.actions.put(slot, action);
    }
    private void back(Holder h, Inventory previous) { item(h, 45, Material.ARROW, "&e← Назад", List.of(previous == null ? "Закрыть меню" : "Вернуться в предыдущее меню"), p -> MenuStyle.returnTo(p, previous)); }
    private UUID town(Player p) { if (!p.hasPermission("neverlandtownyarmy.use")) throw new IllegalArgumentException("Недостаточно прав"); return service.ownTown(p).getUUID(); }
    public void open(Player p, String page, int requested, Inventory previous) throws Exception {
        UUID town = town(p); var h = create(p, "Армия • " + switch (page) { case "roster" -> "Личный состав"; case "stock" -> "Военные запасы"; case "history" -> "Журнал приказов"; case "units" -> "Подразделения"; case "ranks" -> "Звания"; default -> "Штаб города"; });
        back(h, previous);
        if (!service.healthy()) throw new IllegalArgumentException("Армия приостановлена; обратитесь к администратору");
        if (page.equals("menu")) {
            var g = service.garrison(town).orElseThrow(); var own = service.repository().state().soldiers().get(p.getUniqueId());
            item(h, 4, Material.SHIELD, "&6Гарнизон города", List.of("&fБоевой состав: &e" + g.get("active") + " / " + g.get("capacity"), "&fВ резерве: &b" + g.get("reserve"), "&fГотовность: &a" + String.format(Locale.ROOT, "%.1f%%", g.get("readiness")), "&fСила: &e" + String.format(Locale.ROOT, "%.2f", g.get("score")), "", "Готовность зависит от подготовки,", "экипировки, инфраструктуры и снабжения.", "&fОбщая мобилизация: " + (Boolean.TRUE.equals(g.get("alert")) ? "&cобъявлена" : "&aнет")), null);
            item(h, 19, Material.WRITABLE_BOOK, "&bМоя служба", List.of(own == null ? "Вы ещё не подавали заявку." : "&f" + status(own.status()), "", "Нажмите: служебная карточка", "или выбор подразделения"), viewer -> { if (own == null) open(viewer, "units", 0, h.inventory); else record(viewer, own.resident(), h.inventory); });
            item(h, 21, Material.PLAYER_HEAD, "&aЛичный состав", List.of("Заявки, военнослужащие и резервисты", "Нажмите: открыть состав"), viewer -> open(viewer, "roster", 0, h.inventory));
            item(h, 23, Material.CHEST, "&6Военные запасы", List.of("Снабжение, экипировка и обучение", "Нажмите: открыть склад"), viewer -> open(viewer, "stock", 0, h.inventory));
            item(h, 25, Material.IRON_SWORD, "&bПодразделения и инфраструктура", List.of("Пехота • конница • флот • авиация", "Нажмите: требования и поступление"), viewer -> open(viewer, "units", 0, h.inventory));
            item(h, 31, Material.GOLD_NUGGET, "&eЗвания и повышение", List.of("Ступени карьеры и требования", "Нажмите: открыть звания"), viewer -> open(viewer, "ranks", 0, h.inventory));
            if (service.officer(p, town, Rank.LIEUTENANT)) item(h, 49, Material.BOOK, "&eЖурнал командования", List.of("Кадровые приказы и снабжение", "Нажмите: открыть журнал"), viewer -> open(viewer, "history", 0, h.inventory));
            if (service.officer(p, town, Rank.GENERAL)) item(h, 53, Material.BELL, "&6Командование", List.of("/army base set — назначить базу", "/army alert on — общая мобилизация", "/army alert off — отбой", "", "Мэр и министр обороны назначают", "генерала через /army commission."), null);
        } else if (page.equals("roster")) {
            var all = service.repository().state().soldiers().values().stream().filter(s -> s.town().equals(town)).sorted(Comparator.<Soldier, Status>comparing(Soldier::status).thenComparing(Soldier::rank).thenComparing(Soldier::resident)).toList();
            int n = Math.max(0, Math.min(requested, Math.max(0, (all.size() - 1) / 36)));
            for (int i = n * 36; i < Math.min(all.size(), (n + 1) * 36); i++) { var s = all.get(i); item(h, i % 36, service.settings().units().get(s.unit()).icon(), "&b" + name(s.resident()), List.of("&f" + service.settings().ranks().get(s.rank()).title(), "&f" + status(s.status()), "&f" + service.settings().units().get(s.unit()).title(), "Подготовка: " + s.training() + "%", "Нажмите: служебная карточка"), viewer -> record(viewer, s.resident(), h.inventory)); }
            pages(h, page, n, all.size(), previous);
        } else if (page.equals("stock")) {
            var city = service.repository().state().cities().getOrDefault(town, City.empty(town)); int slot = 10;
            for (String resource : RESOURCES.stream().sorted().toList()) item(h, slot++, Material.BARREL, "&e" + resource(resource), List.of("&fВ военном резерве: &a" + amount(city.stock().getOrDefault(resource, 0L))), null);
            item(h, 31, Material.CRAFTING_TABLE, "&bКак работает снабжение", List.of("Resources → военный резерв → гарнизон", "Запасы расходуются на экипировку,", "тренировки и содержание действующих сил.", "", "Перевод выделяет ресурсы армии окончательно.", "Штатная экипировка — стратегический показатель;", "предметы в инвентарь не выдаются."), null);
            if (service.officer(p, town, Rank.SERGEANT)) item(h, 49, Material.HOPPER, "&aПополнение склада", List.of("/army supply <ресурс> <количество>", "/army equip <игрок> — снабдить бойца", "", "Городские защищённые резервы", "Resources остаются неприкосновенными."), null);
        } else if (page.equals("units")) {
            int slot = 10; var g = service.garrison(town).orElseThrow(); var counts = (Map<?, ?>)g.get("units");
            for (Unit u : Unit.values()) {
                var spec = service.settings().units().get(u); var unit = (Map<?, ?>)counts.get(u.id()); var lore = new ArrayList<String>();
                lore.add("&fБоевой состав: &e" + unit.get("active") + " / " + unit.get("capacity")); lore.add(""); lore.add("&bНужные действующие здания:");
                spec.requirements().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> lore.add("• " + building(e.getKey()) + ": уровень " + e.getValue()));
                lore.add(""); lore.add("&fКомплект снабжения:"); spec.kit().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> lore.add("• " + resource(e.getKey()) + ": " + amount(e.getValue())));
                lore.add(""); lore.add("Нажмите: подать заявку на службу");
                item(h, slot, spec.icon(), "&b" + spec.title(), lore, viewer -> { service.apply(viewer, u); ArmyCommand.say(viewer, "&aЗаявка сохранена."); open(viewer, "menu", 0, previous); }); slot += 2;
            }
            item(h, 31, Material.MAP, "&eСлужба в мире Towny", List.of("Подразделения дают стратегическую силу.", "Игроки используют транспорт и снаряжение", "своего сервера. NPC и летательные аппараты", "этот модуль не создаёт."), null);
        } else if (page.equals("ranks")) {
            int slot = 0; for (Rank rank : Rank.values()) { var spec = service.settings().ranks().get(rank); var lore = new ArrayList<String>();
                if (rank == Rank.RECRUIT) lore.add("После одобрения заявки"); else if (rank == Rank.PRIVATE) lore.add("После присяги на базе"); else if (rank == Rank.GENERAL) lore.add("Назначение мэром или министром обороны"); else { lore.add("Подготовка: " + spec.training() + "%"); lore.add("Дежурство: " + spec.dutyMinutes() + " минут"); lore.add("Повышение только на одну ступень"); }
                lore.add(""); if (rank.atLeast(Rank.CAPTAIN)) lore.add("Кадровые приказы младшим по званию"); if (rank.atLeast(Rank.LIEUTENANT)) lore.add("Приём и мобилизация"); if (rank.atLeast(Rank.SERGEANT)) lore.add("Снабжение и экипировка");
                item(h, slot++, rank == Rank.GENERAL ? Material.NETHER_STAR : Material.GOLD_NUGGET, "&e" + spec.title(), lore, null);
            }
        } else if (page.equals("history")) {
            if (!service.officer(p, town, Rank.LIEUTENANT)) throw new IllegalArgumentException("Журнал доступен командованию");
            var all = new ArrayList<>(service.repository().state().audit().stream().filter(a -> a.town().equals(town)).toList()); Collections.reverse(all);
            int n = Math.max(0, Math.min(requested, Math.max(0, (all.size() - 1) / 36)));
            for (int i = n * 36; i < Math.min(all.size(), (n + 1) * 36); i++) { var a = all.get(i); item(h, i % 36, Material.PAPER, "&e" + action(a.action()), List.of("&f" + DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(a.time())), "&fАвтор: " + name(a.actor()), "&fУчастник: " + name(a.subject()), "", a.reason()), null); }
            pages(h, page, n, all.size(), previous);
        } else throw new IllegalArgumentException("Страница не найдена");
        p.openInventory(h.inventory);
    }
    private void pages(Holder h, String page, int n, int size, Inventory previous) {
        if (n > 0) item(h, 48, Material.PAPER, "&e← Предыдущая страница", List.of(), p -> open(p, page, n - 1, previous));
        if ((n + 1) * 36 < size) item(h, 50, Material.PAPER, "&eСледующая страница →", List.of(), p -> open(p, page, n + 1, previous));
        item(h, 49, Material.MAP, "&fСтраница " + (n + 1), List.of("Записей: " + size), null);
    }
    public void record(Player p, UUID id, Inventory previous) throws Exception {
        UUID town = town(p); var s = service.repository().state().soldiers().get(id);
        if (!service.healthy() || s == null || !s.town().equals(town)) throw new IllegalArgumentException("Карточка не найдена в вашем городе");
        var h = create(p, "Армия • " + name(id)); back(h, previous);
        item(h, 13, service.settings().units().get(s.unit()).icon(), "&b" + name(id), List.of("&e" + service.settings().ranks().get(s.rank()).title(), "&f" + status(s.status()), "&f" + service.settings().units().get(s.unit()).title(), "", "Подготовка: " + s.training() + "%", "Экипировка: " + s.equipment() + "%", "Дежурство: " + s.dutyMillis() / 60000 + " минут", "Взыскания: " + s.warnings() + " / 3", "Сейчас на дежурстве: " + (service.onDuty(id) ? "да" : "нет")), null);
        if (id.equals(p.getUniqueId()) && s.serving()) {
            if (s.status() == Status.RECRUIT) item(h, 29, Material.WRITABLE_BOOK, "&aПринять присягу", List.of("Находясь на военной базе", "Нажмите: подтвердить присягу"), viewer -> { service.oath(viewer); record(viewer, id, previous); });
            else item(h, 29, Material.CLOCK, service.onDuty(id) ? "&eЗакончить дежурство" : "&aЗаступить на дежурство", List.of("Заступить можно на базе", "Нажмите: изменить дежурство"), viewer -> { service.duty(viewer, !service.onDuty(id)); record(viewer, id, previous); });
            item(h, 31, Material.TARGET, "&bНачать тренировку", List.of("На базе, во время дежурства", "Нужны запасы, время и движение", "Нажмите: начать подготовку"), viewer -> { service.train(viewer); viewer.closeInventory(); ArmyCommand.say(viewer, "&eТренировка началась. Оставайтесь на базе и двигайтесь."); });
            if (s.status() == Status.RECRUIT) item(h, 33, Material.CLOCK, "&aДежурство рекрута", List.of("Нажмите: заступить или завершить"), viewer -> { service.duty(viewer, !service.onDuty(id)); record(viewer, id, previous); });
        }
        if (service.officer(p, town, Rank.LIEUTENANT) && !id.equals(p.getUniqueId())) {
            if (s.status() == Status.APPLICANT) item(h, 28, Material.LIME_DYE, "&aОдобрить заявку", List.of("Нажмите: принять рекрута"), viewer -> { service.approve(viewer, id); record(viewer, id, previous); });
            else if (s.serving() && s.oath()) item(h, 28, Material.SHIELD, s.status() == Status.ACTIVE ? "&eПеревести в резерв" : "&aМобилизовать", List.of("Проверяются вместимость и право службы", "Нажмите: подтвердить приказ"), viewer -> { var current = service.repository().state().soldiers().get(id); service.mobilize(viewer, id, current.status() != Status.ACTIVE); record(viewer, id, previous); });
        }
        if (service.officer(p, town, Rank.SERGEANT) && s.serving()) item(h, 34, Material.IRON_CHESTPLATE, "&eВыделить снабжение", List.of("Полный комплект из военного резерва", "Нажмите: обеспечить бойца"), viewer -> { service.equip(viewer, id); record(viewer, id, previous); });
        if (service.officer(p, town, Rank.CAPTAIN) && !id.equals(p.getUniqueId())) item(h, 49, Material.WRITABLE_BOOK, "&eКадровые приказы", List.of("/army promote " + name(id) + " <причина>", "/army assign " + name(id) + " <подразделение> <причина>", "/army warn " + name(id) + " <причина>", "/army dismiss " + name(id) + " <причина>"), null);
        p.openInventory(h.inventory);
    }
    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return; e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !h.viewer.equals(p.getUniqueId()) || !p.hasPermission("neverlandtownyarmy.use")) return;
        var action = h.actions.get(e.getRawSlot()); if (action == null) return;
        try { action.run(p); } catch (Exception ex) { ArmyCommand.say(p, "&c" + (ex instanceof IllegalArgumentException ? ex.getMessage() : "Действие приостановлено; обратитесь к администратору")); }
    }
    @EventHandler public void drag(InventoryDragEvent e) { if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true); }
}
