package ru.neverland.mintespionage.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintespionage.model.IntelReport;
import ru.neverland.mintespionage.model.OperationStatus;
import ru.neverland.mintespionage.model.SpyOperation;
import ru.neverland.mintespionage.model.TownSpyData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EspionageRepository {
    private boolean ready, completionBusy, closing;private final Map<UUID,String> appliedPurchases=new LinkedHashMap<>();
    private final ru.neverland.core.DurableSnapshots completionWrites;
    private java.util.concurrent.CompletionStage<?> lastWrite=java.util.concurrent.CompletableFuture.completedFuture(null);
    private void loaded(){ru.neverland.core.AtomicFiles.loaded(file.toPath());ready=true;}
    private void gate(){if(!ready||!ru.neverland.core.AtomicFiles.writable(file.toPath()))throw new IllegalStateException("Хранилище заблокировано: восстановите данные и перезапустите сервер");}

    private final java.util.function.IntSupplier reportLimit,historyLimit;private final java.util.function.BooleanSupplier deleteExpired;private final java.util.function.Consumer<Throwable> warning;private final File file;private final Map<UUID,TownSpyData> towns=new LinkedHashMap<>();
    private final Map<UUID,SpyOperation> operations=new LinkedHashMap<>();private final Map<UUID,List<IntelReport>> reports=new LinkedHashMap<>();private boolean dirty;
    public EspionageRepository(JavaPlugin plugin){this(new File(plugin.getDataFolder(),"data.yml").toPath(),()->plugin.getConfig().getInt("limits.reports-per-town",50),()->plugin.getConfig().getInt("limits.history-per-town",40),()->plugin.getConfig().getBoolean("reports.delete-expired",true),error->plugin.getLogger().log(java.util.logging.Level.WARNING,"Изменения разведки не подтверждены: миссии приостановлены до проверки файла",error));}
    EspionageRepository(java.nio.file.Path path,java.util.function.IntSupplier reportLimit,java.util.function.IntSupplier historyLimit,java.util.function.BooleanSupplier deleteExpired,java.util.function.Consumer<Throwable> warning){this.file=path.toFile();this.reportLimit=reportLimit;this.historyLimit=historyLimit;this.deleteExpired=deleteExpired;this.warning=warning;completionWrites=new ru.neverland.core.DurableSnapshots(path,"NeverLand-spy-result-write",1);}
    public void load(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {ready=false;
        towns.clear();operations.clear();reports.clear();appliedPurchases.clear();YamlConfiguration yaml=ru.neverland.core.SafeYaml.load(file.toPath());ru.neverland.core.SafeYaml.keys(yaml,"towns","operations","reports","purchases");
        var receipts=ru.neverland.core.SafeYaml.section(yaml,"purchases");if(receipts!=null){if(receipts.getKeys(false).size()>SpyPurchases.MAX)throw new IllegalArgumentException("Слишком много квитанций покупки");for(String key:receipts.getKeys(false)){String fingerprint=ru.neverland.core.SafeYaml.text(receipts,key);if(!fingerprint.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Повреждена квитанция покупки");appliedPurchases.put(UUID.fromString(key),fingerprint);}}
        ConfigurationSection townRoot=ru.neverland.core.SafeYaml.section(yaml,"towns");if(townRoot!=null)for(String raw:townRoot.getKeys(false))try{
            UUID id=UUID.fromString(raw);TownSpyData data=new TownSpyData(id);data.networkLevel(ru.neverland.core.SafeYaml.intValue(townRoot,raw+".network-level"));data.defenseLevel(ru.neverland.core.SafeYaml.intValue(townRoot,raw+".defense-level"));
            ConfigurationSection cooldowns=ru.neverland.core.SafeYaml.section(townRoot,raw+".cooldowns");if(cooldowns!=null)for(String key:cooldowns.getKeys(false))data.cooldown(key,ru.neverland.core.SafeYaml.longValue(cooldowns,key));towns.put(id,data);
        }catch(IllegalArgumentException exception){throw new IllegalArgumentException("Повреждены сохранённые данные: EspionageRepository");}
        ConfigurationSection operationRoot=ru.neverland.core.SafeYaml.section(yaml,"operations");if(operationRoot!=null)for(String raw:operationRoot.getKeys(false))try{
            String p="operations."+raw;SpyOperation operation=new SpyOperation(UUID.fromString(raw),UUID.fromString(ru.neverland.core.SafeYaml.stringValue(yaml,p+".attacker-id")),ru.neverland.core.SafeYaml.stringValue(yaml,p+".attacker-name","?"),
                    UUID.fromString(ru.neverland.core.SafeYaml.stringValue(yaml,p+".target-id")),ru.neverland.core.SafeYaml.stringValue(yaml,p+".target-name","?"),UUID.fromString(ru.neverland.core.SafeYaml.stringValue(yaml,p+".actor-id")),ru.neverland.core.SafeYaml.stringValue(yaml,p+".actor-name","?"),
                    ru.neverland.core.SafeYaml.stringValue(yaml,p+".type","reconnaissance"),ru.neverland.core.SafeYaml.longValue(yaml,p+".started-at"),ru.neverland.core.SafeYaml.longValue(yaml,p+".completes-at"),ru.neverland.core.SafeYaml.doubleValue(yaml,p+".success-chance"),
                    ru.neverland.core.SafeYaml.doubleValue(yaml,p+".detection-chance"),ru.neverland.core.SafeYaml.doubleValue(yaml,p+".cost"),OperationStatus.valueOf(ru.neverland.core.SafeYaml.stringValue(yaml,p+".status","CANCELLED")),ru.neverland.core.SafeYaml.booleanValue(yaml,p+".detected"));operation.timer(ru.neverland.core.ActivityTimer.read(yaml,p+".",operation.timer()));operations.put(operation.id(),operation);
        }catch(Exception exception){throw new IllegalArgumentException("Повреждены сохранённые данные: EspionageRepository");}
        ConfigurationSection reportRoot=ru.neverland.core.SafeYaml.section(yaml,"reports");if(reportRoot!=null)for(String rawOwner:reportRoot.getKeys(false))try{
            UUID owner=UUID.fromString(rawOwner);ConfigurationSection values=ru.neverland.core.SafeYaml.section(reportRoot,rawOwner);if(values==null)continue;List<IntelReport> list=new ArrayList<>();
            for(String raw:values.getKeys(false)){String p="reports."+rawOwner+"."+raw;list.add(new IntelReport(UUID.fromString(raw),owner,UUID.fromString(ru.neverland.core.SafeYaml.stringValue(yaml,p+".target-id")),ru.neverland.core.SafeYaml.stringValue(yaml,p+".target-name","?"),ru.neverland.core.SafeYaml.stringValue(yaml,p+".type","reconnaissance"),ru.neverland.core.SafeYaml.longValue(yaml,p+".created-at"),ru.neverland.core.SafeYaml.longValue(yaml,p+".expires-at"),ru.neverland.core.SafeYaml.strings(yaml,p+".lines"),ru.neverland.core.SafeYaml.booleanValue(yaml,p+".read")));}reports.put(owner,list);
        }catch(Exception exception){throw new IllegalArgumentException("Повреждены сохранённые данные: EspionageRepository");}
        dirty=false;
    loaded();} }
    public TownSpyData town(UUID id){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {gate();if((completionBusy||closing)&&!towns.containsKey(id))return new TownSpyData(id);return towns.computeIfAbsent(id,key->{dirty=true;return new TownSpyData(key);});} }
    public void add(SpyOperation operation){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {mutationGate();operations.put(operation.id(),operation);dirty=true;} }
    public SpyOperation operation(UUID id){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {gate();return operations.get(id);} }
    public List<SpyOperation> operations(){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {gate();return List.copyOf(operations.values());} }
    public List<SpyOperation> active(UUID town){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {gate();return operations.values().stream().filter(v->v.status()==OperationStatus.ACTIVE&&v.attackerTownId().equals(town)).sorted(Comparator.comparingLong(SpyOperation::completesAt)).toList();} }
    public void addReport(IntelReport report){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {mutationGate();List<IntelReport> list=reports.computeIfAbsent(report.ownerTownId(),key->new ArrayList<>());list.add(0,report);trim(list,reportLimit.getAsInt());dirty=true;} }
    public List<IntelReport> reports(UUID town){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {gate();return List.copyOf(reports.getOrDefault(town,List.of()));} }
    public void markDirty(){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {mutationGate();dirty=true;} }
    public void cleanup(long now){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {gate();if(completionBusy||closing)return;
        if(deleteExpired.getAsBoolean())for(List<IntelReport> list:reports.values())if(list.removeIf(report->report.expired(now)))dirty=true;
        int limit=Math.max(5,historyLimit.getAsInt());for(UUID town:towns.keySet()){
            List<SpyOperation> finished=operations.values().stream().filter(v->v.attackerTownId().equals(town)&&v.status()!=OperationStatus.ACTIVE).sorted(Comparator.comparingLong(SpyOperation::startedAt).reversed()).toList();
            if(finished.size()>limit)for(SpyOperation operation:finished.subList(limit,finished.size())){operations.remove(operation.id());dirty=true;}
        }
    } }
    private <T> void trim(List<T> list,int maximum){while(list.size()>Math.max(1,maximum))list.remove(list.size()-1);}
    public void saveIfDirty(){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {gate();if(dirty&&!completionBusy&&!closing)saveAsync();} }
    public boolean canMutate(){try(var guard=ru.neverland.core.SuiteState.guard()){return ready&&!completionBusy&&!closing&&ru.neverland.core.AtomicFiles.writable(file.toPath());}}
    public void mutationGate(){gate();if(completionBusy||closing)throw new IllegalStateException("Результат предыдущей операции сохраняется. Повторите действие позже.");}
    public java.util.concurrent.CompletionStage<Void> complete(SpyOperation operation,OperationStatus outcome,boolean detected,IntelReport report){try(var guard=ru.neverland.core.SuiteState.guard()){
        mutationGate();if(operations.get(operation.id())!=operation||operation.status()!=OperationStatus.ACTIVE)throw new IllegalStateException("Операция уже изменилась");
        if(outcome!=OperationStatus.SUCCEEDED&&outcome!=OperationStatus.FAILED)throw new IllegalArgumentException("Неверный результат операции");
        if((outcome==OperationStatus.SUCCEEDED)!=(report!=null)||report!=null&&(!report.ownerTownId().equals(operation.attackerTownId())||!report.targetTownId().equals(operation.targetTownId())||!report.type().equals(operation.type())||reports.values().stream().flatMap(List::stream).anyMatch(r->r.id().equals(report.id()))))throw new IllegalArgumentException("Отчёт не соответствует операции");
        final int retained=Math.max(1,reportLimit.getAsInt());
        var image=capture(operation.id(),outcome,detected,report);completionBusy=true;
        var stage=completionWrites.submit(()->encode(image)).handle((receipt,error)->{try(var committed=ru.neverland.core.SuiteState.guard()){
            if(error!=null){ready=false;completionBusy=false;throw new java.util.concurrent.CompletionException(error);}
            operation.finish(outcome,detected);if(report!=null){var list=reports.computeIfAbsent(report.ownerTownId(),key->new ArrayList<>());list.add(0,report);trim(list,retained);}
            dirty=false;completionBusy=false;return null;
        }});lastWrite=stage;return stage.thenApply(ignored->null);
    }}
    public boolean purchaseApplied(SpyPurchases.Terms t){try(var guard=ru.neverland.core.SuiteState.guard()){gate();String receipt=appliedPurchases.get(t.id());if(receipt!=null&&!receipt.equals(t.fingerprint()))throw new IllegalStateException("UUID покупки связан с другими условиями");return receipt!=null;}}
    public java.util.concurrent.CompletionStage<Void> applyPurchase(SpyPurchases.Purchase purchase,long now){try(var guard=ru.neverland.core.SuiteState.guard()){
        var t=purchase.terms();if(purchase.phase()!=SpyPurchases.Phase.PAID)throw new IllegalArgumentException("Нет подтверждения оплаты");if(purchaseApplied(t))return java.util.concurrent.CompletableFuture.completedFuture(null);mutationGate();if(appliedPurchases.size()>=SpyPurchases.MAX)throw new IllegalStateException("Архив квитанций разведки заполнен");
        var data=towns.getOrDefault(t.town(),new TownSpyData(t.town()));var image=new LinkedHashMap<>(capture(null,null,false,null));SpyOperation operation;
        String prefix="towns."+t.town();image.put(prefix+".network-level",data.networkLevel());image.put(prefix+".defense-level",data.defenseLevel());
        if(t.kind()==SpyPurchases.Kind.MISSION){if(operations.containsKey(t.id()))throw new IllegalStateException("Миссия с этим ID уже существует без квитанции");operation=new SpyOperation(t.id(),t.town(),t.townName(),t.target(),t.targetName(),t.actor(),t.actorName(),t.type(),now,Math.addExact(now,t.duration()),t.success(),t.detection(),t.cents()/100.0,OperationStatus.ACTIVE,false);putOperation(image,operation);image.put(prefix+".cooldowns."+t.target()+"_"+t.type(),Math.addExact(now,t.cooldown()));}
        else{operation=null;int current=t.kind()==SpyPurchases.Kind.NETWORK?data.networkLevel():data.defenseLevel();if(current!=t.fromLevel())throw new IllegalStateException("Уровень города изменён; нужна сверка покупки "+t.id());image.put(prefix+(t.kind()==SpyPurchases.Kind.NETWORK?".network-level":".defense-level"),t.fromLevel()+1);}
        image.put("purchases."+t.id(),t.fingerprint());var frozen=java.util.Collections.unmodifiableMap(image);completionBusy=true;
        var stage=completionWrites.submit(()->encode(frozen)).handle((ack,error)->{try(var committed=ru.neverland.core.SuiteState.guard()){completionBusy=false;if(error!=null){ready=false;throw new java.util.concurrent.CompletionException(error);}var actual=towns.computeIfAbsent(t.town(),TownSpyData::new);if(operation!=null){operations.put(operation.id(),operation);actual.cooldown(t.target()+"_"+t.type(),Math.addExact(now,t.cooldown()));}else if(t.kind()==SpyPurchases.Kind.NETWORK)actual.networkLevel(t.fromLevel()+1);else actual.defenseLevel(t.fromLevel()+1);appliedPurchases.put(t.id(),t.fingerprint());dirty=false;return null;}});lastWrite=stage;return stage.thenApply(v->null);
    }}
    private static void putOperation(Map<String,Object> values,SpyOperation v){String p="operations."+v.id();values.put(p+".attacker-id",v.attackerTownId().toString());values.put(p+".attacker-name",v.attackerName());values.put(p+".target-id",v.targetTownId().toString());values.put(p+".target-name",v.targetName());values.put(p+".actor-id",v.actorId().toString());values.put(p+".actor-name",v.actorName());values.put(p+".type",v.type());values.put(p+".started-at",v.startedAt());values.put(p+".completes-at",v.completesAt());values.put(p+".admin-paused-at",v.timer().pausedAt());values.put(p+".admin-duration",v.timer().duration());values.put(p+".success-chance",v.successChance());values.put(p+".detection-chance",v.detectionChance());values.put(p+".cost",v.cost());values.put(p+".status",v.status().name());values.put(p+".detected",v.detected());}
    private void saveAsync(){
        mutationGate();var image=capture(null,null,false,null);completionBusy=true;
        lastWrite=completionWrites.submit(()->encode(image)).handle((receipt,error)->{try(var guard=ru.neverland.core.SuiteState.guard()){
            completionBusy=false;if(error!=null){ready=false;warning.accept(error);throw new java.util.concurrent.CompletionException(error);}dirty=false;return null;
        }});
    }
    public void save(){try(var guard=ru.neverland.core.SuiteState.guard()){mutationGate();var image=capture(null,null,false,null);try{ru.neverland.core.AtomicFiles.write(file.toPath(),()->encode(image));dirty=false;}catch(IOException error){throw new java.io.UncheckedIOException(error);}}}
    private Map<String,Object> capture(UUID completed,OperationStatus outcome,boolean detected,IntelReport added){
        var values=new LinkedHashMap<String,Object>();appliedPurchases.forEach((id,hash)->values.put("purchases."+id,hash));for(TownSpyData data:towns.values()){String p="towns."+data.townId();values.put(p+".network-level",data.networkLevel());values.put(p+".defense-level",data.defenseLevel());for(var e:data.cooldowns().entrySet())values.put(p+".cooldowns."+e.getKey(),e.getValue());}
        for(SpyOperation v:operations.values()){String p="operations."+v.id();values.put(p+".attacker-id",v.attackerTownId().toString());values.put(p+".attacker-name",v.attackerName());values.put(p+".target-id",v.targetTownId().toString());values.put(p+".target-name",v.targetName());values.put(p+".actor-id",v.actorId().toString());values.put(p+".actor-name",v.actorName());values.put(p+".type",v.type());values.put(p+".started-at",v.startedAt());values.put(p+".completes-at",v.completesAt());values.put(p+".admin-paused-at",v.timer().pausedAt());values.put(p+".admin-duration",v.timer().duration());values.put(p+".success-chance",v.successChance());values.put(p+".detection-chance",v.detectionChance());values.put(p+".cost",v.cost());boolean changed=v.id().equals(completed);values.put(p+".status",(changed?outcome:v.status()).name());values.put(p+".detected",changed?detected:v.detected());}
        var owners=new java.util.HashSet<>(reports.keySet());if(added!=null)owners.add(added.ownerTownId());
        for(UUID owner:owners){var entries=new ArrayList<>(reports.getOrDefault(owner,List.of()));if(added!=null&&added.ownerTownId().equals(owner)){entries.add(0,added);trim(entries,reportLimit.getAsInt());}for(IntelReport v:entries){String p="reports."+owner+"."+v.id();values.put(p+".target-id",v.targetTownId().toString());values.put(p+".target-name",v.targetName());values.put(p+".type",v.type());values.put(p+".created-at",v.createdAt());values.put(p+".expires-at",v.expiresAt());values.put(p+".lines",v.lines());values.put(p+".read",v.read());}}
        return java.util.Collections.unmodifiableMap(values);
    }
    private static String encode(Map<String,Object> values)throws IOException{var yaml=new YamlConfiguration();values.forEach(yaml::set);String text=yaml.saveToString();if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>64*1024*1024)throw new IOException("Файл разведки больше 64 МиБ; требуется проверка администратора");return text;}
    public java.util.concurrent.CompletionStage<Void> prepareClose(){try(var guard=ru.neverland.core.SuiteState.guard()){
        if(!closing){closing=true;if(ready&&dirty&&!completionBusy){var image=capture(null,null,false,null);lastWrite=completionWrites.submit(()->encode(image));}}
        return java.util.concurrent.CompletableFuture.allOf(lastWrite.toCompletableFuture(),completionWrites.closeAsync().toCompletableFuture());
    }}
    public void awaitClosed(){var closed=prepareClose().toCompletableFuture();if(ru.neverland.core.SuiteState.held()&&!closed.isDone())throw new IllegalStateException("Ожидайте диск вне общей блокировки");try{closed.get(10,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception error){throw new IllegalStateException("Сохранение разведки ещё не завершено",error);}}
}
