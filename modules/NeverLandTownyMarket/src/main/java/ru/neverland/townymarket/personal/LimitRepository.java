package ru.neverland.townymarket.personal;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.io.IOException;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;
import static ru.neverland.townymarket.personal.LimitBook.*;

/** New state is visible only after its acknowledgement; terminal claims have a durable, hash-linked archive. */
public final class LimitRepository {
    public static final int RECENT_CLAIMS=512,COMPACT_AFTER=1024;
    private static final int MAX_BYTES=32*1024*1024,LOOKUP_SEGMENTS=4096;
    private final Path file,archiveDirectory;private final AcknowledgedState<State> data;
    private volatile State visible=LimitBook.empty();private volatile boolean readable;
    private boolean preparing,closing,archiveFailed;private CompletableFuture<Void> prepared=CompletableFuture.completedFuture(null);private CompletionStage<Void> closed;
    private final CompletableFuture<Void> workerClosed=new CompletableFuture<>();private final ExecutorService worker;
    private final Bloom archivedIDs=new Bloom();
    private record Archive(long sequence,String previous,List<Receipt> receipts){}
    /** Fixed 512 KiB filter: positives are verified against actual archive files, never trusted as evidence. */
    private static final class Bloom {
        private final long[] words=new long[65536];
        private static long hash(UUID id){long h=id.getMostSignificantBits()^Long.rotateLeft(id.getLeastSignificantBits(),29);h^=h>>>33;h*=0xff51afd7ed558ccdL;h^=h>>>33;h*=0xc4ceb9fe1a85ec53L;return h^(h>>>33);}
        boolean maybe(UUID id){long h=hash(id),step=Long.rotateLeft(h,27)|1;for(int i=0;i<4;i++){int bit=(int)(h+i*step)&(words.length*64-1);if((words[bit>>>6]&(1L<<(bit&63)))==0)return false;}return true;}
        void add(UUID id){long h=hash(id),step=Long.rotateLeft(h,27)|1;for(int i=0;i<4;i++){int bit=(int)(h+i*step)&(words.length*64-1);words[bit>>>6]|=1L<<(bit&63);}}
        void clear(){Arrays.fill(words,0);}
    }
    public LimitRepository(Path file){this.file=file;archiveDirectory=file.resolveSibling("personal-orders-claims");data=new AcknowledgedState<>(file,LimitBook.empty(),LimitRepository::encode,"NeverLand-personal-market-write");worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4),task->{var thread=new Thread(task,"NeverLand-personal-market-archive");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){workerClosed.complete(null);}};}
    public State state(){return visible;}public boolean readable(){return readable;}
    public boolean writable(){try(var guard=SuiteState.guard()){return readable&&!closing&&!archiveFailed&&data.writable();}}
    public boolean busy(){try(var guard=SuiteState.guard()){return preparing||data.busy();}}
    /** Synchronous entry point for offline tools/tests only. Runtime startup must use loadAsync(). */
    public void load()throws Exception{if(SuiteState.held())throw new IllegalStateException("Загружайте архив через loadAsync вне общей блокировки");readable=false;var value=readValidated();try(var guard=SuiteState.guard()){data.loaded(value);visible=value;archiveFailed=false;readable=true;}}
    public CompletionStage<State> loadAsync(){try(var guard=SuiteState.guard()){
        if(preparing||closing||readable)return CompletableFuture.failedFuture(new IllegalStateException("Журнал уже загружается или используется"));preparing=true;readable=false;prepared=new CompletableFuture<>();var completion=prepared;
        CompletableFuture<State> loading;try{loading=CompletableFuture.supplyAsync(()->{try{return readValidated();}catch(Exception error){throw new CompletionException(error);}},worker);}catch(RuntimeException error){preparing=false;completion.complete(null);return CompletableFuture.failedFuture(error);}
        return loading.handle((value,error)->{try(var committed=SuiteState.guard()){try{if(error!=null){archiveFailed=true;throw new CompletionException(error);}data.loaded(value);visible=value;archiveFailed=false;readable=true;return value;}finally{preparing=false;completion.complete(null);}}});
    }}
    private State readValidated()throws Exception{State value=LimitBook.empty();if(!Files.notExists(file)){if(Files.size(file)>MAX_BYTES)throw new IOException("Слишком большой журнал личных заявок");value=decode(SafeYaml.load(file));}verifyArchives(value);return value;}
    /** Run after load as well, so a schema-1 file at its old receipt cap can still release its backed balances. */
    public CompletionStage<State> compactHistory(){try(var guard=SuiteState.guard()){var state=visible;if(state.receipts().values().stream().filter(r->r.order()==null&&r.status()!=ReceiptStatus.PENDING).count()<=COMPACT_AFTER)return CompletableFuture.completedFuture(state);return commit(state,state);}}
    public CompletionStage<State> commit(State before,State next){try(var guard=SuiteState.guard()){
        if(!writable())return CompletableFuture.failedFuture(new IOException("Журнал личных заявок недоступен"));
        if(preparing||data.busy())return CompletableFuture.failedFuture(new IllegalStateException("Предыдущее изменение ещё сохраняется"));
        if(!visible.equals(before))return CompletableFuture.failedFuture(new IllegalStateException("Данные изменились; откройте меню заново"));
        if(!next.claimCheckpoint().equals(before.claimCheckpoint())||!next.receipts().keySet().containsAll(before.receipts().keySet()))return CompletableFuture.failedFuture(new IllegalArgumentException("Итоги архива изменяются только подтверждённой архивацией"));
        preparing=true;prepared=new CompletableFuture<>();var completion=prepared;
        CompletableFuture<State> staging;
        try{staging=CompletableFuture.supplyAsync(()->{try{
            for(UUID id:next.receipts().keySet())if(!before.receipts().containsKey(id)&&archivedIDs.maybe(id)&&lookupArchived(before.claimCheckpoint(),id,1,LOOKUP_SEGMENTS)!=null)throw new IllegalArgumentException("Квитанция уже завершена и находится в архиве");
            return compact(next);
        }catch(IOException error){throw new CompletionException(error);}},worker);}catch(RuntimeException error){preparing=false;completion.complete(null);return CompletableFuture.failedFuture(error);}
        return staging.thenCompose(staged->data.commit(before,staged)).thenCompose(saved->CompletableFuture.supplyAsync(()->{try{forceDirectoryChain(file.toAbsolutePath().getParent());return saved;}catch(IOException error){throw new CompletionException(error);}},worker)).handle((saved,error)->{try(var committed=SuiteState.guard()){
            preparing=false;
            if(error==null){for(UUID id:next.receipts().keySet())if(!saved.receipts().containsKey(id))archivedIDs.add(id);visible=saved;}
            else{Throwable cause=error;while(cause instanceof CompletionException&&cause.getCause()!=null)cause=cause.getCause();if(cause instanceof IOException)archiveFailed=true;}
            completion.complete(null);if(error!=null)throw new CompletionException(error);return saved;
        }});
    }}
    /** Never performs archive I/O on the command/player thread. Old receipts remain inspectable and cannot be replayed. */
    public CompletionStage<Receipt> lookupReceipt(UUID id){try(var guard=SuiteState.guard()){
        if(!readable)return CompletableFuture.failedFuture(new IOException("Журнал ещё не загружен или недоступен"));var current=visible.receipts().get(id);if(current!=null)return CompletableFuture.completedFuture(current);if(!archivedIDs.maybe(id))return CompletableFuture.completedFuture(null);
        if(closing)return CompletableFuture.failedFuture(new IOException("Журнал останавливается"));if(preparing)return CompletableFuture.failedFuture(new IOException("Архив сохраняется; повторите проверку через секунду"));var checkpoint=visible.claimCheckpoint();
        try{return CompletableFuture.supplyAsync(()->{try{return lookupArchived(checkpoint,id,1,LOOKUP_SEGMENTS);}catch(IOException error){throw new CompletionException(error);}},worker);}catch(RuntimeException error){return CompletableFuture.failedFuture(error);}
    }}
    public CompletionStage<Void> prepareClose(){try(var guard=SuiteState.guard()){
        if(closed!=null)return closed;closing=true;
        closed=prepared.handle((ignored,error)->null).thenCompose(ignored->data.prepareClose()).handle((ignored,error)->{worker.shutdown();return error;}).thenCompose(error->workerClosed.thenCompose(ignored->error==null?CompletableFuture.completedFuture(null):CompletableFuture.failedFuture(error)));
        return closed;
    }}
    public void awaitClosed(){var done=prepareClose().toCompletableFuture();if(SuiteState.held()&&!done.isDone())throw new IllegalStateException("Сначала завершите запись вне общей блокировки");try{done.get(10,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("Ожидание архива прервано",error);}catch(ExecutionException|TimeoutException error){throw new IllegalStateException("Архив ещё завершается",error);}}
    private State compact(State state)throws IOException{
        var claims=state.receipts().values().stream().filter(r->r.order()==null&&r.status()!=ReceiptStatus.PENDING).sorted(Comparator.comparingLong(Receipt::resolvedAt).thenComparing(Receipt::id)).toList();if(claims.size()<=COMPACT_AFTER)return state;
        var removed=List.copyOf(claims.subList(0,claims.size()-RECENT_CLAIMS));long sequence=Math.addExact(state.claimCheckpoint().sequence(),1);String payload=encodeArchive(new Archive(sequence,state.claimCheckpoint().hash(),removed));String hash=sha(payload);Path target=archiveFile(sequence,hash);
        if(Files.exists(target)){if(!readText(target).equals(payload))throw new IOException("Архив выдачи изменён");}else AtomicFiles.writeDetached(target,payload);
        try(var channel=FileChannel.open(target,StandardOpenOption.WRITE)){channel.force(true);}forceDirectoryChain(archiveDirectory.toAbsolutePath());
        // The archive acknowledgement is strictly before the main state can forget these receipts.
        return LimitBook.checkpointClaims(state,removed.stream().map(Receipt::id).toList(),sequence,hash);
    }
    /** A forced file alone does not acknowledge a renamed directory entry across power loss. */
    private static void forceDirectoryChain(Path directory)throws IOException{if(directory==null)return;try(var channel=FileChannel.open(directory,StandardOpenOption.READ)){channel.force(true);}Path parent=directory.getParent();if(parent!=null)try(var channel=FileChannel.open(parent,StandardOpenOption.READ)){channel.force(true);}}
    private Path archiveFile(long sequence,String hash){return archiveDirectory.resolve(String.format(Locale.ROOT,"%020d-%s.yml",sequence,hash));}
    private String readText(Path target)throws IOException{if(Files.size(target)>MAX_BYTES)throw new IOException("Слишком большой архив выдачи");return Files.readString(target,StandardCharsets.UTF_8);}
    private Archive readArchive(long sequence,String hash)throws IOException{String text=readText(archiveFile(sequence,hash));if(!sha(text).equals(hash))throw new IOException("Контрольная сумма архива выдачи не совпадает");var yaml=new YamlConfiguration();try{yaml.loadFromString(text);}catch(org.bukkit.configuration.InvalidConfigurationException error){throw new IOException("Повреждён архив выдачи",error);}SafeYaml.keys(yaml,"schema","sequence","previous","receipts");if(num(yaml,"schema")!=1||num(yaml,"sequence")!=sequence)throw new IOException("Неверная последовательность архива выдачи");String previous=text(yaml,"previous");if(!previous.matches("[0-9a-f]{64}")||sequence==1&&!previous.equals(EMPTY_ARCHIVE_HASH)||sequence>1&&previous.equals(EMPTY_ARCHIVE_HASH))throw new IOException("Неверная предыдущая часть архива");var rows=section(yaml,"receipts");if(rows.getKeys(false).isEmpty()||rows.getKeys(false).size()>MAX_RECEIPTS)throw new IOException("Неверный размер части архива");var receipts=new ArrayList<Receipt>();for(String id:rows.getKeys(false)){Receipt receipt=readReceipt(UUID.fromString(id),section(rows,id));if(receipt.order()!=null||receipt.status()==ReceiptStatus.PENDING)throw new IOException("Нельзя архивировать незавершённое обеспечение или выдачу");receipts.add(receipt);}return new Archive(sequence,previous,List.copyOf(receipts));}
    private Receipt lookupArchived(ClaimCheckpoint checkpoint,UUID id,long minimumSequence,int maximumParts)throws IOException{long sequence=checkpoint.sequence();String hash=checkpoint.hash();int read=0;while(sequence>=minimumSequence){if(++read>maximumParts)throw new IOException("Поиск квитанции превысил предел частей архива; нужна отдельная сверка архива");var archive=readArchive(sequence,hash);for(var receipt:archive.receipts())if(receipt.id().equals(id))return receipt;hash=archive.previous();sequence--;}return null;}
    private void verifyArchives(State state)throws IOException{
        archivedIDs.clear();var checkpoint=state.claimCheckpoint();long sequence=checkpoint.sequence();String hash=checkpoint.hash();var cash=new HashMap<UUID,Long>();var items=new HashMap<UUID,Map<String,Long>>();
        while(sequence>0){var archive=readArchive(sequence,hash);var local=new HashSet<UUID>();for(var receipt:archive.receipts()){
            if(!local.add(receipt.id())||state.receipts().containsKey(receipt.id())||archivedIDs.maybe(receipt.id())&&lookupArchived(checkpoint,receipt.id(),sequence+1,LOOKUP_SEGMENTS)!=null)throw new IOException("Повтор квитанции в архиве выдачи");archivedIDs.add(receipt.id());
            if(receipt.status()==ReceiptStatus.DONE){if(receipt.kind()==Kind.CASH_CLAIM)cash.merge(receipt.owner(),receipt.cents(),Math::addExact);else items.computeIfAbsent(receipt.owner(),owner->new HashMap<>()).merge(receipt.product(),(long)receipt.quantity(),Math::addExact);}
        }hash=archive.previous();sequence--;}
        if(!hash.equals(EMPTY_ARCHIVE_HASH)||!cash.equals(checkpoint.paidCash())||!items.equals(checkpoint.paidItems()))throw new IOException("Итоги архива выдачи не соответствуют журналу");
    }
    private static String sha(String text)throws IOException{try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException error){throw new IOException(error);}}
    private static String encodeArchive(Archive archive)throws IOException{var yaml=new YamlConfiguration();yaml.set("schema",1);yaml.set("sequence",archive.sequence());yaml.set("previous",archive.previous());yaml.createSection("receipts");for(var receipt:archive.receipts())writeReceipt(yaml.createSection("receipts."+receipt.id()),receipt);return bounded(yaml.saveToString());}
    private static String bounded(String text)throws IOException{if(text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IOException("Журнал личного рынка достиг предела");return text;}
    public static String encode(State state)throws IOException{
        var y=new YamlConfiguration();y.set("schema",2);for(String root:List.of("orders","receipts","wallets","items","fills"))y.createSection(root);
        for(var o:new TreeMap<>(state.orders()).values()){var s=y.createSection("orders."+o.id());s.set("owner",o.owner().toString());s.set("side",o.side().name());s.set("product",o.product());s.set("quantity",o.quantity());s.set("remaining",o.remaining());s.set("unit-cents",o.unitCents());s.set("created",o.created());s.set("status",o.status().name());}
        for(var r:new TreeMap<>(state.receipts()).values())writeReceipt(y.createSection("receipts."+r.id()),r);
        new TreeMap<>(state.wallets()).forEach((id,n)->y.set("wallets."+id,n));new TreeMap<>(state.items()).forEach((id,items)->{var s=y.createSection("items."+id);new TreeMap<>(items).forEach(s::set);});
        for(var f:state.fills()){var s=y.createSection("fills."+f.id());s.set("buy",f.buy().toString());s.set("sell",f.sell().toString());s.set("buyer",f.buyer().toString());s.set("seller",f.seller().toString());s.set("product",f.product());s.set("quantity",f.quantity());s.set("unit-cents",f.unitCents());s.set("at",f.at());}
        var c=y.createSection("claim-checkpoint");c.set("sequence",state.claimCheckpoint().sequence());c.set("hash",state.claimCheckpoint().hash());c.createSection("paid-cash");c.createSection("paid-items");new TreeMap<>(state.claimCheckpoint().paidCash()).forEach((id,n)->c.set("paid-cash."+id,n));new TreeMap<>(state.claimCheckpoint().paidItems()).forEach((id,row)->{var s=c.createSection("paid-items."+id);new TreeMap<>(row).forEach(s::set);});return bounded(y.saveToString());
    }
    public static State decode(ConfigurationSection y)throws IOException{
        long schema=num(y,"schema");if(schema==1)SafeYaml.keys(y,"schema","orders","receipts","wallets","items","fills");else if(schema==2)SafeYaml.keys(y,"schema","orders","receipts","wallets","items","fills","claim-checkpoint");else throw new IOException("Неизвестная версия личного рынка");
        var orders=new LinkedHashMap<UUID,Order>();var receipts=new LinkedHashMap<UUID,Receipt>();var wallets=new HashMap<UUID,Long>();var items=new HashMap<UUID,Map<String,Long>>();var fills=new ArrayList<Fill>();
        var root=section(y,"orders");for(String id:root.getKeys(false)){var s=section(root,id);SafeYaml.keys(s,"owner","side","product","quantity","remaining","unit-cents","created","status");UUID key=UUID.fromString(id);orders.put(key,new Order(key,uuid(s,"owner"),Side.valueOf(text(s,"side")),text(s,"product"),integer(s,"quantity"),integer(s,"remaining"),num(s,"unit-cents"),num(s,"created"),Status.valueOf(text(s,"status"))));}
        root=section(y,"receipts");for(String id:root.getKeys(false)){UUID key=UUID.fromString(id);receipts.put(key,readReceipt(key,section(root,id)));}
        readCash(section(y,"wallets"),wallets);readItems(section(y,"items"),items);
        root=section(y,"fills");for(String id:root.getKeys(false)){var s=section(root,id);SafeYaml.keys(s,"buy","sell","buyer","seller","product","quantity","unit-cents","at");fills.add(new Fill(UUID.fromString(id),uuid(s,"buy"),uuid(s,"sell"),uuid(s,"buyer"),uuid(s,"seller"),text(s,"product"),integer(s,"quantity"),num(s,"unit-cents"),num(s,"at")));}
        ClaimCheckpoint checkpoint=ClaimCheckpoint.empty();if(schema==2){var c=section(y,"claim-checkpoint");SafeYaml.keys(c,"sequence","hash","paid-cash","paid-items");var paidCash=new HashMap<UUID,Long>();var paidItems=new HashMap<UUID,Map<String,Long>>();readCash(section(c,"paid-cash"),paidCash);readItems(section(c,"paid-items"),paidItems);checkpoint=new ClaimCheckpoint(num(c,"sequence"),text(c,"hash"),paidCash,paidItems);}
        return new State(orders,receipts,wallets,items,fills,checkpoint);
    }
    private static void readCash(ConfigurationSection root,Map<UUID,Long> target)throws IOException{for(String id:root.getKeys(false))target.put(UUID.fromString(id),num(root,id));}
    private static void readItems(ConfigurationSection root,Map<UUID,Map<String,Long>> target)throws IOException{for(String id:root.getKeys(false)){var s=section(root,id);var balances=new HashMap<String,Long>();for(String material:s.getKeys(false))balances.put(material,num(s,material));target.put(UUID.fromString(id),Map.copyOf(balances));}}
    private static void writeReceipt(ConfigurationSection s,Receipt r){s.set("owner",r.owner().toString());s.set("kind",r.kind().name());s.set("order",r.order()==null?"":r.order().toString());s.set("product",r.product());s.set("quantity",r.quantity());s.set("cents",r.cents());s.set("created",r.created());s.set("status",r.status().name());s.set("resolved-at",r.resolvedAt());}
    private static Receipt readReceipt(UUID key,ConfigurationSection s)throws IOException{SafeYaml.keys(s,"owner","kind","order","product","quantity","cents","created","status","resolved-at");String order=text(s,"order");return new Receipt(key,uuid(s,"owner"),Kind.valueOf(text(s,"kind")),order.isEmpty()?null:UUID.fromString(order),text(s,"product"),integer(s,"quantity"),num(s,"cents"),num(s,"created"),ReceiptStatus.valueOf(text(s,"status")),num(s,"resolved-at"));}
    private static ConfigurationSection section(ConfigurationSection s,String key)throws IOException{var child=s.getConfigurationSection(key);if(child==null)throw new IOException("Нет раздела "+key);return child;}
    private static String text(ConfigurationSection s,String key)throws IOException{if(!(s.get(key)instanceof String value))throw new IOException("Неверная строка "+key);return value;}
    private static UUID uuid(ConfigurationSection s,String key)throws IOException{return UUID.fromString(text(s,key));}
    private static int integer(ConfigurationSection s,String key)throws IOException{return Math.toIntExact(num(s,key));}
    private static long num(ConfigurationSection s,String key)throws IOException{if(!(s.get(key)instanceof Number n))throw new IOException("Неверное число "+key);try{return new java.math.BigDecimal(n.toString()).longValueExact();}catch(ArithmeticException|NumberFormatException e){throw new IOException("Неверное целое "+key,e);}}
}
