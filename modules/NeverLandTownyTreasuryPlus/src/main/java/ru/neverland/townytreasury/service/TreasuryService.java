package ru.neverland.townytreasury.service;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.economy.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.*;
import ru.neverland.townytreasury.debits.AsyncDebit;
import ru.neverland.townytreasury.service.BankFlows.Flow;
import java.util.regex.*;
import ru.neverland.townytreasury.api.*;
import ru.neverland.townytreasury.config.TreasurySettings;
import ru.neverland.townytreasury.data.TreasuryRepository;
import ru.neverland.townytreasury.model.*;
public final class TreasuryService implements TownyTreasuryApi {
    private final Plugin plugin;private final TreasuryRepository repo;private TreasurySettings settings;private BukkitTask task;private volatile boolean fault;
    private record Watched(Account account,AccountObserver observer){}
    private final Map<UUID,Watched> watched=new HashMap<>();
    private final BankFlows flows=new BankFlows(4096);private final AsyncBudgetGateway asynchronous;
    private CompletionStage<Void> closing;private CompletionStage<?> maintenance=CompletableFuture.completedFuture(null);private boolean stopping;private int scanOffset;private boolean legacyWarned;
    private static final Pattern TAG=Pattern.compile("^\\[NLT\\|([a-z0-9-]+)\\|([a-z_]+)\\|([a-z0-9_/-]+)\\] ?(.*)$",Pattern.DOTALL);
    public record Offer(UUID town,long revision,long expires,TreasurySettings settings,String action,List<String> args){}
    private final Map<UUID,Offer> offers=new HashMap<>();
    public TreasuryService(Plugin plugin,TreasuryRepository repo,TreasurySettings settings){this.plugin=plugin;this.repo=repo;this.settings=settings;asynchronous=new AsyncBudgetGateway(plugin,this,repo);fault=repo.flowGap();}
    public boolean fault(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return fault;} }public TreasurySettings settings(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return settings;} }public Map<UUID,CityLedger> cities(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return repo.cities();} }
    private void primary(){if(!ru.neverland.core.SuiteState.held())throw new IllegalStateException("Нужен основной поток сервера");}
    private void ready(){primary();if(repo.busy()||asynchronous.busy()||!maintenance.toCompletableFuture().isDone())throw new IllegalStateException("Казна подтверждает платёж; повторите позже");if(!initialized)throw new IllegalStateException("Казна ожидает загрузки экономики");if(fault)throw new IllegalStateException("Учёт казны требует восстановления; обратитесь к администратору");}
    public Town town(Player player){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {var resident=TownyAPI.getInstance().getResident(player);return resident==null?null:resident.getTownOrNull();} }
    public boolean manager(Player player,Town town){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {var r=TownyAPI.getInstance().getResident(player);return r!=null&&town!=null&&town.equals(r.getTownOrNull())&&(town.isMayor(r)||ru.neverland.core.CouncilAccess.check(player,town.getUUID(),"budget",player.hasPermission("neverlandtownytreasury.manage")));} }
    // Vault may expose a provider during onLoad, before its onEnable has finished.
    // Start scanning on the first server tick, after all plugin enable callbacks.
    // Budget operations stay closed until that first scan succeeds.
    public void start()throws Exception{ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {primary();task=ru.neverland.core.SuiteScheduler.get().runTaskTimer(plugin,this::pulse,1,20);} }
    private int ticks;private boolean initialized;
    private void pulse(){
        if(stopping||repo.busy()||asynchronous.busy()||!maintenance.toCompletableFuture().isDone())return;
        if(asynchronous.recover())return;
        if(repo.flowGap()){fault=true;if(!repo.gapDurable()&&repo.writable()){var before=repo.image();track(repo.commit(before,new TreasuryRepository.Image(before.cities(),before.debits(),true)));}return;}
        if(fault)return;
        try{if(!initialized){scan();return;}int turn=++ticks%3;if(turn==0){scan();return;}if(turn==1){asynchronous.pulse();if(asynchronous.busy()||repo.busy())return;}if(!flushAsync())asynchronous.pulse();}catch(Exception ex){fail(ex);}
    }
    private void fail(Throwable ex){fault=true;plugin.getLogger().log(java.util.logging.Level.SEVERE,"Учёт казны приостановлен; новые бюджетные списания закрыты. Сохранённые счета требуют проверки.",ex);}
    private <T>CompletionStage<T> track(CompletionStage<T> stage){maintenance=stage;stage.whenComplete((v,e)->{if(e!=null)fail(e);});return stage;}
    public CompletionStage<Void> stop(){try(var guard=ru.neverland.core.SuiteState.guard()){
        if(closing!=null)return closing;stopping=true;if(task!=null)task.cancel();for(var value:watched.values())value.account().removeObserver(value.observer());watched.clear();offers.clear();
        var gateway=asynchronous.stop();closing=maintenance.handle((v,e)->null).thenCombine(gateway,(a,b)->null).thenCompose(v->drainAsync()).handle((v,e)->e).thenCompose(error->repo.closeAsync().thenApply(v->{if(error!=null)throw new CompletionException(error);return null;}));return closing;
    }}
    private CompletionStage<Void> drainAsync(){try(var guard=ru.neverland.core.SuiteState.guard()){
        var batch=flows.batch(f->!asynchronous.holdFlow(f.operation()),512);var before=repo.image();if(batch.isEmpty()&&(!repo.flowGap()||repo.gapDurable()))return CompletableFuture.completedFuture(null);var next=applyFlows(before.cities(),batch);
        return repo.commit(before,new TreasuryRepository.Image(next,before.debits(),repo.flowGap())).thenCompose(v->{flows.remove(batch);return drainAsync();});
    }}
    public void reload(TreasurySettings value){try(var guard=ru.neverland.core.SuiteState.guard()){ready();settings=value;offers.clear();scanOffset=0;initialized=false;}}
    private CityLedger attach(Town town,CityLedger known)throws Exception{
        UUID id=town.getUUID();Account account=town.getAccount();var old=watched.get(id);if(old!=null&&old.account()==account)return known;if(old!=null)old.account().removeObserver(old.observer());
        var observer=new AccountObserver(){
            public void withdrew(Account source,double amount,String reason){capture(source,amount,reason,false);}
            public void deposited(Account source,double amount,String reason){capture(source,amount,reason,true);}
            private void capture(Account source,double amount,String reason,boolean deposit){
                if(source!=account)return;try{long cents=Money.cents(amount);if(cents<=0)return;String text=reason==null?"":reason;UUID operation=null;var match=TAG.matcher(text);if(match.matches())try{operation=UUID.fromString(match.group(1));}catch(IllegalArgumentException ignored){}
                    if(operation==null&&deposit){var refund=REFUND_TAG.matcher(text);if(refund.find())try{operation=UUID.fromString(refund.group(1));}catch(IllegalArgumentException ignored){}}
                    if(!flows.add(id,System.currentTimeMillis(),cents,deposit,text,operation)){repo.noteFlowGap();fault=true;}
                }catch(RuntimeException ex){repo.noteFlowGap();fault=true;plugin.getLogger().severe("Некорректная операция казны: "+ex.getMessage());}
            }
        };
        synchronized(account){var baseline=known==null?CityLedger.initial(Money.cents(account.getHoldingBalance(true)),settings.shares(),System.currentTimeMillis()):known;account.addObserver(observer);watched.put(id,new Watched(account,observer));return baseline;}
    }
    private static final Pattern REFUND_TAG=Pattern.compile("\\[async-refund:([0-9a-f-]{36})\\]");
    /** At most 64 cities are inspected per scan; the resulting immutable image has one writer ACK. */
    private void scan()throws Exception{
        primary();if(!TownyEconomyHandler.isActive()||TownyEconomyHandler.activeAdapter()==null)return;
        var towns=List.copyOf(TownyAPI.getInstance().getTowns());var before=repo.image();var next=new HashMap<>(before.cities());var notices=new ArrayList<Runnable>();
        if(scanOffset>=towns.size())scanOffset=0;int end=Math.min(towns.size(),scanOffset+64);
        for(int i=scanOffset;i<end;i++){Town town=towns.get(i);UUID id=town.getUUID();var city=attach(town,next.get(id));
            synchronized(town.getAccount()){if(!flows.contains(id))city=LedgerEngine.reconcile(city,Money.cents(town.getAccount().getHoldingBalance(true)),System.currentTimeMillis());}
            city=production(id,city);if(settings.notifyMayor()){var mayor=town.getMayor();Player player=mayor==null?null:mayor.getPlayer();String previous=Week.previous(System.currentTimeMillis());
                if(player!=null&&player.isOnline()&&previous.compareTo(Week.key(city.started()))>=0&&!previous.equals(city.notified())){city=city.notified(previous);notices.add(()->ru.neverland.core.SuiteScheduler.entity(plugin,player,()->{if(player.isOnline())Ui.tell(player,"Готов городской отчёт за неделю с "+previous+". Откройте /t treasury report "+previous);}));}}
            next.put(id,city);
        }
        boolean completed=end>=towns.size();scanOffset=completed?0:end;
        if(completed){var live=new HashSet<UUID>();for(var town:towns)live.add(town.getUUID());for(var id:new ArrayList<>(watched.keySet()))if(!live.contains(id)){var value=watched.remove(id);value.account().removeObserver(value.observer());}offers.values().removeIf(o->o.expires()<System.currentTimeMillis());}
        track(repo.commit(before,new TreasuryRepository.Image(next,before.debits(),before.flowGap())).thenAccept(ack->{try(var guard=ru.neverland.core.SuiteState.guard()){if(completed)initialized=true;if(!stopping)notices.forEach(Runnable::run);}}));
    }
    /** Returns an ACKed snapshot. It never reconciles or writes in the caller's stack. */
    public CityLedger sync(Town town){try(var guard=ru.neverland.core.SuiteState.guard()){ready();if(flows.contains(town.getUUID()))throw new IllegalStateException("Есть новые операции; дождитесь сохранения казны");var city=repo.cities().get(town.getUUID());if(city==null)throw new IllegalStateException("Казна города ещё загружается");return city;}}
    private boolean flushAsync(){
        var batch=flows.batch(f->!asynchronous.holdFlow(f.operation()),512);if(batch.isEmpty())return false;var before=repo.image();var next=applyFlows(before.cities(),batch);
        track(repo.commit(before,new TreasuryRepository.Image(next,before.debits(),before.flowGap())).thenAccept(ack->flows.remove(batch)));return true;
    }
    private Map<UUID,CityLedger> applyFlows(Map<UUID,CityLedger> current,List<Flow> batch){var next=new HashMap<>(current);
        for(var flow:batch){var city=next.get(flow.town());if(city==null)throw new IllegalStateException("Операция неизвестной казны");var tag=TAG.matcher(flow.reason());Budget category=Budget.FREE;String source="other";boolean refund=false;
            if(tag.matches()){category=Budget.parse(tag.group(2));source=tag.group(3);refund=tag.group(1).equals("refund");}
            else {String lower=flowReason(flow);if(settings.taxWords().stream().anyMatch(lower::contains))source="tax";}
            next.put(flow.town(),LedgerEngine.observe(city,flow.amount(),flow.deposit(),category,source,refund,flow.at(),flow.operation()));
        }return next;
    }
    void removeAsyncFlow(UUID id){flows.removeOperation(id);}
    void requireAsyncReady(UUID town){if(!asyncReady(town))throw new IllegalStateException("Казна ещё не готова или требует проверки");if(flows.contains(town))throw new IllegalStateException("Есть новые банковские операции; дождитесь сверки казны и повторите тот же ID");}
    boolean asyncReady(UUID town){return initialized&&!fault&&!stopping&&!repo.flowGap()&&repo.writable()&&!repo.busy()&&maintenance.toCompletableFuture().isDone()&&plugin.isEnabled()&&!ru.neverland.core.SuiteState.frozen(plugin.getName())&&repo.image().cities().containsKey(town);}
    /** Account-monitor caller; bounded immutable/JDK reads only, never acquire Suite here. */
    boolean rawBudgetAllows(AsyncDebit.Terms terms){synchronized(flows){
        if(fault||repo.flowGap())return false;var image=repo.peek();var city=image.cities().get(terms.town());if(city==null||city.pending()==null||!city.pending().id().equals(terms.operation()))return false;
        if(terms.kind()==AsyncDebit.Kind.REFUND){var backing=image.debits().get(terms.backingDebit());long deposits=flows.deposited(terms.town());return city.balance()<=Money.MAX-terms.cents()&&deposits<=Money.MAX-city.balance()-terms.cents()&&backing!=null&&backing.state()==AsyncDebit.State.PAID&&backing.terms().cents()==terms.cents()&&backing.terms().town().equals(terms.town());}
        long available=city.enabled()?city.funds().get(Budget.valueOf(terms.category().toUpperCase(Locale.ROOT))):city.balance();return available>=terms.cents()&&flows.withdrawn(terms.town())<=available-terms.cents();
    }}
    @Override public Set<String> capabilities(){return Set.of("canSpend","spend","treasury","forecast","requestDebit","debitStatus","requestRefund","refundStatus");}
    @Override public CompletionStage<Map<String,Object>> requestDebit(UUID operation,UUID town,String category,String source,long cents,String reason,UUID actor,String ownerPlugin){try(var guard=ru.neverland.core.SuiteState.guard()){return asynchronous.request(new AsyncDebit.Terms(operation,town,category,source,cents,reason,actor,ownerPlugin));}}
    @Override public CompletionStage<Map<String,Object>> requestRefund(UUID refundOperation,UUID paidDebitOperation,UUID actor,String ownerPlugin,String reason){return asynchronous.refund(refundOperation,paidDebitOperation,actor,ownerPlugin,reason);}
    @Override public Map<String,Object> refundStatus(UUID operation){return asynchronous.status(operation);}
    @Override public Map<String,Object> debitStatus(UUID operation){return asynchronous.status(operation);}
    public List<Map<String,Object>> asyncDebits(){return asynchronous.statuses();}
    public CompletionStage<Map<String,Object>> resolveAsync(UUID id,boolean paid,String actor){return asynchronous.resolve(id,paid,actor);}
    private static String flowReason(Flow flow){return flow.reason().toLowerCase(Locale.ROOT);}
    @Override public boolean canSpend(UUID id,String category,double amount){try(var guard=ru.neverland.core.SuiteState.guard()){
        if(repo.busy()||asynchronous.busy()||!initialized||fault||stopping||flows.contains(id))return false;var city=repo.cities().get(id);return city!=null&&TownyEconomyHandler.isActive()&&LedgerEngine.canSpend(city,settings.category(category),Money.cents(amount));
    }}
    /** Legacy same-call payment cannot promise a durable ACK; never launch a hidden payment after false. */
    @Override public boolean spend(UUID id,String category,String source,double amount,String reason){try(var guard=ru.neverland.core.SuiteState.guard()){
        if(!legacyWarned){legacyWarned=true;plugin.getLogger().warning("Старый spend без ID закрыт до обновления вызывающего аддона. Используйте requestDebit и дождитесь PAID; банк не вызывался.");}return false;
    }}
    private CityLedger production(UUID town,CityLedger city){
        var api=ru.neverland.core.ApiServices.connect("NeverLandTownyResources","ru.neverland.townyresources.api.TownyResourcesApi",1,"productionWeeks");if(!api.ready())return city;
        try{Object raw=api.invoke("productionWeeks",new Class<?>[]{UUID.class},town);if(!(raw instanceof Map<?,?> map))return city;
            Map<String,Map<String,Long>> totals=new HashMap<>();for(var e:map.entrySet()){Map<String,Long> values=new HashMap<>();for(var v:((Map<?,?>)e.getValue()).entrySet())values.put((String)v.getKey(),((Number)v.getValue()).longValue());totals.put((String)e.getKey(),values);}return city.production(totals);
        }catch(ReflectiveOperationException|LinkageError ex){return city;}
    }
    @Override public Optional<CityLedger> treasury(UUID town){ try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {return Optional.ofNullable(town==null?null:repo.cities().get(town));} }
    @Override public Map<String,Object> forecast(UUID town,int seconds){try(var guard=ru.neverland.core.SuiteState.guard()){
        primary();if(!plugin.isEnabled()||ru.neverland.core.SuiteState.frozen(plugin.getName())||!TownyEconomyHandler.isActive()||town==null||TownyAPI.getInstance().getTown(town)==null)throw new IllegalStateException("Прогноз казны временно недоступен");
        var city=repo.cities().get(town);if(city==null)throw new IllegalStateException("Казна ещё не сверена с экономикой");
        var sources=ForecastSources.read(town,seconds,settings);var issues=new ArrayList<>(sources.issues());if(fault)issues.add("Учёт казны приостановлен");if(city.pending()!=null)issues.add("Незавершённое списание казны требует сверки");if(flows.contains(town))issues.add("Появились новые операции; обновите прогноз через секунду");
        return TreasuryForecast.calculate(city,seconds,sources.items(),issues);
    }}
    private void versions(){Map<String,String> minimum=Map.of("NeverLandTownyBuilds","0.8.6","NeverLandTownyUpkeep","0.1.4","NeverLandTownyTrade","0.1.10","NeverLandTownyContracts","0.1.4","NeverLandTownyIdeologies","0.1.4","NeverLandTownyEspionage","0.1.4");for(var entry:minimum.entrySet()){var p=Bukkit.getPluginManager().getPlugin(entry.getKey());if(p!=null&&!versionAtLeast(p.getDescription().getVersion(),entry.getValue()))throw new IllegalArgumentException("Для контроля бюджета обновите "+entry.getKey()+" до "+entry.getValue());}}
    public static boolean versionAtLeast(String current,String wanted){ try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {try{var m=Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$").matcher(current);if(!m.matches())return false;var target=wanted.split("\\.");for(int i=0;i<3;i++){int a=Integer.parseInt(m.group(i+1)),b=Integer.parseInt(target[i]);if(a!=b)return a>b;}return true;}catch(Exception e){return false;}} }
    private CityLedger change(CityLedger city,String action,List<String> args,String actor){return switch(action){case "enable"->{versions();yield LedgerEngine.mode(city,true,actor);}case "disable"->LedgerEngine.mode(city,false,actor);case "transfer"->{if(args.size()!=3)throw new IllegalArgumentException("transfer <откуда> <куда> <сумма>");yield LedgerEngine.transfer(city,Budget.parse(args.get(0)),Budget.parse(args.get(1)),Money.parse(args.get(2)),actor);}case "shares"->{if(args.size()!=4)throw new IllegalArgumentException("shares <строительство%> <армия%> <инфраструктура%> <социальные%>");var shares=new EnumMap<Budget,Integer>(Budget.class);int i=0;for(var b:Budget.values())shares.put(b,b==Budget.FREE?0:Integer.parseInt(args.get(i++)));yield LedgerEngine.shares(city,shares,actor);}default->throw new IllegalArgumentException("Неизвестное действие");};}
    public Offer offer(Player player,UUID id,String action,List<String> args)throws Exception{ try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {ready();var town=TownyAPI.getInstance().getTown(id);if(!player.hasPermission("neverlandtownytreasury.use")||!manager(player,town))throw new IllegalArgumentException("Управлять бюджетом может мэр или уполномоченный своего города");var city=sync(town);change(city,action,args,player.getName());var offer=new Offer(id,city.revision(),System.currentTimeMillis()+60000,settings,action,List.copyOf(args));offers.put(player.getUniqueId(),offer);return offer;} }
    public Offer offer(Player player){ try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {var result=offers.get(player.getUniqueId());return result!=null&&result.expires()>=System.currentTimeMillis()?result:null;} }
    public CompletionStage<UUID> confirm(Player player)throws Exception{ try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) {ready();var offer=offers.remove(player.getUniqueId());if(offer==null||offer.expires()<System.currentTimeMillis()||offer.settings()!=settings)throw new IllegalArgumentException("Подтверждение устарело");var town=TownyAPI.getInstance().getTown(offer.town());if(!player.hasPermission("neverlandtownytreasury.use")||!manager(player,town))throw new IllegalArgumentException("Нет полномочий в этом городе");var city=sync(town);if(city.revision()!=offer.revision())throw new IllegalArgumentException("Казна изменилась. Проверьте суммы и повторите выбор.");return track(repo.put(offer.town(),change(city,offer.action(),offer.args(),player.getName())).thenApply(ack->offer.town()));} }
    public CompletionStage<Void> resolve(Player player,UUID town,UUID invoice,boolean paid)throws Exception{ try (var suiteGuard21 = ru.neverland.core.SuiteState.guard()) {ready();if(!player.hasPermission("neverlandtownytreasury.admin"))throw new IllegalArgumentException("Нужны права администратора");if(asynchronous.owns(invoice))throw new IllegalArgumentException("Используйте /townytreasury resolve-request <ID> paid|unpaid confirm после сверки этой квитанции");if(flows.contains(town))throw new IllegalStateException("Дождитесь сохранения новых операций");var city=repo.cities().get(town);var pending=city==null?null:city.pending();if(pending==null||!pending.id().equals(invoice))throw new IllegalArgumentException("Незавершённый счёт не найден");var next=paid?LedgerEngine.observe(city,pending.amount(),false,pending.category(),pending.source(),false,pending.at(),pending.id()):LedgerEngine.cancel(city,pending.id());next=next.next(next.balance(),next.enabled(),next.funds(),next.shares(),next.weeks(),null,player.getName()+": сверил "+invoice+" — "+(paid?"списано":"не списано"));return track(repo.put(town,next).thenApply(ack->null));} }
}
