package ru.neverland.mintcontracts.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.mintcontracts.api.ContractSnapshot;
import ru.neverland.mintcontracts.api.MintTownyContractsApi;
import ru.neverland.mintcontracts.integration.TownyHook;
import ru.neverland.mintcontracts.integration.WarehouseBridge;
import ru.neverland.mintcontracts.model.ActiveContract;
import ru.neverland.mintcontracts.model.ContractDefinition;
import ru.neverland.mintcontracts.model.ContractStatus;
import ru.neverland.mintcontracts.model.ContractType;
import ru.neverland.mintcontracts.util.ColorUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ContractService implements MintTownyContractsApi {
    public enum ActivateResult { SUCCESS, MAX_ACTIVE, DUPLICATE, NO_MONEY, ECONOMY_ERROR, WAREHOUSE_UNAVAILABLE, SAVE_ERROR }
    public record DeliveryResult(WarehouseBridge.Status status, int amount, int progress) {}

    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final ContractRegistry registry;
    private final ContractRepository repository;
    private final WarehouseBridge warehouse;
    private final EconomyService economy;
    private final MessageService messages;
    private BukkitTask task;
    private RussianNames companyNames;
    private final ru.neverland.mintcontracts.integration.CompaniesBridge companies = new ru.neverland.mintcontracts.integration.CompaniesBridge();

    public ContractService(JavaPlugin plugin, TownyHook towny, ContractRegistry registry, ContractRepository repository,
                           WarehouseBridge warehouse, EconomyService economy, MessageService messages) {
        this.plugin = plugin; this.towny = towny; this.registry = registry; this.repository = repository;
        this.warehouse = warehouse; this.economy = economy; this.messages = messages;
    }

    public void start() {
        if (task != null) task.cancel();
        long period = Math.max(10, plugin.getConfig().getLong("contracts.expiration-check-seconds", 30)) * 20;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }
    public void shutdown() { if (task != null) task.cancel(); task = null; repository.save(); }

    public ActivateResult activate(Town town, ContractDefinition definition) {
        if(!Bukkit.isPrimaryThread()||!repository.writable())return ActivateResult.SAVE_ERROR;
        if (town == null || definition == null) return ActivateResult.SAVE_ERROR;
        if (repository.active(town.getUUID()).size() >= plugin.getConfig().getInt("contracts.max-active-per-town", 3)) return ActivateResult.MAX_ACTIVE;
        if (!plugin.getConfig().getBoolean("contracts.allow-duplicate-template", false) && repository.hasTemplate(town.getUUID(), definition.id()))
            return ActivateResult.DUPLICATE;
        if (definition.type() == ContractType.DELIVERY && !warehouse.available()) return ActivateResult.WAREHOUSE_UNAVAILABLE;
        if (!economy.canReserve(town, definition.reward())) return ActivateResult.NO_MONEY;
        if (!economy.reserve(town, definition)) return ActivateResult.ECONOMY_ERROR;
        long now = System.currentTimeMillis();
        ActiveContract contract = new ActiveContract(UUID.randomUUID(), town.getUUID(), definition.id(), now,
                now + definition.durationSeconds() * 1000, 0, definition.goal(), definition.reward(), Map.of());
        repository.add(contract);
        if (!repository.save()) {
            repository.remove(contract);
            if (!economy.refund(town, definition.reward(), definition))
                repository.addPendingTown(town.getUUID(), definition.reward());
            repository.save();
            return ActivateResult.SAVE_ERROR;
        }
        announce(town, "contract-start-town", definition, contract, definition.reward());
        return ActivateResult.SUCCESS;
    }

    public DeliveryResult deliver(Player player, ActiveContract contract, boolean all) {
        if(!canContribute(contract,player.getUniqueId()))return new DeliveryResult(WarehouseBridge.Status.UNAVAILABLE,0,contract==null?0:contract.progress());
        Town town = towny.town(player);
        ContractDefinition definition = definition(contract);
        if (town == null || definition == null || definition.type() != ContractType.DELIVERY || !town.getUUID().equals(contract.townId()))
            return new DeliveryResult(WarehouseBridge.Status.UNAVAILABLE, 0, contract == null ? 0 : contract.progress());
        int found = count(player, definition.deliveryItem());
        if (found <= 0) return new DeliveryResult(WarehouseBridge.Status.SUCCESS, 0, contract.progress());
        int requested = Math.min(contract.goal() - contract.progress(), all ? found : Math.min(found, definition.deliveryItem().getMaxStackSize()));
        WarehouseBridge.Deposit deposit = warehouse.deposit(town.getUUID(), definition.deliveryItem(), requested);
        if (deposit.accepted() <= 0) return new DeliveryResult(deposit.status(), 0, contract.progress());
        remove(player, definition.deliveryItem(), deposit.accepted());
        addInternal(contract, player.getUniqueId(), deposit.accepted());
        return new DeliveryResult(deposit.status(), deposit.accepted(), Math.min(contract.goal(), contract.progress()));
    }

    public void record(Player player, ContractType type, String target, int amount) {
        Town town = towny.town(player);
        if (town == null || amount <= 0) return;
        for (ActiveContract contract : new ArrayList<>(repository.active(town.getUUID()))) {
            ContractDefinition definition = definition(contract);
            if (definition == null || definition.type() != type) continue;
            if (!definition.target().equalsIgnoreCase("ANY") && !definition.target().equalsIgnoreCase(target)) continue;
            addInternal(contract, player.getUniqueId(), amount);
        }
    }

    private boolean addInternal(ActiveContract contract, UUID contributor, int amount) {
        if (!canContribute(contract,contributor)) return false;
        int accepted = contract.add(contributor, amount);
        if (accepted <= 0) return false;
        repository.changed();
        if (contract.completed()) resolve(contract, ContractStatus.SUCCESS);
        else if(contract.companyId()!=null){if(!repository.save())return false;}else saveNow();
        return true;
    }

    public boolean cancel(Town town, ActiveContract contract) {
        if (!Bukkit.isPrimaryThread()||!repository.writable()||town == null || contract == null || !town.getUUID().equals(contract.townId())||repository.find(town.getUUID(),contract.id().toString())!=contract) return false;
        resolve(contract, ContractStatus.CANCELLED);
        return true;
    }

    private void tick() {
        if(!repository.writable())return;
        long now = System.currentTimeMillis();
        for (ActiveContract contract : new ArrayList<>(repository.allActive()))
            if (contract.settlementStatus()!=null) resolve(contract,contract.settlementStatus());
            else if(contract.completed())resolve(contract,ContractStatus.SUCCESS);
            else if (now >= contract.expiresAt()) resolve(contract, ContractStatus.EXPIRED);
        retryTownRefunds();
        repository.saveIfDirty();
    }

    private void resolve(ActiveContract contract, ContractStatus status) {
        if(!repository.writable())return;
        if(contract.companyId()!=null){resolveCompany(contract,status);return;}
        ContractDefinition definition = definition(contract);
        if (definition == null) {
            plugin.getLogger().warning("Шаблон " + contract.templateId() + " удалён; контракт " + contract.id()
                    + " будет закрыт по сохранённым условиям.");
            definition = fallback(contract);
        }
        boolean full = status == ContractStatus.SUCCESS;
        boolean partial = status == ContractStatus.EXPIRED
                ? plugin.getConfig().getBoolean("contracts.partial-payout-on-expire", true)
                : plugin.getConfig().getBoolean("contracts.partial-payout-on-cancel", true);
        double ratio = full ? 1.0 : partial ? contract.ratio() : 0.0;
        long escrowCents = Math.round(contract.escrow() * 100);
        long payoutCents = Math.min(escrowCents, Math.round(contract.escrow() * ratio * 100));
        Map<UUID, Long> shares = shares(contract.contributions(), payoutCents);
        for (Map.Entry<UUID, Long> share : shares.entrySet()) {
            double amount = share.getValue() / 100.0;
            if (!economy.pay(share.getKey(), amount, definition)) repository.addPendingPlayer(share.getKey(), amount);
        }
        double paid = payoutCents / 100.0;
        double refund = (escrowCents - payoutCents) / 100.0;
        Town town = towny.town(contract.townId());
        if (!economy.refund(town, refund, definition) && refund > 0) repository.addPendingTown(contract.townId(), refund);
        repository.addHistory(contract, status, paid, refund,
                plugin.getConfig().getInt("contracts.history-limit-per-town", 30), System.currentTimeMillis());
        saveNow();
        if (town != null) announce(town, status == ContractStatus.SUCCESS ? "contract-complete-town"
                        : status == ContractStatus.CANCELLED ? "contract-cancel-town" : "contract-expire-town",
                definition, contract, paid);
    }

    private boolean canContribute(ActiveContract c,UUID actor) {
        if(!Bukkit.isPrimaryThread()||!repository.writable()||c==null||actor==null||c.settlementStatus()!=null||System.currentTimeMillis()>=c.expiresAt()||repository.find(c.townId(),c.id().toString())!=c)return false;
        return c.companyId()==null||companies.allow("canContribute",actor,c.companyId(),c.townId());
    }
    private void resolveCompany(ActiveContract c,ContractStatus status) {
        if(c.settlementStatus()==null){
            boolean partial=status==ContractStatus.EXPIRED?plugin.getConfig().getBoolean("contracts.partial-payout-on-expire",true):plugin.getConfig().getBoolean("contracts.partial-payout-on-cancel",true);
            long total=Math.round(c.escrow()*100);long payout=status==ContractStatus.SUCCESS?total:partial?Math.min(total,Math.round(total*c.ratio())):0;
            c.settlement(status,payout,total-payout);repository.changed();if(!repository.save())return;
        }
        if(!companies.settle(c.id(),c.companyId(),c.townId(),c.settlementPayout(),c.settlementRefund()))return;
        repository.addHistory(c,c.settlementStatus(),c.settlementPayout()/100.0,c.settlementRefund()/100.0,plugin.getConfig().getInt("contracts.history-limit-per-town",30),System.currentTimeMillis());
        if(!repository.save())return;
        Town town=towny.town(c.townId());ContractDefinition d=definition(c);if(d==null)d=fallback(c);
        if(town!=null)announce(town,c.settlementStatus()==ContractStatus.SUCCESS?"contract-complete-town":c.settlementStatus()==ContractStatus.CANCELLED?"contract-cancel-town":"contract-expire-town",d,c,c.settlementPayout()/100.0);
    }
    public boolean companyContributor(Player p,ActiveContract c){return canContribute(c,p.getUniqueId());}
    public int companyActiveCount(UUID company){return (int)repository.allActive().stream().filter(c->company.equals(c.companyId())).count();}
    public String companyName(ActiveContract c){return c.companyId()==null?"Жители города":companies.name(c.companyId());}
    public List<Map<String,Object>> companyOffers(UUID town) {
        if(!Bukkit.isPrimaryThread()||!repository.writable())throw new IllegalStateException("Контракты временно недоступны");
        List<Map<String,Object>> result=new ArrayList<>();
        if(companyNames==null)companyNames=new RussianNames(plugin);
        var names=companyNames;
        for(ActiveContract c:active(town)) {
            var d=definition(c);if(d==null)continue;
            Map<String,Object> row=new LinkedHashMap<>();row.put("id",c.id().toString());row.put("name",ColorUtil.strip(d.name()));row.put("company",c.companyId()==null?"":c.companyId().toString());row.put("assignee",companyName(c));
            row.put("progress",c.progress());row.put("goal",c.goal());row.put("reward",economy.format(c.escrow()));row.put("expires",c.expiresAt());
            row.put("target",d.type()==ContractType.DELIVERY?names.item(d.deliveryItem()):d.target().equalsIgnoreCase("ANY")?"Любая подходящая цель":names.value(d.target()));result.add(Map.copyOf(row));
        }return List.copyOf(result);
    }
    public boolean takeCompanyContract(Player p,UUID company,UUID id) {
        if(!Bukkit.isPrimaryThread()||!repository.writable())return false;
        Town town=towny.town(p);if(town==null)return false;ActiveContract c=find(town.getUUID(),id.toString());
        if(c==null||c.companyId()!=null||c.progress()!=0||c.settlementStatus()!=null||System.currentTimeMillis()>=c.expiresAt()||!companies.allow("canTake",p.getUniqueId(),company,town.getUUID()))return false;
        c.companyId(company);repository.changed();return repository.save();
    }
    public boolean releaseCompanyContract(Player p,UUID company,UUID id) {
        if(!Bukkit.isPrimaryThread()||!repository.writable())return false;
        Town town=towny.town(p);if(town==null)return false;ActiveContract c=find(town.getUUID(),id.toString());
        if(c==null||!company.equals(c.companyId())||c.progress()!=0||c.settlementStatus()!=null||System.currentTimeMillis()>=c.expiresAt()||!companies.allow("canManage",p.getUniqueId(),company,town.getUUID()))return false;
        c.companyId(null);repository.changed();return repository.save();
    }

    private Map<UUID, Long> shares(Map<UUID, Integer> contributions, long totalCents) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        if (totalCents <= 0 || contributions.isEmpty()) return result;
        long totalUnits = contributions.values().stream().mapToLong(Integer::longValue).sum();
        List<Map.Entry<UUID, Integer>> entries = contributions.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder())).toList();
        long remaining = totalCents;
        for (int index = 0; index < entries.size(); index++) {
            Map.Entry<UUID, Integer> entry = entries.get(index);
            long cents = index == entries.size() - 1 ? remaining : (totalCents * entry.getValue()) / totalUnits;
            if (cents > 0) result.put(entry.getKey(), cents);
            remaining -= cents;
        }
        return result;
    }

    public double claim(Player player) {
        if(!Bukkit.isPrimaryThread()||!repository.writable())return -1;
        double amount = repository.pendingPlayer(player.getUniqueId());
        if (amount <= 0) return 0;
        ContractDefinition fallback = registry.all().stream().findFirst().orElse(null);
        if (fallback == null || !economy.pay(player.getUniqueId(), amount, fallback)) return -1;
        repository.clearPendingPlayer(player.getUniqueId());
        saveNow();
        return amount;
    }

    private void retryTownRefunds() {
        if(!repository.writable())return;
        ContractDefinition fallback = registry.all().stream().findFirst().orElse(null);
        if (fallback == null) return;
        for (Map.Entry<UUID, Double> entry : repository.pendingTowns().entrySet()) {
            Town town = towny.town(entry.getKey());
            if (economy.refund(town, entry.getValue(), fallback)) repository.clearPendingTown(entry.getKey());
        }
    }

    private int count(Player player, ItemStack sample) {
        int result = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) if (item != null && item.isSimilar(sample)) result += item.getAmount();
        return result;
    }
    private void remove(Player player, ItemStack sample, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (item == null || !item.isSimilar(sample)) continue;
            int take = Math.min(item.getAmount(), remaining); remaining -= take;
            if (take == item.getAmount()) contents[slot] = null; else item.setAmount(item.getAmount() - take);
        }
        player.getInventory().setStorageContents(contents);
    }

    private void announce(Town town, String key, ContractDefinition definition, ActiveContract contract, double reward) {
        String setting = key.equals("contract-start-town") ? "announcements.contract-start" :
                key.equals("contract-complete-town") ? "announcements.contract-complete" : "announcements.contract-expire";
        if (!plugin.getConfig().getBoolean(setting, true)) return;
        String text = messages.text(key, Map.of("contract", ColorUtil.strip(definition.name()),
                "goal", definition.goal(), "reward", economy.format(reward)), false);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Town playerTown = towny.town(player);
            if (playerTown != null && playerTown.getUUID().equals(town.getUUID())) player.sendMessage(text);
        }
    }

    public ContractDefinition definition(ActiveContract contract) { return contract == null ? null : registry.get(contract.templateId()); }
    private ContractDefinition fallback(ActiveContract contract) {
        return new ContractDefinition(contract.templateId(), "&f" + contract.templateId(), ContractType.MOB_KILL,
                Material.PAPER, -1, List.of(), "ANY", null, contract.goal(), contract.escrow(),
                Math.max(1, (contract.expiresAt() - contract.createdAt()) / 1000));
    }
    public List<ActiveContract> active(UUID townId) { return repository.active(townId); }
    public ActiveContract find(UUID townId, String id) { return repository.find(townId, id); }
    public ContractRegistry registry() { return registry; }
    public ContractRepository repository() { return repository; }
    public EconomyService economy() { return economy; }
    public double pending(UUID playerId) { return repository.pendingPlayer(playerId); }
    public void reloadRuntime() { companyNames=null;start(); }
    private void saveNow() { if (plugin.getConfig().getBoolean("contracts.save-immediately", true)) repository.save(); }

    @Override public List<ContractSnapshot> activeContracts(UUID townId) {
        return active(townId).stream().map(contract -> {
            ContractDefinition definition = definition(contract);
            return new ContractSnapshot(contract.id(), contract.townId(), contract.templateId(),
                    definition == null ? contract.templateId() : ColorUtil.strip(definition.name()),
                    definition == null ? "UNKNOWN" : definition.type().name(), contract.progress(), contract.goal(),
                    contract.escrow(), contract.expiresAt());
        }).toList();
    }
    @Override public boolean addProgress(UUID contractId, UUID contributorId, int amount, String source) {
        for (ActiveContract contract : repository.allActive()) if (contract.id().equals(contractId)) {
            ContractDefinition definition = definition(contract);
            if (definition == null || definition.type() == ContractType.DELIVERY) return false;
            return addInternal(contract, contributorId, amount);
        }
        return false;
    }
    @Override public double pendingReward(UUID residentId) { return repository.pendingPlayer(residentId); }
}
