package ru.neverland.townydiplomacy.aid;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;

/** Optional, bounded immutable journal. Existing diplomacy.yml is never rewritten here. */
public final class AidRepository {
    private final Path file;private final AcknowledgedState<AidState> data;private final java.util.function.Predicate<AuditRecord> audit;private final java.util.function.UnaryOperator<AuditRecord> names;
    private volatile boolean auditing,auditFailed,closing;private volatile CompletionStage<?> pendingAudit=CompletableFuture.completedFuture(null),pending=CompletableFuture.completedFuture(null);private Set<UUID> interruptedActivations=Set.of();
    private final CompletableFuture<Void> auditClosed=new CompletableFuture<>();
    private final ExecutorService auditor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{var t=new Thread(r,"nlt-diplomacy-aid-audit");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){auditClosed.complete(null);}};
    private final CompletableFuture<Void> readsClosed=new CompletableFuture<>();
    private final ExecutorService reads=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{var t=new Thread(r,"nlt-diplomacy-aid-load");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){readsClosed.complete(null);}};
    public AidRepository(Path file){this(file,null);}
    public AidRepository(Path file,java.util.function.Predicate<AuditRecord> audit){this(file,audit,java.util.function.UnaryOperator.identity());}
    public AidRepository(Path file,java.util.function.Predicate<AuditRecord> audit,java.util.function.UnaryOperator<AuditRecord> names){this.file=file;this.audit=audit;this.names=Objects.requireNonNull(names);data=new AcknowledgedState<>(file,AidState.empty(),AidRepository::encode,"nlt-diplomacy-aid-write");}
    public CompletionStage<AidState> load(){var stage=CompletableFuture.supplyAsync(()->{try{var state=read(file);var incomplete=new HashSet<UUID>();for(var r:state.outbox().values())if(r.outcome().equals("CONFIRMED"))incomplete.add(UUID.fromString(r.operation()));interruptedActivations=Set.copyOf(incomplete);data.loaded(state);return state;}catch(Exception ex){throw new CompletionException(ex);}},reads).thenCompose(this::flush);pending=stage;return stage;}
    public Set<UUID> interruptedActivations(){return interruptedActivations;}
    public AidState state(){return data.get();}public boolean writable(){return data.writable()&&!auditFailed;}public boolean busy(){return data.busy()||auditing;}
    public CompletionStage<AidState> commit(AidState before,AidState next){
        if(closing||busy()||auditFailed)return CompletableFuture.failedFuture(new IOException("Аудит помощи не подтверждён; прежние квитанции сохранены"));
        try{AidState captured=next;if(audit!=null){captured=AidAudit.capture(before,next,System.currentTimeMillis());var receipts=new LinkedHashMap<>(captured.outbox());for(var e:receipts.entrySet())if(!before.outbox().containsKey(e.getKey()))e.setValue(names.apply(e.getValue()));captured=captured.withOutbox(receipts);}var stage=data.commit(before,captured).thenCompose(this::flush);pending=stage;return stage;}catch(RuntimeException invalid){auditFailed=true;return CompletableFuture.failedFuture(invalid);}
    }
    private CompletionStage<AidState> flush(AidState state){
        if(audit==null||state.outbox().isEmpty())return CompletableFuture.completedFuture(state);auditing=true;
        var stage=CompletableFuture.supplyAsync(()->{if(SuiteState.held())throw new IllegalStateException("Аудит выполняется вне общей блокировки");for(var r:state.outbox().values().stream().sorted(Comparator.comparingLong(AuditRecord::at).thenComparing(AuditRecord::id)).toList())if(!audit.test(r))throw new CompletionException(new IOException("Административный журнал помощи не подтвердил запись"));return state;},auditor)
            .thenCompose(saved->data.commit(saved,saved.withOutbox(Map.of()))).whenComplete((saved,error)->{auditing=false;if(error!=null)auditFailed=true;});pendingAudit=stage;return stage;
    }
    public CompletionStage<Void> closeAsync(){closing=true;reads.shutdown();return readsClosed.thenCompose(v->pending.handle((saved,error)->null)).thenCompose(v->pendingAudit.handle((saved,error)->null)).thenCompose(v->{auditor.shutdown();return auditClosed;}).thenCompose(v->data.prepareClose());}
    public static AidState read(Path file)throws Exception{
        if(SuiteState.held())throw new IOException("Чтение помощи требует освобождения общей блокировки");
        if(!Files.exists(file))return AidState.empty();if(Files.size(file)>32L*1024*1024)throw new IOException("Реестр помощи больше 32 МиБ");
        var y=new YamlConfiguration();y.load(file.toFile());long schema=n(y,"schema");if(schema!=1&&schema!=2)throw new IOException("Неизвестная схема помощи");
        var pacts=new LinkedHashMap<UUID,AidPact>();var calls=new LinkedHashMap<UUID,AidCall>();var history=new ArrayList<AidState.History>();
        var ps=section(y,"pacts");if(ps.getKeys(false).size()>2000)throw new IOException("Слишком много соглашений помощи");
        for(String key:ps.getKeys(false)){var s=section(ps,key);UUID id=UUID.fromString(key);pacts.put(id,new AidPact(id,u(s,"alliance"),u(s,"first"),u(s,"second"),u(s,"first-mayor"),u(s,"second-mayor"),t(s,"proposer"),t(s,"accepter"),n(s,"created"),n(s,"offer-until"),n(s,"ends-at"),n(s,"response-ms"),n(s,"required-damage"),AidPact.Phase.valueOf(t(s,"phase")),t(s,"note")));}
        var cs=section(y,"calls");if(cs.getKeys(false).size()>2000)throw new IOException("Слишком много вызовов помощи");
        for(String key:cs.getKeys(false)){var s=section(cs,key);var proof=new ArrayList<AidFact>();var facts=section(s,"proof");if(facts.getKeys(false).size()>128)throw new IOException("Слишком много квитанций вызова");for(String f:facts.getKeys(false))proof.add(fact(section(facts,f)));UUID id=UUID.fromString(key);calls.put(id,new AidCall(id,u(s,"pact"),u(s,"beneficiary"),u(s,"helper"),u(s,"enemy"),n(s,"opened"),n(s,"deadline"),n(s,"required-damage"),AidCall.Status.valueOf(t(s,"status")),t(s,"note"),fact(section(s,"attack")),proof));}
        var hs=section(y,"history");if(hs.getKeys(false).size()>4000)throw new IOException("Слишком много записей истории");for(String key:hs.getKeys(false)){var s=section(hs,key);history.add(new AidState.History(UUID.fromString(key),u(s,"pact"),n(s,"at"),t(s,"actor"),t(s,"action")));}
        var outbox=new LinkedHashMap<String,AuditRecord>();if(schema==2){var section=section(y,"audit-outbox");if(section.getKeys(false).size()>4096)throw new IOException("Слишком много квитанций аудита");for(String key:section.getKeys(false)){Object raw=section.get(key);if(!(raw instanceof List<?> list)||list.stream().anyMatch(v->!(v instanceof String)))throw new IOException("Повреждена квитанция аудита");var fields=list.stream().map(v->(String)v).toList();var receipt=AuditRecord.parse(fields);if(!key.equals(receipt.id()))throw new IOException("Неверный ID квитанции аудита");outbox.put(key,receipt);}}
        history.sort(Comparator.comparingLong(AidState.History::at).thenComparing(AidState.History::id));return new AidState(pacts,calls,history,outbox);
    }
    public static String encode(AidState state)throws IOException{
        var y=new YamlConfiguration();y.set("schema",2);y.createSection("pacts");y.createSection("calls");y.createSection("history");y.createSection("audit-outbox");new TreeMap<>(state.outbox()).forEach((id,record)->y.set("audit-outbox."+id,record.fields()));
        new TreeMap<>(state.pacts()).forEach((id,p)->{String s="pacts."+id+".";put(y,s,"alliance",p.alliance(),"first",p.first(),"second",p.second(),"first-mayor",p.firstMayor(),"second-mayor",p.secondMayor(),"proposer",p.proposer(),"accepter",p.accepter(),"created",p.created(),"offer-until",p.offerUntil(),"ends-at",p.endsAt(),"response-ms",p.responseMillis(),"required-damage",p.requiredMilliDamage(),"phase",p.phase(),"note",p.note());});
        new TreeMap<>(state.calls()).forEach((id,c)->{String s="calls."+id+".";put(y,s,"pact",c.pact(),"beneficiary",c.beneficiary(),"helper",c.helper(),"enemy",c.enemy(),"opened",c.opened(),"deadline",c.deadline(),"required-damage",c.requiredMilliDamage(),"status",c.status(),"note",c.note());writeFact(y,s+"attack.",c.attack());y.createSection(s+"proof");for(int i=0;i<c.proof().size();i++)writeFact(y,s+"proof."+i+".",c.proof().get(i));});
        for(var h:state.history())put(y,"history."+h.id()+".","pact",h.pact(),"at",h.at(),"actor",h.actor(),"action",h.action());String encoded=y.saveToString();if(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32*1024*1024)throw new IOException("Реестр помощи достиг 32 МиБ; прежние квитанции сохранены");return encoded;
    }
    private static void writeFact(YamlConfiguration y,String s,AidFact f){put(y,s,"id",f.id(),"at",f.at(),"actor",f.actor(),"actor-town",f.actorTown(),"victim",f.victim(),"victim-town",f.victimTown(),"territory",f.territory(),"damage",f.milliDamage(),"world",f.world(),"x",f.x(),"y",f.y(),"z",f.z());}
    private static AidFact fact(ConfigurationSection s){return new AidFact(u(s,"id"),n(s,"at"),u(s,"actor"),u(s,"actor-town"),u(s,"victim"),u(s,"victim-town"),u(s,"territory"),n(s,"damage"),u(s,"world"),Math.toIntExact(n(s,"x")),Math.toIntExact(n(s,"y")),Math.toIntExact(n(s,"z")));}
    private static void put(YamlConfiguration y,String s,Object... values){for(int i=0;i<values.length;i+=2){Object v=values[i+1];y.set(s+values[i],v instanceof UUID||v instanceof Enum<?>?v.toString():v);}}
    private static ConfigurationSection section(ConfigurationSection s,String key){var result=s.getConfigurationSection(key);if(result==null)throw new IllegalArgumentException("Отсутствует раздел помощи "+key);return result;}
    private static String t(ConfigurationSection s,String key){Object value=s.get(key);if(!(value instanceof String text))throw new IllegalArgumentException("Нужна строка помощи: "+key);return text;}
    private static long n(ConfigurationSection s,String key){Object value=s.get(key);if(!(value instanceof Integer||value instanceof Long))throw new IllegalArgumentException("Нужно целое число помощи: "+key);return ((Number)value).longValue();}
    private static UUID u(ConfigurationSection s,String key){return UUID.fromString(t(s,key));}
}
