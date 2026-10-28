package ru.neverland.townytaxes.service;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.economy.Account;
import com.palmergames.bukkit.towny.object.economy.transaction.Transaction;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townytaxes.api.NeverLandTownyTaxesApi;
import ru.neverland.townytaxes.data.CivicRepository;
import ru.neverland.townytaxes.integration.TownyHook;
import ru.neverland.townytaxes.model.*;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;

public final class FiscalService implements NeverLandTownyTaxesApi {
    private final JavaPlugin plugin; private final TownyHook towny; private final CivicRepository repository; private final MessageService messages;
    private final DecimalFormat money = new DecimalFormat("#,##0.00"); private final Map<UUID,Double> balanceSnapshots=new HashMap<>(); private BukkitTask task; private boolean internal; private boolean economyPaused;private TaxCoordinator coordinator;
    private final Set<String> audited=ConcurrentHashMap.newKeySet(),auditQueued=ConcurrentHashMap.newKeySet(),announced=new HashSet<>();
    private final Set<Transaction> observedTransactions=Collections.newSetFromMap(new WeakHashMap<>());
    public FiscalService(JavaPlugin plugin,TownyHook towny,CivicRepository repository,MessageService messages){this.plugin=plugin;this.towny=towny;this.repository=repository;this.messages=messages;}
    public void start(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {if(task!=null)task.cancel();if(coordinator==null)coordinator=new TaxCoordinator(plugin,repository,towny,this::receiptChanged);long period=Math.max(20,plugin.getConfig().getLong("scheduler.check-seconds",20)*20);task=ru.neverland.core.SuiteScheduler.get().runTaskTimer(plugin,this::tick,period,period);} }
    public void shutdown(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {if(task!=null)task.cancel();task=null;try{repository.save();}finally{if(coordinator!=null)coordinator.closeAfterSchedulerStop();}} }
    public TaxPolicy createPolicy(TownyHook.Party target,Domain.TaxType type,double value,TownyHook.Party destination,long interval,String actor){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        long now=System.currentTimeMillis();TaxPolicy p=new TaxPolicy(UUID.randomUUID(),target.scope(),target.id(),target.name(),type,value,destination.scope(),destination.id(),destination.name(),interval,now+interval,true,actor);repository.put(p);saveCritical();return p;} }
    public Sanction createSanction(TownyHook.Party target,TownyHook.Party issuer,Domain.SanctionEffect effect,double value,long expires,String reason,String actor){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {long now=System.currentTimeMillis();Sanction s=new Sanction(UUID.randomUUID(),target.scope(),target.id(),target.name(),issuer.scope(),issuer.id(),issuer.name(),effect,value,now,expires,reason,actor);repository.put(s);repository.log(new LedgerEntry(now,"SANCTION_ADD",target.scope(),target.id(),target.name(),value,reason,true));saveCritical();return s;} }
    public Agreement createAgreement(TownyHook.Party first,TownyHook.Party second,Domain.AgreementType type,double value,long expires,String actor,boolean active){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {long now=System.currentTimeMillis();Agreement a=new Agreement(UUID.randomUUID(),first.scope(),first.id(),first.name(),second.scope(),second.id(),second.name(),type,value,now,expires,active?Domain.AgreementStatus.ACTIVE:Domain.AgreementStatus.PENDING,actor);repository.put(a);saveCritical();return a;} }
    public boolean setAgreement(String id,Domain.AgreementStatus status){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {Agreement a=repository.agreement(id);if(a==null)return false;repository.put(a.status(status));saveCritical();return true;} }
    public boolean removePolicy(String id){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {boolean ok=repository.removePolicy(id);if(ok)saveCritical();return ok;} }public boolean removeSanction(String id){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {boolean ok=repository.removeSanction(id);if(ok)saveCritical();return ok;} }
    public void collectNow(){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {tick();} }
    /** A missing provider is a pause, never a zero balance or a failed payment. */
    public boolean economyReady() { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {
        if (!towny.economyAvailable() || coordinator!=null&&!coordinator.ready()) {
            balanceSnapshots.clear();
            if (!economyPaused) plugin.getLogger().warning("Экономика или денежное хранилище налогов недоступны: сбор и погашение долгов приостановлены без штрафов.");
            economyPaused = true;
            repository.economyPaused(true);
            repository.saveIfDirty();
            return false;
        }
        if (economyPaused || repository.economyPaused()) resumeEconomy(System.currentTimeMillis());
        return true;
    } }
    private void resumeEconomy(long now) {
        balanceSnapshots.clear();
        // Skip collections missed while unavailable. Do not move future deadlines.
        for (TaxPolicy policy : repository.policies())
            if (policy.enabled() && policy.type() == Domain.TaxType.FIXED && policy.nextCollection() <= now)
                repository.put(policy.next(now + Math.max(60_000, policy.intervalMillis())));
        repository.economyPaused(false);
        repository.saveIfDirty();
        economyPaused = false;
        plugin.getLogger().info("Экономика Towny доступна: налоги возобновлены без взысканий за время простоя.");
    }
    private void tick() {
        if(coordinator!=null){coordinator.pulse();coordinator.store().snapshot().receipts().values().stream().filter(r->!audited.contains(r.id()+":"+r.phase())&&!auditQueued.contains(r.id()+":"+r.phase())).limit(10).forEach(this::auditReceipt);}
        if (economyReady() && ru.neverland.core.CitizensAccess.available()) {
            scanExternalIncome();
            long now = System.currentTimeMillis();
            for (TaxPolicy policy : new ArrayList<>(repository.policies()))
                if (policy.enabled() && policy.type() == Domain.TaxType.FIXED && now >= policy.nextCollection()) {
                    if (!economyReady()) break;
                    collectFixed(policy);
                    repository.put(policy.next(now + Math.max(60_000, policy.intervalMillis())));
                }
        }
        long now = System.currentTimeMillis();
        for (Agreement agreement : new ArrayList<>(repository.agreements()))
            if (agreement.status() == Domain.AgreementStatus.ACTIVE && agreement.expiresAt() > 0 && now >= agreement.expiresAt())
                repository.put(agreement.status(Domain.AgreementStatus.EXPIRED));
        repository.saveIfDirty();
    }
    private void collectFixed(TaxPolicy p){for(TownyHook.Party subject:subjects(p))charge(subject,p,p.value());}
    /** Towny calls listeners with account monitors held. Never acquire the suite ledger here. */
    public void observeTransaction(Transaction transaction){
        if(ru.neverland.core.SuiteState.frozen(plugin.getName())||internal||coordinator!=null&&coordinator.internal())return;
        if(ru.neverland.core.SuiteState.held())onTransaction(transaction);
        else ru.neverland.core.SuiteScheduler.get().runTask(plugin,()->onTransaction(transaction));
    }
    public void onTransaction(Transaction transaction){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {if(!economyReady()||!ru.neverland.core.CitizensAccess.available())return;if(internal||coordinator!=null&&coordinator.internal()||transaction==null||!observedTransactions.add(transaction)||!transaction.hasReceiverAccount()||transaction.getAmount()<=0||!plugin.getConfig().getBoolean("taxes.transaction-tax-enabled",true))return;Account account=transaction.getReceivingAccount();TownyHook.Party subject=party(account);if(subject==null)return;Domain.TaxType eventType=transaction.hasSenderAccount()?Domain.TaxType.TRANSACTION:Domain.TaxType.INCOME;applyPercentage(subject,eventType,transaction.getAmount());snapshot(subject);} }
    private void scanExternalIncome() {
        for (Resident resident : towny.residents()) {
            if (resident == null || resident.isNPC()) continue;
            if (!economyReady()) return;
            Account account;
            double current;
            try {
                account = towny.account(resident::getAccount);
                if (account == null) { balanceSnapshots.remove(resident.getUUID()); continue; }
                current = account.getHoldingBalance();
                if (!Double.isFinite(current)) { balanceSnapshots.remove(resident.getUUID()); continue; }
            } catch (RuntimeException unavailable) {
                balanceSnapshots.remove(resident.getUUID());
                continue;
            }
            Double previous = balanceSnapshots.put(resident.getUUID(), current);
            if (previous != null && current > previous + 0.009) {
                TownyHook.Party party = new TownyHook.Party(Domain.Scope.PLAYER, resident.getUUID(), resident.getName(), account);
                applyPercentage(party, Domain.TaxType.INCOME, current - previous);
                snapshot(party);
            }
        }
    }
    private void applyPercentage(TownyHook.Party subject,Domain.TaxType type,double base){for(TaxPolicy p:repository.policies())if(p.enabled()&&p.type()==type&&matches(p,subject))charge(subject,p,base*p.value()/100D);}
    private TaxCalculation calculation(TownyHook.Party subject,TaxPolicy policy,double raw,TownyHook.Party destination){
        return TaxCalculation.calculate(raw,citizensMultiplier(subject,policy),municipalMultiplier(subject,policy),taxMultiplier(subject),taxDiscount(subject,destination));
    }
    private void charge(TownyHook.Party subject,TaxPolicy policy,double raw){
        if(!economyReady()||coordinator==null||subject==null||subject.account()==null||raw<=0)return;
        var destination=towny.resolve(policy.destinationScope(),policy.destinationId());if(destination==null||destination.scope()!=Domain.Scope.GLOBAL&&destination.account()==null)return;
        UUID id=policy.type()==Domain.TaxType.FIXED?UUID.nameUUIDFromBytes(("nlt-tax-fixed-v1:"+policy.id()+":"+subject.scope()+":"+subject.id()+":"+policy.nextCollection()).getBytes(StandardCharsets.UTF_8)):UUID.randomUUID();
        var existing=coordinator.store().get(id);if(existing!=null){coordinator.kick(id);return;}
        if(coordinator.busy(subject.scope(),subject.id(),policy.id(),TaxPayment.Kind.TAX))return;
        TaxCalculation quote;try{quote=calculation(subject,policy,raw,destination);}catch(IllegalArgumentException unavailable){plugin.getLogger().warning("Налог "+policy.shortId()+" пропущен без штрафа: "+unavailable.getMessage());return;}if(quote.cents()==0)return;
        var receipt=new TaxPayment(id,policy.id(),TaxPayment.Kind.TAX,subject.scope(),subject.id(),subject.name(),destination.scope(),destination.id(),destination.name(),quote,TaxPayment.Phase.PREPARED,System.currentTimeMillis(),plugin.getConfig().getBoolean("taxes.failed-payment-debt",true),reason(policy));
        coordinator.begin(receipt).whenComplete((v,error)->{if(error!=null&&plugin.isEnabled())plugin.getLogger().warning("Налоговая операция "+id+" не завершена; повторного банковского вызова нет: "+error.getMessage());});
    }
    public boolean payDebt(TownyHook.Party subject){try(var guard=ru.neverland.core.SuiteState.guard()){
        if(!economyReady()||coordinator==null||subject==null||subject.account()==null||coordinator.busy(subject.scope(),subject.id(),null,TaxPayment.Kind.DEBT))return false;
        long amount=coordinator.store().snapshot().debts().getOrDefault(subject.id(),0L);if(amount<=0)return false;var quote=TaxCalculation.calculate(amount/100.0,1,1,1,0);
        var receipt=new TaxPayment(UUID.randomUUID(),null,TaxPayment.Kind.DEBT,subject.scope(),subject.id(),subject.name(),Domain.Scope.GLOBAL,TownyHook.GLOBAL_ID,"Серверный налоговый резерв",quote,TaxPayment.Phase.PREPARED,System.currentTimeMillis(),false,"Погашение налоговой задолженности");coordinator.begin(receipt);return true;
    }}
    private CompletionStage<Void> receiptChanged(TaxPayment receipt){
        snapshot(towny.resolve(receipt.sourceScope(),receipt.sourceId()));snapshot(towny.resolve(receipt.destinationScope(),receipt.destinationId()));
        if(receipt.phase()==TaxPayment.Phase.DONE&&announced.add(receipt.id().toString())&&plugin.getConfig().getBoolean("messages.announce-collections",true)){
            Resident resident=towny.resident(receipt.sourceId());var player=resident==null?null:resident.getPlayer();if(player!=null)ru.neverland.core.SuiteScheduler.player(plugin,player,p->{messages.send(p,"tax-collected",Map.of("amount",format(receipt.calculation().money()),"policy",receipt.kind()==TaxPayment.Kind.DEBT?"погашение долга":receipt.policy().toString().substring(0,8)));p.sendMessage("Расчёт и квитанция: /mytaxes receipts");});
        }
        return auditReceipt(receipt);
    }
    private CompletionStage<Void> auditReceipt(TaxPayment receipt){
        String key=receipt.id()+":"+receipt.phase();if(coordinator==null||audited.contains(key)||!auditQueued.add(key))return CompletableFuture.completedFuture(null);
        var from=auditParty(receipt.sourceScope(),receipt.sourceId(),receipt.sourceName());var to=receipt.phase()==TaxPayment.Phase.REFUNDED?from:auditParty(receipt.destinationScope(),receipt.destinationId(),receipt.destinationName());
        String result=receipt.phase()==TaxPayment.Phase.DONE?"COMPLETED":receipt.phase()==TaxPayment.Phase.REFUNDED?"REFUNDED":receipt.review()?"REVIEW":receipt.phase()==TaxPayment.Phase.DEBITED?"HELD":receipt.phase().name();
        return coordinator.store().background(()->ru.neverland.core.AuditTrail.record(plugin,"tax:"+receipt.phase(),receipt.id().toString(),"TAX_TRANSFER",result,ru.neverland.core.AuditRecord.Party.system(),from,to,"",0,ru.neverland.core.AuditTrail.cents(receipt.calculation().cents()),receipt.note()+"; "+receipt.calculation().describe())).handle((saved,error)->{auditQueued.remove(key);if(error==null&&Boolean.TRUE.equals(saved))audited.add(key);return null;});
    }
    private static ru.neverland.core.AuditRecord.Party auditParty(Domain.Scope scope,UUID id,String name){return new ru.neverland.core.AuditRecord.Party(scope==Domain.Scope.GLOBAL?"SERVER":scope.name(),scope==Domain.Scope.GLOBAL?"":id.toString(),name,scope==Domain.Scope.TOWN?id.toString():"");}
    @Override public Collection<Map<String,Object>> taxPreview(UUID player,long incomeCents,long transactionCents){try(var guard=ru.neverland.core.SuiteState.guard()){
        if(incomeCents<0||transactionCents<0||incomeCents>TaxCalculation.MAX_CENTS||transactionCents>TaxCalculation.MAX_CENTS)throw new IllegalArgumentException("Неверная сумма для предварительного расчёта");
        var resident=towny.resident(player);if(resident==null||resident.isNPC())throw new IllegalArgumentException("Игрок не найден");var subject=new TownyHook.Party(Domain.Scope.PLAYER,player,resident.getName(),null);var rows=new ArrayList<Map<String,Object>>();
        for(var policy:repository.policies())if(policy.enabled()&&matches(policy,subject)&&!(policy.type()==Domain.TaxType.FIXED&&policy.scope()==Domain.Scope.NATION)&&!(policy.type()==Domain.TaxType.TRANSACTION&&!plugin.getConfig().getBoolean("taxes.transaction-tax-enabled",true))){
            var row=new LinkedHashMap<String,Object>();row.put("policy",policy.id().toString());row.put("type",policy.type().name());row.put("rate",policy.value());row.put("destination",policy.destinationName());row.put("next",policy.nextCollection());
            double raw=policy.type()==Domain.TaxType.FIXED?policy.value():(policy.type()==Domain.TaxType.INCOME?incomeCents:transactionCents)/100.0*policy.value()/100.0;
            try{var destination=towny.resolve(policy.destinationScope(),policy.destinationId());if(destination==null)throw new IllegalStateException("Получатель платежа недоступен");var quote=calculation(subject,policy,raw,destination);row.putAll(quote.values());row.put("known",true);row.put("description",quote.describe());}catch(Exception unavailable){row.put("known",false);row.put("description","Сумма уточняется: "+unavailable.getMessage());}
            rows.add(Collections.unmodifiableMap(row));
        }return List.copyOf(rows);
    }}
    private static Map<String,Object> receiptRow(TaxPayment receipt){
        var row=new LinkedHashMap<String,Object>();row.put("id",receipt.id().toString());row.put("subject",receipt.sourceId().toString());row.put("scope",receipt.sourceScope().name());row.put("name",receipt.sourceName());row.put("destination",receipt.destinationName());row.put("kind",receipt.kind().name());row.put("phase",receipt.phase().name());row.put("created",receipt.created());row.put("cents",receipt.calculation().cents());row.put("description",receipt.calculation().describe());row.put("note",receipt.note());return Collections.unmodifiableMap(row);
    }
    @Override public Collection<Map<String,Object>> taxReceipts(UUID subject){try(var guard=ru.neverland.core.SuiteState.guard()){
        if(coordinator==null||!coordinator.ready())throw new IllegalStateException("Квитанции временно недоступны");return coordinator.store().snapshot().receipts().values().stream().filter(r->r.sourceId().equals(subject)).sorted(Comparator.comparingLong(TaxPayment::created).reversed()).limit(100).map(FiscalService::receiptRow).toList();
    }}
    @Override public Collection<Map<String,Object>> taxPayments(){try(var guard=ru.neverland.core.SuiteState.guard()){
        if(coordinator==null||!coordinator.store().healthy())throw new IllegalStateException("Журнал налоговых переводов временно недоступен");return coordinator.store().snapshot().receipts().values().stream().filter(r->!r.terminal()).sorted(Comparator.comparingLong(TaxPayment::created)).map(FiscalService::receiptRow).toList();
    }}
    public static String paymentState(String phase){return switch(TaxPayment.Phase.valueOf(phase)){case PREPARED->"Ожидает доступа к банку";case DEBITING,CREDITING,REFUNDING->"Нужна сверка банковского этапа";case DEBITED->"Средства удержаны, ожидают получателя";case REFUND_READY->"Ожидает возврата";case DONE->"Уплачено";case REFUNDED->"Возвращено";case REJECTED->"Не оплачено";case VOID->"Отменено без списания";};}
    public TaxCoordinator coordinator(){return coordinator;}
    public void awaitClosed(){if(coordinator!=null)coordinator.awaitClosed();}
    private List<TownyHook.Party> subjects(TaxPolicy p){List<TownyHook.Party> out=new ArrayList<>();switch(p.scope()){case PLAYER->{TownyHook.Party v=towny.resolve(Domain.Scope.PLAYER,p.targetId());if(v!=null)out.add(v);}case TOWN->{Town t=towny.town(p.targetId());if(t!=null)for(Resident r:towny.residents(t))if(eligible(r))out.add(new TownyHook.Party(Domain.Scope.PLAYER,r.getUUID(),r.getName(),towny.account(r::getAccount)));}case NATION->{Nation n=towny.nation(p.targetId());if(n!=null)for(Town t:towny.towns(n))out.add(new TownyHook.Party(Domain.Scope.TOWN,t.getUUID(),t.getName(),towny.account(t::getAccount)));}case GLOBAL->{for(Resident r:towny.residents())if(eligible(r))out.add(new TownyHook.Party(Domain.Scope.PLAYER,r.getUUID(),r.getName(),towny.account(r::getAccount)));}}return out;}
    private boolean eligible(Resident r){if(r==null||r.isNPC())return false;if(!plugin.getConfig().getBoolean("taxes.process-offline-residents",true)&&r.getPlayer()==null)return false;long days=plugin.getConfig().getLong("taxes.inactive-resident-days",60);return days<=0||r.getPlayer()!=null||System.currentTimeMillis()-r.getLastOnline()<=days*86_400_000L;}
    private boolean matches(TaxPolicy p,TownyHook.Party s){if(p.scope()==Domain.Scope.GLOBAL)return true;if(p.scope()==s.scope()&&Objects.equals(p.targetId(),s.id()))return true;if(s.scope()==Domain.Scope.PLAYER){Resident r=towny.resident(s.id());Town t=r==null?null:r.getTownOrNull();if(p.scope()==Domain.Scope.TOWN)return t!=null&&p.targetId().equals(t.getUUID());Nation n=t==null?null:t.getNationOrNull();return p.scope()==Domain.Scope.NATION&&n!=null&&p.targetId().equals(n.getUUID());}if(s.scope()==Domain.Scope.TOWN&&p.scope()==Domain.Scope.NATION){Town t=towny.town(s.id());Nation n=t==null?null:t.getNationOrNull();return n!=null&&p.targetId().equals(n.getUUID());}return false;}
    private TownyHook.Party party(Account account){UUID id=account.getUUID();Resident r=towny.resident(id);if(r!=null)return new TownyHook.Party(Domain.Scope.PLAYER,id,r.getName(),account);Town t=towny.town(id);if(t!=null)return new TownyHook.Party(Domain.Scope.TOWN,id,t.getName(),account);Nation n=towny.nation(id);return n==null?null:new TownyHook.Party(Domain.Scope.NATION,id,n.getName(),account);}
    public boolean commandBlocked(Resident resident,String command){ try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {if(resident==null)return false;String lower=command.toLowerCase(Locale.ROOT);for(Sanction s:applicable(resident))if(s.effect()==Domain.SanctionEffect.COMMAND_BLOCK&&!relieved(s))for(String blocked:plugin.getConfig().getStringList("sanctions.default-blocked-commands"))if(lower.equals(blocked.toLowerCase(Locale.ROOT))||lower.startsWith(blocked.toLowerCase(Locale.ROOT)+" "))return true;return false;} }
    private boolean municipal(TaxPolicy p,UUID town){return MunicipalTaxes.matches(p,town);}
    @Override public boolean municipalTaxConfigured(UUID town){ try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {return town!=null&&repository.policies().stream().anyMatch(p->municipal(p,town)&&(p.type()!=Domain.TaxType.TRANSACTION||plugin.getConfig().getBoolean("taxes.transaction-tax-enabled",true)));} }
    private double citizensMultiplier(TownyHook.Party subject,TaxPolicy policy){if(subject.scope()!=Domain.Scope.PLAYER)return 1;Resident resident=towny.resident(subject.id());Town town=resident==null?null:resident.getTownOrNull();return town!=null&&municipal(policy,town.getUUID())?ru.neverland.core.CitizensAccess.tax(town.getUUID(),subject.id()):1;}
    private double municipalMultiplier(TownyHook.Party subject,TaxPolicy policy){if(subject.scope()!=Domain.Scope.PLAYER)return 1;Resident resident=towny.resident(subject.id());Town town=resident==null?null:resident.getTownOrNull();return town!=null&&municipal(policy,town.getUUID())?ru.neverland.integration.PoliciesAccess.tax(town.getUUID()):1;}
    public double taxMultiplier(TownyHook.Party party){ try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {double result=1;for(Sanction s:applicable(party))if(s.effect()==Domain.SanctionEffect.TAX_MULTIPLIER&&!relieved(s))result=Math.max(result,Math.max(1,s.value()));return Math.min(result,plugin.getConfig().getDouble("sanctions.maximum-tax-multiplier",5));} }
    private double taxDiscount(TownyHook.Party a,TownyHook.Party b){if(a==null||b==null)return 0;double best=0;long now=System.currentTimeMillis();for(Agreement x:repository.agreements())if(x.active(now)&&x.type()==Domain.AgreementType.TAX_EXEMPTION&&x.pair(a.scope(),a.id(),b.scope(),b.id()))best=Math.max(best,x.value()/100D);return Math.min(1,best);}
    private List<Sanction> applicable(Resident r){List<Sanction>out=new ArrayList<>();Town t=r.getTownOrNull();Nation n=t==null?null:t.getNationOrNull();for(Sanction s:repository.sanctions())if(s.active(System.currentTimeMillis())&&(s.targetScope()==Domain.Scope.GLOBAL||(s.targetScope()==Domain.Scope.PLAYER&&s.targetId().equals(r.getUUID()))||(t!=null&&s.targetScope()==Domain.Scope.TOWN&&s.targetId().equals(t.getUUID()))||(n!=null&&s.targetScope()==Domain.Scope.NATION&&s.targetId().equals(n.getUUID()))))out.add(s);return out;}
    private List<Sanction> applicable(TownyHook.Party p){if(p.scope()==Domain.Scope.PLAYER){Resident r=towny.resident(p.id());return r==null?List.of():applicable(r);}List<Sanction>out=new ArrayList<>();Town t=p.scope()==Domain.Scope.TOWN?towny.town(p.id()):null;Nation n=p.scope()==Domain.Scope.NATION?towny.nation(p.id()):t==null?null:t.getNationOrNull();for(Sanction s:repository.sanctions())if(s.active(System.currentTimeMillis())&&(s.targetScope()==Domain.Scope.GLOBAL||(s.targetScope()==p.scope()&&s.targetId().equals(p.id()))||(n!=null&&s.targetScope()==Domain.Scope.NATION&&s.targetId().equals(n.getUUID()))))out.add(s);return out;}
    private boolean relieved(Sanction s){long now=System.currentTimeMillis();for(Agreement a:repository.agreements())if(a.active(now)&&a.type()==Domain.AgreementType.SANCTION_RELIEF&&a.pair(s.issuerScope(),s.issuerId(),s.targetScope(),s.targetId()))return true;return false;}
    @Override public boolean isTradeBlocked(UUID firstTown,UUID secondTown){ try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.DiplomacyAccess.tradeBlocked(firstTown,secondTown))return true;Town a=towny.town(firstTown),b=towny.town(secondTown);if(a==null||b==null)return false;for(Sanction s:repository.sanctions())if(s.active(System.currentTimeMillis())&&s.effect()==Domain.SanctionEffect.TRADE_BLOCK&&!relieved(s)&&(targetsTown(s,a)||targetsTown(s,b)))return true;return false;} }
    @Override public double tradePreferenceMultiplier(UUID firstTown,UUID secondTown){ try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {double discount=0;long now=System.currentTimeMillis();for(Agreement a:repository.agreements())if(a.active(now)&&a.type()==Domain.AgreementType.TRADE_PREFERENCE&&a.pair(Domain.Scope.TOWN,firstTown,Domain.Scope.TOWN,secondTown))discount=Math.max(discount,a.value());return Math.max(0,1-Math.min(plugin.getConfig().getDouble("agreements.maximum-trade-discount-percent",50),discount)/100D);} }
    private boolean targetsTown(Sanction s,Town t){if(s.targetScope()==Domain.Scope.TOWN)return s.targetId().equals(t.getUUID());Nation n=t.getNationOrNull();return s.targetScope()==Domain.Scope.NATION&&n!=null&&s.targetId().equals(n.getUUID());}
    @Override public double taxDebt(UUID id){ try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {if(coordinator==null)return repository.debt(id);if(!coordinator.ready())throw new IllegalStateException("Налоговый учёт временно недоступен");return coordinator.store().snapshot().debts().getOrDefault(id,0L)/100.0;} }@Override public double serverReserve(){ try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {if(coordinator==null)return repository.serverBank();if(!coordinator.ready())throw new IllegalStateException("Налоговый учёт временно недоступен");return coordinator.store().snapshot().reserve()/100.0;} }
    public boolean validTaxValue(Domain.TaxType type,double value){ try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) {double max=type==Domain.TaxType.FIXED?plugin.getConfig().getDouble("taxes.maximum-fixed",1_000_000):plugin.getConfig().getDouble("taxes.maximum-percent",50);return Double.isFinite(value)&&value>=0&&value<=max;} }
    public long intervalMillis(long minutes){ try (var suiteGuard21 = ru.neverland.core.SuiteState.guard()) {return Math.max(plugin.getConfig().getLong("taxes.minimum-interval-minutes",10),minutes)*60_000L;} }
    public CivicRepository repository(){ try (var suiteGuard22 = ru.neverland.core.SuiteState.guard()) {return repository;} }public String format(double v){ try (var suiteGuard23 = ru.neverland.core.SuiteState.guard()) {return money.format(v);} }private String reason(TaxPolicy p){return plugin.getConfig().getString("taxes.reason","Налог NeverLand: %policy%").replace("%policy%",p.shortId());}private void saveCritical(){if(plugin.getConfig().getBoolean("storage.save-after-critical-operation",true))repository.save();}
    private void snapshot(TownyHook.Party party){if(!towny.economyAvailable())return;if(party!=null&&party.scope()==Domain.Scope.PLAYER&&party.account()!=null)try{balanceSnapshots.put(party.id(),party.account().getHoldingBalance());}catch(RuntimeException ignored){balanceSnapshots.remove(party.id());}}
}
