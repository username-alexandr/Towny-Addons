package ru.neverland.mintcontracts.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcontracts.model.*;
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
    private final Map<UUID,MunicipalPayment> payments=new LinkedHashMap<>();
    private final Map<UUID,DeliveryIntent> deliveries=new LinkedHashMap<>();
    private boolean dirty;
    private boolean writable = true;
    public boolean writable() { return writable; }

    public ContractRepository(JavaPlugin plugin) { this(new File(plugin.getDataFolder(), "contract-data.yml"),plugin.getLogger()); }
    public ContractRepository(File file,java.util.logging.Logger logger) {this.file=file;this.logger=logger;}

    public synchronized void load() {
        active.clear(); history.clear(); pendingPlayers.clear(); pendingTowns.clear();payments.clear();deliveries.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.load(file); } catch (Exception ex) { writable=false; throw new IllegalStateException("contract-data.yml повреждён; операции остановлены", ex); }
        if(yaml.contains("schema")&&yaml.getInt("schema")!=2&&yaml.getInt("schema")!=3){writable=false;throw new IllegalStateException("Неизвестная схема контрактов");}
        for(String section:List.of("towns","pending","pending.players","pending.towns","payments","deliveries"))if(yaml.contains(section)&&!yaml.isConfigurationSection(section)){writable=false;throw new IllegalStateException("Повреждённый раздел "+section);}
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
                    if(yaml.contains(path+"funded")&&!yaml.isBoolean(path+"funded"))throw new IllegalArgumentException("Повреждён статус резерва");contract.funded(yaml.getBoolean(path+"funded",true));
                    if(yaml.contains(path+"terms"))contract.snapshot(ContractCodec.read(yaml.getConfigurationSection(path+"terms")));
                    if(yaml.contains(path+"area"))contract.area(ContractCodec.area(yaml.getConfigurationSection(path+"area")));
                    var proofs=yaml.getConfigurationSection(path+"proofs");
                    if(proofs!=null)for(String key:proofs.getKeys(false)){var proof=proofs.getConfigurationSection(key);if(proof==null||!(proof.get("at") instanceof Number))throw new IllegalArgumentException("Повреждено подтверждение работы");contract.proof(key,new WorkProof(UUID.fromString(proof.getString("actor","")),proof.getLong("at")));}
                    if(contract.snapshot()!=null&&(contract.snapshot().type()==ContractType.ROAD||contract.snapshot().type()==ContractType.SCOUT)&&contract.area()==null)throw new IllegalArgumentException("Нет участка задания");
                    String settlement=yaml.getString(path+"settlement-status", "");
                    if(!settlement.isEmpty())contract.settlement(ContractStatus.valueOf(settlement),yaml.getLong(path+"settlement-payout"),yaml.getLong(path+"settlement-refund"));
                    active.computeIfAbsent(townId, key -> new ArrayList<>()).add(contract);
                }
                List<ContractHistory> entries = new ArrayList<>();
                for (Map<?, ?> map : yaml.getMapList("towns." + townRaw + ".history")) {
                    entries.add(new ContractHistory(UUID.fromString(text(map.get("id"))), text(map.get("template")),
                            number(map.get("ended-at")), integer(map.get("progress")), integer(map.get("goal")),
                            decimal(map.get("paid")), decimal(map.get("refunded")),
                            ContractStatus.valueOf(text(map.get("status"))),map.get("name")==null?null:text(map.get("name"))));
                }
                history.put(townId, entries);
            } catch (RuntimeException exception) {
                writable=false; throw new IllegalStateException("Повреждённые данные контрактов города " + townRaw, exception);
            }
        }
        readMoney(yaml.getConfigurationSection("pending.players"), pendingPlayers);
        readMoney(yaml.getConfigurationSection("pending.towns"), pendingTowns);
        try {
            var money=yaml.getConfigurationSection("payments");
            if(money!=null)for(String key:money.getKeys(false)){var v=money.getConfigurationSection(key);if(v==null||!integral(v.get("cents"))||!integral(v.get("created")))throw new IllegalArgumentException("Повреждён платёж");UUID id=UUID.fromString(key);String c=v.getString("contract","");payments.put(id,new MunicipalPayment(id,c.isEmpty()?null:UUID.fromString(c),UUID.fromString(v.getString("town","")),UUID.fromString(v.getString("account","")),MunicipalPayment.Kind.valueOf(v.getString("kind","")),v.getLong("cents"),MunicipalPayment.Phase.valueOf(v.getString("phase","")),v.getLong("created")));}
            var items=yaml.getConfigurationSection("deliveries");
            if(items!=null)for(String key:items.getKeys(false)){var v=items.getConfigurationSection(key);if(v==null||!v.isInt("amount")||!integral(v.get("created"))||!v.isBoolean("acknowledged"))throw new IllegalArgumentException("Повреждена поставка");UUID id=UUID.fromString(key);deliveries.put(id,new DeliveryIntent(id,UUID.fromString(v.getString("contract","")),UUID.fromString(v.getString("town","")),UUID.fromString(v.getString("actor","")),v.getString("sample",""),v.getInt("amount"),DeliveryIntent.Phase.valueOf(v.getString("phase","")),v.getLong("created"),v.getBoolean("acknowledged")));}
            for(ActiveContract c:allActive())if(!c.funded()&&payments.values().stream().noneMatch(m->c.id().equals(m.contract())&&m.kind()==MunicipalPayment.Kind.RESERVE&&(m.phase()==MunicipalPayment.Phase.READY||m.phase()==MunicipalPayment.Phase.PENDING)))throw new IllegalArgumentException("Нет резерва неоплаченного задания");
            for(DeliveryIntent d:deliveries.values())if(d.phase()==DeliveryIntent.Phase.PLAYER_PENDING||d.phase()==DeliveryIntent.Phase.TAKEN){ActiveContract c=find(d.town(),d.contract().toString());if(c==null||!c.funded()||c.settlementStatus()!=null)throw new IllegalArgumentException("Поставка без действующего контракта");}
        }catch(RuntimeException ex){writable=false;throw new IllegalStateException("Повреждён журнал операций контрактов",ex);}
        dirty = false;
    }

    private static boolean integral(Object value){return value instanceof Integer||value instanceof Long;}
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
    public synchronized void add(ActiveContract contract) { if(allActive().stream().anyMatch(c->c.id().equals(contract.id())))throw new IllegalArgumentException("ID контракта уже занят");active.computeIfAbsent(contract.townId(), key -> new ArrayList<>()).add(contract); dirty = true; }
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
        entries.removeIf(entry->entry.contractId().equals(contract.id()));
        entries.add(0, new ContractHistory(contract.id(), contract.templateId(), now, contract.progress(), contract.goal(), paid, refunded, status, contract.snapshot()==null?null:contract.snapshot().name()));
        while (entries.size() > Math.max(1, limit)) entries.remove(entries.size() - 1);
        dirty = true;
    }
    public synchronized double pendingPlayer(UUID id) { return pendingPlayers.getOrDefault(id, 0.0)+payments.values().stream().filter(p->p.kind()==MunicipalPayment.Kind.REWARD&&p.account().equals(id)&&(p.phase()==MunicipalPayment.Phase.READY||p.phase()==MunicipalPayment.Phase.PENDING)).mapToLong(MunicipalPayment::cents).sum()/100.0; }
    public synchronized Map<UUID,Double> legacyPlayers(){return Map.copyOf(pendingPlayers);}
    public synchronized Map<UUID,MunicipalPayment> payments(){return Map.copyOf(payments);}
    public synchronized void payment(MunicipalPayment p){var old=payments.get(p.id());if(old!=null&&(!java.util.Objects.equals(old.contract(),p.contract())||!old.town().equals(p.town())||!old.account().equals(p.account())||old.cents()!=p.cents()||old.kind()!=p.kind()))throw new IllegalArgumentException("Условия платежа изменены");payments.put(p.id(),p);dirty=true;}
    public synchronized Map<UUID,DeliveryIntent> deliveries(){return Map.copyOf(deliveries);}
    public synchronized void delivery(DeliveryIntent d){var old=deliveries.get(d.id());if(old!=null&&(!old.contract().equals(d.contract())||!old.town().equals(d.town())||!old.actor().equals(d.actor())||!old.sample().equals(d.sample())||old.amount()!=d.amount()||old.created()!=d.created()))throw new IllegalArgumentException("Условия поставки изменены");deliveries.put(d.id(),d);dirty=true;}
    public synchronized boolean deliveryBusy(UUID contract){return deliveries.values().stream().anyMatch(d->d.contract().equals(contract)&&(d.phase()==DeliveryIntent.Phase.PLAYER_PENDING||d.phase()==DeliveryIntent.Phase.TAKEN));}
    public synchronized void saveOrThrow()throws IOException{if(!save())throw new IOException("Не удалось сохранить контракт. Операции остановлены");}
    public synchronized void addPendingPlayer(UUID id, double amount) { if (amount > 0) pendingPlayers.merge(id, amount, Double::sum); dirty = true; }
    public synchronized void clearPendingPlayer(UUID id) { pendingPlayers.remove(id); dirty = true; }
    public synchronized void addPendingTown(UUID id, double amount) { if (amount > 0) pendingTowns.merge(id, amount, Double::sum); dirty = true; }
    public synchronized Map<UUID, Double> pendingTowns() { return Map.copyOf(pendingTowns); }
    public synchronized void clearPendingTown(UUID id) { pendingTowns.remove(id); dirty = true; }
    public synchronized void saveIfDirty() { if (dirty) save(); }

    public synchronized boolean save() {
        if(!writable)return false;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema",3);yaml.createSection("towns");yaml.createSection("pending.players");yaml.createSection("pending.towns");
        for (ActiveContract contract : allActive()) {
            String path = "towns." + contract.townId() + ".active." + contract.id() + ".";
            yaml.set(path + "template", contract.templateId()); yaml.set(path + "created-at", contract.createdAt());
            yaml.set(path + "expires-at", contract.expiresAt()); yaml.set(path + "progress", contract.progress());
            yaml.set(path + "goal", contract.goal()); yaml.set(path + "escrow", contract.escrow());
            yaml.set(path+"funded",contract.funded());
            if(contract.snapshot()!=null)ContractCodec.write(yaml.createSection(path+"terms"),contract.snapshot());
            if(contract.area()!=null)ContractCodec.write(yaml.createSection(path+"area"),contract.area());
            for(var proof:contract.proofs().entrySet()){String p=path+"proofs."+proof.getKey()+".";yaml.set(p+"actor",proof.getValue().actor().toString());yaml.set(p+"at",proof.getValue().placedAt());}
            yaml.set(path+"company",contract.companyId()==null?"":contract.companyId().toString());
            yaml.set(path+"settlement-status",contract.settlementStatus()==null?"":contract.settlementStatus().name());
            yaml.set(path+"settlement-payout",contract.settlementPayout());yaml.set(path+"settlement-refund",contract.settlementRefund());
            contract.contributions().forEach((player, amount) -> yaml.set(path + "contributions." + player, amount));
        }
        history.forEach((town, entries) -> {
            List<Map<String, Object>> list = new ArrayList<>();
            for (ContractHistory entry : entries) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", entry.contractId().toString()); map.put("template", entry.templateId()); map.put("name",entry.name());
                map.put("ended-at", entry.endedAt()); map.put("progress", entry.progress()); map.put("goal", entry.goal());
                map.put("paid", entry.paid()); map.put("refunded", entry.refunded()); map.put("status", entry.status().name());
                list.add(map);
            }
            yaml.set("towns." + town + ".history", list);
        });
        pendingPlayers.forEach((id, amount) -> yaml.set("pending.players." + id, amount));
        pendingTowns.forEach((id, amount) -> yaml.set("pending.towns." + id, amount));
        yaml.createSection("payments");
        for(var p:payments.values()){var v=yaml.createSection("payments."+p.id());v.set("contract",p.contract()==null?"":p.contract().toString());v.set("town",p.town().toString());v.set("account",p.account().toString());v.set("kind",p.kind().name());v.set("cents",p.cents());v.set("phase",p.phase().name());v.set("created",p.created());}
        yaml.createSection("deliveries");
        for(var d:deliveries.values()){var v=yaml.createSection("deliveries."+d.id());v.set("contract",d.contract().toString());v.set("town",d.town().toString());v.set("actor",d.actor().toString());v.set("sample",d.sample());v.set("amount",d.amount());v.set("phase",d.phase().name());v.set("created",d.created());v.set("acknowledged",d.acknowledged());}
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
