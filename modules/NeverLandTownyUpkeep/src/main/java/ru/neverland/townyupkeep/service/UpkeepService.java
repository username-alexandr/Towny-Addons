package ru.neverland.townyupkeep.service;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townyupkeep.api.*;
import ru.neverland.townyupkeep.config.UpkeepSettings;
import ru.neverland.townyupkeep.data.UpkeepRepository;
import ru.neverland.townyupkeep.integration.CityBridge;
import ru.neverland.townyupkeep.model.*;
import ru.neverland.townyupkeep.model.Entry.*;
import ru.neverland.integration.SpecializationAccess;
import java.util.*;
public final class UpkeepService implements TownyUpkeepApi,PaymentProcessor.Store {
    private final JavaPlugin plugin;private final UpkeepRepository repo;private final CityBridge city=new CityBridge();private final PaymentProcessor payments;
    private UpkeepSettings settings;private long clock;private boolean fault;private BukkitTask task;
    private Map<Key,Cost> quotes=Map.of(),baseQuotes=Map.of();private Map<Key,Integer> levels=Map.of();
    private volatile Map<Key,UpkeepSnapshot> views=Map.of();
    public UpkeepService(JavaPlugin plugin,UpkeepRepository repo,UpkeepSettings settings){this.plugin=plugin;this.repo=repo;this.settings=settings;clock=repo.clock();payments=new PaymentProcessor(this,city);}
    public UpkeepSettings settings(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return settings;} }public boolean fault(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return fault;} }
    public void start()throws Exception{ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {city.verify();scan();task=ru.neverland.core.SuiteScheduler.get().runTaskTimer(plugin,this::pulse,20,20);} }
    public void stop(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {if(task!=null)task.cancel();if(!fault)try{repo.save(clock,repo.entries());}catch(Exception ex){plugin.getLogger().severe("Не сохранены часы обслуживания: "+ex.getMessage());}} }
    public void reload(UpkeepSettings next)throws Exception{ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        city.verify();var previous=settings;settings=next;try{scan();fault=false;publish();}catch(Exception ex){settings=previous;throw ex;}
    } }
    private void scan()throws Exception{
        Map<Key,Cost> nextQuotes=new HashMap<>(),nextBase=new HashMap<>();Map<Key,Integer> nextLevels=new HashMap<>();var entries=new HashMap<>(repo.entries());
        for(Town town:List.copyOf(TownyAPI.getInstance().getTowns())){
            var built=city.buildings(town.getUUID());int total=built.values().stream().mapToInt(Integer::intValue).sum();var factor=settings.factor(town.getNumTownBlocks(),town.getNumResidents(),total);
            for(var b:built.entrySet()){var p=settings.profiles().get(b.getKey());if(p==null)continue;var key=new Key(town.getUUID(),p.id());nextLevels.put(key,b.getValue());var base=p.cost().multiply(b.getValue(),factor);nextBase.put(key,base);nextQuotes.put(key,base.policy(ru.neverland.integration.PoliciesAccess.upkeep(key.town(),key.project())));entries.putIfAbsent(key,Entry.grace(clock+settings.grace()));}
        }
        // Keep records for existing towns even if a footprint is temporarily unclaimed/unloaded.
        // This preserves paid time and prevents reclaiming a footprint from granting another grace period.
        entries.entrySet().removeIf(e->TownyAPI.getInstance().getTown(e.getKey().town())==null&&e.getValue().invoice()==null);
        if(!entries.equals(repo.entries()))repo.save(clock,entries);baseQuotes=Map.copyOf(nextBase);quotes=Map.copyOf(nextQuotes);levels=Map.copyOf(nextLevels);publish();
    }
    private void pulse(){if(fault)return;try{
        clock=Math.addExact(clock,1);if(clock%5==0)scan();
        List<Key> due=repo.entries().entrySet().stream().filter(e->{var bill=e.getValue().invoice();if(bill!=null)return bill.phase()!=Phase.MONEY_PENDING;
            var p=settings.profiles().get(e.getKey().project());return !repo.paused().contains(e.getKey())&&p!=null&&p.enabled()&&SpecializationAccess.allowed(e.getKey().town(),e.getKey().project())&&levels.containsKey(e.getKey())&&e.getValue().due()<=clock;})
            .sorted(Comparator.<Map.Entry<Key,Entry>>comparingLong(e->e.getValue().due()).thenComparingInt(e->{var p=settings.profiles().get(e.getKey().project());return p==null?50:p.priority();}).thenComparing(e->e.getKey().town()+"/"+e.getKey().project())).map(Map.Entry::getKey).limit(settings.budget()).toList();
        for(var key:due){var e=get(key);if(e.invoice()==null){var p=settings.profiles().get(key.project());put(key,new Entry(false,clock,"Оплата содержания",new Invoice(UUID.randomUUID(),baseQuotes.get(key).policy(ru.neverland.integration.PoliciesAccess.upkeep(key.town(),key.project())),settings.period(),Phase.PREPARED)));}
            else if(e.invoice().phase()==Phase.PREPARED&&(!SpecializationAccess.allowed(key.town(),key.project())||!levels.containsKey(key)||!settings.profiles().containsKey(key.project())||!settings.profiles().get(key.project()).enabled()))payments.cancel(key,"Здание недоступно или обслуживание отключено");
            payments.process(key,clock,settings.retry());}
        if(clock-repo.clock()>=30)repo.save(clock,repo.entries());publish();
    }catch(Exception|LinkageError ex){fault=true;publish();plugin.getLogger().log(java.util.logging.Level.SEVERE,"Обслуживание приостановлено. Устраните причину и выполните /townyupkeep reload. Счета сохранены.",ex);}}
    private void publish(){Map<Key,UpkeepSnapshot> next=new HashMap<>();for(var row:levels.entrySet()){
        Key key=row.getKey();var p=settings.profiles().get(key.project());var e=get(key);if(p==null||e==null)continue;
        boolean active=!fault&&!repo.paused().contains(key)&&e.invoice()==null&&(!p.enabled()||(e.active()&&e.due()>clock));
        String status=fault?"Ошибка обслуживания — обратитесь к администратору":repo.paused().contains(key)?"Остановлено городом: производство и бонусы отключены":e.invoice()!=null&&e.invoice().phase()==Phase.MONEY_PENDING?"Списание требует сверки администратора":!p.enabled()?"Обслуживание отключено в настройках":active?e.reason():e.invoice()!=null?"Оплата ещё не завершена":e.active()?"Ожидает оплаты":e.reason();
        next.put(key,new UpkeepSnapshot(key,p.name(),p.icon(),row.getValue(),p.priority(),active,status,Math.max(0,e.due()-clock),quotes.get(key),e.invoice()));}views=Map.copyOf(next);}
    @Override public Entry get(Key key){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {return repo.entries().get(key);} }
    @Override public void put(Key key,Entry value)throws Exception{ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {var next=new HashMap<>(repo.entries());next.put(key,value);repo.save(clock,next);publish();} }
    public void retry(UUID town,String project)throws Exception{ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
        thread();var key=new Key(town,project);var view=views.get(key);if(view==null)throw new IllegalArgumentException("Построенное здание не найдено");var e=get(key);
        if(repo.paused().contains(key))throw new IllegalArgumentException("Сначала возобновите здание: /t upkeep resume "+project);
        if(e.invoice()!=null)throw new IllegalArgumentException("Предыдущий счёт ещё обрабатывается; при необходимости обратитесь к администратору");
        if(view.active())throw new IllegalArgumentException("Здание уже активно; следующий период ещё не наступил");put(key,new Entry(false,clock,"Поставлено в очередь оплаты",null));
    } }
    public void resolve(UUID town,String project,UUID invoice,boolean paid)throws Exception{ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {
        thread();Key key=new Key(town,project);var e=get(key);if(e==null||e.invoice()==null||!e.invoice().id().equals(invoice))throw new IllegalArgumentException("ID счёта не совпадает");payments.resolve(key,paid);payments.process(key,clock,settings.retry());
    } }
    private void thread(){if(!ru.neverland.core.SuiteState.held())throw new IllegalStateException("Нужен основной поток сервера");if(fault)throw new IllegalStateException("Сначала исправьте причину ошибки и выполните reload");}
    public List<Map.Entry<Key,Entry>> pending(){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {return repo.entries().entrySet().stream().filter(e->e.getValue().invoice()!=null).toList();} }
    public Town town(Player p){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {var r=TownyAPI.getInstance().getResident(p);return r==null?null:r.getTownOrNull();} }
    public boolean manager(Player p,Town t){ try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {var r=TownyAPI.getInstance().getResident(p);return r!=null&&t.equals(r.getTownOrNull())&&(t.isMayor(r)||ru.neverland.core.CouncilAccess.check(p,t.getUUID(),"upkeep",r.hasTownRank("assistant")||p.hasPermission("neverlandtownyupkeep.manage")));} }
    @Override public boolean active(UUID town,String project){ try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {if(town==null||project==null)return false;var v=views.get(new Key(town,project));return v!=null?v.active():!fault&&!settings.profiles().containsKey(project);} }
    @Override public List<UpkeepSnapshot> buildings(UUID town){ try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {return views.values().stream().filter(v->v.key().town().equals(town)).sorted(Comparator.comparingInt(UpkeepSnapshot::priority).thenComparing(v->v.key().project())).toList();} }
    public boolean paused(UUID town,String project){try(var guard=ru.neverland.core.SuiteState.guard()){return repo.paused().contains(new Key(town,project));}}
    public void suspend(UUID town,String project,boolean stop,UUID actor)throws Exception{try(var guard=ru.neverland.core.SuiteState.guard()){
        thread();var key=new Key(town,project);if(!views.containsKey(key)||TownyAPI.getInstance().getTown(town)==null)throw new IllegalArgumentException("Построенное здание не найдено");if(repo.paused().contains(key)==stop)return;
        repo.suspend(clock,key,stop);publish();String operation=UUID.randomUUID().toString();
        if(!ru.neverland.core.AuditTrail.record(plugin,"upkeep",operation,"UPKEEP_SAVINGS",stop?"SUSPENDED":"RESUMED",actor==null?ru.neverland.core.AuditRecord.Party.system():ru.neverland.core.AuditTrail.player(actor),ru.neverland.core.AuditTrail.town(town),ru.neverland.core.AuditTrail.town(town),"",0,"","Здание: "+project+"; оплаченный срок не продлевается и не возвращается"))plugin.getLogger().warning("Остановка здания сохранена; общий журнал требует проверки: "+operation);
    }}
    @Override public Collection<Map<String,Object>> plannedBills(UUID town,int seconds){try(var guard=ru.neverland.core.SuiteState.guard()){
        thread();BillingPreview.charges(0,settings.period(),seconds);if(!plugin.isEnabled()||ru.neverland.core.SuiteState.frozen(plugin.getName())||TownyAPI.getInstance().getTown(town)==null)throw new IllegalStateException("Прогноз обслуживания недоступен");
        var out=new ArrayList<Map<String,Object>>();for(var row:buildings(town)){
            var profile=settings.profiles().get(row.key().project());boolean stopped=repo.paused().contains(row.key()),enabled=profile!=null&&profile.enabled()&&SpecializationAccess.allowed(town,row.key().project());
            boolean known=row.invoice()==null;long count=enabled&&!stopped&&known?BillingPreview.charges(row.seconds(),settings.period(),seconds):0;
            var required=new TreeMap<String,Long>();row.cost().resources().forEach((id,n)->required.put(id,Math.multiplyExact(n,count)));
            out.add(Map.of("project",row.key().project(),"title",row.name(),"cents",Math.multiplyExact(row.cost().money(),count),"resources",Map.copyOf(required),"payments",count,"period",settings.period(),"due",row.seconds(),"known",known,"paused",stopped));
        }return List.copyOf(out);
    }}
    @Override public Map<String,Object> savings(UUID town,Set<String> stop,int seconds){try(var guard=ru.neverland.core.SuiteState.guard()){return BillingPreview.summary(plannedBills(town,seconds),Set.copyOf(stop));}}
    @Override public Map<String,Object> development(UUID town,Map<String,Integer> plannedLevels){try(var guard=ru.neverland.core.SuiteState.guard()){
        try{
            var planned=DevelopmentEstimate.levels(plannedLevels);var settlement=TownyAPI.getInstance().getTown(town);
            if(fault||!plugin.isEnabled()||ru.neverland.core.SuiteState.frozen(plugin.getName())||settlement==null)throw new IllegalStateException("Прогноз содержания временно недоступен");
            var policies=forecastProvider("NeverLandTownyPolicies","ru.neverland.townypolicies.api.TownyPoliciesApi","upkeepMultiplier");
            forecastProvider("NeverLandTownySpecialization","ru.neverland.townyspecialization.api.TownySpecializationApi","canUseBuilding");
            var sites=city.developmentSites(town);var current=new HashMap<String,Integer>();for(var e:sites.entrySet()){var site=e.getValue();if(site.completedLevel()>5)throw new IllegalArgumentException("Прогноз поддерживает итоговые уровни от I до V");if(planned.containsKey(e.getKey())&&planned.get(e.getKey())<site.completedLevel())throw new IllegalArgumentException("Прогноз строительства не поддерживает понижение уровня");if(site.completedLevel()>0&&site.owned()&&site.specialized())current.put(e.getKey(),site.completedLevel());}
            var ids=new TreeSet<>(current.keySet());ids.addAll(planned.keySet());var allowed=new HashMap<String,Boolean>();var multipliers=new HashMap<String,Double>();var stopped=new HashSet<String>();
            for(String id:ids){var site=sites.get(id);allowed.put(id,SpecializationAccess.allowed(town,id)&&(site==null||site.owned()));if(repo.paused().contains(new Key(town,id)))stopped.add(id);double multiplier=1;
                if(policies.ready()){Object raw=policies.invoke("upkeepMultiplier",new Class<?>[]{UUID.class,String.class},town,id);if(!(raw instanceof Number n))throw new IllegalStateException("Не удалось проверить городские правила содержания");multiplier=n.doubleValue();}multipliers.put(id,multiplier);
            }
            var result=new LinkedHashMap<>(DevelopmentEstimate.calculate(settings,settlement.getNumTownBlocks(),settlement.getNumResidents(),current,planned,stopped,allowed,multipliers));
            var rows=new ArrayList<Map<String,Object>>();for(Object raw:(List<?>)result.get("buildings")){@SuppressWarnings("unchecked")var row=new LinkedHashMap<>((Map<String,Object>)raw);var site=sites.get((String)row.get("project"));row.put("siteState",site==null?"UNPLACED":site.owned()?"EXISTING_OWNED":"EXISTING_FOREIGN");if(site!=null){row.put("beforeLevel",site.completedLevel());if(!site.owned())row.put("reason","Площадка не принадлежит городу; прогноз не предполагает её захват");}rows.add(Map.copyOf(row));}result.put("buildings",List.copyOf(rows));
            var invoices=new ArrayList<Map<String,Object>>();for(var entry:repo.entries().entrySet())if(entry.getKey().town().equals(town)&&entry.getValue().invoice()!=null){var bill=entry.getValue().invoice();invoices.add(Map.of("project",entry.getKey().project(),"id",bill.id().toString(),"cents",bill.cost().money(),"resources",bill.cost().resources(),"phase",bill.phase().name()));}
            result.put("existingInvoices",List.copyOf(invoices));return Map.copyOf(result);
        }catch(Exception|LinkageError unavailable){return Map.of("known",false,"reason",unavailable instanceof IllegalArgumentException?unavailable.getMessage():"Прогноз содержания недоступен: проверьте состояние города и связанных модулей","periodSeconds",settings.period());}
    }}
    private static ru.neverland.core.ApiServices.Connection forecastProvider(String module,String api,String capability){
        var connection=ru.neverland.core.ApiServices.connect(module,api,1,capability);if(!connection.ready()&&connection.state()!=ru.neverland.core.ApiServices.State.NOT_INSTALLED)throw new IllegalStateException("Поставщик прогноза недоступен");return connection;
    }
}
