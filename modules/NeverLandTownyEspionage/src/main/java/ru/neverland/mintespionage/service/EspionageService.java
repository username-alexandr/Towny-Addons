package ru.neverland.mintespionage.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.mintespionage.integration.BuildBridge;
import ru.neverland.mintespionage.integration.TownyHook;
import ru.neverland.mintespionage.model.IntelReport;
import ru.neverland.mintespionage.model.OperationDefinition;
import ru.neverland.mintespionage.model.OperationStatus;
import ru.neverland.mintespionage.model.SpyOperation;
import ru.neverland.mintespionage.model.TownSpyData;
import ru.neverland.mintespionage.util.ColorUtil;
import ru.neverland.mintespionage.util.EspionageMath;
import ru.neverland.mintespionage.util.TimeUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class EspionageService {
    public enum StartStatus { SUCCESS, PENDING, PAYMENT_UNAVAILABLE, NO_TOWN, SELF_TARGET, NEWCOMER_PROTECTED, PROTECTION_UNAVAILABLE, DIPLOMACY_BLOCKED, LIMIT, DUPLICATE, COOLDOWN, NO_MONEY, ECONOMY_ERROR, STORAGE_BUSY }
    public record StartOutcome(StartStatus status,SpyOperation operation,long remaining,double required){}
    public enum UpgradeStatus { SUCCESS, PENDING, PAYMENT_UNAVAILABLE, MAXIMUM, NO_MONEY, ECONOMY_ERROR, STORAGE_BUSY }
    public record UpgradeOutcome(UpgradeStatus status,int level,double cost){}
    public record Preview(double successChance,double detectionChance,long duration,long cooldown){}
    private static final Map<String,String> BUILDING_NAMES=Map.of(
            "town_hall","Ратуша","forge","Кузница","barracks","Казармы","market","Рынок",
            "miners_guild","Шахтёрская гильдия","temple","Священный храм","great_library","Великая библиотека","agrarian_complex","Аграрный комплекс");
    private final JavaPlugin plugin;private final TownyHook towny;private final DefinitionRegistry registry;private final EspionageRepository repository;
    private final BuildBridge builds;private final EconomyService economy;private final MessageService messages;private BukkitTask task;
    private SpyPurchaseController purchases;public void purchases(SpyPurchaseController value){purchases=value;}public SpyPurchaseController purchases(){return purchases;}
    private ru.neverland.mintespionage.intelligence.IntelController intelligence;
    public void intelligence(ru.neverland.mintespionage.intelligence.IntelController value){intelligence=value;}
    public ru.neverland.mintespionage.intelligence.IntelController intelligence(){return intelligence;}
    private boolean readyForChange(){return repository.canMutate()&&intelligence!=null&&intelligence.available()&&!intelligence.busy();}
    private void changeGate(){if(!readyForChange())throw new IllegalStateException("Разведка сохраняет результат или временно приостановлена. Повторите действие позже.");}
    public EspionageService(JavaPlugin plugin,TownyHook towny,DefinitionRegistry registry,EspionageRepository repository,BuildBridge builds,EconomyService economy,MessageService messages){
        this.plugin=plugin;this.towny=towny;this.registry=registry;this.repository=repository;this.builds=builds;this.economy=economy;this.messages=messages;
    }
    public void startScheduler(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {stopScheduler();long ticks=Math.max(20,plugin.getConfig().getLong("scheduler.update-ticks",100));task=ru.neverland.core.SuiteScheduler.get().runTaskTimer(plugin,this::tick,20,ticks);} }
    public void stopScheduler(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {if(task!=null){task.cancel();task=null;}} }
    private java.util.concurrent.CompletionStage<Void> closing;
    public java.util.concurrent.CompletionStage<Void> prepareClose(){try(var guard=ru.neverland.core.SuiteState.guard()){if(closing!=null)return closing;stopScheduler();var archive=intelligence==null?java.util.concurrent.CompletableFuture.<Void>completedFuture(null):intelligence.prepareClose();var payments=purchases==null?java.util.concurrent.CompletableFuture.<Void>completedFuture(null):purchases.prepareClose();closing=java.util.concurrent.CompletableFuture.allOf(archive.toCompletableFuture(),payments.toCompletableFuture(),repository.prepareClose().toCompletableFuture());return closing;}}
    public void shutdown(){prepareClose();}
    public void awaitClosed(){var done=prepareClose();if(ru.neverland.core.SuiteState.held())return;try{done.toCompletableFuture().get(30,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception error){throw new IllegalStateException("Сохранение разведки ещё не завершено",error);}}
    public StartOutcome start(Player actor,Town target,OperationDefinition definition){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        if(!readyForChange()||!intelligence.acceptsMission()||purchases==null||!purchases.ready())return new StartOutcome(StartStatus.STORAGE_BUSY,null,0,0);
        Town attacker=towny.town(actor);if(attacker==null||target==null)return new StartOutcome(StartStatus.NO_TOWN,null,0,0);
        if(purchases.pending(attacker.getUUID()))return new StartOutcome(StartStatus.STORAGE_BUSY,null,0,0);if(definition.cost()>0&&!economy.purchaseAvailable())return new StartOutcome(StartStatus.PAYMENT_UNAVAILABLE,null,0,definition.cost());
        if(attacker.getUUID().equals(target.getUUID()))return new StartOutcome(StartStatus.SELF_TARGET,null,0,0);
        try {
            long shield=Math.max(ru.neverland.core.NewcomerProtection.remaining(plugin,attacker.getUUID()),ru.neverland.core.NewcomerProtection.remaining(plugin,target.getUUID()));
            if(shield>0)return new StartOutcome(StartStatus.NEWCOMER_PROTECTED,null,shield,0);
        }catch(RuntimeException unavailable){return new StartOutcome(StartStatus.PROTECTION_UNAVAILABLE,null,0,0);}
        if(ru.neverland.core.DiplomacyAccess.hostileBlocked(attacker.getUUID(),target.getUUID()))return new StartOutcome(StartStatus.DIPLOMACY_BLOCKED,null,0,0);
        int active=repository.active(attacker.getUUID()).size();int limit=activeLimit(attacker);
        if(active>=limit)return new StartOutcome(StartStatus.LIMIT,null,0,0);
        if(repository.active(attacker.getUUID()).stream().anyMatch(value->value.targetTownId().equals(target.getUUID())&&value.type().equals(definition.id())))return new StartOutcome(StartStatus.DUPLICATE,null,0,0);
        TownSpyData own=repository.town(attacker.getUUID());String cooldownKey=target.getUUID()+"_"+definition.id();long now=System.currentTimeMillis(),until=own.cooldown(cooldownKey);
        if(until>now)return new StartOutcome(StartStatus.COOLDOWN,null,until-now,0);
        Preview preview;try{if(economy.balance(attacker)<definition.cost())return new StartOutcome(StartStatus.NO_MONEY,null,0,definition.cost());preview=preview(attacker,target,definition);}catch(RuntimeException unavailable){return new StartOutcome(StartStatus.ECONOMY_ERROR,null,0,definition.cost());}
        double success=preview.successChance(),detection=preview.detectionChance();long duration=preview.duration();
        try{var terms=new SpyPurchases.Terms(UUID.randomUUID(),attacker.getUUID(),attacker.getName(),actor.getUniqueId(),actor.getName(),SpyPurchases.Kind.MISSION,SpyPurchases.cents(definition.cost()),economy.operationReason(definition,target.getName()),now,target.getUUID(),target.getName(),definition.id(),duration,definition.cooldownMillis(),success,detection,0);purchases.submit(terms);return new StartOutcome(StartStatus.PENDING,null,duration,definition.cost());}catch(RuntimeException invalid){plugin.getLogger().log(java.util.logging.Level.WARNING,"Покупка миссии не принята; списание не начиналось",invalid);return new StartOutcome(StartStatus.ECONOMY_ERROR,null,0,definition.cost());}
    } }
    public UpgradeOutcome upgrade(Player actor,Town town,boolean network){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        if(!readyForChange()||purchases==null||!purchases.ready()||purchases.pending(town.getUUID()))return new UpgradeOutcome(UpgradeStatus.STORAGE_BUSY,0,0);
        TownSpyData data=repository.town(town.getUUID());String path=network?"network":"counterintelligence";int current=network?data.networkLevel():data.defenseLevel();int maximum=Math.max(0,plugin.getConfig().getInt(path+".maximum-level",5));
        if(current>=maximum)return new UpgradeOutcome(UpgradeStatus.MAXIMUM,current,0);List<Double> costs=plugin.getConfig().getDoubleList(path+".upgrade-costs");double cost=current<costs.size()?Math.max(0,costs.get(current)):0;
        if(cost>0&&!economy.purchaseAvailable())return new UpgradeOutcome(UpgradeStatus.PAYMENT_UNAVAILABLE,current,cost);
        try{if(economy.balance(town)<cost)return new UpgradeOutcome(UpgradeStatus.NO_MONEY,current,cost);var terms=new SpyPurchases.Terms(UUID.randomUUID(),town.getUUID(),town.getName(),actor.getUniqueId(),actor.getName(),network?SpyPurchases.Kind.NETWORK:SpyPurchases.Kind.DEFENSE,SpyPurchases.cents(cost),economy.upgradeReason(network),System.currentTimeMillis(),null,null,null,0,0,0,0,current);purchases.submit(terms);return new UpgradeOutcome(UpgradeStatus.PENDING,current+1,cost);}catch(RuntimeException unavailable){plugin.getLogger().log(java.util.logging.Level.WARNING,"Улучшение разведки не принято; списание не начиналось",unavailable);return new UpgradeOutcome(UpgradeStatus.ECONOMY_ERROR,current,cost);}
    } }
    public boolean purchaseImpossible(SpyPurchases.Terms t){return towny.town(t.town())==null||t.kind()==SpyPurchases.Kind.MISSION&&towny.town(t.target())==null;}
    public boolean purchasePermitted(SpyPurchases.Terms t){if(!readyForChange()||ru.neverland.core.SuiteState.frozen(plugin.getName())||towny.town(t.town())==null)return false;if(t.kind()!=SpyPurchases.Kind.MISSION)return true;try{return towny.town(t.target())!=null&&ru.neverland.core.NewcomerProtection.remaining(plugin,t.town())==0&&ru.neverland.core.NewcomerProtection.remaining(plugin,t.target())==0&&ru.neverland.core.DiplomacyAccess.available()&&!ru.neverland.core.DiplomacyAccess.hostileBlocked(t.town(),t.target());}catch(RuntimeException unavailable){return false;}}
    public boolean forceComplete(UUID id){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {SpyOperation operation=repository.operation(id);if(operation==null||operation.status()!=OperationStatus.ACTIVE)return false;complete(operation,true);return operation.status()!=OperationStatus.ACTIVE||intelligence!=null&&intelligence.busy(id);} }
    public boolean cancel(UUID id){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {if(!repository.canMutate()||intelligence!=null&&intelligence.busy())return false;SpyOperation operation=repository.operation(id);if(operation==null||operation.status()!=OperationStatus.ACTIVE)return false;operation.finish(OperationStatus.CANCELLED,false);repository.markDirty();repository.save();return true;} }
    public void setLevel(Town town,boolean network,int level){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {changeGate();if(purchases!=null&&purchases.pending(town.getUUID()))throw new IllegalStateException("Дождитесь подтверждения покупки города; уровень сохранён в её условиях");TownSpyData data=repository.town(town.getUUID());int max=plugin.getConfig().getInt((network?"network":"counterintelligence")+".maximum-level",5);if(network)data.networkLevel(Math.min(max,Math.max(0,level)));else data.defenseLevel(Math.min(max,Math.max(0,level)));repository.markDirty();repository.save();} }
    public int activeLimit(Town town){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {TownSpyData data=repository.town(town.getUUID());return Math.max(1,plugin.getConfig().getInt("network.base-active-operations",1)+data.networkLevel()*plugin.getConfig().getInt("network.operations-per-level",1));} }
    public Preview preview(Town attacker,Town target,OperationDefinition definition){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {
        TownSpyData own=repository.town(attacker.getUUID()),defending=repository.town(target.getUUID());BuildBridge.Defense defense=builds.defense(target.getUUID());
        double success=EspionageMath.chance(definition.successChance(),own.networkLevel(),plugin.getConfig().getDouble("network.success-bonus-per-level",.04),defending.defenseLevel(),plugin.getConfig().getDouble("counterintelligence.success-penalty-per-level",.05),defense.successPenalty(),plugin.getConfig().getDouble("limits.minimum-success-chance",.05),plugin.getConfig().getDouble("limits.maximum-success-chance",.95));
        double detection=EspionageMath.detection(definition.detectionChance(),defending.defenseLevel(),plugin.getConfig().getDouble("counterintelligence.detection-bonus-per-level",.07),defense.detectionBonus(),plugin.getConfig().getDouble("limits.minimum-detection-chance",.02),plugin.getConfig().getDouble("limits.maximum-detection-chance",.95));
        long duration=EspionageMath.duration(definition.durationMillis(),own.networkLevel(),plugin.getConfig().getDouble("network.duration-reduction-per-level",.05));
        return new Preview(success,detection,duration,cooldown(attacker,target,definition.id()));
    } }
    public double defenseStrength(Town town){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {if(town==null)return 0;TownSpyData data=repository.town(town.getUUID());BuildBridge.Defense defense=builds.defense(town.getUUID());return data.defenseLevel()*plugin.getConfig().getDouble("counterintelligence.success-penalty-per-level",.05)+defense.successPenalty();} }
    public int unread(UUID town){ try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {int count=0;long now=System.currentTimeMillis();for(IntelReport report:repository.reports(town))if(!report.read()&&!report.expired(now))count++;return count;} }
    public void read(IntelReport report){ try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {if(repository.canMutate()&&(intelligence==null||!intelligence.busy())){report.markRead();repository.markDirty();}} }
    public TownSpyData data(Town town){ try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {return repository.town(town.getUUID());} }public DefinitionRegistry registry(){ try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {return registry;} }public EspionageRepository repository(){ try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {return repository;} }public EconomyService economy(){ try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {return economy;} }
    private void tick(){
        if(purchases!=null)purchases.pulse();
        if(intelligence==null||!intelligence.available()||!repository.canMutate())return;
        intelligence.recover();long now=System.currentTimeMillis();
        for(SpyOperation operation:repository.operations())if(operation.status()==OperationStatus.ACTIVE&&!operation.timer().paused()&&operation.completesAt()<=now)complete(operation,null);
        if(!intelligence.busy()){repository.cleanup(now);repository.saveIfDirty();}
    }
    private void complete(SpyOperation operation,Boolean forceSuccess){
        if(!readyForChange())return;
        try{if(Math.max(ru.neverland.core.NewcomerProtection.remaining(plugin,operation.attackerTownId()),ru.neverland.core.NewcomerProtection.remaining(plugin,operation.targetTownId()))>0){cancel(operation.id());return;}}
        catch(RuntimeException unavailable){return;}
        if(!ru.neverland.core.DiplomacyAccess.available())return;
        if(ru.neverland.core.DiplomacyAccess.hostileBlocked(operation.attackerTownId(),operation.targetTownId())){cancel(operation.id());return;}
        intelligence.complete(operation,forceSuccess);
    }
    public boolean completionPermitted(SpyOperation operation){
        if(operation.status()!=OperationStatus.ACTIVE||operation.timer().paused()||!intelligence.available()||ru.neverland.core.SuiteState.frozen(plugin.getName()))return false;
        try{return Math.max(ru.neverland.core.NewcomerProtection.remaining(plugin,operation.attackerTownId()),ru.neverland.core.NewcomerProtection.remaining(plugin,operation.targetTownId()))==0&&ru.neverland.core.DiplomacyAccess.available()&&!ru.neverland.core.DiplomacyAccess.hostileBlocked(operation.attackerTownId(),operation.targetTownId());}
        catch(RuntimeException unavailable){return false;}
    }
    public void notifyFinished(SpyOperation operation,boolean reveal){
        OperationDefinition definition=registry.get(operation.type());String name=definition==null?operation.type():ColorUtil.strip(definition.name());
        Player actor=Bukkit.getPlayer(operation.actorId());if(actor!=null)ru.neverland.core.SuiteScheduler.entity(plugin,actor,()->messages.send(actor,operation.status()==OperationStatus.SUCCEEDED?"operation-success":"operation-failed",Map.of("operation",name,"target",operation.targetName())));
        Town target=towny.town(operation.targetTownId());if(operation.detected()&&target!=null&&plugin.getConfig().getBoolean("reports.notify-target-on-detection",true))towny.notifyTown(target,messages.text(reveal?"operation-detected":"operation-detected-unknown",Map.of("operation",name,"attacker",operation.attackerName()),true));
    }
    public String operationName(String id){ try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {OperationDefinition definition=registry.get(id);return definition==null?id:ColorUtil.strip(definition.name());} }
    public long cooldown(Town source,Town target,String type){ try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {return Math.max(0,repository.town(source.getUUID()).cooldown(target.getUUID()+"_"+type)-System.currentTimeMillis());} }
    public String summary(SpyOperation operation){ try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) {return operation.targetName()+" / "+operationName(operation.type())+" / "+(operation.timer().paused()?"Пауза • ":"")+TimeUtil.format(operation.timer().remaining(System.currentTimeMillis()));} }

    public java.util.List<ru.neverland.core.ActivityAdmin.Target> adminTargets() { try (var suiteGuard21 = ru.neverland.core.SuiteState.guard()) {
        ru.neverland.core.ApiServices.primaryThread();
        return repository.operations().stream().filter(c->c.status()==OperationStatus.ACTIVE).map(c -> new ru.neverland.core.ActivityAdmin.Target(c.id().toString(),c.attackerName()+" → "+c.targetName()+" / "+c.type(),
                c.status()==OperationStatus.ACTIVE ? ru.neverland.core.ActivityAdmin.TIMED : java.util.Set.of("status"), (action,minutes) -> {
            ru.neverland.core.ApiServices.primaryThread();
            if(action.equals("status"))return c.id()+" | "+c.attackerName()+" → "+c.targetName()+" / "+c.type()+" | "+c.timer().describe(System.currentTimeMillis());
            if(!(c.status()==OperationStatus.ACTIVE))throw new IllegalStateException("Задача уже завершается или ожидает расчёта; используйте штатную сверку");
            if(action.equals("cancel")){if(!cancel(c.id()))throw new IllegalStateException("Операция уже завершена"); return "Задача отменена без провала; выполненные действия сохранены";}
            changeGate();var before=c.timer();
            try{c.timer(before.edit(action,minutes,System.currentTimeMillis())); repository.markDirty();repository.save();}
            catch(Exception ex){c.timer(before); throw ex;}
            return c.id()+" | "+c.timer().describe(System.currentTimeMillis());
        })).toList();
    } }

}
