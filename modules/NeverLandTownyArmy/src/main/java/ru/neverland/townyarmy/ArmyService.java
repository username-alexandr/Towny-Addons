package ru.neverland.townyarmy;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.core.CouncilAccess;
import ru.neverland.townyarmy.api.TownyArmyApi;
import static ru.neverland.townyarmy.ArmyModel.*;

/** Server adapter. Durable state is published before permissions, messages or training rewards. */
public final class ArmyService implements TownyArmyApi {
    private final NeverLandTownyArmy plugin;
    private final ArmyRepository repository;
    private final ArmyBridge bridge = new ArmyBridge();
    private ArmySettings settings;
    private final Map<UUID, Duty> duty = new HashMap<>();
    private final Map<UUID, Training> training = new HashMap<>();
    private final Map<UUID, PermissionAttachment> permissions = new HashMap<>();
    private final Map<UUID, Set<String>> grants = new HashMap<>();
    private long lastError;
    private static final class Duty {
        long accounted, moved; Location position;
        Duty(Player p, long now) { accounted = moved = now; position = p.getLocation().clone(); }
    }
    private static final class Training {
        final long started; double movement;
        Training(long now) { started = now; }
    }
    public ArmyService(NeverLandTownyArmy plugin, ArmyRepository repository, ArmySettings settings) {
        this.plugin = plugin; this.repository = repository; this.settings = settings;
    }
    static void mainThread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Army API требует основной поток сервера"); }
    public ArmyRepository repository() { return repository; }
    public ArmySettings settings() { return settings; }
    public void reload(ArmySettings next) { mainThread(); settings = next; repository.auditLimit(next.auditLimit()); training.clear(); refresh(); }
    public void migrate() throws Exception { mainThread(); ArmyMigration.resume(repository, bridge, System.currentTimeMillis()); }
    @Override public boolean healthy() { mainThread(); return repository.writable() && repository.state().handoff(); }
    private void writable() { if (!healthy()) throw new IllegalStateException("Армия приостановлена: проверьте хранилище и перенос состава"); }
    public Town ownTown(Player p) {
        var r = TownyAPI.getInstance().getResident(p.getUniqueId()); var t = r == null ? null : r.getTownOrNull();
        if (t == null) throw new IllegalArgumentException("Вы не состоите в городе"); return t;
    }
    public boolean executive(Player p, UUID town) {
        var r = TownyAPI.getInstance().getResident(p.getUniqueId()); var t = TownyAPI.getInstance().getTown(town);
        return p.hasPermission("neverlandtownyarmy.manage") && r != null && t != null && r.getTownOrNull() == t
                && !r.isJailed() && (t.getMayor() == r || CouncilAccess.allows(p, town, "army"));
    }
    private boolean eligible(Soldier s, long now) throws Exception { return s.suspendedUntil() <= now && bridge.eligible(s.town(), s.resident()); }
    public boolean officer(Player p, UUID town, Rank minimum) {
        if (executive(p, town)) return true;
        var s = repository.state().soldiers().get(p.getUniqueId());
        try { return p.hasPermission("neverlandtownyarmy.manage") && s != null && s.town().equals(town) && s.serving() && s.oath()
                && s.rank().atLeast(minimum) && eligible(s, System.currentTimeMillis()); }
        catch (Exception | LinkageError e) { return false; }
    }
    private UUID manage(Player p, Rank minimum) {
        writable(); UUID town = ownTown(p).getUUID();
        if (!officer(p, town, minimum)) throw new IllegalArgumentException("Нужны полномочия командования своей армии"); return town;
    }
    private void superior(Player actor, Soldier target) {
        if (actor.getUniqueId().equals(target.resident())) throw new IllegalArgumentException("Приказ в отношении самого себя запрещён");
        if (!executive(actor, target.town())) {
            var own = repository.state().soldiers().get(actor.getUniqueId());
            if (own == null || own.rank().ordinal() <= target.rank().ordinal()) throw new IllegalArgumentException("Можно отдавать кадровые приказы только младшим по званию");
        }
    }
    private Soldier target(UUID town, UUID id) {
        var s = repository.state().soldiers().get(id);
        if (s == null || !town.equals(s.town())) throw new IllegalArgumentException("Военнослужащий не найден в вашем городе"); return s;
    }
    private Soldier self(Player p) throws Exception {
        writable(); var s = target(ownTown(p).getUUID(), p.getUniqueId());
        if (!s.serving() || !eligible(s, System.currentTimeMillis())) throw new IllegalArgumentException("Служба недоступна: проверьте статус, гражданство, RP-возраст и взыскания"); return s;
    }
    private void room(int additional) {
        var s = repository.state();
        if ((long)s.soldiers().size() + s.cities().size() + s.transfers().size() + additional > settings.records())
            throw new IllegalArgumentException("Реестр армии заполнен; обратитесь к администратору");
    }
    private void save(ArmyRepository.Draft d, UUID town, UUID actor, UUID subject, String action, String reason) throws Exception {
        repository.commit(d, town, actor, subject, action, reason, System.currentTimeMillis()); refresh();
    }
    private List<Soldier> members(UUID town) { return repository.state().soldiers().values().stream().filter(s -> s.town().equals(town)).sorted(Comparator.<Soldier, Rank>comparing(Soldier::rank).reversed().thenComparing(Soldier::resident)).toList(); }
    private ArmyRules.Result result(UUID town) throws Exception {
        var roster = members(town); var valid = new HashSet<UUID>(); long now = System.currentTimeMillis();
        for (var s : roster) if (s.serving() && eligible(s, now)) valid.add(s.resident());
        City c = repository.state().cities().getOrDefault(town, City.empty(town));
        return ArmyRules.calculate(roster, valid, duty.keySet(), bridge.levels(town, settings), now < c.suppliedUntil(), settings);
    }
    public void apply(Player p, Unit unit) throws Exception {
        writable(); UUID town = ownTown(p).getUUID(); var old = repository.state().soldiers().get(p.getUniqueId());
        if (old != null && old.status() != Status.DISCHARGED) throw new IllegalArgumentException("Заявка или служба уже существует");
        if (!bridge.eligible(town, p.getUniqueId())) throw new IllegalArgumentException("Нужны гражданские права на должность и подтверждённый RP-возраст от 18 лет");
        var levels = bridge.levels(town, settings);
        if (levels.getOrDefault("army", 0) < 1 || ArmyRules.capacity(unit, levels, settings) == 0) throw new IllegalArgumentException("Город не подготовил штаб и инфраструктуру этого подразделения");
        if (members(town).stream().filter(s -> s.status() != Status.DISCHARGED).count() >= (long)levels.get("army") * settings.hqCapacity() * settings.reserveMultiplier()) throw new IllegalArgumentException("Личный состав и резерв укомплектованы");
        room(old == null ? 2 : 1); var d = repository.draft(); d.soldier(Soldier.applicant(p.getUniqueId(), town, unit, System.currentTimeMillis()));
        save(d, town, p.getUniqueId(), p.getUniqueId(), "APPLICATION", "Заявка на службу: " + unit.id());
    }
    public void approve(Player p, UUID id) throws Exception {
        UUID town = manage(p, Rank.LIEUTENANT); var s = target(town, id); superior(p, s);
        if (s.status() != Status.APPLICANT || !eligible(s, System.currentTimeMillis())) throw new IllegalArgumentException("Заявка не готова к приёму");
        var d = repository.draft(); d.soldier(s.status(Status.RECRUIT)); save(d, town, p.getUniqueId(), id, "ADMISSION", "Заявка одобрена командованием");
    }
    public void oath(Player p) throws Exception {
        var s = self(p); if (s.status() != Status.RECRUIT) throw new IllegalArgumentException("Присяга доступна принятому рекруту");
        if (!atBase(s.town(), p.getLocation())) throw new IllegalArgumentException("Для присяги прибудьте на военную базу города");
        var d = repository.draft(); d.soldier(s.swear()); save(d, s.town(), s.resident(), s.resident(), "OATH", "Военнослужащий принял присягу");
    }
    public void commission(Player p, UUID id, String reason) throws Exception {
        UUID town = manage(p, Rank.GENERAL); if (!executive(p, town)) throw new IllegalArgumentException("Генерала назначает мэр или министр обороны");
        var s = target(town, id); superior(p, s);
        if (!s.serving() || !s.oath() || !eligible(s, System.currentTimeMillis())) throw new IllegalArgumentException("Назначить можно действующего военнослужащего после присяги");
        var d = repository.draft(); long now = System.currentTimeMillis();
        for (var old : members(town)) if (old.serving() && old.rank() == Rank.GENERAL && !old.resident().equals(id)) d.soldier(old.rank(Rank.COLONEL, now));
        d.soldier(s.rank(Rank.GENERAL, now)); save(d, town, p.getUniqueId(), id, "COMMANDER", reason);
    }
    public void promote(Player p, UUID id, String reason) throws Exception {
        UUID town = manage(p, Rank.CAPTAIN); var s = target(town, id); superior(p, s);
        if (!eligible(s, System.currentTimeMillis()) || s.rank() == Rank.GENERAL) throw new IllegalArgumentException("Повышение недоступно");
        Rank next = Rank.values()[s.rank().ordinal() + 1];
        if (!ArmyRules.canPromote(repository.state().soldiers().get(p.getUniqueId()), s, next, executive(p, town), settings)) {
            var gate = settings.ranks().get(next); throw new IllegalArgumentException("Нужны подготовка " + gate.training() + "% и " + gate.dutyMinutes() + " минут службы; генерала назначает глава города");
        }
        var d = repository.draft(); d.soldier(s.rank(next, System.currentTimeMillis())); save(d, town, p.getUniqueId(), id, "PROMOTION", reason);
    }
    public void personnel(Player p, UUID id, String action, String reason) throws Exception {
        UUID town = manage(p, Rank.CAPTAIN); var s = target(town, id); superior(p, s); long now = System.currentTimeMillis();
        if (s.status() == Status.DISCHARGED) throw new IllegalArgumentException("Военнослужащий уже уволен");
        var next = switch (action) {
            case "dismiss" -> s.status(Status.DISCHARGED);
            case "warn" -> s.warning(false, now);
            case "pardon" -> s.warning(true, now);
            case "demote" -> { if (!s.oath() || s.rank().ordinal() <= Rank.PRIVATE.ordinal()) throw new IllegalArgumentException("Понижение недоступно"); yield s.rank(Rank.values()[s.rank().ordinal() - 1], now); }
            default -> throw new IllegalArgumentException("Неизвестный кадровый приказ");
        };
        var d = repository.draft(); d.soldier(next); save(d, town, p.getUniqueId(), id, action.toUpperCase(Locale.ROOT), reason);
        if (!next.serving() || next.suspendedUntil() > now) endSession(id);
    }
    public void assign(Player p, UUID id, Unit unit, String reason) throws Exception {
        UUID town = manage(p, Rank.CAPTAIN); var s = target(town, id); superior(p, s);
        if (!s.serving() || !eligible(s, System.currentTimeMillis()) || s.unit() == unit) throw new IllegalArgumentException("Перевод недоступен");
        if (ArmyRules.capacity(unit, bridge.levels(town, settings), settings) == 0) throw new IllegalArgumentException("Инфраструктура подразделения не готова");
        var d = repository.draft(); d.soldier(s.unit(unit).status(s.oath() ? Status.RESERVE : Status.RECRUIT));
        save(d, town, p.getUniqueId(), id, "TRANSFER", reason); endSession(id);
    }
    public void mobilize(Player p, UUID id, boolean active) throws Exception {
        UUID town = manage(p, Rank.LIEUTENANT); var s = target(town, id);
        if (!s.serving() || !s.oath() || !eligible(s, System.currentTimeMillis())) throw new IllegalArgumentException("Военнослужащий недоступен для мобилизации");
        if (!s.resident().equals(p.getUniqueId())) superior(p, s);
        var d = repository.draft(); var next = s.status(active ? Status.ACTIVE : Status.RESERVE); d.soldier(next);
        if (active && s.status() != Status.ACTIVE && !result(town).active().contains(id)) {
            var live = result(town); if (live.active().size() >= live.totalCapacity() || live.count().get(s.unit()) >= live.capacity().get(s.unit())) throw new IllegalArgumentException("Гарнизон или подразделение укомплектованы");
        }
        save(d, town, p.getUniqueId(), id, active ? "MOBILIZE" : "RESERVE", active ? "Приказ о мобилизации" : "Перевод в военный резерв");
        if (!active) endSession(id);
    }
    public int alert(Player p, boolean enabled) throws Exception {
        UUID town = manage(p, Rank.GENERAL); var d = repository.draft(); d.cities.put(town, d.city(town).alert(enabled)); int changed = 0;
        var levels = bridge.levels(town, settings); var valid = new HashSet<UUID>(); long now = System.currentTimeMillis();
        for (var s : members(town)) if (s.serving() && s.oath() && eligible(s, now)) {
            // Mass orders are issued by the executive or the appointed commander; no rank change.
            valid.add(s.resident()); d.soldier(s.status(enabled ? Status.ACTIVE : Status.RESERVE));
        }
        var selected = ArmyRules.calculate(d.soldiers.values().stream().filter(s -> s.town().equals(town)).toList(), valid, Set.of(), levels, false, settings).active();
        for (UUID id : valid) { var s = d.soldiers.get(id); boolean mobilized = enabled && selected.contains(id); var next = s.status(mobilized ? Status.ACTIVE : Status.RESERVE); if (repository.state().soldiers().get(id).status() != next.status()) changed++; d.soldier(next); }
        save(d, town, p.getUniqueId(), SYSTEM, "ALERT", enabled ? "Общая мобилизация по вместимости гарнизона" : "Отбой общей мобилизации");
        if (!enabled) for (UUID id : valid) endSession(id); return changed;
    }
    public void base(Player p) throws Exception {
        UUID town = manage(p, Rank.GENERAL); var loc = p.getLocation(); var block = TownyAPI.getInstance().getTownBlock(loc);
        if (block == null || block.getTownOrNull() == null || !town.equals(block.getTownOrNull().getUUID()) || block.hasResident()) throw new IllegalArgumentException("База должна находиться на муниципальном участке вашего города");
        if (bridge.levels(town, settings).getOrDefault("army", 0) < 1) throw new IllegalArgumentException("Нужен работающий штаб армии");
        var d = repository.draft(); d.cities.put(town, d.city(town).base(new Base(loc.getWorld().getUID(), loc.getX(), loc.getY(), loc.getZ())));
        save(d, town, p.getUniqueId(), SYSTEM, "BASE", "Назначена точка военной базы"); training.clear();
    }
    public boolean atBase(UUID town, Location loc) {
        var city = repository.state().cities().get(town); var b = city == null ? null : city.base();
        if (b == null || loc.getWorld() == null || !loc.getWorld().getUID().equals(b.world())) return false;
        var block = TownyAPI.getInstance().getTownBlock(loc);
        return block != null && block.getTownOrNull() != null && town.equals(block.getTownOrNull().getUUID()) && !block.hasResident()
                && new Location(loc.getWorld(), b.x(), b.y(), b.z()).distanceSquared(loc) <= settings.baseRadius() * settings.baseRadius();
    }
    public UUID supply(Player p, String resource, long amount) throws Exception {
        UUID town = manage(p, Rank.SERGEANT); if (!RESOURCES.contains(resource) || amount <= 0 || amount > settings.maxTransfer()) throw new IllegalArgumentException("Укажите ресурс и количество в пределах лимита поставки");
        bridge.resourceReady(town); room(2); var stock = repository.state().cities().getOrDefault(town, City.empty(town)).stock();
        for (var tx : repository.state().transfers().values()) if (tx.town().equals(town) && tx.phase() == Phase.PLANNED) stock = add(stock, tx.amounts());
        add(stock, Map.of(resource, amount)); UUID id = UUID.randomUUID(); var d = repository.draft(); d.city(town);
        d.transfers.put(id, new Transfer(id, town, p.getUniqueId(), Map.of(resource, amount), Phase.PLANNED, System.currentTimeMillis()));
        save(d, town, p.getUniqueId(), p.getUniqueId(), "SUPPLY_ORDER", "Перевод городских ресурсов: " + id);
        process(id); return id;
    }
    public void process(UUID id) throws Exception { writable(); ArmySupplies.resume(id, repository, bridge); ArmySupplies.resume(id, repository, bridge); }
    public void equip(Player p, UUID id) throws Exception {
        UUID town = manage(p, Rank.SERGEANT); var s = target(town, id);
        if (!s.serving() || !eligible(s, System.currentTimeMillis()) || s.equipment() == 100) throw new IllegalArgumentException("Снабжение бойца недоступно или уже полное");
        var d = repository.draft(); var city = d.city(town); d.cities.put(town, city.stock(subtract(city.stock(), settings.units().get(s.unit()).kit()))); d.soldier(s.equipment(100));
        save(d, town, p.getUniqueId(), id, "EQUIPMENT", "Выделено штатное снабжение подразделения");
    }
    public boolean onDuty(UUID id) { return duty.containsKey(id); }
    public void duty(Player p, boolean enabled) throws Exception {
        var s = self(p); UUID id = p.getUniqueId();
        if (!enabled) { account(id, System.currentTimeMillis()); endSession(id); refresh(); return; }
        if (duty.containsKey(id)) throw new IllegalArgumentException("Вы уже на дежурстве");
        if (!atBase(s.town(), p.getLocation())) throw new IllegalArgumentException("Заступить на дежурство можно на базе");
        duty.put(id, new Duty(p, System.currentTimeMillis())); refresh();
    }
    public void train(Player p) throws Exception {
        var s = self(p); long now = System.currentTimeMillis();
        if (!onDuty(s.resident()) || !atBase(s.town(), p.getLocation())) throw new IllegalArgumentException("Тренировка доступна на базе во время дежурства");
        if (training.containsKey(s.resident()) || s.training() == 100 || now - s.lastTraining() < settings.trainingCooldown()) throw new IllegalArgumentException("Тренировка уже идёт, подготовка максимальна или ещё действует перерыв");
        if (!covers(repository.state().cities().get(s.town()).stock(), settings.trainingCost())) throw new IllegalArgumentException("Не хватает запасов для тренировки");
        training.put(s.resident(), new Training(now));
    }
    public void moved(Player p, Location from, Location to) {
        if (to == null || from.getWorld() != to.getWorld()) { training.remove(p.getUniqueId()); return; }
        double distance = from.distance(to); if (distance < .05) return;
        var session = duty.get(p.getUniqueId()); if (session == null) return;
        session.moved = System.currentTimeMillis(); session.position = to.clone();
        var t = training.get(p.getUniqueId()); var s = repository.state().soldiers().get(p.getUniqueId());
        if (t != null) { if (s == null || !atBase(s.town(), to) || distance > 8) training.remove(p.getUniqueId()); else t.movement += distance; }
    }
    public void teleported(UUID id) { training.remove(id); }
    private void account(UUID id, long now) throws Exception {
        var session = duty.get(id); var s = repository.state().soldiers().get(id);
        if (session == null || s == null || !s.serving()) return;
        long millis = Math.max(0, Math.min(now, session.moved + settings.dutyIdle()) - session.accounted);
        if (millis > 0 && eligible(s, now)) { var d = repository.draft(); d.soldier(s.duty(millis)); repository.commit(d.freeze()); }
        session.accounted = now;
    }
    public void leave(UUID id) { try { if (healthy()) account(id, System.currentTimeMillis()); } catch (Exception e) { report(e); } finally { endSession(id); removePermissions(id); } }
    private void endSession(UUID id) { duty.remove(id); training.remove(id); }
    public void pulse() {
        mainThread(); if (!healthy()) { clearPermissions(); duty.clear(); training.clear(); return; }
        try {
            long now = System.currentTimeMillis(); var d = repository.draft(); boolean changed = false;
            for (var s : repository.state().soldiers().values()) {
                var resident = TownyAPI.getInstance().getResident(s.resident());
                if (s.status() != Status.DISCHARGED && (resident == null || resident.getTownOrNull() == null || !s.town().equals(resident.getTownOrNull().getUUID()))) {
                    d.soldier(s.status(Status.DISCHARGED)); changed = true;
                    d.audit.add(new Audit(UUID.randomUUID(), s.town(), SYSTEM, s.resident(), "LEFT_TOWN", "Служба прекращена после выхода из города", now)); endSession(s.resident());
                }
            }
            if (changed) { while (d.audit.size() > settings.auditLimit()) d.audit.remove(0); repository.commit(d.freeze()); }
            for (UUID id : List.copyOf(duty.keySet())) {
                var p = Bukkit.getPlayer(id); var s = repository.state().soldiers().get(id); var session = duty.get(id);
                if (p == null || !p.isOnline() || p.isDead() || s == null || !s.serving() || !eligible(s, now)) { endSession(id); continue; }
                if (now - session.accounted >= 60000) account(id, now);
                if (now - session.moved >= settings.dutyIdle()) { endSession(id); ArmyCommand.say(p, "&eДежурство завершено из-за отсутствия активности."); continue; }
                var t = training.get(id);
                if (t != null) {
                    if (!atBase(s.town(), p.getLocation())) training.remove(id);
                    else if (now - t.started >= settings.trainingDuration()) {
                        training.remove(id);
                        if (ArmyRules.trainingComplete(now - t.started, t.movement, true, true, settings)) {
                            s = repository.state().soldiers().get(id); var next = repository.draft(); var city = next.city(s.town());
                            if (covers(city.stock(), settings.trainingCost())) {
                                next.cities.put(s.town(), city.stock(subtract(city.stock(), settings.trainingCost()))); next.soldier(s.trained(settings.trainingGain(), now));
                                save(next, s.town(), id, id, "TRAINING", "Завершена подготовка на военной базе"); ArmyCommand.say(p, "&aТренировка завершена. Подготовка +" + settings.trainingGain() + "%.");
                            } else ArmyCommand.say(p, "&eТренировка завершилась без зачёта: не хватило запасов.");
                        } else ArmyCommand.say(p, "&eТренировка не зачтена: требуется движение по территории базы.");
                    }
                }
            }
            for (var tx : repository.state().transfers().values()) if (tx.phase() != Phase.CLOSED && tx.phase() != Phase.CANCELLED) try { process(tx.id()); } catch (Exception e) { report(e); }
            for (UUID town : List.copyOf(repository.state().cities().keySet())) upkeep(town, now);
            refresh();
        } catch (Exception | LinkageError e) { clearPermissions(); duty.clear(); training.clear(); report(e); }
    }
    private void upkeep(UUID town, long now) throws Exception {
        if (TownyAPI.getInstance().getTown(town) == null) return;
        var city = repository.state().cities().get(town); if (now < city.nextSupply()) return;
        var active = result(town).active(); if (active.isEmpty()) return;
        var cost = ArmyRules.upkeep(members(town), active, settings); boolean enough = covers(city.stock(), cost); var d = repository.draft();
        d.cities.put(town, city.supplied(enough ? subtract(city.stock(), cost) : city.stock(), now + settings.supplyInterval(), enough ? now + settings.supplyInterval() : 0));
        if (!enough) for (UUID id : active) { var s = d.soldiers.get(id); d.soldier(s.equipment(Math.max(0, s.equipment() - settings.shortageLoss()))); }
        repository.commit(d, town, SYSTEM, SYSTEM, enough ? "UPKEEP" : "SHORTAGE", enough ? "Гарнизон обеспечен на следующий период" : "Недостаток снабжения: снижена готовность гарнизона", now);
    }
    private void report(Throwable e) { long now = System.currentTimeMillis(); if (now - lastError > 60000) { lastError = now; plugin.getLogger().log(java.util.logging.Level.WARNING, "Армия: действие приостановлено, данные сохранены", e); } }
    private void removePermissions(UUID id) { var attachment = permissions.remove(id); grants.remove(id); if (attachment != null) try { attachment.remove(); } catch (IllegalArgumentException ignored) { } }
    private void clearPermissions() { for (UUID id : List.copyOf(permissions.keySet())) removePermissions(id); }
    public void refresh() {
        if (!healthy()) { clearPermissions(); return; }
        var effective = new HashMap<UUID, Set<UUID>>(); long now = System.currentTimeMillis();
        for (var p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId(); var s = repository.state().soldiers().get(id); var nodes = new HashSet<String>();
            try {
                if (s != null && s.serving() && eligible(s, now)) {
                    nodes.add("neverlandtownyarmy.rank." + s.rank().id()); nodes.add("neverlandtownyarmy.unit." + s.unit().id());
                    if (duty.containsKey(id)) nodes.add("neverlandtownyarmy.duty");
                    if (!effective.containsKey(s.town())) effective.put(s.town(), result(s.town()).active());
                    if (s.status() == Status.ACTIVE && effective.get(s.town()).contains(id)) nodes.add("neverlandtownybuilds.army.soldier");
                }
            } catch (Exception | LinkageError e) { nodes.clear(); report(e); }
            if (!nodes.equals(grants.getOrDefault(id, Set.of()))) { removePermissions(id); if (!nodes.isEmpty()) { var a = p.addAttachment(plugin); for (String node : nodes) a.setPermission(node, true); permissions.put(id, a); grants.put(id, Set.copyOf(nodes)); } }
        }
        for (UUID id : List.copyOf(permissions.keySet())) if (Bukkit.getPlayer(id) == null) removePermissions(id);
    }
    public void stop() { for (UUID id : List.copyOf(duty.keySet())) leave(id); clearPermissions(); training.clear(); }
    @Override public boolean isMobilized(UUID id) { mainThread(); if (id == null || !healthy()) return false; var s = repository.state().soldiers().get(id); if (s == null || s.status() != Status.ACTIVE) return false; try { return result(s.town()).active().contains(id); } catch (Exception e) { throw new IllegalStateException("Гарнизон недоступен", e); } }
    @Override public Set<UUID> soldiers(UUID town) { mainThread(); writable(); if (town == null) return Set.of(); try { var active = result(town).active(); var out = new HashSet<UUID>(); for (var s : members(town)) if (s.status() == Status.ACTIVE && active.contains(s.resident())) out.add(s.resident()); return Set.copyOf(out); } catch (Exception e) { throw new IllegalStateException("Гарнизон недоступен", e); } }
    private Map<String, Object> record(Soldier s) {
        var out = new LinkedHashMap<String, Object>(); out.put("resident", s.resident()); out.put("town", s.town()); out.put("unit", s.unit().id()); out.put("rank", s.rank().id()); out.put("status", s.status().name()); out.put("oath", s.oath()); out.put("training", s.training()); out.put("equipment", s.equipment()); out.put("dutyMillis", s.dutyMillis()); out.put("onDuty", duty.containsKey(s.resident())); out.put("warnings", s.warnings()); out.put("suspendedUntil", s.suspendedUntil()); return Map.copyOf(out);
    }
    @Override public Optional<Map<String, Object>> serviceRecord(UUID id) { mainThread(); writable(); return id == null ? Optional.empty() : Optional.ofNullable(repository.state().soldiers().get(id)).map(this::record); }
    @Override public Collection<Map<String, Object>> roster(UUID town) { mainThread(); writable(); return members(town).stream().map(this::record).toList(); }
    @Override public Map<String, Long> reserves(UUID town) { mainThread(); writable(); return town == null ? Map.of() : repository.state().cities().getOrDefault(town, City.empty(town)).stock(); }
    @Override public Optional<Map<String, Object>> garrison(UUID town) {
        mainThread(); writable(); if (town == null || TownyAPI.getInstance().getTown(town) == null) return Optional.empty();
        try { var r = result(town); var c = repository.state().cities().getOrDefault(town, City.empty(town)); var out = new LinkedHashMap<String, Object>();
            out.put("town", town); out.put("active", r.active().size()); out.put("capacity", r.totalCapacity()); out.put("score", r.score()); out.put("readiness", r.readiness()); out.put("defenseBonus", r.defense()); out.put("alert", c.alert()); out.put("suppliedUntil", c.suppliedUntil());
            out.put("reserve", members(town).stream().filter(s -> s.status() == Status.RESERVE).count()); out.put("onDuty", members(town).stream().filter(s -> duty.containsKey(s.resident())).count());
            var units = new TreeMap<String, Object>(); for (Unit unit : Unit.values()) units.put(unit.id(), Map.of("active", r.count().get(unit), "capacity", r.capacity().get(unit))); out.put("units", Map.copyOf(units)); return Optional.of(Map.copyOf(out));
        } catch (Exception e) { throw new IllegalStateException("Расчёт гарнизона приостановлен", e); }
    }
    @Override public Collection<Map<String, Object>> history(UUID town) { mainThread(); writable(); return repository.state().audit().stream().filter(a -> a.town().equals(town)).map(a -> Map.<String, Object>of("id", a.id(), "actor", a.actor(), "subject", a.subject(), "action", a.action(), "reason", a.reason(), "time", a.time())).toList(); }
}
