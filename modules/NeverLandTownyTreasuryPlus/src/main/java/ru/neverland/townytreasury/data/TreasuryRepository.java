package ru.neverland.townytreasury.data;
import org.bukkit.configuration.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townytreasury.model.*;
import ru.neverland.townytreasury.debits.AsyncDebit;
import ru.neverland.townytreasury.debits.SpendingAdmission;
import ru.neverland.core.*;
import java.util.concurrent.*;
import java.util.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
public final class TreasuryRepository {
    private final Path file;private boolean writable,busy,closing;private volatile boolean flowGap;private final DurableSnapshots writer;
    public record Image(Map<UUID,CityLedger> cities,Map<UUID,AsyncDebit> debits,boolean flowGap){public Image(Map<UUID,CityLedger> cities,Map<UUID,AsyncDebit> debits){this(cities,debits,false);}public Image{
        cities=Map.copyOf(cities);debits=Map.copyOf(debits);if(debits.size()>AsyncDebit.MAX_RECEIPTS)throw new IllegalArgumentException("Достигнут предел квитанций казны");var refunds=new HashSet<UUID>();
        for(var e:debits.entrySet()){var d=e.getValue();var t=d.terms();var city=cities.get(t.town());if(!e.getKey().equals(t.operation())||city==null)throw new IllegalArgumentException("Неверная связь квитанции и города");
            if(d.unresolved()){var pending=city.pending();if(pending==null||!pending.id().equals(t.operation())||pending.amount()!=t.cents()||pending.category()!=Budget.parse(t.category())||!pending.source().equals(t.source())||!pending.reason().equals(t.reason()))throw new IllegalArgumentException("Резерв бюджета не соответствует активной квитанции");}
            if(t.kind()==AsyncDebit.Kind.REFUND){var original=debits.get(t.backingDebit());if(original==null||original.state()!=AsyncDebit.State.PAID||original.terms().kind()!=AsyncDebit.Kind.DEBIT)throw new IllegalArgumentException("У возврата нет оплаченного обеспечения");var backing=original.terms();if(t.cents()!=backing.cents()||!t.town().equals(backing.town())||!t.category().equals(backing.category())||!t.source().equals(backing.source())||!t.owner().equals(backing.owner()))throw new IllegalArgumentException("Условия возврата отличаются от обеспечения");if(d.state()!=AsyncDebit.State.REJECTED&&!refunds.add(t.backingDebit()))throw new IllegalArgumentException("Повтор обеспеченного возврата");}
        }
        for(var e:cities.entrySet()){var pending=e.getValue().pending();if(pending==null)continue;var receipt=debits.get(pending.id());if(receipt!=null&&(!receipt.unresolved()||!receipt.terms().town().equals(e.getKey())))throw new IllegalArgumentException("Резерв связан с завершённой квитанцией или чужим городом");}
    }}
    private volatile Image image=new Image(Map.of(),Map.of());
    public TreasuryRepository(Path file){this.file=file;writer=new DurableSnapshots(file,"nlt-treasury-state",2);}public Map<UUID,CityLedger> cities(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return image.cities();} }
    public void load()throws Exception{if(SuiteState.held())throw new IllegalStateException("Чтение казны должно быть вне общей блокировки");writable=false;if(!Files.exists(file)){image=new Image(Map.of(),Map.of());ru.neverland.core.AtomicFiles.loaded(file);writable=true;return;}var y=new YamlConfiguration();y.load(file.toFile());if(number(y,"schema")!=1)throw new IOException("Неверная схема казны");var root=section(y,"cities");var next=new HashMap<UUID,CityLedger>();for(String id:root.getKeys(false)){var p=section(root,id);Map<Budget,Long> funds=new EnumMap<>(Budget.class);Map<Budget,Integer> shares=new EnumMap<>(Budget.class);for(var b:Budget.values()){funds.put(b,number(p,"funds."+b.id()));shares.put(b,Math.toIntExact(number(p,"shares."+b.id())));}Map<String,Week> weeks=new HashMap<>();var w=section(p,"weeks");for(String key:w.getKeys(false)){var v=section(w,key);Map<Budget,Long> categories=new EnumMap<>(Budget.class);for(var b:Budget.values())categories.put(b,number(v,"categories."+b.id()));weeks.put(key,new Week(values(section(v,"income")),values(section(v,"expenses")),categories,number(v,"refunds"),number(v,"adjustments"),values(section(v,"production")),bool(v,"production-known")));}
        CityLedger.Pending pending=null;if(p.contains("pending")){var v=section(p,"pending");pending=new CityLedger.Pending(UUID.fromString(v.getString("id")),number(v,"at"),number(v,"amount"),Budget.parse(v.getString("category")),v.getString("source"),v.getString("reason"));}
        Object history=p.get("history");if(!(history instanceof List<?> list)||list.stream().anyMatch(v->!(v instanceof String)))throw new IOException("Неверная история бюджета");next.put(UUID.fromString(id),new CityLedger(number(p,"balance"),bool(p,"enabled"),funds,shares,number(p,"revision"),number(p,"started"),Objects.requireNonNull(p.getString("notified")),weeks,pending,p.getStringList("history")));}var receipts=new HashMap<UUID,AsyncDebit>();if(y.contains("async-debits")){var records=section(y,"async-debits");for(String raw:records.getKeys(false)){var r=section(records,raw);UUID id=UUID.fromString(raw);var terms=new AsyncDebit.Terms(id,UUID.fromString(r.getString("town")),r.getString("category"),r.getString("source"),number(r,"cents"),r.getString("reason"),UUID.fromString(r.getString("actor")),r.getString("owner"),AsyncDebit.Kind.valueOf(r.getString("kind","DEBIT")),r.getString("backing-debit","").isEmpty()?null:UUID.fromString(r.getString("backing-debit")));receipts.put(id,new AsyncDebit(terms,AsyncDebit.State.valueOf(r.getString("state")),number(r,"created-at"),number(r,"changed-at"),r.getString("review",""),r.contains("audited")?bool(r,"audited"):false,r.contains("spending-admission")?SpendingAdmission.read(section(r,"spending-admission").getValues(false)):SpendingAdmission.legacy()));}}image=new Image(next,receipts,y.contains("flow-gap")?bool(y,"flow-gap"):false);flowGap=image.flowGap();ru.neverland.core.AtomicFiles.loaded(file);writable=true;}
    /** Immutable volatile snapshot for observer/bank code; it never acquires Suite. */
    public Image peek(){return image;}
    /** Observer-side overflow closes spending immediately; the next ACK persists the diagnostic. */
    public void noteFlowGap(){flowGap=true;}
    public boolean flowGap(){return flowGap;}
    public boolean gapDurable(){return image.flowGap();}
    public Image image(){try(var g=SuiteState.guard()){return image;}}
    public boolean busy(){try(var g=SuiteState.guard()){return busy;}}
    public boolean writable(){try(var g=SuiteState.guard()){return writable&&!closing&&AtomicFiles.writable(file);}}
    public CompletionStage<Image> commit(Image expected,Image next){try(var g=SuiteState.guard()){
        if(!writable())return CompletableFuture.failedFuture(new IOException("Казна недоступна для записи"));if(busy)return CompletableFuture.failedFuture(new IllegalStateException("Казна сохраняет предыдущее изменение"));if(image!=expected)return CompletableFuture.failedFuture(new IllegalStateException("Казна изменилась; повторите запрос с тем же ID"));var value=flowGap&&!next.flowGap()?new Image(next.cities(),next.debits(),true):next;if(image.flowGap()&&!value.flowGap())return CompletableFuture.failedFuture(new IllegalStateException("Нельзя очистить диагностический разрыв учёта"));if(image.equals(value))return CompletableFuture.completedFuture(image);busy=true;
        return writer.submit(()->encode(value)).handle((ack,error)->{try(var lock=SuiteState.guard()){busy=false;if(error!=null){writable=false;throw new CompletionException(error);}image=value;return value;}});
    }}
    public CompletionStage<Void> closeAsync(){try(var g=SuiteState.guard()){closing=true;return writer.closeAsync();}}

    private static ConfigurationSection section(ConfigurationSection p,String key)throws IOException{var v=p.getConfigurationSection(key);if(v==null)throw new IOException("Отсутствует раздел: "+key);return v;}
    private static boolean bool(ConfigurationSection p,String key)throws IOException{if(!(p.get(key) instanceof Boolean v))throw new IOException("Неверный флаг: "+key);return v;}
    private static long number(ConfigurationSection p,String key)throws IOException{try{return new java.math.BigDecimal(String.valueOf(p.get(key))).longValueExact();}catch(Exception ex){throw new IOException("Неверное число: "+key,ex);}}
    private static Map<String,Long> values(ConfigurationSection p)throws IOException{var out=new HashMap<String,Long>();for(String key:p.getKeys(false))out.put(key,number(p,key));return out;}
    public CompletionStage<Image> put(UUID town,CityLedger city){try(var guard=SuiteState.guard()){var before=image;var next=new HashMap<>(before.cities());next.put(town,city);return commit(before,new Image(next,before.debits(),before.flowGap()));}}
    /** All writers use the same ACK boundary; no disk work runs under Suite. */
    public CompletionStage<Image> replace(Map<UUID,CityLedger> next){try(var guard=SuiteState.guard()){var before=image;return commit(before,new Image(next,before.debits(),before.flowGap()));}}
    private static String encode(Image next){var y=new YamlConfiguration();y.set("schema",1);y.set("flow-gap",next.flowGap());y.createSection("cities");for(var entry:next.cities().entrySet()){String k="cities."+entry.getKey()+".";var c=entry.getValue();y.set(k+"balance",c.balance());y.set(k+"enabled",c.enabled());y.set(k+"revision",c.revision());y.set(k+"started",c.started());y.set(k+"notified",c.notified());y.set(k+"history",c.history());for(var b:Budget.values()){y.set(k+"funds."+b.id(),c.funds().get(b));y.set(k+"shares."+b.id(),c.shares().get(b));}y.createSection(k+"weeks");for(var entryWeek:c.weeks().entrySet()){String v=k+"weeks."+entryWeek.getKey()+".";var w=entryWeek.getValue();y.createSection(v+"income",w.income());y.createSection(v+"expenses",w.expenses());y.createSection(v+"production",w.production());y.set(v+"production-known",w.productionKnown());y.set(v+"refunds",w.refunds());y.set(v+"adjustments",w.adjustments());for(var b:Budget.values())y.set(v+"categories."+b.id(),w.categories().get(b));}if(c.pending()!=null){var p=c.pending();y.set(k+"pending.id",p.id().toString());y.set(k+"pending.at",p.at());y.set(k+"pending.amount",p.amount());y.set(k+"pending.category",p.category().id());y.set(k+"pending.source",p.source());y.set(k+"pending.reason",p.reason());}}

        y.createSection("async-debits");for(var e:next.debits().entrySet()){String p="async-debits."+e.getKey()+".";var d=e.getValue();var t=d.terms();y.set(p+"town",t.town().toString());y.set(p+"category",t.category());y.set(p+"source",t.source());y.set(p+"cents",t.cents());y.set(p+"reason",t.reason());y.set(p+"actor",t.actor().toString());y.set(p+"owner",t.owner());y.set(p+"kind",t.kind().name());if(t.backingDebit()!=null)y.set(p+"backing-debit",t.backingDebit().toString());y.set(p+"audited",d.audited());y.set(p+"state",d.state().name());y.set(p+"created-at",d.createdAt());y.set(p+"changed-at",d.changedAt());y.set(p+"review",d.review());y.createSection(p+"spending-admission",d.admission().view());}return y.saveToString();
    }
}
