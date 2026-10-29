package ru.neverland.townyarmy;

import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.core.CouncilAccess;
import ru.neverland.core.SuiteState;
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
    private long lastError;private boolean stopping;
    private CompletionStage<?> operation=CompletableFuture.completedFuture(null);private CompletionStage<Void> closing;
    public boolean busy(){return repository.busy()||!operation.toCompletableFuture().isDone();}
    private <T>CompletionStage<T> track(CompletionStage<T> future){operation=future;future.whenComplete((v,error)->{if(error!=null)report(error);});return future;}
    private <T>CompletionStage<T> after(CompletionStage<T> future,Runnable callback){return future.thenApply(value->{try(var guard=SuiteState.guard()){callback.run();return value;}});}

    private static final class Duty {
        long accounted, moved, endedAt=Long.MAX_VALUE; Location position;
        Duty(Player p, long now) { accounted = moved = now; position = p.getLocation().clone(); }
    }
    private static final class Training {
        final long started; double movement;
        Training(long now) { started = now; }
    }
    public ArmyService(NeverLandTownyArmy plugin, ArmyRepository repository, ArmySettings settings) {
        this.plugin = plugin; this.repository = repository; this.settings = settings;
    }
    static void mainThread() { if (!ru.neverland.core.SuiteState.held()) throw new IllegalStateException("Army API требует основной поток сервера"); }
    public ArmyRepository repository() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return repository; } }
    public ArmySettings settings() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return settings; } }
    public void reload(ArmySettings next) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { mainThread(); settings = next; repository.auditLimit(next.auditLimit()); training.clear(); refresh(); } }
    public CompletionStage<Void> migrate() throws Exception {try(var guard=SuiteState.guard()){mainThread();if(stopping||busy())throw new IllegalArgumentException("Армия подтверждает предыдущее изменение");return track(ArmyMigration.resume(repository,bridge,System.currentTimeMillis()));}}
    @Override public boolean healthy() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { mainThread(); return !stopping&&!SuiteState.frozen(plugin.getName())&&repository.writable() && repository.state().handoff(); } }
    private void readable(){if(!healthy())throw new IllegalStateException("Армия приостановлена: проверьте хранилище и перенос состава");}
    private void writable() { readable();if(busy())throw new IllegalArgumentException("Предыдущее изменение армии сохраняется. Повторите через секунду"); }
    public Town ownTown(Player p) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        var r = TownyAPI.getInstance().getResident(p.getUniqueId()); var t = r == null ? null : r.getTownOrNull();
        if (t == null) throw new IllegalArgumentException("Вы не состоите в городе"); return t;
    } }
    public boolean executive(Player p, UUID town) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        var r = TownyAPI.getInstance().getResident(p.getUniqueId()); var t = TownyAPI.getInstance().getTown(town);
        return p.hasPermission("neverlandtownyarmy.manage") && r != null && t != null && r.getTownOrNull() == t
                && !r.isJailed() && (t.getMayor() == r || CouncilAccess.allows(p, town, "army"));
    } }
    private boolean eligible(Soldier s, long now) throws Exception { return s.suspendedUntil() <= now && bridge.eligible(s.town(), s.resident()); }
    public boolean officer(Player p, UUID town, Rank minimum) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
        if (executive(p, town)) return true;
        var s = repository.state().soldiers().get(p.getUniqueId());
        try { return p.hasPermission("neverlandtownyarmy.manage") && s != null && s.town().equals(town) && s.serving() && s.oath()
                && s.rank().atLeast(minimum) && eligible(s, System.currentTimeMillis()) && CouncilAccess.check(p,town,"army",true); }
        catch (Exception | LinkageError e) { return false; }
    } }
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
    private CompletionStage<Void> save(ArmyRepository.Draft d,UUID town,UUID actor,UUID subject,String action,String reason){
        return track(after(repository.commit(d,town,actor,subject,action,reason,System.currentTimeMillis()).thenApply(value->(Void)null),()->{if(!stopping)refresh();}));
    }
    private List<Soldier> members(UUID town) { return repository.state().soldiers().values().stream().filter(s -> s.town().equals(town)).sorted(Comparator.<Soldier, Rank>comparing(Soldier::rank).reversed().thenComparing(Soldier::resident)).toList(); }
    private ArmyRules.Result result(UUID town) throws Exception {
        var roster = members(town); var valid = new HashSet<UUID>(); long now = System.currentTimeMillis();
        for (var s : roster) if (s.serving() && eligible(s, now)) valid.add(s.resident());
        City c = repository.state().cities().getOrDefault(town, City.empty(town));
        return ArmyRules.calculate(roster, valid, duty.entrySet().stream().filter(e->e.getValue().endedAt==Long.MAX_VALUE).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()), bridge.levels(town, settings), now < c.suppliedUntil(), settings);
    }
    public CompletionStage<Void> apply(Player p, Unit unit) throws Exception { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {
        writable(); UUID town = ownTown(p).getUUID(); var old = repository.state().soldiers().get(p.getUniqueId());
        if (old != null && old.status() != Status.DISCHARGED) throw new IllegalArgumentException("Заявка или служба уже существует");
        if (!bridge.eligible(town, p.getUniqueId())) throw new IllegalArgumentException("Нужны гражданские права на должность и подтверждённый RP-возраст от 18 лет");
        var levels = bridge.levels(town, settings);
        if (levels.getOrDefault("army", 0) < 1 || ArmyRules.capacity(unit, levels, settings) == 0) throw new IllegalArgumentException("Город не подготовил штаб и инфраструктуру этого подразделения");
        if (members(town).stream().filter(s -> s.status() != Status.DISCHARGED).count() >= (long)levels.get("army") * settings.hqCapacity() * settings.reserveMultiplier()) throw new IllegalArgumentException("Личный состав и резерв укомплектованы");
        room(old == null ? 2 : 1); var d = repository.draft(); d.soldier(Soldier.applicant(p.getUniqueId(), town, unit, System.currentTimeMillis()));
        return save(d, town, p.getUniqueId(), p.getUniqueId(), "APPLICATION", "Заявка на службу: " + unit.id());
    } }
    public CompletionStage<Void> approve(Player p, UUID id) throws Exception { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {
        UUID town = manage(p, Rank.LIEUTENANT); var s = target(town, id); superior(p, s);
        if (s.status() != Status.APPLICANT || !eligible(s, System.currentTimeMillis())) throw new IllegalArgumentException("Заявка не готова к приёму");
        var d = repository.draft(); d.soldier(s.status(Status.RECRUIT)); return save(d, town, p.getUniqueId(), id, "ADMISSION", "Заявка одобрена командованием");
    } }
    public CompletionStage<Void> oath(Player p) throws Exception { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {
        var s = self(p); if (s.status() != Status.RECRUIT) throw new IllegalArgumentException("Присяга доступна принятому рекруту");
        if (!atBase(s.town(), p.getLocation())) throw new IllegalArgumentException("Для присяги прибудьте на военную базу города");
        var d = repository.draft(); d.soldier(s.swear()); return save(d, s.town(), s.resident(), s.resident(), "OATH", "Военнослужащий принял присягу");
    } }
    public CompletionStage<Void> commission(Player p, UUID id, String reason) throws Exception { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {
        UUID town = manage(p, Rank.GENERAL); if (!executive(p, town)) throw new IllegalArgumentException("Генерала назначает мэр или министр обороны");
        var s = target(town, id); superior(p, s);
        if (!s.serving() || !s.oath() || !eligible(s, System.currentTimeMillis())) throw new IllegalArgumentException("Назначить можно действующего военнослужащего после присяги");
        var d = repository.draft(); long now = System.currentTimeMillis();
        for (var old : members(town)) if (old.serving() && old.rank() == Rank.GENERAL && !old.resident().equals(id)) d.soldier(old.rank(Rank.COLONEL, now));
        d.soldier(s.rank(Rank.GENERAL, now)); return save(d, town, p.getUniqueId(), id, "COMMANDER", reason);
    } }
    public CompletionStage<Void> promote(Player p, UUID id, String reason) throws Exception { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {
        UUID town = manage(p, Rank.CAPTAIN); var s = target(town, id); superior(p, s);
        if (!eligible(s, System.currentTimeMillis()) || s.rank() == Rank.GENERAL) throw new IllegalArgumentException("Повышение недоступно");
        Rank next = Rank.values()[s.rank().ordinal() + 1];
        if (!ArmyRules.canPromote(repository.state().soldiers().get(p.getUniqueId()), s, next, executive(p, town), settings)) {
            var gate = settings.ranks().get(next); throw new IllegalArgumentException("Нужны подготовка " + gate.training() + "% и " + gate.dutyMinutes() + " минут службы; генерала назначает глава города");
        }
        var d = repository.draft(); d.soldier(s.rank(next, System.currentTimeMillis())); return save(d, town, p.getUniqueId(), id, "PROMOTION", reason);
    } }
    public CompletionStage<Void> personnel(Player p, UUID id, String action, String reason) throws Exception { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {
        UUID town = manage(p, Rank.CAPTAIN); var s = target(town, id); superior(p, s); long now = System.currentTimeMillis();
        if (s.status() == Status.DISCHARGED) throw new IllegalArgumentException("Военнослужащий уже уволен");
        var next = switch (action) {
            case "dismiss" -> s.status(Status.DISCHARGED);
            case "warn" -> s.warning(false, now);
            case "pardon" -> s.warning(true, now);
            case "demote" -> { if (!s.oath() || s.rank().ordinal() <= Rank.PRIVATE.ordinal()) throw new IllegalArgumentException("Понижение недоступно"); yield s.rank(Rank.values()[s.rank().ordinal() - 1], now); }
            default -> throw new IllegalArgumentException("Неизвестный кадровый приказ");
        };
        var d=repository.draft();d.soldier(next);return after(save(d,town,p.getUniqueId(),id,action.toUpperCase(Locale.ROOT),reason),()->{if(!next.serving()||next.suspendedUntil()>now)endSession(id);});
    } }
    public CompletionStage<Void> assign(Player p, UUID id, Unit unit, String reason) throws Exception { try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {
        UUID town = manage(p, Rank.CAPTAIN); var s = target(town, id); superior(p, s);
        if (!s.serving() || !eligible(s, System.currentTimeMillis()) || s.unit() == unit) throw new IllegalArgumentException("Перевод недоступен");
        if (ArmyRules.capacity(unit, bridge.levels(town, settings), settings) == 0) throw new IllegalArgumentException("Инфраструктура подразделения не готова");
        var d = repository.draft(); d.soldier(s.unit(unit).status(s.oath() ? Status.RESERVE : Status.RECRUIT));
        return after(save(d,town,p.getUniqueId(),id,"TRANSFER",reason),()->endSession(id));
    } }
    public CompletionStage<Void> mobilize(Player p, UUID id, boolean active) throws Exception { try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {
        UUID town = manage(p, Rank.LIEUTENANT); var s = target(town, id);
        if (!s.serving() || !s.oath() || !eligible(s, System.currentTimeMillis())) throw new IllegalArgumentException("Военнослужащий недоступен для мобилизации");
        if (!s.resident().equals(p.getUniqueId())) superior(p, s);
        var d = repository.draft(); var next = s.status(active ? Status.ACTIVE : Status.RESERVE); d.soldier(next);
        if (active && s.status() != Status.ACTIVE && !result(town).active().contains(id)) {
            var live = result(town); if (live.active().size() >= live.totalCapacity() || live.count().get(s.unit()) >= live.capacity().get(s.unit())) throw new IllegalArgumentException("Гарнизон или подразделение укомплектованы");
        }
        return after(save(d,town,p.getUniqueId(),id,active?"MOBILIZE":"RESERVE",active?"Приказ о мобилизации":"Перевод в военный резерв"),()->{if(!active)endSession(id);});
    } }
    public CompletionStage<Integer> alert(Player p, boolean enabled) throws Exception { try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {
        UUID town = manage(p, Rank.GENERAL); var d = repository.draft(); d.cities.put(town, d.city(town).alert(enabled)); int changed = 0;
        var levels = bridge.levels(town, settings); var valid = new HashSet<UUID>(); long now = System.currentTimeMillis();
        for (var s : members(town)) if (s.serving() && s.oath() && eligible(s, now)) {
            // Mass orders are issued by the executive or the appointed commander; no rank change.
            valid.add(s.resident()); d.soldier(s.status(enabled ? Status.ACTIVE : Status.RESERVE));
        }
        var selected = ArmyRules.calculate(d.soldiers.values().stream().filter(s -> s.town().equals(town)).toList(), valid, Set.of(), levels, false, settings).active();
        for (UUID id : valid) { var s = d.soldiers.get(id); boolean mobilized = enabled && selected.contains(id); var next = s.status(mobilized ? Status.ACTIVE : Status.RESERVE); if (repository.state().soldiers().get(id).status() != next.status()) changed++; d.soldier(next); }
        int count=changed;return after(save(d,town,p.getUniqueId(),SYSTEM,"ALERT",enabled?"Общая мобилизация по вместимости гарнизона":"Отбой общей мобилизации"),()->{if(!enabled)for(UUID id:valid)endSession(id);}).thenApply(v->count);
    } }
    public CompletionStage<Void> base(Player p) throws Exception { try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {
        UUID town = manage(p, Rank.GENERAL); var loc = p.getLocation(); var block = TownyAPI.getInstance().getTownBlock(loc);
        if (block == null || block.getTownOrNull() == null || !town.equals(block.getTownOrNull().getUUID()) || block.hasResident()) throw new IllegalArgumentException("База должна находиться на муниципальном участке вашего города");
        if (bridge.levels(town, settings).getOrDefault("army", 0) < 1) throw new IllegalArgumentException("Нужен работающий штаб армии");
        var d = repository.draft(); d.cities.put(town, d.city(town).base(new Base(loc.getWorld().getUID(), loc.getX(), loc.getY(), loc.getZ())));
        return after(save(d,town,p.getUniqueId(),SYSTEM,"BASE","Назначена точка военной базы"),training::clear);
    } }
    public boolean atBase(UUID town, Location loc) { try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {
        var city = repository.state().cities().get(town); var b = city == null ? null : city.base();
        if (b == null || loc.getWorld() == null || !loc.getWorld().getUID().equals(b.world())) return false;
        var block = TownyAPI.getInstance().getTownBlock(loc);
        return block != null && block.getTownOrNull() != null && town.equals(block.getTownOrNull().getUUID()) && !block.hasResident()
                && new Location(loc.getWorld(), b.x(), b.y(), b.z()).distanceSquared(loc) <= settings.baseRadius() * settings.baseRadius();
    } }
    public CompletionStage<UUID> supply(Player p, String resource, long amount) throws Exception { try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) {
        UUID town = manage(p, Rank.SERGEANT); if (!RESOURCES.contains(resource) || amount <= 0 || amount > settings.maxTransfer()) throw new IllegalArgumentException("Укажите ресурс и количество в пределах лимита поставки");
        bridge.resourceReady(town); room(2); var stock = repository.state().cities().getOrDefault(town, City.empty(town)).stock();
        for (var tx : repository.state().transfers().values()) if (tx.town().equals(town) && tx.phase() == Phase.PLANNED) stock = add(stock, tx.amounts());
        add(stock, Map.of(resource, amount)); UUID id = UUID.randomUUID(); var d = repository.draft(); d.city(town);
        d.transfers.put(id, new Transfer(id, town, p.getUniqueId(), Map.of(resource, amount), Phase.PLANNED, System.currentTimeMillis()));
        return save(d,town,p.getUniqueId(),p.getUniqueId(),"SUPPLY_ORDER","Перевод городских ресурсов: "+id).thenApply(v->id);
    } }
    public CompletionStage<Void> process(UUID id) throws Exception {try(var guard=SuiteState.guard()){writable();return track(ArmySupplies.resume(id,repository,bridge));}}
    public CompletionStage<Void> equip(Player p, UUID id) throws Exception { try (var suiteGuard22 = ru.neverland.core.SuiteState.guard()) {
        UUID town = manage(p, Rank.SERGEANT); var s = target(town, id);
        if (!s.serving() || !eligible(s, System.currentTimeMillis()) || s.equipment() == 100) throw new IllegalArgumentException("Снабжение бойца недоступно или уже полное");
        var d = repository.draft(); var city = d.city(town); d.cities.put(town, city.stock(subtract(city.stock(), settings.units().get(s.unit()).kit()))); d.soldier(s.equipment(100));
        return save(d, town, p.getUniqueId(), id, "EQUIPMENT", "Выделено штатное снабжение подразделения");
    } }
    public boolean onDuty(UUID id) { try (var suiteGuard23 = ru.neverland.core.SuiteState.guard()) { return duty.containsKey(id)&&duty.get(id).endedAt==Long.MAX_VALUE; } }
    public void duty(Player p,boolean enabled)throws Exception{try(var guard=SuiteState.guard()){
        UUID id=p.getUniqueId();if(!enabled){if(!onDuty(id))throw new IllegalArgumentException("Вы не на дежурстве");endSession(id);refresh();return;}
        var soldier=self(p);if(duty.containsKey(id))throw new IllegalArgumentException("Предыдущее дежурство ещё сохраняется");
        if(!atBase(soldier.town(),p.getLocation()))throw new IllegalArgumentException("Заступить на дежурство можно на базе");duty.put(id,new Duty(p,System.currentTimeMillis()));refresh();
    }}
    public void train(Player p) throws Exception { try (var suiteGuard25 = ru.neverland.core.SuiteState.guard()) {
        var s = self(p); long now = System.currentTimeMillis();
        if (!onDuty(s.resident()) || !atBase(s.town(), p.getLocation())) throw new IllegalArgumentException("Тренировка доступна на базе во время дежурства");
        if (training.containsKey(s.resident()) || s.training() == 100 || now - s.lastTraining() < settings.trainingCooldown()) throw new IllegalArgumentException("Тренировка уже идёт, подготовка максимальна или ещё действует перерыв");
        if (!covers(repository.state().cities().get(s.town()).stock(), settings.trainingCost())) throw new IllegalArgumentException("Не хватает запасов для тренировки");
        training.put(s.resident(), new Training(now));
    } }
    public void moved(Player p, Location from, Location to) { try (var suiteGuard26 = ru.neverland.core.SuiteState.guard()) {
        if (to == null || from.getWorld() != to.getWorld()) { training.remove(p.getUniqueId()); return; }
        double distance = from.distance(to); if (distance < .05) return;
        var session = duty.get(p.getUniqueId()); if (session == null||session.endedAt!=Long.MAX_VALUE) return;
        long now=System.currentTimeMillis();if(now-session.moved>=settings.dutyIdle()){endSession(p.getUniqueId());return;}
        session.moved = now; session.position = to.clone();
        var t = training.get(p.getUniqueId()); var s = repository.state().soldiers().get(p.getUniqueId());
        if (t != null) { if (s == null || !atBase(s.town(), to) || distance > 8) training.remove(p.getUniqueId()); else t.movement += distance; }
    } }
    public void teleported(UUID id) { try (var suiteGuard27 = ru.neverland.core.SuiteState.guard()) { training.remove(id); } }
    /** Keep stopped sessions until their accrued time is acknowledged, including quits during another write. */
    private CompletionStage<Boolean> accountDuty(boolean force)throws Exception{
        long now=System.currentTimeMillis();var d=repository.draft();var checkpoints=new HashMap<UUID,Long>();var sessions=new HashMap<UUID,Duty>();
        for(var entry:List.copyOf(duty.entrySet())){UUID id=entry.getKey();Duty session=entry.getValue();if(!force&&session.endedAt==Long.MAX_VALUE&&now-session.accounted<60_000)continue;
            var soldier=d.soldiers.get(id);long boundary=Math.min(now,session.endedAt);long millis=Math.max(0,Math.min(boundary,session.moved+settings.dutyIdle())-session.accounted);
            var resident=TownyAPI.getInstance().getResident(id);boolean valid=soldier!=null&&soldier.serving()&&resident!=null&&resident.getTownOrNull()!=null&&soldier.town().equals(resident.getTownOrNull().getUUID())&&(force||eligible(soldier,now));
            if(millis>0&&valid){d.soldier(soldier.duty(millis));checkpoints.put(id,boundary);sessions.put(id,session);}
            else {session.accounted=Math.max(session.accounted,boundary);if(session.endedAt<=boundary)duty.remove(id,session);}
            if(!force&&checkpoints.size()>=128)break;
        }
        if(checkpoints.isEmpty())return CompletableFuture.completedFuture(false);
        return track(after(repository.commit(d),()->{for(var entry:checkpoints.entrySet()){var session=sessions.get(entry.getKey());session.accounted=Math.max(session.accounted,entry.getValue());if(session.endedAt<=entry.getValue())duty.remove(entry.getKey(),session);}}).thenApply(value->true));
    }
    public void leave(UUID id){try(var guard=SuiteState.guard()){endSession(id);removePermissions(id);}}
    private void endSession(UUID id){var session=duty.get(id);if(session!=null)session.endedAt=Math.min(session.endedAt,System.currentTimeMillis());training.remove(id);}
    private int supplyCursor,upkeepCursor;
    public void pulse(){try(var guard=SuiteState.guard()){
        if(stopping||busy())return;
        if(!repository.writable()){clearPermissions();return;}
        try{
            if(!repository.state().handoff()){migrate();return;}
            long now=System.currentTimeMillis();var d=repository.draft();boolean changed=false;
            for(var soldier:repository.state().soldiers().values()){var resident=TownyAPI.getInstance().getResident(soldier.resident());if(soldier.status()!=Status.DISCHARGED&&(resident==null||resident.getTownOrNull()==null||!soldier.town().equals(resident.getTownOrNull().getUUID()))){d.soldier(soldier.status(Status.DISCHARGED));d.audit.add(new Audit(UUID.randomUUID(),soldier.town(),SYSTEM,soldier.resident(),"LEFT_TOWN","Служба прекращена после выхода из города",now));endSession(soldier.resident());changed=true;}}
            if(changed){while(d.audit.size()>settings.auditLimit())d.audit.remove(0);track(after(repository.commit(d),()->refresh()));return;}
            accountDuty(false);if(busy())return;
            var transfers=repository.state().transfers().values().stream().filter(t->t.phase()!=Phase.CLOSED&&t.phase()!=Phase.CANCELLED&&t.phase()!=Phase.RAID_SPENT&&t.phase()!=Phase.KIT_SPENT&&t.phase()!=Phase.KIT_RETURNED).toList();
            if(!transfers.isEmpty()){var transfer=transfers.get(Math.floorMod(supplyCursor++,transfers.size()));try{process(transfer.id());}catch(Exception error){report(error);}if(busy())return;}
            var cities=List.copyOf(repository.state().cities().keySet());if(!cities.isEmpty()){upkeep(cities.get(Math.floorMod(upkeepCursor++,cities.size())),now);if(busy())return;}
            for(UUID id:List.copyOf(duty.keySet())){if(!onDuty(id))continue;var player=Bukkit.getPlayer(id);if(player==null){endSession(id);continue;}ru.neverland.core.SuiteScheduler.player(plugin,player,online->{try{activity(online);}catch(Exception error){report(error);}});}
            refresh();
        }catch(Exception|LinkageError error){report(error);}
    }}
    private void activity(Player p)throws Exception{
        if(stopping||busy()||!healthy())return;long now=System.currentTimeMillis();UUID id=p.getUniqueId();var session=duty.get(id);if(session==null||session.endedAt!=Long.MAX_VALUE)return;var soldier=repository.state().soldiers().get(id);
        if(!p.isOnline()||p.isDead()||soldier==null||!soldier.serving()||!eligible(soldier,now)){endSession(id);return;}
        if(now-session.moved>=settings.dutyIdle()){endSession(id);ArmyCommand.say(p,"&eДежурство завершено из-за отсутствия активности.");return;}
        var exercise=training.get(id);if(exercise==null)return;if(!atBase(soldier.town(),p.getLocation())){training.remove(id);return;}if(now-exercise.started<settings.trainingDuration())return;
        if(!ArmyRules.trainingComplete(now-exercise.started,exercise.movement,true,true,settings)){training.remove(id);ArmyCommand.say(p,"&eТренировка не зачтена: требуется движение по территории базы.");return;}
        var d=repository.draft();var city=d.city(soldier.town());if(!covers(city.stock(),settings.trainingCost())){training.remove(id);ArmyCommand.say(p,"&eТренировка завершилась без зачёта: не хватило запасов.");return;}
        d.cities.put(soldier.town(),city.stock(subtract(city.stock(),settings.trainingCost())));d.soldier(soldier.trained(settings.trainingGain(),now));
        var done=after(save(d,soldier.town(),id,id,"TRAINING","Завершена подготовка на военной базе"),()->training.remove(id,exercise));
        done.whenComplete((v,error)->{if(error==null&&!stopping&&plugin.isEnabled())ru.neverland.core.SuiteScheduler.player(plugin,p,online->ArmyCommand.say(online,"&aТренировка сохранена. Подготовка +"+settings.trainingGain()+"%."));});
    }
    private void upkeep(UUID town,long now)throws Exception{
        if(TownyAPI.getInstance().getTown(town)==null)return;var city=repository.state().cities().get(town);if(now<city.nextSupply())return;var active=result(town).active();if(active.isEmpty())return;
        var cost=ArmyRules.upkeep(members(town),active,settings);boolean enough=covers(city.stock(),cost);var d=repository.draft();d.cities.put(town,city.supplied(enough?subtract(city.stock(),cost):city.stock(),now+settings.supplyInterval(),enough?now+settings.supplyInterval():0));
        if(!enough)for(UUID id:active){var soldier=d.soldiers.get(id);d.soldier(soldier.equipment(Math.max(0,soldier.equipment()-settings.shortageLoss())));}
        save(d,town,SYSTEM,SYSTEM,enough?"UPKEEP":"SHORTAGE",enough?"Гарнизон обеспечен на следующий период":"Недостаток снабжения: снижена готовность гарнизона");
    }
    private void report(Throwable e) { try(var guard=SuiteState.guard()){long now = System.currentTimeMillis(); if (now - lastError > 60000) { lastError = now; plugin.getLogger().log(java.util.logging.Level.WARNING, "Армия: действие приостановлено, данные сохранены", e); } }}
    private void removePermissions(UUID id) { var attachment = permissions.remove(id); grants.remove(id); if (attachment != null) {var player=Bukkit.getPlayer(id);if(player!=null) ru.neverland.core.SuiteScheduler.cleanupPlayer(plugin,player,p->{try{attachment.remove();}catch(IllegalArgumentException ignored){}});} }
    private void clearPermissions() { for (UUID id : List.copyOf(permissions.keySet())) removePermissions(id); }
    public void refresh() { try (var suiteGuard30 = ru.neverland.core.SuiteState.guard()) {
        if (!healthy()) { clearPermissions(); return; }
        var effective = new HashMap<UUID, Set<UUID>>(); long now = System.currentTimeMillis();
        ru.neverland.core.SuiteScheduler.players(plugin, p -> {{
            if(!healthy()){removePermissions(p.getUniqueId());return;}
            UUID id = p.getUniqueId(); var s = repository.state().soldiers().get(id); var nodes = new HashSet<String>();
            try {
                if (s != null && s.serving() && eligible(s, now)) {
                    if(plugin.tactics()!=null&&plugin.tactics().available())nodes.add("neverlandtownyarmy.specialty."+plugin.tactics().role(s.town(),s.resident()).id());
                    nodes.add("neverlandtownyarmy.rank." + s.rank().id()); nodes.add("neverlandtownyarmy.unit." + s.unit().id());
                    if (onDuty(id)) nodes.add("neverlandtownyarmy.duty");
                    if (!effective.containsKey(s.town())) effective.put(s.town(), result(s.town()).active());
                    if (s.status() == Status.ACTIVE && effective.get(s.town()).contains(id)) nodes.add("neverlandtownybuilds.army.soldier");
                }
            } catch (Exception | LinkageError e) { nodes.clear(); report(e); }
            if (!nodes.equals(grants.getOrDefault(id, Set.of()))) { removePermissions(id); if (!nodes.isEmpty()) { var a = p.addAttachment(plugin); for (String node : nodes) a.setPermission(node, true); permissions.put(id, a); grants.put(id, Set.copyOf(nodes)); } }
        }});
        for (UUID id : List.copyOf(permissions.keySet())) if (Bukkit.getPlayer(id) == null) removePermissions(id);
    } }
    public CompletionStage<Void> stop(){try(var guard=SuiteState.guard()){
        if(closing!=null)return closing;stopping=true;for(UUID id:List.copyOf(duty.keySet()))endSession(id);clearPermissions();training.clear();
        closing=operation.handle((value,error)->error).thenCompose(error->{try(var locked=SuiteState.guard()){if(error!=null)return repository.close().thenCompose(v->CompletableFuture.<Void>failedFuture(error));try{return accountDuty(true).thenCompose(v->repository.close());}catch(Exception ex){return repository.close().thenCompose(v->CompletableFuture.<Void>failedFuture(ex));}}});return closing;
    }}
    @Override public boolean isMobilized(UUID id) { try (var suiteGuard32 = ru.neverland.core.SuiteState.guard()) { mainThread(); if (id == null || !healthy()) return false; var s = repository.state().soldiers().get(id); if (s == null || s.status() != Status.ACTIVE) return false; try { return result(s.town()).active().contains(id); } catch (Exception e) { throw new IllegalStateException("Гарнизон недоступен", e); } } }
    @Override public Set<UUID> soldiers(UUID town) { try (var suiteGuard33 = ru.neverland.core.SuiteState.guard()) { mainThread(); readable(); if (town == null) return Set.of(); try { var active = result(town).active(); var out = new HashSet<UUID>(); for (var s : members(town)) if (s.status() == Status.ACTIVE && active.contains(s.resident())) out.add(s.resident()); return Set.copyOf(out); } catch (Exception e) { throw new IllegalStateException("Гарнизон недоступен", e); } } }
    private Map<String, Object> record(Soldier s) {
        var out = new LinkedHashMap<String, Object>(); out.put("resident", s.resident()); out.put("town", s.town()); out.put("unit", s.unit().id()); out.put("rank", s.rank().id()); out.put("status", s.status().name()); out.put("oath", s.oath()); out.put("training", s.training()); out.put("equipment", s.equipment()); out.put("dutyMillis", s.dutyMillis()); out.put("onDuty", onDuty(s.resident())); out.put("warnings", s.warnings()); out.put("suspendedUntil", s.suspendedUntil()); return Map.copyOf(out);
    }
    @Override public Optional<Map<String, Object>> serviceRecord(UUID id) { try (var suiteGuard34 = ru.neverland.core.SuiteState.guard()) { mainThread(); readable(); return id == null ? Optional.empty() : Optional.ofNullable(repository.state().soldiers().get(id)).map(this::record); } }
    @Override public Collection<Map<String, Object>> roster(UUID town) { try (var suiteGuard35 = ru.neverland.core.SuiteState.guard()) { mainThread(); readable(); return members(town).stream().map(this::record).toList(); } }
    /** A boolean caller may only observe an already confirmed receipt; it cannot enqueue a hidden withdrawal. */
    @Override public boolean raidSupplyReceipt(UUID town,UUID receipt,Map<String,Long> amounts){try(var guard=SuiteState.guard()){if(!repository.writable())throw new IllegalStateException("Военные запасы требуют проверки");var old=repository.transfer(receipt);if(old==null)return false;if(!old.town().equals(town)||old.phase()!=Phase.RAID_SPENT||!old.amounts().equals(ArmyModel.stock(amounts)))throw new IllegalArgumentException("Квитанция снабжения не совпадает");return true;}}
    @Override public boolean consumeRaidSupplies(UUID town,UUID receipt,Map<String,Long> amounts)throws Exception{try(var guard=SuiteState.guard()){var old=repository.transfer(receipt);if(old==null)throw new IllegalStateException("Обновите осадный аддон: снабжение требует подтверждаемого requestRaidSupplies; запасы не списаны");if(!old.town().equals(town)||old.phase()!=Phase.RAID_SPENT||!old.amounts().equals(ArmyModel.stock(amounts)))throw new IllegalArgumentException("Квитанция снабжения не совпадает");return true;}}
    @Override public CompletionStage<Boolean> requestRaidSupplies(UUID town,UUID receipt,Map<String,Long> amounts)throws Exception{try(var guard=SuiteState.guard()){
        if(repository.transfer(receipt)!=null)return CompletableFuture.completedFuture(consumeRaidSupplies(town,receipt,amounts));writable();if(SuiteState.frozen(plugin.getName()))throw new IllegalStateException("Армия приостановлена");room(1);return track(after(repository.spendRaid(town,receipt,amounts,System.currentTimeMillis()),()->{if(!stopping)refresh();}));
    }}
    /** Internal optional tactics integration; no equipment is minted by these stock-only methods. */
    public Soldier participant(Player p)throws Exception{try(var guard=SuiteState.guard()){return self(p);}}
    public UUID commander(Player p,Rank minimum){try(var guard=SuiteState.guard()){return manage(p,minimum);}}
    public String equipmentReceipt(UUID town,UUID id,UUID player,Map<String,Long> cost){try(var guard=SuiteState.guard()){if(!repository.writable())throw new IllegalStateException("Военные запасы ожидают восстановления; результат списания нельзя считать отсутствующим");return repository.equipmentReceipt(town,id,player,cost);}}
    public CompletionStage<Boolean> fundEquipment(UUID town,UUID id,UUID player,Map<String,Long> cost)throws Exception{try(var guard=SuiteState.guard()){
        String receipt=equipmentReceipt(town,id,player,cost);if(receipt.equals("SPENT"))return CompletableFuture.completedFuture(true);if(receipt.equals("RETURNED"))throw new IllegalArgumentException("Средства этого комплекта уже возвращены");writable();room(1);return track(repository.fundEquipment(town,id,player,cost,System.currentTimeMillis()));
    }}
    public CompletionStage<Void> returnEquipment(UUID town,UUID id,UUID player,Map<String,Long> cost)throws Exception{try(var guard=SuiteState.guard()){
        String receipt=equipmentReceipt(town,id,player,cost);if(receipt.equals("NONE")||receipt.equals("RETURNED"))return CompletableFuture.completedFuture(null);writable();return track(repository.returnEquipment(town,id,player,cost,System.currentTimeMillis()));
    }}
    @Override public Map<String, Long> reserves(UUID town) { try (var suiteGuard36 = ru.neverland.core.SuiteState.guard()) { mainThread(); readable(); return town == null ? Map.of() : repository.state().cities().getOrDefault(town, City.empty(town)).stock(); } }
    @Override public Optional<Map<String, Object>> garrison(UUID town) { try (var suiteGuard37 = ru.neverland.core.SuiteState.guard()) {
        mainThread(); readable(); if (town == null || TownyAPI.getInstance().getTown(town) == null) return Optional.empty();
        try { var r = result(town); var c = repository.state().cities().getOrDefault(town, City.empty(town)); var out = new LinkedHashMap<String, Object>();
            out.put("town", town); out.put("active", r.active().size()); out.put("capacity", r.totalCapacity()); out.put("score", r.score()); out.put("readiness", r.readiness()); out.put("defenseBonus", r.defense()); out.put("alert", c.alert()); out.put("suppliedUntil", c.suppliedUntil());
            out.put("reserve", members(town).stream().filter(s -> s.status() == Status.RESERVE).count()); out.put("onDuty", members(town).stream().filter(s -> onDuty(s.resident())).count());
            var units = new TreeMap<String, Object>(); for (Unit unit : Unit.values()) units.put(unit.id(), Map.of("active", r.count().get(unit), "capacity", r.capacity().get(unit))); out.put("units", Map.copyOf(units)); return Optional.of(Map.copyOf(out));
        } catch (Exception e) { throw new IllegalStateException("Расчёт гарнизона приостановлен", e); }
    } }
    @Override public Collection<Map<String, Object>> history(UUID town) { try (var suiteGuard38 = ru.neverland.core.SuiteState.guard()) { mainThread(); readable(); return repository.state().audit().stream().filter(a -> a.town().equals(town)).map(a -> Map.<String, Object>of("id", a.id(), "actor", a.actor(), "subject", a.subject(), "action", a.action(), "reason", a.reason(), "time", a.time())).toList(); } }
}
