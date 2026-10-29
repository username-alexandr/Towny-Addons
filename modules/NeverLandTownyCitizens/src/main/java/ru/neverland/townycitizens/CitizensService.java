package ru.neverland.townycitizens;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.neverland.townycitizens.api.TownyCitizensApi;
import ru.neverland.townycitizens.model.*;

public final class CitizensService implements TownyCitizensApi {
    private final CitizensRepository repository;
    private CitizensSettings settings;private final org.bukkit.plugin.Plugin plugin;
    public CitizensService(CitizensRepository repository,CitizensSettings settings){this(repository,settings,null);}
    public CitizensService(CitizensRepository repository,CitizensSettings settings,org.bukkit.plugin.Plugin plugin){this.repository=repository;this.settings=settings;this.plugin=plugin;}
    public CitizensRepository repository() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return repository; } }
    @Override public boolean healthy() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return ru.neverland.core.SuiteState.held() && repository.readable(); } }
    public CitizensSettings settings() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return settings; } }
    public void settings(CitizensSettings settings) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { this.settings = settings; } }
    private void ready() {
        if (!ru.neverland.core.SuiteState.held()) throw new IllegalStateException("Гражданство доступно в основном потоке");
        if (!repository.readable()) throw new IllegalStateException("Реестр гражданства недоступен; обратитесь к администратору");
    }
    public Town ownTown(UUID player) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { var r = TownyAPI.getInstance().getResident(player); return r == null ? null : r.getTownOrNull(); } }
    private boolean member(UUID town, UUID player) { Town own = ownTown(player); return own != null && own.getUUID().equals(town); }
    public CitizenshipStatus effective(UUID town, UUID player) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        ready(); if (town == null || player == null || TownyAPI.getInstance().getTown(town) == null) return CitizenshipStatus.FOREIGNER;
        var record = repository.get(town, player); boolean member = member(town, player);
        return record == null ? (member ? CitizenshipStatus.CITIZEN : CitizenshipStatus.FOREIGNER) : record.effective(member, System.currentTimeMillis());
    } }
    @Override public String status(UUID town, UUID resident) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return effective(town, resident).name(); } }
    @Override public long expiresAt(UUID town, UUID resident) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { ready(); var r = repository.get(town, resident); return r == null ? 0 : r.expiresAt(); } }
    @Override public boolean allows(UUID town, UUID resident, String right) { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {
        CitizenRight parsed; try { parsed = CitizenRight.valueOf(right); } catch (IllegalArgumentException | NullPointerException ex) { return false; }
        var status = effective(town, resident);
        if (town == null || TownyAPI.getInstance().getTown(town) == null) return false;
        // Citizenship can restrict Towny's permissions; it never confers membership or a town rank.
        if ((parsed == CitizenRight.VOTE || parsed == CitizenRight.HOLD_OFFICE || parsed == CitizenRight.STORAGE)
                && !member(town, resident)) return false;
        return settings.policies().get(status).rights().contains(parsed);
    } }
    @Override public double taxMultiplier(UUID town, UUID resident) { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) { return settings.policies().get(effective(town, resident)).tax(); } }
    @Override public Map<String, String> passport(UUID resident) { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {
        ready(); var town = ownTown(resident); return passportFields(town == null ? null : town.getUUID(), resident);
    } }
    @Override public Map<String, String> passportFields(UUID town, UUID resident) { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {
        var status = effective(town, resident); long expiry = expiresAt(town, resident);
        return Map.of("citizenship", status.title(), "citizenship_id", status.name(), "citizenship_town", town == null ? "" : town.toString(),
                "citizenship_town_name", town == null || TownyAPI.getInstance().getTown(town) == null ? "Нет города" : TownyAPI.getInstance().getTown(town).getName(),
                "citizenship_expires", expiry == 0 ? "Бессрочно" : Instant.ofEpochMilli(expiry).toString(),
                "citizenship_tax_multiplier", Double.toString(taxMultiplier(town, resident)),
                "citizenship_vote", allows(town, resident, "VOTE") ? "Да" : "Нет");
    } }
    public boolean manages(CommandSender actor, Town town) { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {
        if (actor.hasPermission("neverlandtownycitizens.admin")) return true;
        if (!(actor instanceof Player player) || !actor.hasPermission("neverlandtownycitizens.manage")) return false;
        var r = TownyAPI.getInstance().getResident(player);
        return r != null && town != null && town.equals(r.getTownOrNull()) && town.isMayor(r);
    } }
    public java.util.concurrent.CompletionStage<Admissions.State> assign(CommandSender actor, Town town, UUID target, CitizenshipStatus status, int days, String reason) throws IOException { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {
        ready(); if (town == null || !manages(actor, town)) throw new IllegalArgumentException("Назначать статус может мэр своего города или администратор");
        if (TownyAPI.getInstance().getResident(target) == null) throw new IllegalArgumentException("Игрок не зарегистрирован в Towny");
        if ((status == CitizenshipStatus.CITIZEN || status == CitizenshipStatus.TEMPORARY) && !member(town.getUUID(), target))
            throw new IllegalArgumentException("Сначала пригласите игрока в город через Towny");
        if (town.getMayor() != null && town.getMayor().getUUID().equals(target)
                && status != CitizenshipStatus.CITIZEN && status != CitizenshipStatus.HONORARY)
            throw new IllegalArgumentException("Мэр должен оставаться гражданином или почётным гражданином");
        if (status == CitizenshipStatus.TEMPORARY ? days < 1 || days > settings.maximumTemporaryDays() : days != 0)
            throw new IllegalArgumentException("Укажите допустимый срок только для временного жителя");
        long now = System.currentTimeMillis();
        var before=repository.state();var record=new CitizenshipRecord(town.getUUID(),target,status,days==0?0:Math.addExact(now,Math.multiplyExact((long)days,Admissions.DAY)),now,actor instanceof Player p?p.getUniqueId().toString():actor.getName(),reason);var next=Admissions.record(before,record,actor instanceof Player p?p.getUniqueId():null,actor.getName());return persist(actor,town.getUUID(),before,next,"CITIZEN_STATUS",target+" → "+status.name()+"; "+reason);
    } }
    public java.util.concurrent.CompletionStage<Admissions.State> persist(CommandSender actor,UUID town,Admissions.State before,Admissions.State next,String kind,String details){
        ready();if(!repository.writable()||plugin!=null&&(!plugin.isEnabled()||ru.neverland.core.SuiteState.frozen(plugin.getName())))throw new IllegalStateException("Изменения гражданства приостановлены");var person=actor instanceof Player p?ru.neverland.core.AuditTrail.player(p.getUniqueId()):ru.neverland.core.AuditRecord.Party.system();var city=ru.neverland.core.AuditTrail.town(town);
        return repository.commit(before,next).thenApply(image->{if(plugin!=null&&image!=before&&!ru.neverland.core.AuditTrail.record(plugin,"citizenship",town+":"+image.board().revision(),kind,"COMPLETED",person,city,city,"",0,"",details))plugin.getLogger().warning("Гражданство сохранено; журнал требует проверки");return image;}).whenComplete((image,error)->{if(error!=null&&plugin!=null&&!repository.writable())plugin.getLogger().log(java.util.logging.Level.WARNING,"Запись гражданства не подтверждена; прежние сохранённые статусы продолжают действовать",error);});
    }
    public java.util.concurrent.CompletionStage<Void> prepareClose(){return repository.prepareClose();}public void awaitClosed(){repository.awaitClosed();}
}
