package ru.neverland.mintexpeditions.service;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;

/** Manual return tickets retain their old file layout; all runtime file work is detached. */
public final class ReturnTickets {
    public record Ticket(UUID campOwner,long expiresAt){}
    private final AcknowledgedState<Map<UUID,Ticket>> data;private final CompletionStage<Void> loaded;
    private CompletionStage<Void> tail=CompletableFuture.completedFuture(null);private int queued;private boolean closing;
    public ReturnTickets(JavaPlugin plugin){this(new File(plugin.getDataFolder(),"return-tickets.yml"),plugin.getLogger(),System.currentTimeMillis(),false);}
    ReturnTickets(File file,java.util.logging.Logger logger,long now){this(file,logger,now,true);}
    private ReturnTickets(File file,java.util.logging.Logger logger,long now,boolean offline){
        data=new AcknowledgedState<>(file.toPath(),Map.of(),ReturnTickets::encode,"nlt-expedition-return-write");
        var reader=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"nlt-expedition-return-load");t.setDaemon(true);return t;});
        loaded=CompletableFuture.runAsync(()->{try{var values=new HashMap<UUID,Ticket>();if(file.exists()){if(file.length()>4L*1024*1024)throw new IOException("Слишком много возвратов");var y=new YamlConfiguration();y.load(file);if(y.getKeys(false).size()>16_384)throw new IOException("Слишком много возвратов");for(String id:y.getKeys(false)){Object expires=y.get(id+".expires");if(!(expires instanceof Integer||expires instanceof Long))throw new IOException("Повреждён срок возврата");var ticket=new Ticket(UUID.fromString(y.getString(id+".camp")),((Number)expires).longValue());if(ticket.expiresAt()>now)values.put(UUID.fromString(id),ticket);}}data.loaded(Map.copyOf(values));}catch(Exception error){throw new CompletionException(error);}},reader).whenComplete((v,e)->reader.shutdown());
        if(offline)await(loaded);
    }
    public CompletionStage<Void> readyAsync(){return loaded;}
    public Ticket get(UUID player,long now){var ticket=data.get().get(player);return ticket!=null&&ticket.expiresAt()>now?ticket:null;}
    private CompletionStage<Void> update(java.util.function.UnaryOperator<Map<UUID,Ticket>> change){try(var guard=SuiteState.guard()){
        if(closing||queued>=128)return CompletableFuture.failedFuture(new IOException("Очередь возвратов заполнена или останавливается"));queued++;
        var task=tail.thenCompose(v->loaded).thenCompose(v->{try(var state=SuiteState.guard()){
            var before=data.get();var next=Map.copyOf(change.apply(new HashMap<>(before)));
            if(next.size()>16_384)return CompletableFuture.failedFuture(new IOException("Реестр возвратов заполнен"));
            return data.commit(before,next).thenApply(saved->(Void)null);
        }}).whenComplete((v,e)->{try(var state=SuiteState.guard()){queued--;}});tail=task;return task;
    }}
    public CompletionStage<Void> grantAsync(Iterable<UUID> players,UUID camp,long expiresAt){var ids=new ArrayList<UUID>();players.forEach(ids::add);return update(next->{for(UUID p:ids)next.put(p,new Ticket(camp,expiresAt));return next;});}
    public CompletionStage<Void> removeAsync(UUID player){return update(next->{next.remove(player);return next;});}
    public CompletionStage<Void> pruneAsync(long now){try(var guard=SuiteState.guard()){if(queued>0)return CompletableFuture.completedFuture(null);return update(next->{next.values().removeIf(t->t.expiresAt()<=now);return next;});}}
    public void grant(Iterable<UUID> players,UUID camp,long expires){await(grantAsync(players,camp,expires));}public void remove(UUID player){await(removeAsync(player));}public void prune(long now){await(pruneAsync(now));}
    public CompletionStage<Void> prepareClose(){try(var guard=SuiteState.guard()){closing=true;return loaded.handle((v,e)->null).thenCompose(v->tail.handle((saved,error)->null)).thenCompose(v->data.prepareClose());}}
    private static void await(CompletionStage<?> operation){if(SuiteState.held())throw new IllegalStateException("Ожидайте возвраты вне Suite");try{operation.toCompletableFuture().get(10,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException("Возврат не сохранён",e);}}
    private static String encode(Map<UUID,Ticket> values){var y=new YamlConfiguration();new TreeMap<>(values).forEach((p,t)->{y.set(p+".camp",t.campOwner().toString());y.set(p+".expires",t.expiresAt());});return y.saveToString();}
}
