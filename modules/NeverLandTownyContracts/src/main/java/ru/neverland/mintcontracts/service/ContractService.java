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
import ru.neverland.mintcontracts.model.*;
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
    private MunicipalPayments payments;
    private MunicipalDeliveries deliveries;
    private FieldWorkService fieldWork;
    private int pulses;
    private RussianNames companyNames;
    private final ru.neverland.mintcontracts.integration.CompaniesBridge companies = new ru.neverland.mintcontracts.integration.CompaniesBridge();

    public ContractService(JavaPlugin plugin, TownyHook towny, ContractRegistry registry, ContractRepository repository,
                           WarehouseBridge warehouse, EconomyService economy, MessageService messages) {
        this.plugin = plugin; this.towny = towny; this.registry = registry; this.repository = repository;
        this.warehouse = warehouse; this.economy = economy; this.messages = messages;
        payments=new MunicipalPayments(new MunicipalPayments.Store(){
            public MunicipalPayment get(UUID id){return repository.payments().get(id);}
            public void put(MunicipalPayment p)throws Exception{repository.payment(p);repository.saveOrThrow();}
            public void result(MunicipalPayment p,boolean paid)throws Exception{
                repository.payment(p);
                if(p.kind()==MunicipalPayment.Kind.RESERVE){ActiveContract c=find(p.town(),p.contract().toString());if(c==null)throw new IllegalStateException("Нет задания для резерва");
                    if(paid)c.funded(true);else repository.addHistory(c,ContractStatus.CANCELLED,0,0,30,System.currentTimeMillis());}
                repository.changed();repository.saveOrThrow();
            }
        },new ru.neverland.mintcontracts.integration.MunicipalBank());
        deliveries=new MunicipalDeliveries(new MunicipalDeliveries.Store(){
            public DeliveryIntent get(UUID id){return repository.deliveries().get(id);}
            public void put(DeliveryIntent d)throws Exception{repository.delivery(d);repository.saveOrThrow();}
            public void complete(DeliveryIntent d)throws Exception{
                ActiveContract c=find(d.town(),d.contract().toString());if(c==null||!c.funded()||c.settlementStatus()!=null||c.goal()-c.progress()<d.amount())throw new IllegalStateException("Поставка не соответствует заданию");
                if(c.add(d.actor(),d.amount())!=d.amount())throw new IllegalStateException("Поставка не засчитана целиком");repository.delivery(d);repository.changed();repository.saveOrThrow();
            }
        },new MunicipalDeliveries.Warehouse(){
            public String deposit(DeliveryIntent d)throws Exception{return warehouse.deposit(d.town(),d.id(),ContractCodec.item(d.sample()),d.amount());}
            public void acknowledge(DeliveryIntent d)throws Exception{warehouse.acknowledge(d.town(),d.id());}
        });
        fieldWork=new FieldWorkService(plugin,this,towny);
    }
    public void initialize()throws Exception {
        for(ActiveContract c:repository.allActive())if(c.snapshot()==null){ContractDefinition d=registry.get(c.templateId());
            if(d==null)d=fallback(c);c.snapshot(new ContractDefinition(c.templateId(),d.name(),d.type(),d.icon(),d.slot(),d.description(),d.target(),d.deliveryItem(),c.goal(),c.escrow(),Math.max(1,(c.expiresAt()-c.createdAt())/1000)));repository.changed();}
        for(var e:repository.legacyPlayers().entrySet()){long n=Math.round(e.getValue()*100);if(n>0)repository.payment(new MunicipalPayment(UUID.randomUUID(),null,new UUID(0,0),e.getKey(),MunicipalPayment.Kind.REWARD,n,MunicipalPayment.Phase.READY,System.currentTimeMillis()));repository.clearPendingPlayer(e.getKey());}
        for(var e:repository.pendingTowns().entrySet()){long n=Math.round(e.getValue()*100);if(n>0)repository.payment(new MunicipalPayment(UUID.randomUUID(),null,e.getKey(),e.getKey(),MunicipalPayment.Kind.REFUND,n,MunicipalPayment.Phase.READY,System.currentTimeMillis()));repository.clearPendingTown(e.getKey());}
        repository.saveOrThrow();
    }
    public FieldWorkService fieldWork(){return fieldWork;}
    public void reconcilePayment(UUID id,boolean applied)throws Exception{payments.resolve(id,applied);}
    public void reconcileDelivery(UUID id,boolean taken)throws Exception{deliveries.resolve(id,taken);}


    public void start() {
        if(task!=null)task.cancel();task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,20);
    }
    public void shutdown(){if(task!=null)task.cancel();task=null;repository.save();}
    public ActivateResult activate(Town town,ContractDefinition definition){return activate(town,definition,null);}
    public ActivateResult activate(Town town,ContractDefinition definition,WorkArea area) {
        if(!Bukkit.isPrimaryThread()||!repository.writable()||town==null||definition==null)return ActivateResult.SAVE_ERROR;
        if(repository.active(town.getUUID()).size()>=Math.max(1,Math.min(45,plugin.getConfig().getInt("contracts.max-active-per-town",3)))||repository.allActive().size()>=Math.max(1,plugin.getConfig().getInt("contracts.max-active-server",500)))return ActivateResult.MAX_ACTIVE;
        if(!plugin.getConfig().getBoolean("contracts.allow-duplicate-template",false)&&repository.hasTemplate(town.getUUID(),definition.id()))return ActivateResult.DUPLICATE;
        if(definition.type()==ContractType.DELIVERY&&!warehouse.available())return ActivateResult.WAREHOUSE_UNAVAILABLE;
        if((definition.type()==ContractType.ROAD||definition.type()==ContractType.SCOUT)&&area==null)return ActivateResult.SAVE_ERROR;
        if(!economy.canReserve(town,definition.reward()))return ActivateResult.NO_MONEY;
        long now=System.currentTimeMillis();ActiveContract c=new ActiveContract(UUID.randomUUID(),town.getUUID(),definition.id(),now,now+definition.durationSeconds()*1000,0,definition.goal(),definition.reward(),Map.of());
        c.snapshot(definition);if(area!=null)c.area(area);c.funded(definition.reward()==0);repository.add(c);
        MunicipalPayment reserve=null;
        if(definition.reward()>0){reserve=new MunicipalPayment(MunicipalPayment.key(c.id(),"reserve"),c.id(),c.townId(),c.townId(),MunicipalPayment.Kind.RESERVE,Math.round(c.escrow()*100),MunicipalPayment.Phase.READY,now);repository.payment(reserve);}
        try{repository.saveOrThrow();if(reserve!=null)payments.process(reserve.id());}
        catch(Exception ex){plugin.getLogger().severe("Публикация заказа остановлена: "+ex.getMessage());return ActivateResult.SAVE_ERROR;}
        if(!c.funded())return ActivateResult.ECONOMY_ERROR;
        announce(town,"contract-start-town",definition,c,c.escrow());return ActivateResult.SUCCESS;
    }

    public DeliveryResult deliver(Player player,ActiveContract contract,boolean all) {
        if(!eligibleMode(player)||!canContribute(contract,player.getUniqueId())||!player.hasPermission("mintcontracts.contribute")||repository.deliveryBusy(contract.id()))return new DeliveryResult(WarehouseBridge.Status.UNAVAILABLE,0,contract==null?0:contract.progress());
        Town town=towny.town(player);ContractDefinition d=definition(contract);
        if(town==null||d==null||d.type()!=ContractType.DELIVERY||!town.getUUID().equals(contract.townId()))return new DeliveryResult(WarehouseBridge.Status.UNAVAILABLE,0,contract.progress());
        ItemStack sample=d.deliveryItem();int found=count(player,sample),space=warehouse.capacity(town.getUUID(),sample);
        if(space==-2)return new DeliveryResult(WarehouseBridge.Status.BUSY,0,contract.progress());
        if(space<0)return new DeliveryResult(WarehouseBridge.Status.UNAVAILABLE,0,contract.progress());
        if(space==0)return new DeliveryResult(WarehouseBridge.Status.FULL,0,contract.progress());
        int amount=Math.min(space,Math.min(contract.goal()-contract.progress(),all?found:Math.min(found,sample.getMaxStackSize())));
        if(amount<=0)return new DeliveryResult(WarehouseBridge.Status.SUCCESS,0,contract.progress());
        var intent=new DeliveryIntent(UUID.randomUUID(),contract.id(),town.getUUID(),player.getUniqueId(),ContractCodec.item(sample),amount,DeliveryIntent.Phase.PLAYER_PENDING,System.currentTimeMillis(),false);
        try{deliveries.start(intent,()->{if(count(player,sample)<amount)return false;remove(player,sample,amount);player.saveData();return true;});
            if(repository.deliveries().get(intent.id()).phase()==DeliveryIntent.Phase.COMPLETE){if(contract.completed())resolve(contract,ContractStatus.SUCCESS);return new DeliveryResult(WarehouseBridge.Status.SUCCESS,amount,contract.progress());}
        }catch(Exception ex){plugin.getLogger().severe("Поставка "+intent.id()+" ожидает восстановления: "+ex.getMessage());}
        player.sendMessage(ColorUtil.color("&6NeverLand &8» &eПоставка сохранена и ожидает завершения: "+intent.id()));return new DeliveryResult(WarehouseBridge.Status.UNAVAILABLE,0,contract.progress());
    }

    public void record(Player player, ContractType type, String target, int amount) {
        Town town = towny.town(player);
        if (!eligibleMode(player)||town == null || amount <= 0||!player.hasPermission("mintcontracts.contribute")) return;
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
        else if(!repository.save())return false;
        return true;
    }

    public boolean cancel(Town town, ActiveContract contract) {
        if (!Bukkit.isPrimaryThread()||!repository.writable()||town == null || contract == null ||!contract.funded()||repository.deliveryBusy(contract.id())|| !town.getUUID().equals(contract.townId())||repository.find(town.getUUID(),contract.id().toString())!=contract) return false;
        resolve(contract, ContractStatus.CANCELLED);
        return true;
    }

    public void tick() {
        if(!repository.writable())return;
        try{
            fieldWork.pulse();if(++pulses%Math.max(1,plugin.getConfig().getInt("contracts.expiration-check-seconds",30))!=0)return;
            for(var d:repository.deliveries().values())if(d.phase()==DeliveryIntent.Phase.TAKEN||d.phase()==DeliveryIntent.Phase.COMPLETE&&!d.acknowledged()){
                try{deliveries.process(d.id());}catch(Exception ex){if(!repository.writable())throw ex;}
            }
            for(var p:repository.payments().values())if(p.phase()==MunicipalPayment.Phase.READY)payments.process(p.id());
            long now=System.currentTimeMillis();
            for(ActiveContract c:repository.allActive()){
                if(!c.funded()||repository.deliveryBusy(c.id()))continue;
                if(c.settlementStatus()!=null)resolve(c,c.settlementStatus());
                else if(c.completed()&&definition(c).type()!=ContractType.ROAD)resolve(c,ContractStatus.SUCCESS);
                else if(now>=c.expiresAt())resolve(c,ContractStatus.EXPIRED);
            }
            repository.saveIfDirty();
        }catch(Exception ex){plugin.getLogger().severe("Расчёт контрактов отложен: "+ex.getMessage());}
    }
    public boolean eligibleMode(Player p){return p!=null&&(plugin.getConfig().getBoolean("progress.allow-creative",false)||p.getGameMode()!=org.bukkit.GameMode.CREATIVE&&p.getGameMode()!=org.bukkit.GameMode.SPECTATOR);}
    private long payout(ActiveContract c,ContractStatus status){
        long total=Math.round(c.escrow()*100);if(status==ContractStatus.SUCCESS)return total;
        if(definition(c).type()==ContractType.ROAD)return 0;
        boolean partial=plugin.getConfig().getBoolean(status==ContractStatus.EXPIRED?"contracts.partial-payout-on-expire":"contracts.partial-payout-on-cancel",true);
        return partial?Math.min(total,Math.round(total*c.ratio())):0;
    }
    private void resolve(ActiveContract c,ContractStatus status) {
        if(!repository.writable()||!c.funded()||repository.deliveryBusy(c.id())||find(c.townId(),c.id().toString())!=c)return;
        if(c.companyId()!=null){resolveCompany(c,status);return;}
        long total=Math.round(c.escrow()*100),paid=payout(c,status);if(c.contributions().isEmpty())paid=0;
        var shares=shares(c.contributions(),paid);long now=System.currentTimeMillis();
        c.settlement(status,paid,total-paid);
        for(var share:shares.entrySet())repository.payment(new MunicipalPayment(MunicipalPayment.key(c.id(),"player:"+share.getKey()),c.id(),c.townId(),share.getKey(),MunicipalPayment.Kind.REWARD,share.getValue(),MunicipalPayment.Phase.READY,now));
        if(total>paid)repository.payment(new MunicipalPayment(MunicipalPayment.key(c.id(),"refund"),c.id(),c.townId(),c.townId(),MunicipalPayment.Kind.REFUND,total-paid,MunicipalPayment.Phase.READY,now));
        repository.addHistory(c,status,paid/100.0,(total-paid)/100.0,plugin.getConfig().getInt("contracts.history-limit-per-town",30),now);
        if(!repository.save())return;
        for(var payment:repository.payments().values())if(c.id().equals(payment.contract())&&payment.phase()==MunicipalPayment.Phase.READY)try{payments.process(payment.id());}catch(Exception ex){plugin.getLogger().severe("Выплата "+payment.id()+" отложена: "+ex.getMessage());break;}
        Town town=towny.town(c.townId());if(town!=null)announce(town,status==ContractStatus.SUCCESS?"contract-complete-town":status==ContractStatus.CANCELLED?"contract-cancel-town":"contract-expire-town",definition(c),c,paid/100.0);
    }
    public void completeField(ActiveContract c){if(c.completed())resolve(c,ContractStatus.SUCCESS);}
    public void saveField()throws java.io.IOException{repository.changed();repository.saveOrThrow();}
    private boolean canContribute(ActiveContract c,UUID actor) {
        if(!Bukkit.isPrimaryThread()||!repository.writable()||c==null||actor==null||!c.funded()||c.settlementStatus()!=null||System.currentTimeMillis()>=c.expiresAt()||repository.find(c.townId(),c.id().toString())!=c)return false;
        var resident=towny.resident(actor);if(resident==null||resident.getTownOrNull()==null||!resident.getTownOrNull().getUUID().equals(c.townId()))return false;
        return c.companyId()==null||companies.allow("canContribute",actor,c.companyId(),c.townId());
    }
    private void resolveCompany(ActiveContract c,ContractStatus status) {
        if(c.settlementStatus()==null){
            long total=Math.round(c.escrow()*100),payout=payout(c,status);
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
            row.put("target",d.type()==ContractType.SCOUT?"Разведка территории":d.type()==ContractType.DELIVERY?names.item(d.deliveryItem()):d.target().equalsIgnoreCase("ANY")?"Любая подходящая цель":names.value(d.target()));result.add(Map.copyOf(row));
        }return List.copyOf(result);
    }
    public boolean takeCompanyContract(Player p,UUID company,UUID id) {
        if(!Bukkit.isPrimaryThread()||!repository.writable())return false;
        Town town=towny.town(p);if(town==null)return false;ActiveContract c=find(town.getUUID(),id.toString());
        if(c==null||!c.funded()||repository.deliveryBusy(c.id())||!c.proofs().isEmpty()||c.companyId()!=null||c.progress()!=0||c.settlementStatus()!=null||System.currentTimeMillis()>=c.expiresAt()||!companies.allow("canTake",p.getUniqueId(),company,town.getUUID()))return false;
        c.companyId(company);repository.changed();return repository.save();
    }
    public boolean releaseCompanyContract(Player p,UUID company,UUID id) {
        if(!Bukkit.isPrimaryThread()||!repository.writable())return false;
        Town town=towny.town(p);if(town==null)return false;ActiveContract c=find(town.getUUID(),id.toString());
        if(c==null||!c.funded()||repository.deliveryBusy(c.id())||!c.proofs().isEmpty()||!company.equals(c.companyId())||c.progress()!=0||c.settlementStatus()!=null||System.currentTimeMillis()>=c.expiresAt()||!companies.allow("canManage",p.getUniqueId(),company,town.getUUID()))return false;
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
        long paid=0;for(var p:repository.payments().values())if(p.kind()==MunicipalPayment.Kind.REWARD&&p.account().equals(player.getUniqueId())&&p.phase()==MunicipalPayment.Phase.READY){
            try{payments.process(p.id());if(repository.payments().get(p.id()).phase()==MunicipalPayment.Phase.DONE)paid+=p.cents();}catch(Exception ex){return paid>0?paid/100.0:-1;}}
        return paid>0?paid/100.0:repository.pendingPlayer(player.getUniqueId())>0?-1:0;
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

    public ContractDefinition definition(ActiveContract contract) { return contract == null ? null : contract.snapshot()!=null?contract.snapshot():registry.get(contract.templateId()); }
    private ContractDefinition fallback(ActiveContract contract) {
        return new ContractDefinition(contract.templateId(), "&fЗаказ №" + contract.shortId(), ContractType.MOB_KILL,
                Material.PAPER, -1, List.of("Исходный шаблон удалён"), "REMOVED_TEMPLATE", null, contract.goal(), contract.escrow(),
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
            if (definition == null || definition.type() == ContractType.DELIVERY||definition.type()==ContractType.ROAD||definition.type()==ContractType.SCOUT) return false;
            return addInternal(contract, contributorId, amount);
        }
        return false;
    }
    @Override public double pendingReward(UUID residentId) { return repository.pendingPlayer(residentId); }
}
