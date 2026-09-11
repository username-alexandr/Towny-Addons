package ru.neverland.mintcontracts.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcontracts.model.ActiveContract;
import ru.neverland.mintcontracts.model.ContractHistory;
import ru.neverland.mintcontracts.model.ContractStatus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ContractRepository {
    private final java.util.logging.Logger logger;
    private final File file;
    private final Map<UUID, List<ActiveContract>> active = new LinkedHashMap<>();
    private final Map<UUID, List<ContractHistory>> history = new LinkedHashMap<>();
    private final Map<UUID, Double> pendingPlayers = new LinkedHashMap<>();
    private final Map<UUID, Double> pendingTowns = new LinkedHashMap<>();
    private boolean dirty;
    private boolean writable = true;
    public boolean writable() { return writable; }

    public ContractRepository(JavaPlugin plugin) { this(new File(plugin.getDataFolder(), "contract-data.yml"),plugin.getLogger()); }
    public ContractRepository(File file,java.util.logging.Logger logger) {this.file=file;this.logger=logger;}

    public synchronized void load() {
        active.clear(); history.clear(); pendingPlayers.clear(); pendingTowns.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.load(file); } catch (Exception ex) { writable=false; throw new IllegalStateException("contract-data.yml повреждён; операции остановлены", ex); }
        if(yaml.contains("schema")&&yaml.getInt("schema")!=2){writable=false;throw new IllegalStateException("Неизвестная схема контрактов");}
        for(String section:List.of("towns","pending","pending.players","pending.towns"))if(yaml.contains(section)&&!yaml.isConfigurationSection(section)){writable=false;throw new IllegalStateException("Повреждённый раздел "+section);}
        ConfigurationSection root = yaml.getConfigurationSection("towns");
        if (root != null) for (String townRaw : root.getKeys(false)) {
            try {
                UUID townId = UUID.fromString(townRaw);
                if(!root.isConfigurationSection(townRaw)||root.contains(townRaw+".active")&&!root.isConfigurationSection(townRaw+".active"))throw new IllegalArgumentException("Повреждённый город");
                ConfigurationSection contracts = root.getConfigurationSection(townRaw + ".active");
                if (contracts != null) for (String contractRaw : contracts.getKeys(false)) {
                    String path = "towns." + townRaw + ".active." + contractRaw + ".";
                    if(!yaml.isConfigurationSection(path.substring(0,path.length()-1))||!yaml.isString(path+"template")||!yaml.isLong(path+"created-at")&&!yaml.isInt(path+"created-at")||!yaml.isLong(path+"expires-at")&&!yaml.isInt(path+"expires-at")||!yaml.isInt(path+"goal")||!yaml.isInt(path+"progress")||!(yaml.get(path+"escrow") instanceof Number))throw new IllegalArgumentException("Повреждённые условия контракта");
                    Map<UUID, Integer> contributions = new LinkedHashMap<>();
                    ConfigurationSection parts = yaml.getConfigurationSection(path + "contributions");
                    if (parts != null) for (String playerRaw : parts.getKeys(false))
                        contributions.put(UUID.fromString(playerRaw), parts.getInt(playerRaw));
                    ActiveContract contract = new ActiveContract(UUID.fromString(contractRaw), townId,
                            yaml.getString(path + "template", ""), yaml.getLong(path + "created-at"),
                            yaml.getLong(path + "expires-at"), yaml.getInt(path + "progress"),
                            yaml.getInt(path + "goal", 1), yaml.getDouble(path + "escrow"), contributions);
                    if (!Double.isFinite(contract.escrow()) || contract.escrow()>1_000_000_000.0 || yaml.getDouble(path+"escrow")<0 || yaml.getInt(path+"goal")<1 || yaml.getInt(path+"progress")<0 || contract.progress()>contract.goal() || contributions.values().stream().anyMatch(n->n<=0) || contributions.values().stream().mapToLong(Integer::longValue).sum()!=contract.progress()) throw new IllegalArgumentException("Некорректные условия или прогресс");
                    String company=yaml.getString(path+"company", "");
                    if(!company.isEmpty())contract.restoreCompany(UUID.fromString(company));
                    String settlement=yaml.getString(path+"settlement-status", "");
                    if(!settlement.isEmpty())contract.settlement(ContractStatus.valueOf(settlement),yaml.getLong(path+"settlement-payout"),yaml.getLong(path+"settlement-refund"));
                    active.computeIfAbsent(townId, key -> new ArrayList<>()).add(contract);
                }
                List<ContractHistory> entries = new ArrayList<>();
                for (Map<?, ?> map : yaml.getMapList("towns." + townRaw + ".history")) {
                    entries.add(new ContractHistory(UUID.fromString(text(map.get("id"))), text(map.get("template")),
                            number(map.get("ended-at")), integer(map.get("progress")), integer(map.get("goal")),
                            decimal(map.get("paid")), decimal(map.get("refunded")),
                            ContractStatus.valueOf(text(map.get("status")))));
                }
                history.put(townId, entries);
            } catch (RuntimeException exception) {
                writable=false; throw new IllegalStateException("Повреждённые данные контрактов города " + townRaw, exception);
            }
        }
        readMoney(yaml.getConfigurationSection("pending.players"), pendingPlayers);
        readMoney(yaml.getConfigurationSection("pending.towns"), pendingTowns);
        dirty = false;
    }

    private void readMoney(ConfigurationSection section, Map<UUID, Double> target) {
        if (section == null) return;
        for (String raw : section.getKeys(false)) try { double amount=section.getDouble(raw); if(!section.isDouble(raw)&&!section.isInt(raw)&&!section.isLong(raw)||!Double.isFinite(amount)||amount<0)throw new IllegalArgumentException("Некорректный остаток"); target.put(UUID.fromString(raw), amount); }
        catch (IllegalArgumentException ex) { writable=false; throw new IllegalStateException("Повреждённый остаток контракта",ex); }
    }
    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private long number(Object value) { return value instanceof Number number ? number.longValue() : 0; }
    private int integer(Object value) { return value instanceof Number number ? number.intValue() : 0; }
    private double decimal(Object value) { return value instanceof Number number ? number.doubleValue() : 0; }

    public synchronized List<ActiveContract> active(UUID townId) { return List.copyOf(active.getOrDefault(townId, List.of())); }
    public synchronized List<ActiveContract> allActive() { return active.values().stream().flatMap(List::stream).toList(); }
    public synchronized List<ContractHistory> history(UUID townId) { return List.copyOf(history.getOrDefault(townId, List.of())); }
    public synchronized void add(ActiveContract contract) { active.computeIfAbsent(contract.townId(), key -> new ArrayList<>()).add(contract); dirty = true; }
    public synchronized void remove(ActiveContract contract) {
        List<ActiveContract> list = active.get(contract.townId());
        if (list != null) { list.removeIf(item -> item.id().equals(contract.id())); if (list.isEmpty()) active.remove(contract.townId()); }
        dirty = true;
    }
    public synchronized ActiveContract find(UUID townId, String id) {
        for (ActiveContract contract : active(townId))
            if (contract.id().toString().equalsIgnoreCase(id) || contract.shortId().equalsIgnoreCase(id)) return contract;
        return null;
    }
    public synchronized boolean hasTemplate(UUID townId, String templateId) {
        return active(townId).stream().anyMatch(contract -> contract.templateId().equalsIgnoreCase(templateId));
    }
    public synchronized void changed() { dirty = true; }
    public synchronized void addHistory(ActiveContract contract, ContractStatus status, double paid, double refunded, int limit, long now) {
        remove(contract);
        List<ContractHistory> entries = history.computeIfAbsent(contract.townId(), key -> new ArrayList<>());
        entries.add(0, new ContractHistory(contract.id(), contract.templateId(), now, contract.progress(), contract.goal(), paid, refunded, status));
        while (entries.size() > Math.max(1, limit)) entries.remove(entries.size() - 1);
        dirty = true;
    }
    public synchronized double pendingPlayer(UUID id) { return pendingPlayers.getOrDefault(id, 0.0); }
    public synchronized void addPendingPlayer(UUID id, double amount) { if (amount > 0) pendingPlayers.merge(id, amount, Double::sum); dirty = true; }
    public synchronized void clearPendingPlayer(UUID id) { pendingPlayers.remove(id); dirty = true; }
    public synchronized void addPendingTown(UUID id, double amount) { if (amount > 0) pendingTowns.merge(id, amount, Double::sum); dirty = true; }
    public synchronized Map<UUID, Double> pendingTowns() { return Map.copyOf(pendingTowns); }
    public synchronized void clearPendingTown(UUID id) { pendingTowns.remove(id); dirty = true; }
    public synchronized void saveIfDirty() { if (dirty) save(); }

    public synchronized boolean save() {
        if(!writable)return false;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema",2);yaml.createSection("towns");yaml.createSection("pending.players");yaml.createSection("pending.towns");
        for (ActiveContract contract : allActive()) {
            String path = "towns." + contract.townId() + ".active." + contract.id() + ".";
            yaml.set(path + "template", contract.templateId()); yaml.set(path + "created-at", contract.createdAt());
            yaml.set(path + "expires-at", contract.expiresAt()); yaml.set(path + "progress", contract.progress());
            yaml.set(path + "goal", contract.goal()); yaml.set(path + "escrow", contract.escrow());
            yaml.set(path+"company",contract.companyId()==null?"":contract.companyId().toString());
            yaml.set(path+"settlement-status",contract.settlementStatus()==null?"":contract.settlementStatus().name());
            yaml.set(path+"settlement-payout",contract.settlementPayout());yaml.set(path+"settlement-refund",contract.settlementRefund());
            contract.contributions().forEach((player, amount) -> yaml.set(path + "contributions." + player, amount));
        }
        history.forEach((town, entries) -> {
            List<Map<String, Object>> list = new ArrayList<>();
            for (ContractHistory entry : entries) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", entry.contractId().toString()); map.put("template", entry.templateId());
                map.put("ended-at", entry.endedAt()); map.put("progress", entry.progress()); map.put("goal", entry.goal());
                map.put("paid", entry.paid()); map.put("refunded", entry.refunded()); map.put("status", entry.status().name());
                list.add(map);
            }
            yaml.set("towns." + town + ".history", list);
        });
        pendingPlayers.forEach((id, amount) -> yaml.set("pending.players." + id, amount));
        pendingTowns.forEach((id, amount) -> yaml.set("pending.towns." + id, amount));
        try {
            java.nio.file.Path target=file.toPath().toAbsolutePath();java.nio.file.Files.createDirectories(target.getParent());
            var tmp=java.nio.file.Files.createTempFile(target.getParent(),"contracts-",".tmp");
            try {yaml.save(tmp.toFile());try(var channel=java.nio.channels.FileChannel.open(tmp,java.nio.file.StandardOpenOption.WRITE)){channel.force(true);}
                try{java.nio.file.Files.move(tmp,target,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
                catch(java.nio.file.AtomicMoveNotSupportedException ex){java.nio.file.Files.move(tmp,target,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            }finally{java.nio.file.Files.deleteIfExists(tmp);}
            dirty = false; return true; }
        catch (IOException|RuntimeException exception) { writable=false; logger.severe("Не удалось сохранить contract-data.yml: " + exception.getMessage()); return false; }
    }
}
