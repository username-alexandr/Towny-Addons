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
        UNKNOWN_PLAYER, NOT_RESIDENT, OFFICE_FULL, ALREADY_APPOINTED, NOT_APPOINTED, MULTIPLE_OFFICES
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

    public void start() {
        stopTasks();
        long resolvePeriod = Math.max(20L, plugin.getConfig().getLong("voting.resolution-interval-seconds", 20) * 20L);
        long savePeriod = Math.max(20L, plugin.getConfig().getLong("storage.autosave-seconds", 60) * 20L);
        resolver = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, resolvePeriod);
        autosave = Bukkit.getScheduler().runTaskTimer(plugin, repository::saveIfDirty, savePeriod, savePeriod);
    }

    public void shutdown() { stopTasks(); repository.save(); }
    private void stopTasks() { if (resolver != null) resolver.cancel(); if (autosave != null) autosave.cancel(); resolver = autosave = null; }

    public ProposalResult propose(Player player, String lawId, ProposalAction action) {
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
    }

    public VoteResult vote(Player player, String proposalId, VoteChoice choice) {
        Town town = towny.town(player); if (town == null) return new VoteResult(Result.NO_TOWN, null);
        if (!player.hasPermission("townygovernance.vote")) return new VoteResult(Result.NO_PERMISSION, null);
        Proposal proposal = repository.findOpen(town.getUUID(), proposalId);
        if (proposal == null) return new VoteResult(Result.UNKNOWN_PROPOSAL, null);
        if (!electorate(town).contains(player.getUniqueId())) return new VoteResult(Result.NOT_ELIGIBLE, proposal);
        if (proposal.voteOf(player.getUniqueId()) != null && !plugin.getConfig().getBoolean("voting.allow-change", true))
            return new VoteResult(Result.CHANGE_DISABLED, proposal);
        proposal.vote(player.getUniqueId(), choice); repository.changed();
        if (System.currentTimeMillis() >= proposal.endsAt()) resolve(proposal, false);
        return new VoteResult(Result.SUCCESS, proposal);
    }

    public Result appoint(Player actor, String officeId, String residentName) {
        Town town = towny.town(actor); if (town == null) return Result.NO_TOWN;
        if (!actor.hasPermission("townygovernance.offices") || !towny.isManager(actor, town)) return Result.NO_PERMISSION;
        OfficeDefinition office = definitions.office(officeId); if (office == null) return Result.UNKNOWN_OFFICE;
        Resident resident = towny.resident(residentName); if (resident == null) return Result.UNKNOWN_PLAYER;
        if (!town.equals(resident.getTownOrNull())) return Result.NOT_RESIDENT;
        TownGovernanceData data = data(town); List<OfficeHolder> holders = data.offices().computeIfAbsent(office.id(), key -> new ArrayList<>());
        if (holders.stream().anyMatch(value -> value.residentId().equals(resident.getUUID()))) return Result.ALREADY_APPOINTED;
        if (holders.size() >= office.maxHolders()) return Result.OFFICE_FULL;
        if (!plugin.getConfig().getBoolean("management.allow-multiple-offices", true) && holdsAnyOffice(data, resident.getUUID())) return Result.MULTIPLE_OFFICES;
        holders.add(new OfficeHolder(resident.getUUID(), resident.getName(), System.currentTimeMillis(), actor.getUniqueId())); repository.changed();
        repository.history(data, new HistoryEntry(System.currentTimeMillis(), "APPOINT", resident.getName() + " назначен: "
                + ColorUtil.strip(office.name()), actor.getUniqueId(), actor.getName()));
        return Result.SUCCESS;
    }

    public Result dismiss(Player actor, String officeId, String residentName) {
        Town town = towny.town(actor); if (town == null) return Result.NO_TOWN;
        if (!actor.hasPermission("townygovernance.offices") || !towny.isManager(actor, town)) return Result.NO_PERMISSION;
        OfficeDefinition office = definitions.office(officeId); if (office == null) return Result.UNKNOWN_OFFICE;
        TownGovernanceData data = data(town); List<OfficeHolder> holders = data.offices().getOrDefault(office.id(), new ArrayList<>());
        OfficeHolder holder = holders.stream().filter(value -> value.residentName().equalsIgnoreCase(residentName)).findFirst().orElse(null);
        if (holder == null) return Result.NOT_APPOINTED;
        holders.remove(holder); repository.changed();
        repository.history(data, new HistoryEntry(System.currentTimeMillis(), "DISMISS", holder.residentName() + " снят: "
                + ColorUtil.strip(office.name()), actor.getUniqueId(), actor.getName()));
        return Result.SUCCESS;
    }

    public boolean mayPropose(Player player, Town town, LawCategory category) {
        if (player.hasPermission("townygovernance.bypass") || towny.isManager(player, town)) return true;
        TownGovernanceData data = data(town);
        for (Map.Entry<String, List<OfficeHolder>> entry : data.offices().entrySet()) {
            OfficeDefinition office = definitions.office(entry.getKey()); if (office == null || !office.mayPropose(category)) continue;
            if (entry.getValue().stream().anyMatch(value -> value.residentId().equals(player.getUniqueId()))) return true;
        }
        return false;
    }

    public Set<UUID> council(Town town) {
        Set<UUID> result = new LinkedHashSet<>(towny.townRankCouncil(town));
        TownGovernanceData data = data(town);
        for (Map.Entry<String, List<OfficeHolder>> entry : data.offices().entrySet()) {
            OfficeDefinition office = definitions.office(entry.getKey()); if (office == null || !office.councilMember()) continue;
            for (OfficeHolder holder : entry.getValue()) if (isResident(town, holder.residentId())) result.add(holder.residentId());
        }
        return result;
    }

    public Set<UUID> electorate(Town town) {
        if (plugin.getConfig().getString("voting.electorate", "COUNCIL").equalsIgnoreCase("ALL_RESIDENTS")) {
            Set<UUID> result = new LinkedHashSet<>(); town.getResidents().forEach(resident -> result.add(resident.getUUID())); return result;
        }
        return council(town);
    }

    public TownGovernanceData data(Town town) { return repository.town(town.getUUID(), town.getName()); }
    public List<Proposal> open(Town town) { return town == null ? List.of() : repository.open(town.getUUID()); }
    public Proposal find(Town town, String id) { return town == null ? null : repository.findOpen(town.getUUID(), id); }
    public Collection<LawDefinition> laws() { return definitions.laws(); }
    public Collection<OfficeDefinition> offices() { return definitions.offices(); }
    public LawDefinition law(String id) { return definitions.law(id); }
    public OfficeDefinition office(String id) { return definitions.office(id); }
    public boolean hasLaw(UUID townId, String lawId) { TownGovernanceData data = repository.town(townId); return data != null && data.activeLaws().containsKey(lawId.toLowerCase(Locale.ROOT)); }

    public double constructionCost(UUID townId) { return multiplier(townId, Multiplier.CONSTRUCTION); }
    public double ideologyCost(UUID townId) { return multiplier(townId, Multiplier.IDEOLOGY_COST); }
    public double ideologyExperience(UUID townId) { return multiplier(townId, Multiplier.IDEOLOGY_EXPERIENCE); }

    public boolean enact(Town town, LawDefinition law, UUID actorId, String actorName) {
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
    }

    public boolean repeal(Town town, LawDefinition law, UUID actorId, String actorName) {
        if (town == null || law == null) return false; TownGovernanceData data = data(town);
        if (!data.activeLaws().containsKey(law.id())) return false;
        removeLaw(town, data, law, true); town.save(); repository.changed();
        repository.history(data, new HistoryEntry(System.currentTimeMillis(), "REPEAL", "Отменён закон «" + ColorUtil.strip(law.name()) + "»", actorId, actorName));
        return true;
    }

    public boolean resolveNow(String proposalPrefix) {
        Proposal match = null;
        for (Proposal proposal : repository.open()) if (proposal.id().toString().startsWith(proposalPrefix.toLowerCase(Locale.ROOT))) {
            if (match != null) return false; match = proposal;
        }
        if (match == null) return false; resolve(match, true); return true;
    }

    private void tick() {
        cleanupOffices(); long now = System.currentTimeMillis();
        for (Proposal proposal : List.copyOf(repository.open())) if (proposal.endsAt() <= now) resolve(proposal, false);
    }

    private void resolve(Proposal proposal, boolean forced) {
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
    public String choiceText(VoteChoice choice) { return ColorUtil.strip(messages.raw(switch (choice) { case YES -> "choice-yes"; case NO -> "choice-no"; case ABSTAIN -> "choice-abstain"; })); }
    private enum Multiplier { CONSTRUCTION, IDEOLOGY_COST, IDEOLOGY_EXPERIENCE }
}
