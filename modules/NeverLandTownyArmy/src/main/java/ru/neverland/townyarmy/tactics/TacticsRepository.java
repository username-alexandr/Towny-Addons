package ru.neverland.townyarmy.tactics;
import java.nio.file.*;import java.io.IOException;import java.util.*;import java.util.concurrent.*;
import com.google.gson.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;
import static ru.neverland.townyarmy.tactics.TacticsBook.*;
/** Optional feature file: an unreadable sidecar never replaces existing Army data. */
public final class TacticsRepository {
 private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();private static final long MAX_BYTES=32L*1024*1024;
 private final Path file;private final AcknowledgedState<State> images;private boolean readable;
 public TacticsRepository(Path file){this.file=file;images=new AcknowledgedState<>(file,State.empty(),TacticsRepository::encode,"nlt-army-tactics");}
 public State state(){return images.get();}public boolean writable(){return readable&&images.writable();}public boolean busy(){return images.busy();}
 public void load()throws Exception{if(SuiteState.held())throw new IllegalStateException("Дополнительный журнал армии загружается вне общей блокировки");readable=false;if(!Files.exists(file)){images.loaded(State.empty());readable=true;return;}if(Files.size(file)>MAX_BYTES)throw new IOException("Военный журнал больше 32 МиБ");var yaml=SafeYaml.load(file);SafeYaml.keys(yaml,"schema","revision","profiles","orders","kits","history");if(SafeYaml.integer(yaml,"schema")!=1)throw new IOException("Неизвестная схема дополнительного журнала армии");var profiles=new HashMap<String,Profile>();var orders=new HashMap<UUID,Order>();var kits=new HashMap<UUID,Kit>();for(Profile p:read(yaml,"profiles",Profile[].class))if(profiles.put(p.key(),p)!=null)throw new IOException("Повтор профиля");for(Order o:read(yaml,"orders",Order[].class))if(orders.put(o.id(),o)!=null)throw new IOException("Повтор приказа");for(Kit k:read(yaml,"kits",Kit[].class))if(kits.put(k.id(),k)!=null)throw new IOException("Повтор комплекта");images.loaded(new State(profiles,orders,kits,List.of(read(yaml,"history",Entry[].class)),SafeYaml.integer(yaml,"revision")));readable=true;}
 private static <T>T[] read(YamlConfiguration y,String key,Class<T[]> type)throws IOException{try{if(!y.isString(key))throw new IOException("Нет раздела "+key);var rows=JSON.fromJson(y.getString(key),type);if(rows==null||Arrays.stream(rows).anyMatch(Objects::isNull))throw new IOException("Пустая запись "+key);return rows;}catch(RuntimeException error){throw new IOException("Повреждён раздел "+key,error);}}
 public CompletionStage<State> commit(State before,State next){if(!writable())return CompletableFuture.failedFuture(new IOException("Специальности и выдача снаряжения временно недоступны"));return images.commit(before,next);}
 public CompletionStage<Void> close(){return images.prepareClose();}
 private static String encode(State s)throws IOException{var y=new YamlConfiguration();y.set("schema",1);y.set("revision",s.revision());y.set("profiles",JSON.toJson(new TreeMap<>(s.profiles()).values()));y.set("orders",JSON.toJson(new TreeMap<>(s.orders()).values()));y.set("kits",JSON.toJson(new TreeMap<>(s.kits()).values()));y.set("history",JSON.toJson(s.history()));String text=y.saveToString();if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)throw new IOException("Военный журнал больше 32 МиБ");return text;}
}
