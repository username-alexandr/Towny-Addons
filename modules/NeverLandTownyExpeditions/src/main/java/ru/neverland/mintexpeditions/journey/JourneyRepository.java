package ru.neverland.mintexpeditions.journey;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import ru.neverland.core.*;
import ru.neverland.mintexpeditions.model.*;

/** Optional journal: a failed append pauses only opted-in journeys; legacy expeditions remain usable. */
public final class JourneyRepository {
    public record Image(Map<UUID,Journey> journeys,Map<String,AuditRecord> outbox){
        public Image{journeys=Map.copyOf(journeys);outbox=Map.copyOf(outbox);if(journeys.size()>512||outbox.size()>1024)throw new IllegalArgumentException("История походов заполнена; обратитесь к администратору");}
    }
    private final Path file;private final AcknowledgedState<Image> data;private final Predicate<AuditRecord> audit;
    private final CompletableFuture<Void> workerClosed=new CompletableFuture<>();
    private final ExecutorService worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(2),r->{var t=new Thread(r,"nlt-expedition-journey-journal");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){workerClosed.complete(null);}};
    private boolean busy,failed,closing;private CompletionStage<?> operation=CompletableFuture.completedFuture(null);private CompletionStage<Void> close;
    public JourneyRepository(Path file,Predicate<AuditRecord> audit){this.file=file;this.audit=Objects.requireNonNull(audit);data=new AcknowledgedState<>(file,new Image(Map.of(),Map.of()),JourneyRepository::encode,"nlt-expedition-journey-state");}
    public CompletionStage<Void> load(){try(var guard=SuiteState.guard()){
        busy=true;var stage=CompletableFuture.supplyAsync(()->{try{var image=read(file);data.loaded(image);return image;}catch(Exception e){throw new CompletionException(e);}},worker).thenCompose(this::flush).thenApply(v->(Void)null).whenComplete((v,e)->{try(var state=SuiteState.guard()){busy=false;if(e!=null)failed=true;}});operation=stage;return stage;
    }}
    public boolean ready(){try(var guard=SuiteState.guard()){return !failed&&!closing&&data.writable();}}
    public boolean busy(){try(var guard=SuiteState.guard()){return busy;}}
    public Journey get(UUID id){return data.get().journeys().get(id);}
    public Collection<Journey> all(){return data.get().journeys().values();}
    public CompletionStage<Journey> commit(Journey expected,Journey next,UUID actor,String step,String details){try(var guard=SuiteState.guard()){
        if(!ready()||busy)return CompletableFuture.failedFuture(new IOException("Поэтапные походы приостановлены или предыдущий шаг ещё сохраняется"));
        var before=data.get();if(!Objects.equals(before.journeys().get(next.id()),expected))return CompletableFuture.failedFuture(new IOException("Условия похода изменились; откройте меню заново"));
        var journeys=new HashMap<>(before.journeys());journeys.put(next.id(),next);var outbox=new LinkedHashMap<>(before.outbox());
        String id=AuditRecord.id("NeverLandTownyExpeditions",next.id().toString(),step);
        var person=actor==null?AuditRecord.Party.system():new AuditRecord.Party("PLAYER",actor.toString(),actor.toString(),"");
        var record=new AuditRecord(id,System.currentTimeMillis(),"NeverLandTownyExpeditions","EXPEDITION_PLAN",next.state()==Journey.State.REVIEW?"REVIEW":next.state()==Journey.State.CLOSED?"CLOSED":"CONFIRMED",next.id().toString(),person,AuditRecord.Party.system(),AuditRecord.Party.system(),"",0,"",details);
        outbox.put(record.id(),record);Image image=new Image(journeys,outbox);busy=true;
        var stage=data.commit(before,image).thenCompose(this::flush).thenApply(v->v.journeys().get(next.id())).whenComplete((v,e)->{try(var state=SuiteState.guard()){busy=false;if(e!=null)failed=true;}});operation=stage;return stage;
    }}
    private CompletionStage<Image> flush(Image image){
        if(image.outbox().isEmpty())return CompletableFuture.completedFuture(image);
        return CompletableFuture.supplyAsync(()->{if(SuiteState.held())throw new IllegalStateException("Аудит требует освобождения Suite");for(var r:image.outbox().values().stream().sorted(Comparator.comparingLong(AuditRecord::at).thenComparing(AuditRecord::id)).toList())if(!audit.test(r))throw new CompletionException(new IOException("Журнал похода не подтвердил запись"));return image;},worker).thenCompose(saved->data.commit(saved,new Image(saved.journeys(),Map.of())));
    }
    public CompletionStage<Void> prepareClose(){try(var guard=SuiteState.guard()){if(close!=null)return close;closing=true;close=operation.handle((v,e)->null).thenCompose(v->{worker.shutdown();return workerClosed;}).thenCompose(v->data.prepareClose());return close;}}
    public static Image read(Path path)throws Exception{
        if(SuiteState.held())throw new IOException("Чтение походов требует освобождения Suite");if(!Files.exists(path))return new Image(Map.of(),Map.of());if(Files.size(path)>32L*1024*1024)throw new IOException("История походов больше 32 МиБ");
        var y=new YamlConfiguration();y.load(path.toFile());if(number(y,"schema")!=1)throw new IOException("Неизвестная схема походов");var result=new LinkedHashMap<UUID,Journey>();var js=section(y,"journeys");if(js.getKeys(false).size()>512)throw new IOException("История походов заполнена");
        for(String key:js.getKeys(false)){
            var s=section(js,key);var participants=new HashSet<UUID>();for(String p:strings(s,"participants"))participants.add(UUID.fromString(p));var roles=new HashMap<UUID,Journey.Role>();var rs=section(s,"roles");for(String p:rs.getKeys(false))roles.put(UUID.fromString(p),Journey.Role.valueOf(text(rs,p)));
            var contributions=new LinkedHashMap<String,Journey.Contribution>();var cs=section(s,"contributions");if(cs.getKeys(false).size()>512)throw new IOException("Слишком много квитанций похода");
            for(String k:cs.getKeys(false)){var c=section(cs,k);String proof=text(c,"key");String role=text(c,"role");if(contributions.put(proof,new Journey.Contribution(proof,Journey.Action.valueOf(text(c,"action")),UUID.fromString(text(c,"player")),role.isEmpty()?null:Journey.Role.valueOf(role),number(c,"at")))!=null)throw new IOException("Повтор квитанции похода");}
            UUID id=UUID.fromString(key);result.put(id,new Journey(id,definition(section(s,"definition")),Journey.Branch.valueOf(text(s,"branch")),participants,roles,contributions,Journey.State.valueOf(text(s,"state")),text(s,"note")));
        }
        var receipts=new LinkedHashMap<String,AuditRecord>();var outbox=section(y,"outbox");if(outbox.getKeys(false).size()>1024)throw new IOException("Слишком много квитанций аудита");for(String key:outbox.getKeys(false)){var receipt=AuditRecord.parse(strings(outbox,key));if(!key.equals(receipt.id()))throw new IOException("Неверный ID аудита");receipts.put(key,receipt);}return new Image(result,receipts);
    }
    private static ExpeditionDefinition definition(ConfigurationSection s){return new ExpeditionDefinition(text(s,"id"),text(s,"name"),ExpeditionType.valueOf(text(s,"type")),ObjectiveType.valueOf(text(s,"objective")),Material.valueOf(text(s,"icon")),integer(s,"slot"),integer(s,"level"),number(s,"duration"),integer(s,"min-distance"),integer(s,"max-distance"),integer(s,"goal"),Material.valueOf(text(s,"objective-material")),EntityType.valueOf(text(s,"mob")),integer(s,"mobs"),items(s,"costs"),items(s,"rewards"),Double.parseDouble(text(s,"money")),strings(s,"description"));}
    private static List<ItemAmount> items(ConfigurationSection s,String key){var section=section(s,key);if(section.getKeys(false).size()>64)throw new IllegalArgumentException("Слишком много предметов похода");var result=new ArrayList<ItemAmount>();for(String id:section.getKeys(false)){var item=section(section,id);Object raw=item.get("item");if(!(raw instanceof ItemStack stack)||stack.getType().isAir()||stack.getAmount()<1||stack.getAmount()>99)throw new IllegalArgumentException("Повреждён предмет похода");result.add(new ItemAmount(text(item,"key"),stack.clone(),integer(item,"amount")));}return result;}
    public static String encode(Image image)throws IOException{
        var y=new YamlConfiguration();y.set("schema",1);y.createSection("journeys");y.createSection("outbox");new TreeMap<>(image.outbox()).forEach((id,r)->y.set("outbox."+id,r.fields()));
        for(var j:image.journeys().values().stream().sorted(Comparator.comparing(Journey::id)).toList()){
            String p="journeys."+j.id()+".";y.set(p+"branch",j.branch().name());y.set(p+"state",j.state().name());y.set(p+"note",j.note());y.set(p+"participants",j.participants().stream().sorted().map(UUID::toString).toList());y.createSection(p+"roles");new TreeMap<>(j.roles()).forEach((id,r)->y.set(p+"roles."+id,r.name()));y.createSection(p+"contributions");int i=0;
            for(var c:j.contributions().values().stream().sorted(Comparator.comparing(Journey.Contribution::key)).toList()){String q=p+"contributions."+(i++)+".";y.set(q+"key",c.key());y.set(q+"action",c.action().name());y.set(q+"player",c.player().toString());y.set(q+"role",c.role()==null?"":c.role().name());y.set(q+"at",c.at());}
            writeDefinition(y,p+"definition.",j.frozen());
        }
        String value=y.saveToString();if(value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32*1024*1024)throw new IOException("История походов достигла 32 МиБ");return value;
    }
    private static void writeDefinition(YamlConfiguration y,String p,ExpeditionDefinition d){
        Object[] pairs={"id",d.id(),"name",d.name(),"type",d.type().name(),"objective",d.objective().name(),"icon",d.icon().name(),"slot",d.slot(),"level",d.minCampLevel(),"duration",d.durationSeconds(),"min-distance",d.minDistance(),"max-distance",d.maxDistance(),"goal",d.goal(),"objective-material",d.objectiveMaterial().name(),"mob",d.mob().name(),"mobs",d.mobCount(),"money",Double.toString(d.moneyPerParticipant()),"description",d.description()};for(int i=0;i<pairs.length;i+=2)y.set(p+pairs[i],pairs[i+1]);writeItems(y,p+"costs",d.costs());writeItems(y,p+"rewards",d.rewards());
    }
    private static void writeItems(YamlConfiguration y,String p,List<ItemAmount> items){y.createSection(p);for(int i=0;i<items.size();i++){var item=items.get(i);var unit=item.item().clone();unit.setAmount(1);y.set(p+"."+i+".key",item.key());y.set(p+"."+i+".item",unit);y.set(p+"."+i+".amount",item.amount());}}
    private static ConfigurationSection section(ConfigurationSection s,String key){var result=s.getConfigurationSection(key);if(result==null)throw new IllegalArgumentException("Нет раздела похода "+key);return result;}
    private static String text(ConfigurationSection s,String key){Object value=s.get(key);if(!(value instanceof String v))throw new IllegalArgumentException("Нужна строка: "+key);return v;}
    private static long number(ConfigurationSection s,String key){Object value=s.get(key);if(!(value instanceof Integer||value instanceof Long))throw new IllegalArgumentException("Нужно целое число: "+key);return ((Number)value).longValue();}
    private static int integer(ConfigurationSection s,String key){return Math.toIntExact(number(s,key));}
    private static List<String> strings(ConfigurationSection s,String key){Object raw=s.get(key);if(!(raw instanceof List<?> list)||list.stream().anyMatch(v->!(v instanceof String)))throw new IllegalArgumentException("Нужен список строк: "+key);return list.stream().map(v->(String)v).toList();}
}
