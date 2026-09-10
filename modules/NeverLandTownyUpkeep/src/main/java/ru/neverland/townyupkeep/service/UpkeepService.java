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
import java.util.*;
public final class UpkeepService implements TownyUpkeepApi,PaymentProcessor.Store {
    private final JavaPlugin plugin;private final UpkeepRepository repo;private final CityBridge city=new CityBridge();private final PaymentProcessor payments;
    private UpkeepSettings settings;private long clock;private boolean fault;private BukkitTask task;
    private Map<Key,Cost> quotes=Map.of();private Map<Key,Integer> levels=Map.of();
    private volatile Map<Key,UpkeepSnapshot> views=Map.of();
    public UpkeepService(JavaPlugin plugin,UpkeepRepository repo,UpkeepSettings settings){this.plugin=plugin;this.repo=repo;this.settings=settings;clock=repo.clock();payments=new PaymentProcessor(this,city);}
    public UpkeepSettings settings(){return settings;}public boolean fault(){return fault;}
    public void start()throws Exception{city.verify();scan();task=Bukkit.getScheduler().runTaskTimer(plugin,this::pulse,20,20);}
    public void stop(){if(task!=null)task.cancel();if(!fault)try{repo.save(clock,repo.entries());}catch(Exception ex){plugin.getLogger().severe("Не сохранены часы обслуживания: "+ex.getMessage());}}
    public void reload(UpkeepSettings next)throws Exception{
        city.verify();var previous=settings;settings=next;try{scan();fault=false;publish();}catch(Exception ex){settings=previous;throw ex;}
    }
    private void scan()throws Exception{
        Map<Key,Cost> nextQuotes=new HashMap<>();Map<Key,Integer> nextLevels=new HashMap<>();var entries=new HashMap<>(repo.entries());
        for(Town town:List.copyOf(TownyAPI.getInstance().getTowns())){
            var built=city.buildings(town.getUUID());int total=built.values().stream().mapToInt(Integer::intValue).sum();var factor=settings.factor(town.getNumTownBlocks(),town.getNumResidents(),total);
            for(var b:built.entrySet()){var p=settings.profiles().get(b.getKey());if(p==null)continue;var key=new Key(town.getUUID(),p.id());nextLevels.put(key,b.getValue());nextQuotes.put(key,p.cost().multiply(b.getValue(),factor));entries.putIfAbsent(key,Entry.grace(clock+settings.grace()));}
        }
        // Keep records for existing towns even if a footprint is temporarily unclaimed/unloaded.
        // This preserves paid time and prevents reclaiming a footprint from granting another grace period.
        entries.entrySet().removeIf(e->TownyAPI.getInstance().getTown(e.getKey().town())==null&&e.getValue().invoice()==null);
        if(!entries.equals(repo.entries()))repo.save(clock,entries);quotes=Map.copyOf(nextQuotes);levels=Map.copyOf(nextLevels);publish();
    }
    private void pulse(){if(fault)return;try{
        clock=Math.addExact(clock,1);if(clock%5==0)scan();
        List<Key> due=repo.entries().entrySet().stream().filter(e->{var bill=e.getValue().invoice();if(bill!=null)return bill.phase()!=Phase.MONEY_PENDING;
            var p=settings.profiles().get(e.getKey().project());return p!=null&&p.enabled()&&levels.containsKey(e.getKey())&&e.getValue().due()<=clock;})
            .sorted(Comparator.<Map.Entry<Key,Entry>>comparingLong(e->e.getValue().due()).thenComparingInt(e->{var p=settings.profiles().get(e.getKey().project());return p==null?50:p.priority();}).thenComparing(e->e.getKey().town()+"/"+e.getKey().project())).map(Map.Entry::getKey).limit(settings.budget()).toList();
        for(var key:due){var e=get(key);if(e.invoice()==null){var p=settings.profiles().get(key.project());put(key,new Entry(false,clock,"Оплата содержания",new Invoice(UUID.randomUUID(),quotes.get(key),settings.period(),Phase.PREPARED)));}
            else if(e.invoice().phase()==Phase.PREPARED&&(!levels.containsKey(key)||!settings.profiles().containsKey(key.project())||!settings.profiles().get(key.project()).enabled()))payments.cancel(key,"Здание недоступно или обслуживание отключено");
            payments.process(key,clock,settings.retry());}
        if(clock-repo.clock()>=30)repo.save(clock,repo.entries());publish();
    }catch(Exception|LinkageError ex){fault=true;publish();plugin.getLogger().log(java.util.logging.Level.SEVERE,"Обслуживание приостановлено. Устраните причину и выполните /townyupkeep reload. Счета сохранены.",ex);}}
    private void publish(){Map<Key,UpkeepSnapshot> next=new HashMap<>();for(var row:levels.entrySet()){
        Key key=row.getKey();var p=settings.profiles().get(key.project());var e=get(key);if(p==null||e==null)continue;
        boolean active=!fault&&e.invoice()==null&&(!p.enabled()||(e.active()&&e.due()>clock));
        String status=fault?"Ошибка обслуживания — обратитесь к администратору":e.invoice()!=null&&e.invoice().phase()==Phase.MONEY_PENDING?"Списание требует сверки администратора":!p.enabled()?"Обслуживание отключено в настройках":active?e.reason():e.invoice()!=null?"Оплата ещё не завершена":e.active()?"Ожидает оплаты":e.reason();
        next.put(key,new UpkeepSnapshot(key,p.name(),p.icon(),row.getValue(),p.priority(),active,status,Math.max(0,e.due()-clock),quotes.get(key),e.invoice()));}views=Map.copyOf(next);}
    @Override public Entry get(Key key){return repo.entries().get(key);}
    @Override public void put(Key key,Entry value)throws Exception{var next=new HashMap<>(repo.entries());next.put(key,value);repo.save(clock,next);publish();}
    public void retry(UUID town,String project)throws Exception{
        thread();var key=new Key(town,project);var view=views.get(key);if(view==null)throw new IllegalArgumentException("Построенное здание не найдено");var e=get(key);
        if(e.invoice()!=null)throw new IllegalArgumentException("Предыдущий счёт ещё обрабатывается; при необходимости обратитесь к администратору");
        if(view.active())throw new IllegalArgumentException("Здание уже активно; следующий период ещё не наступил");put(key,new Entry(false,clock,"Поставлено в очередь оплаты",null));
    }
    public void resolve(UUID town,String project,UUID invoice,boolean paid)throws Exception{
        thread();Key key=new Key(town,project);var e=get(key);if(e==null||e.invoice()==null||!e.invoice().id().equals(invoice))throw new IllegalArgumentException("ID счёта не совпадает");payments.resolve(key,paid);payments.process(key,clock,settings.retry());
    }
    private void thread(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Нужен основной поток сервера");if(fault)throw new IllegalStateException("Сначала исправьте причину ошибки и выполните reload");}
    public List<Map.Entry<Key,Entry>> pending(){return repo.entries().entrySet().stream().filter(e->e.getValue().invoice()!=null).toList();}
    public Town town(Player p){var r=TownyAPI.getInstance().getResident(p);return r==null?null:r.getTownOrNull();}
    public boolean manager(Player p,Town t){var r=TownyAPI.getInstance().getResident(p);return r!=null&&t.equals(r.getTownOrNull())&&(t.isMayor(r)||r.hasTownRank("assistant")||p.hasPermission("neverlandtownyupkeep.manage"));}
    @Override public boolean active(UUID town,String project){if(town==null||project==null)return false;var v=views.get(new Key(town,project));return v!=null?v.active():!fault&&!settings.profiles().containsKey(project);}
    @Override public List<UpkeepSnapshot> buildings(UUID town){return views.values().stream().filter(v->v.key().town().equals(town)).sorted(Comparator.comparingInt(UpkeepSnapshot::priority).thenComparing(v->v.key().project())).toList();}
}
