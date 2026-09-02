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
    public enum StartStatus { SUCCESS, NO_TOWN, SELF_TARGET, LIMIT, DUPLICATE, COOLDOWN, NO_MONEY, ECONOMY_ERROR }
    public record StartOutcome(StartStatus status,SpyOperation operation,long remaining,double required){}
    public enum UpgradeStatus { SUCCESS, MAXIMUM, NO_MONEY, ECONOMY_ERROR }
    public record UpgradeOutcome(UpgradeStatus status,int level,double cost){}
    public record Preview(double successChance,double detectionChance,long duration,long cooldown){}
    private static final Map<String,String> BUILDING_NAMES=Map.of(
            "town_hall","Ратуша","forge","Кузница","barracks","Казармы","market","Рынок",
            "miners_guild","Шахтёрская гильдия","temple","Священный храм","great_library","Великая библиотека","agrarian_complex","Аграрный комплекс");
    private final JavaPlugin plugin;private final TownyHook towny;private final DefinitionRegistry registry;private final EspionageRepository repository;
    private final BuildBridge builds;private final EconomyService economy;private final MessageService messages;private BukkitTask task;
    public EspionageService(JavaPlugin plugin,TownyHook towny,DefinitionRegistry registry,EspionageRepository repository,BuildBridge builds,EconomyService economy,MessageService messages){
        this.plugin=plugin;this.towny=towny;this.registry=registry;this.repository=repository;this.builds=builds;this.economy=economy;this.messages=messages;
    }
    public void startScheduler(){stopScheduler();long ticks=Math.max(20,plugin.getConfig().getLong("scheduler.update-ticks",100));task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,ticks);}
    public void stopScheduler(){if(task!=null){task.cancel();task=null;}}
    public void shutdown(){stopScheduler();repository.save();}
    public StartOutcome start(Player actor,Town target,OperationDefinition definition){
        Town attacker=towny.town(actor);if(attacker==null||target==null)return new StartOutcome(StartStatus.NO_TOWN,null,0,0);
        if(attacker.getUUID().equals(target.getUUID()))return new StartOutcome(StartStatus.SELF_TARGET,null,0,0);
        int active=repository.active(attacker.getUUID()).size();int limit=activeLimit(attacker);
        if(active>=limit)return new StartOutcome(StartStatus.LIMIT,null,0,0);
        if(repository.active(attacker.getUUID()).stream().anyMatch(value->value.targetTownId().equals(target.getUUID())&&value.type().equals(definition.id())))return new StartOutcome(StartStatus.DUPLICATE,null,0,0);
        TownSpyData own=repository.town(attacker.getUUID());String cooldownKey=target.getUUID()+"_"+definition.id();long now=System.currentTimeMillis(),until=own.cooldown(cooldownKey);
        if(until>now)return new StartOutcome(StartStatus.COOLDOWN,null,until-now,0);
        if(economy.balance(attacker)<definition.cost())return new StartOutcome(StartStatus.NO_MONEY,null,0,definition.cost());
        Preview preview=preview(attacker,target,definition);double success=preview.successChance(),detection=preview.detectionChance();long duration=preview.duration();
        if(!economy.withdrawOperation(attacker,definition,target.getName()))return new StartOutcome(StartStatus.ECONOMY_ERROR,null,0,definition.cost());
        SpyOperation operation=new SpyOperation(UUID.randomUUID(),attacker.getUUID(),attacker.getName(),target.getUUID(),target.getName(),actor.getUniqueId(),actor.getName(),definition.id(),now,now+duration,success,detection,definition.cost(),OperationStatus.ACTIVE,false);
        own.cooldown(cooldownKey,now+definition.cooldownMillis());repository.add(operation);repository.save();return new StartOutcome(StartStatus.SUCCESS,operation,duration,definition.cost());
    }
    public UpgradeOutcome upgrade(Town town,boolean network){
        TownSpyData data=repository.town(town.getUUID());String path=network?"network":"counterintelligence";int current=network?data.networkLevel():data.defenseLevel();int maximum=Math.max(0,plugin.getConfig().getInt(path+".maximum-level",5));
        if(current>=maximum)return new UpgradeOutcome(UpgradeStatus.MAXIMUM,current,0);List<Double> costs=plugin.getConfig().getDoubleList(path+".upgrade-costs");double cost=current<costs.size()?Math.max(0,costs.get(current)):0;
        if(economy.balance(town)<cost)return new UpgradeOutcome(UpgradeStatus.NO_MONEY,current,cost);if(!economy.withdrawUpgrade(town,cost,network))return new UpgradeOutcome(UpgradeStatus.ECONOMY_ERROR,current,cost);
        if(network)data.networkLevel(current+1);else data.defenseLevel(current+1);repository.markDirty();repository.save();return new UpgradeOutcome(UpgradeStatus.SUCCESS,current+1,cost);
    }
    public boolean forceComplete(UUID id){SpyOperation operation=repository.operation(id);if(operation==null||operation.status()!=OperationStatus.ACTIVE)return false;complete(operation,true);return true;}
    public boolean cancel(UUID id){SpyOperation operation=repository.operation(id);if(operation==null||operation.status()!=OperationStatus.ACTIVE)return false;operation.finish(OperationStatus.CANCELLED,false);repository.markDirty();repository.save();return true;}
    public void setLevel(Town town,boolean network,int level){TownSpyData data=repository.town(town.getUUID());int max=plugin.getConfig().getInt((network?"network":"counterintelligence")+".maximum-level",5);if(network)data.networkLevel(Math.min(max,Math.max(0,level)));else data.defenseLevel(Math.min(max,Math.max(0,level)));repository.markDirty();repository.save();}
    public int activeLimit(Town town){TownSpyData data=repository.town(town.getUUID());return Math.max(1,plugin.getConfig().getInt("network.base-active-operations",1)+data.networkLevel()*plugin.getConfig().getInt("network.operations-per-level",1));}
    public Preview preview(Town attacker,Town target,OperationDefinition definition){
        TownSpyData own=repository.town(attacker.getUUID()),defending=repository.town(target.getUUID());BuildBridge.Defense defense=builds.defense(target.getUUID());
        double success=EspionageMath.chance(definition.successChance(),own.networkLevel(),plugin.getConfig().getDouble("network.success-bonus-per-level",.04),defending.defenseLevel(),plugin.getConfig().getDouble("counterintelligence.success-penalty-per-level",.05),defense.successPenalty(),plugin.getConfig().getDouble("limits.minimum-success-chance",.05),plugin.getConfig().getDouble("limits.maximum-success-chance",.95));
        double detection=EspionageMath.detection(definition.detectionChance(),defending.defenseLevel(),plugin.getConfig().getDouble("counterintelligence.detection-bonus-per-level",.07),defense.detectionBonus(),plugin.getConfig().getDouble("limits.minimum-detection-chance",.02),plugin.getConfig().getDouble("limits.maximum-detection-chance",.95));
        long duration=EspionageMath.duration(definition.durationMillis(),own.networkLevel(),plugin.getConfig().getDouble("network.duration-reduction-per-level",.05));
        return new Preview(success,detection,duration,cooldown(attacker,target,definition.id()));
    }
    public double defenseStrength(Town town){if(town==null)return 0;TownSpyData data=repository.town(town.getUUID());BuildBridge.Defense defense=builds.defense(town.getUUID());return data.defenseLevel()*plugin.getConfig().getDouble("counterintelligence.success-penalty-per-level",.05)+defense.successPenalty();}
    public int unread(UUID town){int count=0;long now=System.currentTimeMillis();for(IntelReport report:repository.reports(town))if(!report.read()&&!report.expired(now))count++;return count;}
    public void read(IntelReport report){report.markRead();repository.markDirty();}
    public TownSpyData data(Town town){return repository.town(town.getUUID());}public DefinitionRegistry registry(){return registry;}public EspionageRepository repository(){return repository;}public EconomyService economy(){return economy;}
    private void tick(){long now=System.currentTimeMillis();for(SpyOperation operation:repository.operations())if(operation.status()==OperationStatus.ACTIVE&&operation.completesAt()<=now)complete(operation,null);repository.cleanup(now);repository.saveIfDirty();}
    private void complete(SpyOperation operation,Boolean forceSuccess){
        OperationDefinition definition=registry.get(operation.type());boolean success=forceSuccess!=null?forceSuccess:ThreadLocalRandom.current().nextDouble()<operation.successChance();
        boolean detected=ThreadLocalRandom.current().nextDouble()<operation.detectionChance();operation.finish(success?OperationStatus.SUCCEEDED:OperationStatus.FAILED,detected);
        Town target=towny.town(operation.targetTownId());Town attacker=towny.town(operation.attackerTownId());
        if(success){long lifetime=definition==null?72*3600000L:definition.reportLifetimeMillis();repository.addReport(new IntelReport(UUID.randomUUID(),operation.attackerTownId(),operation.targetTownId(),operation.targetName(),operation.type(),System.currentTimeMillis(),System.currentTimeMillis()+lifetime,reportLines(attacker,target,operation),false));}
        Player actor=Bukkit.getPlayer(operation.actorId());if(actor!=null){String name=definition==null?operation.type():ColorUtil.strip(definition.name());messages.send(actor,success?"operation-success":"operation-failed",Map.of("operation",name,"target",operation.targetName()));}
        if(detected&&target!=null&&plugin.getConfig().getBoolean("reports.notify-target-on-detection",true)){
            boolean reveal=plugin.getConfig().getBoolean("reports.reveal-attacker-on-detection",true);String name=definition==null?operation.type():ColorUtil.strip(definition.name());
            String text=messages.text(reveal?"operation-detected":"operation-detected-unknown",Map.of("operation",name,"attacker",operation.attackerName()),true);towny.notifyTown(target,text);
        }
        repository.markDirty();repository.save();
    }
    private List<String> reportLines(Town attacker,Town target,SpyOperation operation){
        List<String> lines=new ArrayList<>();if(target==null){lines.add("&cГород был удалён до завершения операции.");return lines;}
        switch(operation.type().toLowerCase(Locale.ROOT)){
            case "treasury"->{double balance=economy.balance(target);lines.add("&7Баланс казны: &#FFD45A"+economy.format(balance));lines.add("&7Финансовое положение: &f"+wealth(balance));lines.add("&7Население: &f"+target.getResidents().size());}
            case "military"->{BuildBridge.Defense defense=builds.defense(target.getUUID());lines.add("&7Население/потенциальный гарнизон: &f"+target.getResidents().size());lines.add("&7Уровень Казарм: &f"+defense.levels().getOrDefault("barracks",0));lines.add("&7Контрразведка: &f"+repository.town(target.getUUID()).defenseLevel()+"/"+plugin.getConfig().getInt("counterintelligence.maximum-level",5));lines.add("&7Оборонный штраф агентам: &#FF7777"+Math.round(defenseStrength(target)*100)+"%");lines.add("&7PvP в городе: &f"+(towny.flag(target,"isPVP")?"включён":"выключен"));}
            case "infrastructure"->{for(Map.Entry<String,Integer> entry:builds.knownLevels(target.getUUID()).entrySet())lines.add("&7"+BUILDING_NAMES.getOrDefault(entry.getKey(),entry.getKey())+": &f"+entry.getValue()+" ур.");}
            case "diplomacy"->{lines.add("&7Нация: &f"+towny.nation(target));lines.add("&7Отношение к вашему городу: &f"+towny.relation(attacker,target));List<String> allies=towny.diplomaticNames(target,"getAllies");List<String> enemies=towny.diplomaticNames(target,"getEnemies");lines.add("&7Союзники нации: &f"+(allies.isEmpty()?"нет сведений":String.join(", ",allies)));lines.add("&7Противники нации: &f"+(enemies.isEmpty()?"нет сведений":String.join(", ",enemies)));lines.add("&7Город открыт: &f"+(towny.flag(target,"isOpen")?"да":"нет"));}
            default->{lines.add("&7Мэр: &f"+towny.mayor(target));lines.add("&7Жителей: &f"+target.getResidents().size());lines.add("&7Занято участков: &f"+target.getTownBlocks().size());lines.add("&7Бонусных участков: &f"+target.getBonusBlocks());lines.add("&7Нация: &f"+towny.nation(target));}
        }
        lines.add("");lines.add("&8Достоверность операции: "+Math.round(operation.successChance()*100)+"%");return lines;
    }
    private String wealth(double value){if(value<10000)return "скромное";if(value<50000)return "стабильное";if(value<200000)return "богатое";return "очень богатое";}
    public String operationName(String id){OperationDefinition definition=registry.get(id);return definition==null?id:ColorUtil.strip(definition.name());}
    public long cooldown(Town source,Town target,String type){return Math.max(0,repository.town(source.getUUID()).cooldown(target.getUUID()+"_"+type)-System.currentTimeMillis());}
    public String summary(SpyOperation operation){return operation.targetName()+" / "+operationName(operation.type())+" / "+TimeUtil.format(operation.completesAt()-System.currentTimeMillis());}
}
