package ru.neverland.governance.service;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.governance.integration.TownyHook;
import ru.neverland.governance.model.ActiveLaw;
import ru.neverland.governance.model.HistoryEntry;
import ru.neverland.governance.model.LawCategory;
import ru.neverland.governance.model.LawDefinition;
import ru.neverland.governance.model.OfficeDefinition;
import ru.neverland.governance.model.OfficeHolder;
import ru.neverland.governance.model.Proposal;
import ru.neverland.governance.model.ProposalAction;
import ru.neverland.governance.model.ProposalStatus;
import ru.neverland.governance.model.TownGovernanceData;
import ru.neverland.governance.model.VoteChoice;
import ru.neverland.governance.util.ColorUtil;
import ru.neverland.governance.util.GovernanceMath;
import ru.neverland.governance.util.TimeUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GovernanceService {
    public enum Result {
        SUCCESS, NO_TOWN, NO_PERMISSION, NOT_COUNCIL, UNKNOWN_LAW, UNKNOWN_OFFICE, UNKNOWN_PROPOSAL,
        ALREADY_ACTIVE, NOT_ACTIVE, DUPLICATE, LIMIT, COOLDOWN, NOT_ELIGIBLE, CHANGE_DISABLED,
        PAUSED, UNKNOWN_PLAYER, NOT_RESIDENT, OFFICE_FULL, ALREADY_APPOINTED, NOT_APPOINTED, MULTIPLE_OFFICES
    }
    public record ProposalResult(Result result, Proposal proposal, long remainingMillis) { }
    public record VoteResult(Result result, Proposal proposal) { }

    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final DefinitionRegistry definitions;
    private final GovernanceRepository repository;
    private final MessageService messages;
    private BukkitTask resolver;
    private BukkitTask autosave;

    public GovernanceService(JavaPlugin plugin, TownyHook towny, DefinitionRegistry definitions,
                             GovernanceRepository repository, MessageService messages) {
        this.plugin = plugin; this.towny = towny; this.definitions = definitions; this.repository = repository; this.messages = messages;
    }

    public void start() { synchronized (ru.neverland.core.SuiteState.lock()) {
        stopTasks();
        long resolvePeriod = Math.max(20L, plugin.getConfig().getLong("voting.resolution-interval-seconds", 20) * 20L);
        long savePeriod = Math.max(20L, plugin.getConfig().getLong("storage.autosave-seconds", 60) * 20L);
        resolver = ru.neverland.core.SuiteScheduler.get().runTaskTimer(plugin, this::tick, 20L, resolvePeriod);
        autosave = ru.neverland.core.SuiteScheduler.get().runTaskTimer(plugin, repository::saveIfDirty, savePeriod, savePeriod);
    } }

    public void shutdown() { synchronized (ru.neverland.core.SuiteState.lock()) { stopTasks(); repository.save(); } }
    private void stopTasks() { if (resolver != null) resolver.cancel(); if (autosave != null) autosave.cancel(); resolver = autosave = null; }

    public ProposalResult propose(Player player, String lawId, ProposalAction action) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Town town = towny.town(player); if (town == null) return new ProposalResult(Result.NO_TOWN, null, 0);
        LawDefinition law = definitions.law(lawId); if (law == null) return new ProposalResult(Result.UNKNOWN_LAW, null, 0);
        if (!player.hasPermission("townygovernance.propose") || !mayPropose(player, town, law.category()))
            return new ProposalResult(Result.NO_PERMISSION, null, 0);
        TownGovernanceData data = data(town);
        if (action == ProposalAction.ENACT && data.activeLaws().containsKey(law.id())) return new ProposalResult(Result.ALREADY_ACTIVE, null, 0);
        if (action == ProposalAction.REPEAL && !data.activeLaws().containsKey(law.id())) return new ProposalResult(Result.NOT_ACTIVE, null, 0);
        for (Proposal open : repository.open(town.getUUID())) if (open.lawId().equals(law.id())) return new ProposalResult(Result.DUPLICATE, open, 0);
        int maximum = Math.max(1, plugin.getConfig().getInt("voting.max-open-per-town", 5));
        if (repository.open(town.getUUID()).size() >= maximum) return new ProposalResult(Result.LIMIT, null, 0);
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0, plugin.getConfig().getLong("voting.proposal-cooldown-minutes", 15)) * 60_000L;
        long remaining = data.lastProposalAt() + cooldown - now;
        if (remaining > 0 && !player.hasPermission("townygovernance.bypass")) return new ProposalResult(Result.COOLDOWN, null, remaining);
        long duration = Math.max(1, plugin.getConfig().getLong("voting.duration-hours", 24)) * 3_600_000L;
        Proposal proposal = new Proposal(UUID.randomUUID(), town.getUUID(), town.getName(), law.id(), action,
                player.getUniqueId(), player.getName(), now, now + duration, ProposalStatus.OPEN);
        if (electorate(town).contains(player.getUniqueId())) proposal.vote(player.getUniqueId(), VoteChoice.YES);
        repository.add(proposal); data.lastProposalAt(now); repository.changed();
        repository.history(data, new HistoryEntry(now, "PROPOSAL", "Создано предложение #" + proposal.shortId() + ": "
                + actionText(action) + " «" + ColorUtil.strip(law.name()) + "»", player.getUniqueId(), player.getName()));
        if (plugin.getConfig().getBoolean("messages.broadcast-new-proposal", true)) broadcast(town, "proposal-broadcast", Map.of(
                "player", player.getName(), "action", actionText(action), "law", law.name()));
        return new ProposalResult(Result.SUCCESS, proposal, duration);
    } }

    public VoteResult vote(Player player, String proposalId, VoteChoice choice) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Town town = towny.town(player); if (town == null) return new VoteResult(Result.NO_TOWN, null);
        if (!player.hasPermission("townygovernance.vote")) return new VoteResult(Result.NO_PERMISSION, null);
        Proposal proposal = repository.findOpen(town.getUUID(), proposalId);
        if (proposal == null) return new VoteResult(Result.UNKNOWN_PROPOSAL, null);
        if(proposal.timer().paused())return new VoteResult(Result.PAUSED,proposal);
        if (!electorate(town).contains(player.getUniqueId())) return new VoteResult(Result.NOT_ELIGIBLE, proposal);
        if (proposal.voteOf(player.getUniqueId()) != null && !plugin.getConfig().getBoolean("voting.allow-change", true))
            return new VoteResult(Result.CHANGE_DISABLED, proposal);
        proposal.vote(player.getUniqueId(), choice); repository.changed();
        if (System.currentTimeMillis() >= proposal.endsAt()) resolve(proposal, false);
        return new VoteResult(Result.SUCCESS, proposal);
    } }

    /** Read-only previews share the exact checks used by commands and menu confirmations. */
    public String officeAccessReason(Player actor,String officeId) { synchronized(ru.neverland.core.SuiteState.lock()) {
        Town town=towny.town(actor);if(town==null)return "Сначала вступите в город.";
        if(!actor.hasPermission("townygovernance.offices"))return "Назначение на должности для вас отключено администрацией сервера.";
        if(!towny.isManager(actor,town))return "Назначения доступны мэру и уполномоченным им управляющим.";
        if(!ru.neverland.core.CitizensAccess.allows(town.getUUID(),actor.getUniqueId(),"HOLD_OFFICE"))return "Ваш статус жителя не позволяет управлять должностями.";
        if(definitions.office(officeId)==null)return "Должность больше не существует.";
        if(data(town).electedOffices().contains(officeId)&&elective(town)&&!actor.hasPermission("townygovernance.bypass"))return "Эту должность занимают по результатам выборов.";
        return "";
    } }
    public String proposalAccessReason(Player player,Town town,LawCategory category) { synchronized(ru.neverland.core.SuiteState.lock()) {
        if(town==null||!town.equals(towny.town(player)))return "Сначала вступите в этот город.";
        if(!player.hasPermission("townygovernance.propose"))return "Предложение законов для вас отключено администрацией сервера.";
        if(!ru.neverland.core.CitizensAccess.allows(town.getUUID(),player.getUniqueId(),"HOLD_OFFICE"))return "Ваш статус жителя не позволяет предлагать законы.";
        return mayPropose(player,town,category)?"":"У вас нет права предлагать законы этой категории. Обратитесь к мэру.";
    } }
    public String voteAccessReason(Player player,Town town,Proposal proposal) { synchronized(ru.neverland.core.SuiteState.lock()) {
        if(proposal==null)return "Голосование уже завершено.";
        if(!player.hasPermission("townygovernance.vote"))return "Голосование для вас отключено администрацией сервера.";
        if(proposal.timer().paused())return "Голосование приостановлено. Можно проголосовать после возобновления.";
        if(!ru.neverland.core.CitizensAccess.allows(town.getUUID(),player.getUniqueId(),"VOTE"))return "Ваш статус жителя не позволяет голосовать.";
        if(!electorate(town).contains(player.getUniqueId()))return "В этом голосовании участвует только городской совет. Вы пока не входите в его состав.";
        if(proposal.voteOf(player.getUniqueId())!=null&&!plugin.getConfig().getBoolean("voting.allow-change",true))return "Вы уже проголосовали. Изменение голоса отключено.";
        return "";
    } }
    public Result officeAccess(Player actor, String officeId) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Town town = towny.town(actor); if (town == null) return Result.NO_TOWN;
        if (!actor.hasPermission("townygovernance.offices") || !towny.isManager(actor, town)
                || !ru.neverland.core.CitizensAccess.allows(town.getUUID(), actor.getUniqueId(), "HOLD_OFFICE")) return Result.NO_PERMISSION;
        OfficeDefinition office = definitions.office(officeId); if (office == null) return Result.UNKNOWN_OFFICE;
        if (data(town).electedOffices().contains(office.id()) && elective(town) && !actor.hasPermission("townygovernance.bypass")) return Result.CHANGE_DISABLED;
        return Result.SUCCESS;
    } }

    public Result appointmentStatus(Player actor, String officeId, UUID residentId) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Result access = officeAccess(actor, officeId); if (access != Result.SUCCESS) return access;
        Town town = towny.town(actor); OfficeDefinition office = definitions.office(officeId);
        Resident resident = residentId == null ? null : towny.resident(residentId); if (resident == null) return Result.UNKNOWN_PLAYER;
        if (!town.equals(resident.getTownOrNull())) return Result.NOT_RESIDENT;
        if (!ru.neverland.core.CitizensAccess.allows(town.getUUID(), resident.getUUID(), "HOLD_OFFICE")) return Result.NOT_ELIGIBLE;
        TownGovernanceData data = data(town); List<OfficeHolder> holders = data.offices().getOrDefault(office.id(), List.of());
        if (holders.stream().anyMatch(value -> value.residentId().equals(resident.getUUID()))) return Result.ALREADY_APPOINTED;
        if (holders.size() >= office.maxHolders()) return Result.OFFICE_FULL;
        if (!plugin.getConfig().getBoolean("management.allow-multiple-offices", true) && holdsAnyOffice(data, resident.getUUID())) return Result.MULTIPLE_OFFICES;
        return Result.SUCCESS;
    } }

    public Result appoint(Player actor, String officeId, String residentName) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Result access = officeAccess(actor, officeId); if (access != Result.SUCCESS) return access;
        Resident resident = towny.resident(residentName);
        return appoint(actor, officeId, resident == null ? null : resident.getUUID());
    } }

    /** UUID is selected from the server-owned resident list, not accepted from a client command. */
    public Result appoint(Player actor, String officeId, UUID residentId) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Result result = appointmentStatus(actor, officeId, residentId); if (result != Result.SUCCESS) return result;
        Town town = towny.town(actor); OfficeDefinition office = definitions.office(officeId); Resident resident = towny.resident(residentId);
        TownGovernanceData data = data(town);
        data.offices().computeIfAbsent(office.id(), key -> new ArrayList<>()).add(new OfficeHolder(resident.getUUID(), resident.getName(), System.currentTimeMillis(), actor.getUniqueId()));
        repository.changed();
        repository.history(data, new HistoryEntry(System.currentTimeMillis(), "APPOINT", resident.getName() + " назначен: "
                + ColorUtil.strip(office.name()), actor.getUniqueId(), actor.getName()));
        return Result.SUCCESS;
    } }

    public Result dismissalStatus(Player actor, String officeId, UUID residentId) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Result access = officeAccess(actor, officeId); if (access != Result.SUCCESS) return access;
        return data(towny.town(actor)).offices().getOrDefault(definitions.office(officeId).id(), List.of()).stream()
                .anyMatch(holder -> holder.residentId().equals(residentId)) ? Result.SUCCESS : Result.NOT_APPOINTED;
    } }

    public Result dismiss(Player actor, String officeId, String residentName) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Result access = officeAccess(actor, officeId); if (access != Result.SUCCESS) return access;
        OfficeHolder holder = data(towny.town(actor)).offices().getOrDefault(definitions.office(officeId).id(), List.of()).stream()
                .filter(value -> value.residentName().equalsIgnoreCase(residentName)).findFirst().orElse(null);
        return holder == null ? Result.NOT_APPOINTED : dismiss(actor, officeId, holder.residentId());
    } }

    public Result dismiss(Player actor, String officeId, UUID residentId) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Result result = dismissalStatus(actor, officeId, residentId); if (result != Result.SUCCESS) return result;
        TownGovernanceData data = data(towny.town(actor)); OfficeDefinition office = definitions.office(officeId);
        List<OfficeHolder> holders = data.offices().get(office.id());
        OfficeHolder holder = holders.stream().filter(value -> value.residentId().equals(residentId)).findFirst().orElseThrow();
        holders.remove(holder); repository.changed();
        repository.history(data, new HistoryEntry(System.currentTimeMillis(), "DISMISS", holder.residentName() + " снят: "
                + ColorUtil.strip(office.name()), actor.getUniqueId(), actor.getName()));
        return Result.SUCCESS;
    } }

    public boolean mayPropose(Player player,Town town,LawCategory category){ synchronized(ru.neverland.core.SuiteState.lock()) {
        if(town==null||!town.equals(towny.town(player))||!ru.neverland.core.CitizensAccess.allows(town.getUUID(),player.getUniqueId(),"HOLD_OFFICE"))return false;
        if(town.getMayor()!=null&&town.getMayor().getUUID().equals(player.getUniqueId()))return true;
        boolean legacy=player.hasPermission("townygovernance.bypass")||towny.isManager(player,town);
        for(var entry:data(town).offices().entrySet()){
            var office=definitions.office(entry.getKey());if(office!=null&&office.mayPropose(category)&&entry.getValue().stream().anyMatch(v->v.residentId().equals(player.getUniqueId())))legacy=true;
        }
        return ru.neverland.core.CouncilAccess.check(player,town.getUUID(),"law_"+category.name().toLowerCase(java.util.Locale.ROOT),legacy);
    } }

    public Set<UUID> council(Town town) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Set<UUID> result = new LinkedHashSet<>(towny.townRankCouncil(town));
        TownGovernanceData data = data(town);
        boolean electedCouncil = data.electedOffices().contains("councillor") && elective(town);
        if (electedCouncil) {
            result.clear();
            if (plugin.getConfig().getBoolean("council.mayor-is-member", true) && town.getMayor() != null) result.add(town.getMayor().getUUID());
        }
        for (Map.Entry<String, List<OfficeHolder>> entry : data.offices().entrySet()) {
            OfficeDefinition office = definitions.office(entry.getKey()); if (office == null || !office.councilMember()) continue;
            if (electedCouncil && !entry.getKey().equals("councillor")) continue;
            for (OfficeHolder holder : entry.getValue()) if (isResident(town, holder.residentId())) result.add(holder.residentId());
        }
        result.removeIf(id -> !ru.neverland.core.CitizensAccess.allows(town.getUUID(), id, "HOLD_OFFICE"));
        return result;
    } }

    public Set<UUID> electorate(Town town) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (plugin.getConfig().getString("voting.electorate", "COUNCIL").equalsIgnoreCase("ALL_RESIDENTS")) {
            Set<UUID> result = new LinkedHashSet<>(); town.getResidents().forEach(resident -> result.add(resident.getUUID()));
            result.removeIf(id -> !ru.neverland.core.CitizensAccess.allows(town.getUUID(), id, "VOTE")); return result;
        }
        Set<UUID> result = council(town);
        result.removeIf(id -> !ru.neverland.core.CitizensAccess.allows(town.getUUID(), id, "VOTE"));
        return result;
    } }

    private void electionThread() { if (!ru.neverland.core.SuiteState.held()) throw new IllegalStateException("Governance API: main thread required"); }
    private boolean elective(Town town) {
        var connection = ru.neverland.core.ApiServices.connect("NeverLandTownyElections", "ru.neverland.townyelections.api.TownyElectionsApi", 1, "elective");
        if (connection.state() == ru.neverland.core.ApiServices.State.NOT_INSTALLED) return false;
        try { return Boolean.TRUE.equals(connection.invoke("elective", new Class<?>[]{UUID.class}, town.getUUID())); }
        catch (ReflectiveOperationException | RuntimeException ex) { return true; } // Keep elected seats protected while an installed API is unavailable.
    }
    public Map<String, Integer> officeCatalog() { synchronized (ru.neverland.core.SuiteState.lock()) {
        electionThread(); Map<String, Integer> result = new java.util.LinkedHashMap<>();
        offices().forEach(o -> result.put(o.id(), o.maxHolders())); return Map.copyOf(result);
    } }
    public Map<String, List<UUID>> officeHolders(UUID id) { synchronized (ru.neverland.core.SuiteState.lock()) {
        electionThread(); Town town = towny.town(id); if (town == null) throw new IllegalArgumentException("Unknown town");
        Map<String, List<UUID>> result = new java.util.LinkedHashMap<>();
        data(town).offices().forEach((key, holders) -> result.put(key, holders.stream().map(OfficeHolder::residentId).toList()));
        return Map.copyOf(result);
    } }
    public String electionReceipt(UUID id) { synchronized (ru.neverland.core.SuiteState.lock()) {
        electionThread(); Town town = towny.town(id); if (town == null) throw new IllegalArgumentException("Unknown town"); return data(town).electionReceipt();
    } }
    public void applyElection(UUID id, UUID election, Map<String, List<UUID>> winners) { synchronized (ru.neverland.core.SuiteState.lock()) {
        electionThread(); java.util.Objects.requireNonNull(election);
        Town town = towny.town(id); if (town == null) throw new IllegalArgumentException("Unknown town");
        TownGovernanceData data = data(town);
        if (election.toString().equals(data.electionReceipt())) return;
        var next = new java.util.LinkedHashMap<String, List<OfficeHolder>>(); var seen = new java.util.HashSet<UUID>();
        long now = System.currentTimeMillis();
        for (var entry : winners.entrySet()) {
            var definition = definitions.office(entry.getKey());
            if (definition == null || entry.getValue().isEmpty() || entry.getValue().size() > definition.maxHolders()) throw new IllegalArgumentException("Invalid elected office");
            var holders = new ArrayList<OfficeHolder>();
            for (UUID resident : entry.getValue()) {
                if (!seen.add(resident) || !town.hasResident(resident) || !ru.neverland.core.CitizensAccess.allows(id, resident, "HOLD_OFFICE")) throw new IllegalArgumentException("Ineligible elected resident");
                holders.add(new OfficeHolder(resident, towny.resident(resident).getName(), now, election));
            }
            next.put(entry.getKey(), holders);
        }
        if (!plugin.getConfig().getBoolean("management.allow-multiple-offices", true)) {
            for (var entry : data.offices().entrySet()) if (!winners.containsKey(entry.getKey())
                    && entry.getValue().stream().anyMatch(h -> seen.contains(h.residentId()))) throw new IllegalArgumentException("Multiple offices disabled");
        }
        var old = new java.util.LinkedHashMap<>(data.offices()); var oldManaged = Set.copyOf(data.electedOffices());
        String oldReceipt = data.electionReceipt(); var oldHistory = new ArrayList<>(data.history());
        try {
            data.offices().putAll(next); data.electedOffices().addAll(next.keySet()); data.electionReceipt(election.toString());
            repository.history(data, new HistoryEntry(now, "ELECTION", "Итоги выборов " + election, election, "Выборы"));
            repository.save();
        } catch (RuntimeException ex) {
            data.offices().clear(); data.offices().putAll(old); data.electedOffices().clear(); data.electedOffices().addAll(oldManaged);
            data.electionReceipt(oldReceipt); data.history().clear(); data.history().addAll(oldHistory); throw ex;
        }
    } }

    public TownGovernanceData data(Town town) { synchronized (ru.neverland.core.SuiteState.lock()) { return repository.town(town.getUUID(), town.getName()); } }
    public List<Proposal> open(Town town) { synchronized (ru.neverland.core.SuiteState.lock()) { return town == null ? List.of() : repository.open(town.getUUID()); } }
    public Proposal find(Town town, String id) { synchronized (ru.neverland.core.SuiteState.lock()) { return town == null ? null : repository.findOpen(town.getUUID(), id); } }
    public Collection<LawDefinition> laws() { synchronized (ru.neverland.core.SuiteState.lock()) { return definitions.laws(); } }
    public Collection<OfficeDefinition> offices() { synchronized (ru.neverland.core.SuiteState.lock()) { return definitions.offices(); } }
    public LawDefinition law(String id) { synchronized (ru.neverland.core.SuiteState.lock()) { return definitions.law(id); } }
    public OfficeDefinition office(String id) { synchronized (ru.neverland.core.SuiteState.lock()) { return definitions.office(id); } }
    public boolean hasLaw(UUID townId, String lawId) { synchronized (ru.neverland.core.SuiteState.lock()) { TownGovernanceData data = repository.town(townId); return data != null && data.activeLaws().containsKey(lawId.toLowerCase(Locale.ROOT)); } }

    public double constructionCost(UUID townId) { synchronized (ru.neverland.core.SuiteState.lock()) { return multiplier(townId, Multiplier.CONSTRUCTION); } }
    public double ideologyCost(UUID townId) { synchronized (ru.neverland.core.SuiteState.lock()) { return multiplier(townId, Multiplier.IDEOLOGY_COST); } }
    public double ideologyExperience(UUID townId) { synchronized (ru.neverland.core.SuiteState.lock()) { return multiplier(townId, Multiplier.IDEOLOGY_EXPERIENCE); } }

    public boolean enact(Town town, LawDefinition law, UUID actorId, String actorName) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (town == null || law == null) return false; TownGovernanceData data = data(town);
        if (data.activeLaws().containsKey(law.id())) return false;
        if (!law.exclusiveGroup().isBlank()) {
            List<String> conflicts = data.activeLaws().keySet().stream().filter(id -> {
                LawDefinition active = definitions.law(id); return active != null && law.exclusiveGroup().equalsIgnoreCase(active.exclusiveGroup());
            }).toList();
            for (String conflict : conflicts) removeLaw(town, data, definitions.law(conflict), false);
        }
        if (law.effects().changesTax() && data.baselineTax() == null) data.baselineTax(town.getTaxes(), town.isTaxPercentage());
        applyEffects(town, law, true); data.activeLaws().put(law.id(), new ActiveLaw(law.id(), System.currentTimeMillis(), actorId, actorName));
        town.save(); repository.changed();
        repository.history(data, new HistoryEntry(System.currentTimeMillis(), "ENACT", "Принят закон «" + ColorUtil.strip(law.name()) + "»", actorId, actorName));
        return true;
    } }

    public boolean repeal(Town town, LawDefinition law, UUID actorId, String actorName) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (town == null || law == null) return false; TownGovernanceData data = data(town);
        if (!data.activeLaws().containsKey(law.id())) return false;
        removeLaw(town, data, law, true); town.save(); repository.changed();
        repository.history(data, new HistoryEntry(System.currentTimeMillis(), "REPEAL", "Отменён закон «" + ColorUtil.strip(law.name()) + "»", actorId, actorName));
        return true;
    } }

    public boolean resolveNow(String proposalPrefix) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Proposal match = null;
        for (Proposal proposal : repository.open()) if (proposal.id().toString().startsWith(proposalPrefix.toLowerCase(Locale.ROOT))) {
            if (match != null) return false; match = proposal;
        }
        if (match == null) return false; resolve(match, true); return true;
    } }

    private void tick() {
        cleanupOffices(); long now = System.currentTimeMillis();
        for (Proposal proposal : List.copyOf(repository.open())) if (!proposal.timer().paused() && proposal.endsAt() <= now) resolve(proposal, false);
    }

    private void resolve(Proposal proposal, boolean forced) {
        if (!ru.neverland.core.CitizensAccess.available()) return;
        Town town = towny.town(proposal.townId()); LawDefinition law = definitions.law(proposal.lawId());
        if (town == null || law == null) { proposal.status(ProposalStatus.CANCELLED); repository.remove(proposal); return; }
        Set<UUID> electorate = electorate(town); int yes = 0, no = 0, abstain = 0;
        for (Map.Entry<UUID, VoteChoice> vote : proposal.votes().entrySet()) if (electorate.contains(vote.getKey())) switch (vote.getValue()) {
            case YES -> yes++; case NO -> no++; case ABSTAIN -> abstain++;
        }
        double quorumRatio = plugin.getConfig().getDouble("voting.quorum", 0.5), approval = plugin.getConfig().getDouble("voting.approval", 0.5);
        int quorum = GovernanceMath.quorum(electorate.size(), quorumRatio); boolean passed = GovernanceMath.passed(yes, no, abstain, electorate.size(), quorumRatio, approval);
        if (forced && yes + no + abstain == 0) passed = false;
        if (passed) {
            boolean applied = proposal.action() == ProposalAction.ENACT ? enact(town, law, proposal.proposerId(), proposal.proposerName())
                    : repeal(town, law, proposal.proposerId(), proposal.proposerName());
            passed = applied;
        }
        proposal.status(passed ? ProposalStatus.PASSED : ProposalStatus.REJECTED); repository.remove(proposal);
        if (plugin.getConfig().getBoolean("messages.broadcast-resolution", true)) broadcast(town, passed ? "proposal-passed" : "proposal-rejected", Map.of(
                "action", actionText(proposal.action()), "law", law.name(), "yes", yes, "no", no, "abstain", abstain,
                "participation", yes + no + abstain, "quorum", quorum));
    }

    private void applyEffects(Town town, LawDefinition law, boolean enabling) {
        int direction = enabling ? 1 : -1; int blocks = law.effects().bonusClaimBlocks() * direction;
        if (blocks != 0) town.setBonusBlocks(Math.max(0, town.getBonusBlocks() + blocks));
        if (enabling && law.effects().changesTax()) { town.setTaxPercentage(law.effects().taxPercentage()); town.setTaxes(Math.max(0, law.effects().taxAmount())); }
    }

    private void removeLaw(Town town, TownGovernanceData data, LawDefinition law, boolean restoreTax) {
        if (law == null) return; data.activeLaws().remove(law.id()); applyEffects(town, law, false);
        if (restoreTax && law.effects().changesTax()) restoreTaxIfNeeded(town, data);
    }

    private void restoreTaxIfNeeded(Town town, TownGovernanceData data) {
        boolean otherTax = data.activeLaws().keySet().stream().map(definitions::law).filter(java.util.Objects::nonNull).anyMatch(value -> value.effects().changesTax());
        if (!otherTax && data.baselineTax() != null) {
            town.setTaxPercentage(Boolean.TRUE.equals(data.baselineTaxPercentage())); town.setTaxes(Math.max(0, data.baselineTax())); data.clearBaselineTax();
        }
    }

    private double multiplier(UUID townId, Multiplier type) {
        TownGovernanceData data = repository.town(townId); if (data == null) return 1.0; double value = 1.0;
        for (String id : data.activeLaws().keySet()) { LawDefinition law = definitions.law(id); if (law == null) continue;
            value *= switch (type) { case CONSTRUCTION -> law.effects().constructionCostMultiplier(); case IDEOLOGY_COST -> law.effects().ideologyCostMultiplier(); case IDEOLOGY_EXPERIENCE -> law.effects().ideologyExperienceMultiplier(); };
        }
        return GovernanceMath.clamp(value, plugin.getConfig().getDouble("modifiers.minimum", 0.1), plugin.getConfig().getDouble("modifiers.maximum", 5.0));
    }

    private void cleanupOffices() {
        for (TownGovernanceData data : repository.towns()) {
            Town town = towny.town(data.townId()); if (town == null) continue;
            for (List<OfficeHolder> holders : data.offices().values()) if (holders.removeIf(holder -> !isResident(town, holder.residentId()))) repository.changed();
        }
    }
    private boolean holdsAnyOffice(TownGovernanceData data, UUID resident) { return data.offices().values().stream().flatMap(List::stream).anyMatch(value -> value.residentId().equals(resident)); }
    private boolean isResident(Town town, UUID resident) { return town != null && town.hasResident(resident); }
    private void broadcast(Town town, String path, Map<String, ?> replacements) { towny.online(town).forEach(player -> messages.send(player, path, replacements)); }
    private String actionText(ProposalAction action) { return ColorUtil.strip(messages.raw(action == ProposalAction.ENACT ? "action-enact" : "action-repeal")); }
    public String choiceText(VoteChoice choice) { synchronized (ru.neverland.core.SuiteState.lock()) { return ColorUtil.strip(messages.raw(switch (choice) { case YES -> "choice-yes"; case NO -> "choice-no"; case ABSTAIN -> "choice-abstain"; })); } }
    private enum Multiplier { CONSTRUCTION, IDEOLOGY_COST, IDEOLOGY_EXPERIENCE }

    public java.util.List<ru.neverland.core.ActivityAdmin.Target> adminTargets() { synchronized (ru.neverland.core.SuiteState.lock()) {
        ru.neverland.core.ApiServices.primaryThread();
        return repository.open().stream().map(c -> new ru.neverland.core.ActivityAdmin.Target(c.id().toString(),c.townName()+" / "+c.lawId()+" / голосов "+c.votes().size(),
                c.status()==ProposalStatus.OPEN ? ru.neverland.core.ActivityAdmin.TIMED : java.util.Set.of("status"), (action,minutes) -> {
            ru.neverland.core.ApiServices.primaryThread();
            if(action.equals("status"))return c.id()+" | "+c.townName()+" / "+c.lawId()+" / голосов "+c.votes().size()+" | "+c.timer().describe(System.currentTimeMillis());
            if(!(c.status()==ProposalStatus.OPEN))throw new IllegalStateException("Задача уже завершается или ожидает расчёта; используйте штатную сверку");
            if(action.equals("cancel")){c.status(ProposalStatus.CANCELLED);repository.remove(c);repository.save(); return "Задача отменена без провала; выполненные действия сохранены";}
            var before=c.timer();
            try{c.timer(before.edit(action,minutes,System.currentTimeMillis())); repository.changed();repository.save();}
            catch(Exception ex){c.timer(before); throw ex;}
            return c.id()+" | "+c.timer().describe(System.currentTimeMillis());
        })).toList();
    } }

}
