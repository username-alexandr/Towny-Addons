package ru.neverland.earnings;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongSupplier;
import ru.neverland.core.AtomicFiles;

/** Durable outbox. An interrupted external deposit is NEVER automatically replayed. */
public final class RewardLedger {
    public enum State { HELD, PENDING, SENDING, REVIEW, PAID, VOID }
    public record Entry(UUID id, UUID player, long cents, String source, long created, long paidAt,
                        State state, List<String> history) {
        public Entry { history=List.copyOf(history); if(cents<=0||cents>100_000_000||created<0||paidAt<0||source.isBlank())throw new IllegalArgumentException("Invalid reward"); }
    }
    public interface Bank { boolean ready(UUID player); boolean deposit(Entry entry); }
    public static final long DAY=86_400_000L;
    private final Path directory;
    private final LongSupplier clock;
    private final Map<UUID,Entry> entries=new LinkedHashMap<>();
    private final LinkedHashSet<UUID> pending=new LinkedHashSet<>();
    private final long playerLimit,serverLimit;
    private boolean fault;
    public RewardLedger(Path directory,long playerLimit,long serverLimit) { this(directory,playerLimit,serverLimit,System::currentTimeMillis); }
    public RewardLedger(Path directory,long playerLimit,long serverLimit,LongSupplier clock) {
        if(playerLimit<=0||serverLimit<playerLimit)throw new IllegalArgumentException("Лимиты выплат должны быть положительными; серверный >= личного");
        this.directory=directory;this.playerLimit=playerLimit;this.serverLimit=serverLimit;this.clock=clock;
    }
    public void load() throws IOException { synchronized(ru.neverland.core.SuiteState.lock()) {
        entries.clear();pending.clear();fault=true;Files.createDirectories(directory);
        try(var files=Files.list(directory)) {
            for(Path file:files.filter(p->p.toString().endsWith(".properties")).sorted().toList()) {
                Properties p=new Properties();try(var reader=Files.newBufferedReader(file)){p.load(reader);}
                if(!"1".equals(p.getProperty("schema")))throw new IOException("Unknown reward schema: "+file);
                UUID id=UUID.fromString(p.getProperty("id"));if(!file.getFileName().toString().equals(id+".properties"))throw new IOException("Reward filename mismatch");
                int count=Integer.parseInt(p.getProperty("history-count"));if(count<1||count>10000)throw new IOException("Invalid reward history");
                List<String> history=new ArrayList<>();for(int i=0;i<count;i++)history.add(Objects.requireNonNull(p.getProperty("history."+i)));
                var entry=new Entry(id,UUID.fromString(p.getProperty("player")),Long.parseLong(p.getProperty("cents")),p.getProperty("source"),Long.parseLong(p.getProperty("created")),Long.parseLong(p.getProperty("paid-at")),State.valueOf(p.getProperty("state")),history);
                entries.put(id,entry);if(entry.state()==State.PENDING)pending.add(id);AtomicFiles.loaded(file);
            }
        } catch(RuntimeException ex){throw new IOException("Повреждён журнал начислений; выплаты остановлены",ex);}
        fault=false;
        for(Entry e:List.copyOf(entries.values()))if(e.state()==State.SENDING)change(e,State.REVIEW,"restart: outcome unknown",0);
    } }
    public Entry get(UUID id) { synchronized(ru.neverland.core.SuiteState.lock()){return entries.get(id);} }
    public List<Entry> all() { synchronized(ru.neverland.core.SuiteState.lock()){return List.copyOf(entries.values());} }
    /** Rotate attempts, including unavailable recipients, so one account cannot starve others. */
    public List<Entry> pendingBatch(int maximum) { synchronized(ru.neverland.core.SuiteState.lock()) {
        if(maximum<1)throw new IllegalArgumentException("Positive batch size required");
        List<Entry> batch=new ArrayList<>();int count=Math.min(maximum,pending.size());
        for(int i=0;i<count;i++){
            var iterator=pending.iterator();UUID id=iterator.next();iterator.remove();pending.add(id);
            batch.add(entries.get(id));
        }
        return List.copyOf(batch);
    } }
    public boolean fault(){return fault;}
    public long used(UUID player) { synchronized(ru.neverland.core.SuiteState.lock()) {
        long after=clock.getAsLong()-DAY,total=0;
        for(Entry e:entries.values())if((player==null||e.player().equals(player))&&e.state()!=State.VOID&&(e.state()!=State.PAID||e.paidAt()>after))total=Math.addExact(total,e.cents());
        return total;
    } }
    public long remaining(UUID player){synchronized(ru.neverland.core.SuiteState.lock()){return Math.max(0,Math.min(playerLimit-used(player),serverLimit-used(null)));}}
    public Entry reserve(UUID id,UUID player,long cents,String source,boolean held)throws IOException { synchronized(ru.neverland.core.SuiteState.lock()) {
        check();Entry old=entries.get(id);if(old!=null){if(!old.player().equals(player)||old.cents()!=cents||!old.source().equals(source))throw new IllegalArgumentException("Конфликт ID начисления");return old;}
        if(cents>remaining(player))throw new IllegalArgumentException("Лимит серверных выплат за 24 часа исчерпан; награда не списана");
        var e=new Entry(id,player,cents,source,clock.getAsLong(),0,held?State.HELD:State.PENDING,List.of(clock.getAsLong()+" reserve "+source));save(e);return e;
    } }
    public void release(UUID id,String proof)throws IOException { synchronized(ru.neverland.core.SuiteState.lock()) {
        Entry e=require(id);if(e.state()==State.HELD)change(e,State.PENDING,"inventory receipt: "+proof,0);
    } }
    public void submit(UUID id,Bank bank)throws IOException { synchronized(ru.neverland.core.SuiteState.lock()) {
        check();Entry e=require(id);if(e.state()!=State.PENDING||!bank.ready(e.player()))return;
        change(e,State.SENDING,"bank call prepared",0);
        boolean paid=false;String reason="provider rejected; reconciliation required";
        try{paid=bank.deposit(e);}catch(RuntimeException ex){reason="bank exception: "+ex.getClass().getSimpleName();}
        // Persisted SENDING is sufficient to prevent replay if the following write fails.
        change(require(id),paid?State.PAID:State.REVIEW,paid?"Towny account receipt":reason,paid?clock.getAsLong():0);
    } }
    public void resolve(UUID id,String decision,String actor,String reason)throws IOException { synchronized(ru.neverland.core.SuiteState.lock()) {
        check();Entry e=require(id);if(reason==null||reason.trim().length()<8)throw new IllegalArgumentException("Укажите основание сверки (не менее 8 символов)");
        State next=switch(decision){
            case "paid" -> {if(e.state()!=State.REVIEW)throw new IllegalArgumentException("Только REVIEW");yield State.PAID;}
            case "retry" -> {if(e.state()!=State.REVIEW)throw new IllegalArgumentException("Только REVIEW");yield State.PENDING;}
            case "taken" -> {if(e.state()!=State.HELD)throw new IllegalArgumentException("Только HELD");yield State.PENDING;}
            case "untouched" -> {if(e.state()!=State.HELD)throw new IllegalArgumentException("Только HELD");yield State.VOID;}
            default -> throw new IllegalArgumentException("paid | retry | taken | untouched");};
        change(e,next,"admin "+actor+" "+decision+": "+reason,next==State.PAID?clock.getAsLong():0);
    } }
    private Entry require(UUID id){Entry e=entries.get(id);if(e==null)throw new IllegalArgumentException("Начисление не найдено");return e;}
    private void check()throws IOException{if(fault)throw new IOException("Журнал остановлен после ошибки записи; нужен перезапуск после исправления диска");}
    private void change(Entry e,State state,String note,long paid)throws IOException{
        var history=new ArrayList<>(e.history());history.add(clock.getAsLong()+" "+state+" "+note);
        save(new Entry(e.id(),e.player(),e.cents(),e.source(),e.created(),paid,state,history));
    }
    private void save(Entry e)throws IOException {
        check();Properties p=new Properties();p.setProperty("schema","1");p.setProperty("id",e.id().toString());p.setProperty("player",e.player().toString());p.setProperty("cents",Long.toString(e.cents()));p.setProperty("source",e.source());p.setProperty("created",Long.toString(e.created()));p.setProperty("paid-at",Long.toString(e.paidAt()));p.setProperty("state",e.state().name());p.setProperty("history-count",Integer.toString(e.history().size()));for(int i=0;i<e.history().size();i++)p.setProperty("history."+i,e.history().get(i));
        try{AtomicFiles.write(directory.resolve(e.id()+".properties"),()->{var out=new StringWriter();p.store(out,"NeverLand server reward; do not edit");return out.toString();});entries.put(e.id(),e);if(e.state()==State.PENDING)pending.add(e.id());else pending.remove(e.id());}
        catch(IOException ex){fault=true;throw ex;}
    }
}
