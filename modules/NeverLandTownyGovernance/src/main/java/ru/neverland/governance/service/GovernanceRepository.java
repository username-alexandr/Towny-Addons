package ru.neverland.governance.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.governance.model.ActiveLaw;
import ru.neverland.governance.model.HistoryEntry;
import ru.neverland.governance.model.OfficeHolder;
import ru.neverland.governance.model.Proposal;
import ru.neverland.governance.model.ProposalAction;
import ru.neverland.governance.model.ProposalStatus;
import ru.neverland.governance.model.TownGovernanceData;
import ru.neverland.governance.model.VoteChoice;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class GovernanceRepository {
    private boolean ready;
    private void loaded(){ru.neverland.core.AtomicFiles.loaded(file.toPath());ready=true;}
    private void gate(){if(!ready||!ru.neverland.core.AtomicFiles.writable(file.toPath()))throw new IllegalStateException("Хранилище заблокировано: восстановите данные и перезапустите сервер");}

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, TownGovernanceData> towns = new LinkedHashMap<>();
    private final Map<UUID, Proposal> proposals = new LinkedHashMap<>();
    private boolean dirty;

    public GovernanceRepository(JavaPlugin plugin) { this.plugin = plugin; this.file = new File(plugin.getDataFolder(), "data.yml"); }

    public void load() {ready=false;
        towns.clear(); proposals.clear();

        YamlConfiguration yaml = ru.neverland.core.SafeYaml.load(file.toPath());ru.neverland.core.SafeYaml.keys(yaml,"towns","proposals");
        ConfigurationSection townRoot = ru.neverland.core.SafeYaml.section(yaml,"towns");
        if (townRoot != null) for (String key : townRoot.getKeys(false)) loadTown(key, ru.neverland.core.SafeYaml.section(townRoot,key));
        ConfigurationSection proposalRoot = ru.neverland.core.SafeYaml.section(yaml,"proposals");
        if (proposalRoot != null) for (String key : proposalRoot.getKeys(false)) loadProposal(key, ru.neverland.core.SafeYaml.section(proposalRoot,key));
        dirty = false;
    loaded();}

    public TownGovernanceData town(UUID id, String name) {gate();
        TownGovernanceData data = towns.computeIfAbsent(id, key -> new TownGovernanceData(key, name));
        if (name != null && !name.equals(data.townName())) data.townName(name);
        return data;
    }
    public TownGovernanceData town(UUID id) {gate(); return towns.get(id); }
    public Collection<TownGovernanceData> towns() {gate(); return towns.values(); }
    public void add(Proposal proposal) {gate(); proposals.put(proposal.id(), proposal); changed(); }
    public void remove(Proposal proposal) {gate(); proposals.remove(proposal.id()); changed(); }
    public List<Proposal> open(UUID townId) {gate(); return proposals.values().stream().filter(value -> value.townId().equals(townId) && value.status() == ProposalStatus.OPEN).sorted(Comparator.comparingLong(Proposal::endsAt)).toList(); }
    public Collection<Proposal> open() {gate(); return proposals.values().stream().filter(value -> value.status() == ProposalStatus.OPEN).toList(); }
    public Proposal findOpen(UUID townId, String prefix) {gate();
        if (prefix == null || prefix.isBlank()) return null;
        String normalized = prefix.toLowerCase(Locale.ROOT);
        Proposal result = null;
        for (Proposal proposal : open(townId)) if (proposal.id().toString().toLowerCase(Locale.ROOT).startsWith(normalized)) {
            if (result != null) return null; result = proposal;
        }
        return result;
    }
    public void history(TownGovernanceData data, HistoryEntry entry) {gate();
        data.history().add(0, entry);
        int limit = Math.max(1, plugin.getConfig().getInt("storage.history-limit", 50));
        while (data.history().size() > limit) data.history().remove(data.history().size() - 1);
        changed();
    }
    public void changed() {gate(); dirty = true; }
    public void saveIfDirty() {gate(); if (dirty) save(); }

    public void save() {gate();
        YamlConfiguration yaml = new YamlConfiguration();
        for (TownGovernanceData data : towns.values()) saveTown(yaml, data);
        for (Proposal proposal : proposals.values()) if (proposal.status() == ProposalStatus.OPEN) saveProposal(yaml, proposal);
        try { ru.neverland.core.AtomicFiles.write(file.toPath(),yaml::saveToString); dirty = false; }
        catch(IOException exception){throw new java.io.UncheckedIOException(exception);}
    }

    private void loadTown(String key, ConfigurationSection section) {
        if (section == null) return;
        try {
            UUID id = UUID.fromString(key); TownGovernanceData data = new TownGovernanceData(id, ru.neverland.core.SafeYaml.stringValue(section,"name", key));
            if (section.contains("baseline-tax.amount")) data.baselineTax(ru.neverland.core.SafeYaml.doubleValue(section,"baseline-tax.amount"), ru.neverland.core.SafeYaml.booleanValue(section,"baseline-tax.percentage"));
            data.lastProposalAt(ru.neverland.core.SafeYaml.longValue(section,"last-proposal-at", 0));
            String receipt = ru.neverland.core.SafeYaml.stringValue(section, "election-receipt", "");
            if (!receipt.isEmpty()) UUID.fromString(receipt);
            data.electionReceipt(receipt);
            if (section.contains("elected-offices")) {
                Object raw = section.get("elected-offices");
                if (!(raw instanceof List<?> list) || list.stream().anyMatch(v -> !(v instanceof String))) throw new IllegalArgumentException("elected-offices");
                data.electedOffices().addAll(section.getStringList("elected-offices"));
            }
            ConfigurationSection laws = ru.neverland.core.SafeYaml.section(section,"active-laws");
            if (laws != null) for (String law : laws.getKeys(false)) data.activeLaws().put(law,
                    new ActiveLaw(law, ru.neverland.core.SafeYaml.longValue(laws,law + ".enacted-at"), uuid(ru.neverland.core.SafeYaml.stringValue(laws,law + ".enacted-by")), ru.neverland.core.SafeYaml.stringValue(laws,law + ".enacted-by-name", "—")));
            ConfigurationSection offices = ru.neverland.core.SafeYaml.section(section,"offices");
            if (offices != null) for (String office : offices.getKeys(false)) {
                List<OfficeHolder> holders = new ArrayList<>();
                for (Map<?, ?> map : ru.neverland.core.SafeYaml.maps(offices,office)) {
                    UUID resident = uuid(String.valueOf(map.get("uuid"))); if (resident == null) continue;
                    holders.add(new OfficeHolder(resident, String.valueOf(map.get("name")), number(map.get("appointed-at")), uuid(String.valueOf(map.get("appointed-by")))));
                }
                data.offices().put(office, holders);
            }
            for (Map<?, ?> map : ru.neverland.core.SafeYaml.maps(section,"history")) data.history().add(new HistoryEntry(number(map.get("timestamp")),
                    String.valueOf(map.get("type")), String.valueOf(map.get("description")), uuid(String.valueOf(map.get("actor-uuid"))), String.valueOf(map.get("actor"))));
            towns.put(id, data);
        } catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Повреждены сохранённые данные: GovernanceRepository"); }
    }

    private void loadProposal(String key, ConfigurationSection section) {
        if (section == null) return;
        try {
            UUID id = UUID.fromString(key); UUID townId = UUID.fromString(ru.neverland.core.SafeYaml.stringValue(section,"town-uuid", ""));
            Proposal proposal = new Proposal(id, townId, ru.neverland.core.SafeYaml.stringValue(section,"town", "—"), ru.neverland.core.SafeYaml.stringValue(section,"law", ""),
                    ProposalAction.valueOf(ru.neverland.core.SafeYaml.stringValue(section,"action", "ENACT")), uuid(ru.neverland.core.SafeYaml.stringValue(section,"proposer-uuid")),
                    ru.neverland.core.SafeYaml.stringValue(section,"proposer", "—"), ru.neverland.core.SafeYaml.longValue(section,"created-at"), ru.neverland.core.SafeYaml.longValue(section,"ends-at"), ProposalStatus.OPEN);
            proposal.timer(ru.neverland.core.ActivityTimer.read(section,"",proposal.timer()));
            Map<UUID, VoteChoice> votes = new LinkedHashMap<>();
            ConfigurationSection voteSection = ru.neverland.core.SafeYaml.section(section,"votes");
            if (voteSection != null) for (String voter : voteSection.getKeys(false)) {
                UUID uuid = uuid(voter); VoteChoice choice = VoteChoice.parse(ru.neverland.core.SafeYaml.stringValue(voteSection,voter));
                if (uuid != null && choice != null) votes.put(uuid, choice);
            }
            proposal.loadVotes(votes); proposals.put(id, proposal);
        } catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Повреждены сохранённые данные: GovernanceRepository"); }
    }

    private void saveTown(YamlConfiguration yaml, TownGovernanceData data) {
        String root = "towns." + data.townId(); yaml.set(root + ".name", data.townName()); yaml.set(root + ".last-proposal-at", data.lastProposalAt());
        yaml.set(root + ".election-receipt", data.electionReceipt());
        yaml.set(root + ".elected-offices", new ArrayList<>(data.electedOffices()));
        if (data.baselineTax() != null) { yaml.set(root + ".baseline-tax.amount", data.baselineTax()); yaml.set(root + ".baseline-tax.percentage", data.baselineTaxPercentage()); }
        for (ActiveLaw law : data.activeLaws().values()) {
            String path = root + ".active-laws." + law.lawId(); yaml.set(path + ".enacted-at", law.enactedAt());
            yaml.set(path + ".enacted-by", text(law.enactedBy())); yaml.set(path + ".enacted-by-name", law.enactedByName());
        }
        for (Map.Entry<String, List<OfficeHolder>> entry : data.offices().entrySet()) {
            List<Map<String, Object>> holders = new ArrayList<>();
            for (OfficeHolder holder : entry.getValue()) holders.add(Map.of("uuid", holder.residentId().toString(), "name", holder.residentName(),
                    "appointed-at", holder.appointedAt(), "appointed-by", text(holder.appointedBy())));
            yaml.set(root + ".offices." + entry.getKey(), holders);
        }
        List<Map<String, Object>> history = new ArrayList<>();
        for (HistoryEntry entry : data.history()) history.add(Map.of("timestamp", entry.timestamp(), "type", entry.type(), "description", entry.description(),
                "actor-uuid", text(entry.actorId()), "actor", entry.actorName() == null ? "—" : entry.actorName()));
        yaml.set(root + ".history", history);
    }

    private void saveProposal(YamlConfiguration yaml, Proposal proposal) {
        String root = "proposals." + proposal.id(); yaml.set(root + ".town-uuid", proposal.townId().toString()); yaml.set(root + ".town", proposal.townName());
        yaml.set(root + ".law", proposal.lawId()); yaml.set(root + ".action", proposal.action().name()); yaml.set(root + ".proposer-uuid", text(proposal.proposerId()));
        yaml.set(root + ".proposer", proposal.proposerName()); yaml.set(root + ".created-at", proposal.createdAt()); yaml.set(root + ".ends-at", proposal.endsAt()); proposal.timer().write(yaml,root+".");
        proposal.votes().forEach((voter, choice) -> yaml.set(root + ".votes." + voter, choice.name()));
    }

    private UUID uuid(String value) { try { return value == null || value.equals("null") ? null : UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; } }
    private long number(Object value) { return value instanceof Number number ? number.longValue() : 0L; }
    private String text(UUID value) { return value == null ? "" : value.toString(); }
}
