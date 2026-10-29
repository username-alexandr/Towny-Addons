package ru.neverland.townycitizens;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.AtomicFiles;
import ru.neverland.townycitizens.model.CitizenshipRecord;
import ru.neverland.townycitizens.model.CitizenshipStatus;

public final class CitizensRepository {
    private final Path file;
    private final ru.neverland.core.AcknowledgedState<ru.neverland.townycitizens.model.Admissions.State> images;
    private volatile boolean ready;
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
    public CitizensRepository(Path file){this.file=file;images=new ru.neverland.core.AcknowledgedState<>(file,ru.neverland.townycitizens.model.Admissions.State.empty(),CitizensRepository::encode,"NeverLand-citizens-write");}
    public boolean readable(){return ready;}public boolean writable(){return ready&&images.writable();}public boolean busy(){return images.busy();}
    public ru.neverland.townycitizens.model.Admissions.State state(){return images.get();}
    public Map<String,CitizenshipRecord> all(){return state().records();}
    public CitizenshipRecord get(UUID town,UUID resident){return state().record(town,resident);}
    public void load()throws Exception{ready=false;var records=new LinkedHashMap<String,CitizenshipRecord>();var board=ru.neverland.townycitizens.model.Admissions.Board.empty();
        if(Files.exists(file)){if(Files.size(file)>64*1024*1024)throw new IOException("Слишком большой реестр гражданства");var y=ru.neverland.core.SafeYaml.load(file);long schema=number(y,"schema");if(schema!=1&&schema!=2)throw new IOException("Неизвестная схема гражданства");ru.neverland.core.SafeYaml.keys(y,schema==1?new String[]{"schema","records"}:new String[]{"schema","records","admissions-version","admissions"});var root=y.getConfigurationSection("records");if(root==null)throw new IOException("Нет реестра гражданства");for(String key:root.getKeys(false)){var r=root.getConfigurationSection(key);if(r==null)throw new IOException("Повреждена запись гражданства");var record=new CitizenshipRecord(UUID.fromString(string(r,"town")),UUID.fromString(string(r,"resident")),CitizenshipStatus.valueOf(string(r,"status")),number(r,"expires-at"),number(r,"changed-at"),string(r,"actor"),string(r,"reason"));if(!record.key().equals(key))throw new IOException("ID гражданства не совпадает");records.put(key,record);}if(schema==2){if(number(y,"admissions-version")!=1)throw new IOException("Неизвестная версия заявлений");board=JSON.fromJson(string(y,"admissions"),ru.neverland.townycitizens.model.Admissions.Board.class);}}
        images.loaded(new ru.neverland.townycitizens.model.Admissions.State(records,board));ready=true;
    }
    public java.util.concurrent.CompletionStage<ru.neverland.townycitizens.model.Admissions.State> commit(ru.neverland.townycitizens.model.Admissions.State before,ru.neverland.townycitizens.model.Admissions.State next){if(!writable())return java.util.concurrent.CompletableFuture.failedFuture(new IOException("Запись гражданства недоступна"));return images.commit(before,next);}
    public java.util.concurrent.CompletionStage<ru.neverland.townycitizens.model.Admissions.State> putAsync(CitizenshipRecord record){var before=state();UUID actor=null;try{actor=UUID.fromString(record.actor());}catch(IllegalArgumentException ignored){}return commit(before,ru.neverland.townycitizens.model.Admissions.record(before,record,actor,record.actor()));}
    public void put(CitizenshipRecord record)throws IOException{if(ru.neverland.core.SuiteState.held())throw new IllegalStateException("Используйте асинхронную запись гражданства");try{putAsync(record).toCompletableFuture().get(10,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Запись прервана",e);}catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException e){throw new IOException("Запись не подтверждена",e);}}
    public java.util.concurrent.CompletionStage<Void> prepareClose(){return images.prepareClose();}public void awaitClosed(){images.awaitClosed();}
    private static String encode(ru.neverland.townycitizens.model.Admissions.State state)throws IOException{var y=new YamlConfiguration();y.set("schema",2);y.createSection("records");new java.util.TreeMap<>(state.records()).forEach((key,r)->{String p="records."+key+".";y.set(p+"town",r.town().toString());y.set(p+"resident",r.resident().toString());y.set(p+"status",r.status().name());y.set(p+"expires-at",r.expiresAt());y.set(p+"changed-at",r.changedAt());y.set(p+"actor",r.actor());y.set(p+"reason",r.reason());});y.set("admissions-version",1);y.set("admissions",JSON.toJson(state.board()));String result=y.saveToString();if(result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>64*1024*1024)throw new IOException("Реестр требует архивирования");return result;}
    static long number(ConfigurationSection s,String key)throws IOException{Object value=s.get(key);if(!(value instanceof Integer||value instanceof Long)||((Number)value).longValue()<0)throw new IOException("Ожидалось целое неотрицательное число: "+key);return((Number)value).longValue();}
    static String string(ConfigurationSection s,String key)throws IOException{if(!(s.get(key) instanceof String value))throw new IOException("Ожидалась строка: "+key);return value;}
}
