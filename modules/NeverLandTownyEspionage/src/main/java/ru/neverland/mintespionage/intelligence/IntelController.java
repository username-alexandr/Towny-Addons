package ru.neverland.mintespionage.intelligence;

import java.util.*;
import java.util.concurrent.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.mintespionage.integration.TownyHook;
import ru.neverland.mintespionage.model.*;
import ru.neverland.mintespionage.service.*;
import static ru.neverland.mintespionage.intelligence.IntelEvidence.*;

/** Durable capture -> acknowledged original data image -> confirmed archive. No banking in this workflow. */
public final class IntelController {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'МСК'").withZone(ZoneId.of("Europe/Moscow"));
    private final JavaPlugin plugin;private final EspionageService service;private final TownyHook towny;private final IntelRepository store;private final IntelCollector collector;
    private final Set<UUID> pending=new HashSet<>();private boolean closing;private long warned;
    public IntelController(JavaPlugin plugin,EspionageService service,TownyHook towny,IntelRepository store){this.plugin=plugin;this.service=service;this.towny=towny;this.store=store;collector=new IntelCollector(plugin,towny);}
    public boolean available(){return !closing&&Boolean.TRUE.equals(plugin.getConfig().get("intelligence.enabled",true))&&store.writable();}
    public boolean acceptsMission(){if(!available())return false;var captured=store.state().receipts();return captured.size()+service.repository().operations().stream().filter(o->o.status()==OperationStatus.ACTIVE&&!captured.containsKey(o.id())).count()<20_000;}
    public boolean archiveReadable(){return store.readable();}
    public boolean busy(UUID id){return pending.contains(id);}public boolean busy(){return !pending.isEmpty()||store.busy();}
    public void complete(SpyOperation operation,Boolean forceSuccess){
        if(!available()||busy()||!service.repository().canMutate()||operation.timer().paused())return;
        var receipt=store.state().receipts().get(operation.id());
        if(receipt==null){
            if(store.state().receipts().size()>=20_000){warn(new IllegalStateException("Журнал разведданных заполнен; требуется административная проверка"));return;}
            boolean success=forceSuccess!=null?forceSuccess:ThreadLocalRandom.current().nextDouble()<operation.successChance();boolean detected=ThreadLocalRandom.current().nextDouble()<operation.detectionChance();long at=System.currentTimeMillis();var definition=service.registry().get(operation.type());long lifetime=definition==null?72*3_600_000L:definition.reportLifetimeMillis();
            var attacker=towny.town(operation.attackerTownId());var target=towny.town(operation.targetTownId());var profile=Profile.of(attacker==null?0:service.data(attacker).networkLevel(),target==null?0:service.data(target).defenseLevel());
            List<Fact> facts=List.of();if(success)try{facts=collector.collect(attacker,target,operation,profile,at);}catch(RuntimeException|LinkageError error){facts=List.of(Fact.unknown("source","Сведения","Espionage.collection",at,"Источник временно недоступен; нулевые значения не подставлены"));}
            receipt=new Receipt(operation.id(),UUID.nameUUIDFromBytes(("espionage-report:"+operation.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),operation.attackerTownId(),operation.targetTownId(),operation.targetName(),operation.type(),at,Math.addExact(at,lifetime),success,detected,plugin.getConfig().getBoolean("reports.reveal-attacker-on-detection",true),operation.attackerName(),profile,facts,operation.successChance());
        }
        if(!matches(receipt,operation)){warn(new IllegalStateException("Сохранённый результат относится к другим условиям миссии"));return;}
        var captured=receipt;pending.add(operation.id());
        CompletionStage<?> saved=store.state().receipts().containsKey(operation.id())?CompletableFuture.completedFuture(null):store.commit(captured);
        saved.whenComplete((value,error)->{if(error!=null){failed(operation.id(),error);return;}try(var guard=SuiteState.guard()){
            if(closing||!plugin.isEnabled()||service.repository().operation(operation.id())!=operation||operation.status()!=OperationStatus.ACTIVE){pending.remove(operation.id());return;}
            if(!service.completionPermitted(operation)){pending.remove(operation.id());return;}
            IntelReport report=captured.succeeded()?new IntelReport(captured.report(),captured.owner(),captured.target(),captured.targetName(),captured.missionType(),captured.sampledAt(),captured.expiresAt(),lines(captured),false):null;
            service.repository().complete(operation,captured.succeeded()?OperationStatus.SUCCEEDED:OperationStatus.FAILED,captured.detected(),report).whenComplete((done,failure)->{
                if(failure!=null){failed(operation.id(),failure);return;}
                try(var closingGuard=SuiteState.guard()){if(closing){pending.remove(operation.id());return;}}
                store.applied(operation.id()).whenComplete((archive,archiveError)->{if(archiveError!=null){failed(operation.id(),archiveError);return;}try(var ledger=SuiteState.guard()){pending.remove(operation.id());if(!closing&&plugin.isEnabled())SuiteScheduler.get().runTask(plugin,()->service.notifyFinished(operation,captured.revealAttacker()));}});
            });
        }catch(Throwable failure){failed(operation.id(),failure);}});
    }
    /** Repair only the publication marker of an already completed original mission, never reroll or bank. */
    public void recover(){if(!available()||busy())return;for(var operation:service.repository().operations()){
        var receipt=store.state().receipts().get(operation.id());if(receipt==null||store.state().applied().contains(operation.id()))continue;
        if(!matches(receipt,operation)){warn(new IllegalStateException("Несовпадение квитанции с исходной миссией"));continue;}
        if(operation.status()==OperationStatus.ACTIVE&&service.completionPermitted(operation)&&service.repository().canMutate()){complete(operation,null);return;}
        if(operation.status()==(receipt.succeeded()?OperationStatus.SUCCEEDED:OperationStatus.FAILED)&&operation.detected()==receipt.detected()){
            pending.add(operation.id());store.applied(operation.id()).whenComplete((value,error)->{if(error!=null)failed(operation.id(),error);else try(var guard=SuiteState.guard()){pending.remove(operation.id());}});return;
        }
    }}
    private static boolean matches(Receipt receipt,SpyOperation operation){return receipt.owner().equals(operation.attackerTownId())&&receipt.target().equals(operation.targetTownId())&&receipt.missionType().equals(operation.type())&&receipt.targetName().equals(operation.targetName())&&receipt.attackerName().equals(operation.attackerName())&&Double.compare(receipt.successChance(),operation.successChance())==0;}
    private void failed(UUID id,Throwable error){try(var guard=SuiteState.guard()){pending.remove(id);warn(error);}}
    private void warn(Throwable error){long now=System.currentTimeMillis();if(now-warned>60_000){warned=now;plugin.getLogger().log(java.util.logging.Level.WARNING,"Результат разведки ожидает восстановления; повторной оплаты и нового броска нет",error);}}
    public Optional<Receipt> report(UUID owner,UUID report){if(!store.readable())return Optional.empty();return store.state().receipts().values().stream().filter(r->r.owner().equals(owner)&&r.report().equals(report)&&r.succeeded()).findFirst();}
    public List<Receipt> alerts(UUID defender){if(!store.readable())return List.of();var state=store.state();return state.receipts().values().stream().filter(r->r.target().equals(defender)&&r.detected()&&state.applied().contains(r.mission())).sorted(Comparator.comparingLong(Receipt::sampledAt).reversed().thenComparing(Receipt::mission)).limit(50).toList();}
    public List<Check> compare(Receipt receipt){var retained=service.repository().reports(receipt.owner()).stream().map(IntelReport::id).collect(java.util.stream.Collectors.toSet());return IntelEvidence.compare(receipt,store.state().receipts().values().stream().filter(r->retained.contains(r.report())).toList());}
    public static List<String> lines(Receipt receipt){var lines=new ArrayList<String>();lines.add("&7Наблюдение: &f"+DATE.format(Instant.ofEpochMilli(receipt.sampledAt())));for(var fact:receipt.facts()){lines.add("&7"+fact.label()+": &f"+fact.display());if(fact.availability()!=Availability.KNOWN)lines.add("&e"+fact.explanation());}lines.add("&8Миссия: "+receipt.mission());lines.add("&8Диапазон — округлённая оценка, не вероятность истины.");return List.copyOf(lines);}
    public CompletionStage<Void> prepareClose(){closing=true;return store.prepareClose();}public void awaitClosed(){store.awaitClosed();}
}
