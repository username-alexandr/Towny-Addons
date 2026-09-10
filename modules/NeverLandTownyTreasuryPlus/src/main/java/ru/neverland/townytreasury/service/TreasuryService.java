package ru.neverland.townytreasury.service;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.economy.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.*;
import ru.neverland.townytreasury.api.*;
import ru.neverland.townytreasury.config.TreasurySettings;
import ru.neverland.townytreasury.data.TreasuryRepository;
import ru.neverland.townytreasury.model.*;
public final class TreasuryService implements TownyTreasuryApi {
    private final Plugin plugin;private final TreasuryRepository repo;private TreasurySettings settings;private BukkitTask task;private volatile boolean fault;
    private record Watched(Account account,AccountObserver observer){}
    private final Map<UUID,Watched> watched=new HashMap<>();
    private record Flow(UUID town,long at,long amount,boolean deposit,String reason,UUID operation){}
    private final ConcurrentLinkedQueue<Flow> flows=new ConcurrentLinkedQueue<>();
    private static final Pattern TAG=Pattern.compile("^\\[NLT\\|([a-z0-9-]+)\\|([a-z_]+)\\|([a-z0-9_/-]+)\\] ?(.*)$",Pattern.DOTALL);
    private static final class Attempt {final UUID town,id;boolean observed;Attempt(UUID town,UUID id){this.town=town;this.id=id;}}
    private final ThreadLocal<Attempt> attempt=new ThreadLocal<>();
    public record Offer(UUID town,long revision,long expires,TreasurySettings settings,String action,List<String> args){}
    private final Map<UUID,Offer> offers=new HashMap<>();
    public TreasuryService(Plugin plugin,TreasuryRepository repo,TreasurySettings settings){this.plugin=plugin;this.repo=repo;this.settings=settings;}
    public boolean fault(){return fault;}public TreasurySettings settings(){return settings;}public Map<UUID,CityLedger> cities(){return repo.cities();}
    private void primary(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Нужен основной поток сервера");}
    private void ready(){primary();if(fault)throw new IllegalStateException("Учёт казны требует восстановления; обратитесь к администратору");}
    public Town town(Player player){var resident=TownyAPI.getInstance().getResident(player);return resident==null?null:resident.getTownOrNull();}
    public boolean manager(Player player,Town town){var r=TownyAPI.getInstance().getResident(player);return r!=null&&town!=null&&town.equals(r.getTownOrNull())&&(town.isMayor(r)||player.hasPermission("neverlandtownytreasury.manage"));}
    public void start()throws Exception{primary();scan();task=Bukkit.getScheduler().runTaskTimer(plugin,this::pulse,20,20);}
    private int ticks;
    private void pulse(){if(fault)return;try{flush();if(++ticks%5==0)scan();}catch(Exception ex){fail(ex);}}
    private void fail(Exception ex){fault=true;plugin.getLogger().log(java.util.logging.Level.SEVERE,"Учёт казны приостановлен; новые бюджетные списания закрыты. Сохранённые счета требуют проверки.",ex);}
    public void stop(){if(task!=null)task.cancel();for(var value:watched.values())value.account().removeObserver(value.observer());watched.clear();try{flush();}catch(Exception ex){fail(ex);}offers.clear();}
    public void reload(TreasurySettings value)throws Exception{primary();var previous=settings;flush();settings=value;offers.clear();fault=false;try{scan();}catch(Exception ex){settings=previous;fault=true;throw ex;}}
    private void attach(Town town)throws Exception{UUID id=town.getUUID();Account account=town.getAccount();var old=watched.get(id);if(old!=null&&old.account()==account)return;if(old!=null)old.account().removeObserver(old.observer());
        var observer=new AccountObserver(){
            public void withdrew(Account source,double amount,String reason){capture(source,amount,reason,false);}
            public void deposited(Account source,double amount,String reason){capture(source,amount,reason,true);}
            private void capture(Account source,double amount,String reason,boolean deposit){
                // Closed economy sends a second notification for the server bank to these observers.
                if(source!=account)return;try{long cents=Money.cents(amount);if(cents<=0)return;String text=reason==null?"":reason;UUID operation=null;var match=TAG.matcher(text);if(match.matches())try{operation=UUID.fromString(match.group(1));}catch(IllegalArgumentException ignored){}
                    var current=attempt.get();if(!deposit&&current!=null&&current.town.equals(id)&&current.id.equals(operation))current.observed=true;
                    flows.add(new Flow(id,System.currentTimeMillis(),cents,deposit,text,operation));
                }catch(RuntimeException ex){fault=true;plugin.getLogger().severe("Некорректная операция казны: "+ex.getMessage());}}
        };
        synchronized(account){if(!repo.cities().containsKey(id))repo.put(id,CityLedger.initial(Money.cents(account.getHoldingBalance(true)),settings.shares(),System.currentTimeMillis()));account.addObserver(observer);watched.put(id,new Watched(account,observer));}
    }
    private void scan()throws Exception{primary();if(!TownyEconomyHandler.isActive())return;Set<UUID> live=new HashSet<>();for(Town town:List.copyOf(TownyAPI.getInstance().getTowns())){live.add(town.getUUID());attach(town);if(TownyEconomyHandler.isActive())sync(town);production(town.getUUID());notifyReport(town);}for(var id:new ArrayList<>(watched.keySet()))if(!live.contains(id)){var value=watched.remove(id);value.account().removeObserver(value.observer());}offers.values().removeIf(o->o.expires()<System.currentTimeMillis());}
    public CityLedger sync(Town town)throws Exception{primary();attach(town);synchronized(town.getAccount()){flush();var city=repo.cities().get(town.getUUID());if(TownyEconomyHandler.isActive()){var next=LedgerEngine.reconcile(city,Money.cents(town.getAccount().getHoldingBalance(true)),System.currentTimeMillis());repo.put(town.getUUID(),next);return next;}return city;}}

    private void flush()throws Exception{primary();while(!flows.isEmpty()){
        var batch=flows.stream().limit(512).toList();var next=new HashMap<>(repo.cities());
        for(var flow:batch){var city=next.get(flow.town());if(city==null)throw new IllegalStateException("Операция неизвестной казны");var tag=TAG.matcher(flow.reason());Budget category=Budget.FREE;String source="other";boolean refund=false;
            if(tag.matches()){category=Budget.parse(tag.group(2));source=tag.group(3);refund=tag.group(1).equals("refund");}
            else {String lower=flowReason(flow);if(settings.taxWords().stream().anyMatch(lower::contains))source="tax";}
            next.put(flow.town(),LedgerEngine.observe(city,flow.amount(),flow.deposit(),category,source,refund,flow.at(),flow.operation()));
        }
        repo.replace(next);for(int i=0;i<batch.size();i++)flows.remove();
    }}
    private static String flowReason(Flow flow){return flow.reason().toLowerCase(Locale.ROOT);}
    @Override public boolean canSpend(UUID id,String category,double amount){ready();var town=TownyAPI.getInstance().getTown(id);if(town==null||!TownyEconomyHandler.isActive())return false;try{return LedgerEngine.canSpend(sync(town),settings.category(category),Money.cents(amount));}catch(Exception ex){throw new IllegalStateException(ex.getMessage(),ex);}}
    @Override public boolean spend(UUID id,String category,String source,double amount,String reason){ready();long cents=Money.positive(Money.cents(amount));if(cents==0)return true;Town town=TownyAPI.getInstance().getTown(id);if(town==null||!TownyEconomyHandler.isActive())return false;Budget budget=settings.category(category);UUID operation=UUID.randomUUID();var observed=new Attempt(id,operation);

        synchronized(town.getAccount()){
            try{sync(town);}catch(Exception ex){fail(ex);return false;}
            var pending=new CityLedger.Pending(operation,System.currentTimeMillis(),cents,budget,source,reason);
            var journal=new BudgetDebit.Journal(){
                public CityLedger get(){return repo.cities().get(id);}
                public void put(CityLedger value)throws Exception{repo.put(id,value);}
                public void flush()throws Exception{TreasuryService.this.flush();}
                public void fault(Exception error){fail(error);}
            };
            attempt.set(observed);
            try{return BudgetDebit.execute(journal,pending,()->town.getAccount().withdraw(cents/100.0,"[NLT|"+operation+"|"+budget.id()+"|"+source+"] "+reason),()->observed.observed);}
            finally{attempt.remove();}
        }
    }
    private void production(UUID town)throws Exception{
        var plugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyResources");if(plugin==null||!plugin.isEnabled())return;
        try{Class<?> api=Class.forName("ru.neverland.townyresources.api.TownyResourcesApi",true,plugin.getClass().getClassLoader());Object service=Bukkit.getServicesManager().load(api);if(service==null)return;Object raw=api.getMethod("productionWeeks",UUID.class).invoke(service,town);if(!(raw instanceof Map<?,?> map))return;
            Map<String,Map<String,Long>> totals=new HashMap<>();for(var e:map.entrySet()){Map<String,Long> values=new HashMap<>();for(var v:((Map<?,?>)e.getValue()).entrySet())values.put((String)v.getKey(),((Number)v.getValue()).longValue());totals.put((String)e.getKey(),values);}repo.put(town,repo.cities().get(town).production(totals));
        }catch(ReflectiveOperationException|LinkageError ex){/* Older/temporarily unavailable Resources leaves explicit coverage flags. */}
    }
    private void notifyReport(Town town)throws Exception{if(!settings.notifyMayor())return;var mayor=town.getMayor();Player player=mayor==null?null:mayor.getPlayer();if(player==null||!player.isOnline())return;var city=repo.cities().get(town.getUUID());String previous=Week.previous(System.currentTimeMillis());if(previous.compareTo(Week.key(city.started()))<0||previous.equals(city.notified()))return;repo.put(town.getUUID(),city.notified(previous));Ui.tell(player,"Готов городской отчёт за неделю с "+previous+". Откройте /t treasury report "+previous);}
    @Override public Optional<CityLedger> treasury(UUID town){return Optional.ofNullable(town==null?null:repo.cities().get(town));}
    private void versions(){Map<String,String> minimum=Map.of("NeverLandTownyBuilds","0.8.6","NeverLandTownyUpkeep","0.1.4","NeverLandTownyTrade","0.1.10","NeverLandTownyContracts","0.1.4","NeverLandTownyIdeologies","0.1.4","NeverLandTownyEspionage","0.1.4");for(var entry:minimum.entrySet()){var p=Bukkit.getPluginManager().getPlugin(entry.getKey());if(p!=null&&!versionAtLeast(p.getDescription().getVersion(),entry.getValue()))throw new IllegalArgumentException("Для контроля бюджета обновите "+entry.getKey()+" до "+entry.getValue());}}
    public static boolean versionAtLeast(String current,String wanted){try{var m=Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$").matcher(current);if(!m.matches())return false;var target=wanted.split("\\.");for(int i=0;i<3;i++){int a=Integer.parseInt(m.group(i+1)),b=Integer.parseInt(target[i]);if(a!=b)return a>b;}return true;}catch(Exception e){return false;}}
    private CityLedger change(CityLedger city,String action,List<String> args,String actor){return switch(action){case "enable"->{versions();yield LedgerEngine.mode(city,true,actor);}case "disable"->LedgerEngine.mode(city,false,actor);case "transfer"->{if(args.size()!=3)throw new IllegalArgumentException("transfer <откуда> <куда> <сумма>");yield LedgerEngine.transfer(city,Budget.parse(args.get(0)),Budget.parse(args.get(1)),Money.parse(args.get(2)),actor);}case "shares"->{if(args.size()!=4)throw new IllegalArgumentException("shares <строительство%> <армия%> <инфраструктура%> <социальные%>");var shares=new EnumMap<Budget,Integer>(Budget.class);int i=0;for(var b:Budget.values())shares.put(b,b==Budget.FREE?0:Integer.parseInt(args.get(i++)));yield LedgerEngine.shares(city,shares,actor);}default->throw new IllegalArgumentException("Неизвестное действие");};}
    public Offer offer(Player player,UUID id,String action,List<String> args)throws Exception{ready();var town=TownyAPI.getInstance().getTown(id);if(!player.hasPermission("neverlandtownytreasury.use")||!manager(player,town))throw new IllegalArgumentException("Управлять бюджетом может мэр или уполномоченный своего города");var city=sync(town);change(city,action,args,player.getName());var offer=new Offer(id,city.revision(),System.currentTimeMillis()+60000,settings,action,List.copyOf(args));offers.put(player.getUniqueId(),offer);return offer;}
    public Offer offer(Player player){var result=offers.get(player.getUniqueId());return result!=null&&result.expires()>=System.currentTimeMillis()?result:null;}
    public UUID confirm(Player player)throws Exception{ready();var offer=offers.remove(player.getUniqueId());if(offer==null||offer.expires()<System.currentTimeMillis()||offer.settings()!=settings)throw new IllegalArgumentException("Подтверждение устарело");var town=TownyAPI.getInstance().getTown(offer.town());if(!player.hasPermission("neverlandtownytreasury.use")||!manager(player,town))throw new IllegalArgumentException("Нет полномочий в этом городе");var city=sync(town);if(city.revision()!=offer.revision())throw new IllegalArgumentException("Казна изменилась. Проверьте суммы и повторите выбор.");repo.put(offer.town(),change(city,offer.action(),offer.args(),player.getName()));return offer.town();}
    public void resolve(Player player,UUID town,UUID invoice,boolean paid)throws Exception{ready();if(!player.hasPermission("neverlandtownytreasury.admin"))throw new IllegalArgumentException("Нужны права администратора");flush();var city=repo.cities().get(town);var pending=city==null?null:city.pending();if(pending==null||!pending.id().equals(invoice))throw new IllegalArgumentException("Незавершённый счёт не найден");var next=paid?LedgerEngine.observe(city,pending.amount(),false,pending.category(),pending.source(),false,pending.at(),pending.id()):LedgerEngine.cancel(city,pending.id());next=next.next(next.balance(),next.enabled(),next.funds(),next.shares(),next.weeks(),null,player.getName()+": сверил "+invoice+" — "+(paid?"списано":"не списано"));repo.put(town,next);var t=TownyAPI.getInstance().getTown(town);if(t!=null)sync(t);}
}
