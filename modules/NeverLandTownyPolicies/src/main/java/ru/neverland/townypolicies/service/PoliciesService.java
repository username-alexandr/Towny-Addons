package ru.neverland.townypolicies.service;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.math.BigDecimal;
import ru.neverland.integration.PolicyEffects;
import ru.neverland.townypolicies.api.*;
import ru.neverland.townypolicies.model.*;
import ru.neverland.townypolicies.config.PoliciesSettings;
import ru.neverland.townypolicies.data.PoliciesRepository;
public final class PoliciesService implements TownyPoliciesApi {
    private final Plugin plugin;private final PoliciesRepository repository;private volatile PoliciesSettings settings;private BukkitTask task;
    private record Directory(Map<UUID,String> towns,Map<UUID,UUID> nations,Map<UUID,UUID> residents){Directory{towns=Map.copyOf(towns);nations=Map.copyOf(nations);residents=Map.copyOf(residents);}}
    private volatile Directory directory=new Directory(Map.of(),Map.of(),Map.of());
    public record Offer(UUID town,String group,String mode,long revision,PoliciesSettings settings,long expires,boolean admin){}
    private final Map<UUID,Offer> offers=new HashMap<>();
    private final PresetService presets;
    public PresetService presets(){return presets;}
    public void authorize(Player p,UUID id,boolean admin){var town=TownyAPI.getInstance().getTown(id);if(town==null)throw new IllegalArgumentException("Город не найден");if(admin?!p.hasPermission("neverlandtownypolicies.admin"):!p.hasPermission("neverlandtownypolicies.use")||!manager(p,town))throw new IllegalArgumentException("Выбор доступен мэру или уполномоченному своего города");}
    public void clearOffer(UUID actor){offers.remove(actor);}
    public java.util.concurrent.CompletionStage<UUID> persist(Player p,UUID town,CityPolicies next){
        primary();if(ru.neverland.core.SuiteState.frozen(plugin.getName())||!plugin.isEnabled())throw new IllegalStateException("Политики временно приостановлены");UUID actor=p.getUniqueId();var person=new ru.neverland.core.AuditRecord.Party("PLAYER",actor.toString(),p.getName(),town.toString());var city=ru.neverland.core.AuditTrail.town(town);long previous=state(town).revision();if(next.revision()<=previous)throw new IllegalArgumentException("Нет новых изменений для сохранения");int changed=(int)Math.min(next.history().size(),Math.max(0,next.revision()-previous));String detail=next.history().stream().skip(next.history().size()-changed).map(c->{var g=settings.groups().get(c.group());var o=g.options().get(c.after());return g.name()+" → "+(o==null?c.after():o.name());}).collect(java.util.stream.Collectors.joining("; "));
        return repository.putAsync(town,next).thenApply(v->{try(var guard=ru.neverland.core.SuiteState.guard()){offers.remove(actor);presets.clear(actor);}if(!ru.neverland.core.AuditTrail.record(plugin,"selection",town+":"+next.revision(),"POLICY_SELECTION","COMPLETED",person,city,city,"",0,"","Версия "+previous+" → "+next.revision()+"; "+detail))plugin.getLogger().warning("Указ сохранён; общий журнал требует проверки");return town;});
    }
    public boolean saving(){return repository.busy();}
    public java.util.concurrent.CompletionStage<Void> prepareClose(){try(var guard=ru.neverland.core.SuiteState.guard()){stop();return repository.prepareClose();}}
    public void awaitClosed(){repository.awaitClosed();}
    public PoliciesService(Plugin plugin,PoliciesRepository repository,PoliciesSettings settings){this.plugin=plugin;this.repository=repository;this.settings=settings;this.presets=new PresetService(this,ru.neverland.townypolicies.config.PresetSettings.disabled());}
    private void primary(){if(!ru.neverland.core.SuiteState.held())throw new IllegalStateException("Нужен основной поток сервера");}
    public PoliciesSettings settings(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return settings;} }public CityPolicies state(UUID town){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return repository.get(town);} }
    public void start(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {primary();refresh();schedule();} }private void schedule(){task=ru.neverland.core.SuiteScheduler.get().runTaskTimer(plugin,this::refresh,settings.interval()*20L,settings.interval()*20L);}
    public void reload(PoliciesSettings next){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {primary();if(repository.busy())throw new IllegalStateException("Дождитесь сохранения указа");settings=next;offers.clear();presets.clearAll();if(task!=null)task.cancel();refresh();schedule();} }
    public void stop(){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {if(task!=null)task.cancel();directory=new Directory(Map.of(),Map.of(),Map.of());offers.clear();presets.clearAll();} }
    public void refresh(){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {primary();try{Map<UUID,String> towns=new HashMap<>();Map<UUID,UUID> nations=new HashMap<>(),residents=new HashMap<>();for(Town town:List.copyOf(TownyAPI.getInstance().getTowns())){towns.put(town.getUUID(),town.getName());if(town.getNationOrNull()!=null)nations.put(town.getUUID(),town.getNationOrNull().getUUID());town.getResidents().forEach(r->residents.put(r.getUUID(),town.getUUID()));}directory=new Directory(towns,nations,residents);offers.values().removeIf(o->o.expires()<System.currentTimeMillis()||!towns.containsKey(o.town()));presets.prune(towns.keySet());}catch(Exception e){directory=new Directory(Map.of(),Map.of(),Map.of());plugin.getLogger().warning("Политики ожидают данные Towny: "+e.getMessage());}} }
    public Town town(Player p){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {var r=TownyAPI.getInstance().getResident(p);return r==null?null:r.getTownOrNull();} }
    public boolean manager(Player p,Town town){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {var r=TownyAPI.getInstance().getResident(p);return r!=null&&town!=null&&town.equals(r.getTownOrNull())&&(town.isMayor(r)||ru.neverland.core.CouncilAccess.check(p,town.getUUID(),"policies",p.hasPermission("neverlandtownypolicies.manage")));} }
    private boolean taxConfigured(UUID town){try{return Boolean.TRUE.equals(ru.neverland.core.ApiServices.call("NeverLandTownyTaxes","ru.neverland.townytaxes.api.NeverLandTownyTaxesApi","municipalTaxConfigured",new Class<?>[]{UUID.class},town));}catch(Exception|LinkageError e){return false;}}
    /** Null is unavailable evidence, not a revoked already accepted unlock. */
    private Boolean unlockState(UUID town,String unlock){try{var link=ru.neverland.core.ApiServices.connect("NeverLandTownyQuests","ru.neverland.townyquests.api.TownyQuestsApi",1,"unlocked");if(!link.ready())return null;Object result=link.invoke("unlocked",new Class<?>[]{UUID.class,String.class},town,unlock);return result instanceof Boolean flag?flag:null;}catch(Exception|LinkageError ex){return null;}}

    private static String dependencyName(String name){return switch(name){case "NeverLandTownyBuilds"->"Строительство";case "NeverLandTownyResources"->"Снабжение города";case "NeverLandTownyPopulation"->"Население города";case "NeverLandTownyUpkeep"->"Содержание зданий";case "NeverLandTownyTrade"->"Торговое сообщение";case "NeverLandTownyTaxes"->"Налогообложение";case "NeverLandTownyQuests"->"Выполнение городских проектов";default->"Управление городом";};}
    /** Choosing always requires current evidence; failure must not grant a new reform. */
    public String unavailable(UUID town,PolicyGroup group,PolicyOption option){try(var guard=ru.neverland.core.SuiteState.guard()){return requirements(town,group,option,false);}}
    private String requirements(UUID town,PolicyGroup group,PolicyOption option,boolean preserveAcceptedUnlock){
        if(option==null)return "Сохранённый режим отсутствует в настройках";if(option.id().equals(group.standard()))return "";if(!group.enabled())return "Политика отключена в настройках";
        for(String name:option.requires()){
            // Only Quests is an acquisition gate. Actual cost/benefit suppliers remain strict.
            if(preserveAcceptedUnlock&&!option.unlock().isEmpty()&&name.equals("NeverLandTownyQuests"))continue;
            var dependency=Bukkit.getPluginManager().getPlugin(name);if(dependency==null||!dependency.isEnabled()||ru.neverland.core.SuiteState.frozen(name))return dependencyName(name)+" сейчас недоступно. Обратитесь к администрации";
            String minimum=DependencyVersions.MINIMUM.get(name);if(!DependencyVersions.atLeast(dependency.getDescription().getVersion(),minimum))return "Для этого режима администратору нужно обновить: "+dependencyName(name);
        }
        if(!option.unlock().isEmpty()){Boolean earned=unlockState(town,option.unlock());if(Boolean.FALSE.equals(earned)||earned==null&&!preserveAcceptedUnlock)return earned==null?"Городские проекты временно недоступны. Новые реформы пока выбрать нельзя":"Сначала завершите городской проект, открывающий этот режим: /t quests";}
        if(group.id().equals("taxes")&&!taxConfigured(town))return "В городе пока не настроен действующий сбор в свою казну";return "";
    }
    private PolicyOption selected(UUID town,PolicyGroup g){return g.options().get(repository.get(town).mode(g));}
    private boolean acknowledged(UUID town,PolicyGroup g,PolicyOption option){var choice=repository.get(town).choices().get(g.id());return choice!=null&&option!=null&&choice.mode().equals(option.id());}
    private PolicyOption active(UUID town,PolicyGroup g){if(town==null||!directory.towns().containsKey(town)||!g.enabled())return null;var option=selected(town,g);return requirements(town,g,option,acknowledged(town,g,option)).isEmpty()?option:null;}

    public java.util.concurrent.CompletionStage<UUID> choose(Player p,UUID id,String group,String mode,long revision,PoliciesSettings quoted,boolean admin){try(var guard=ru.neverland.core.SuiteState.guard()){primary();var next=proposed(p,id,group,mode,revision,quoted,admin);return persist(p,id,next);}}
    private CityPolicies proposed(Player p,UUID id,String group,String mode,long revision,PoliciesSettings quoted,boolean admin){primary();authorize(p,id,admin);if(quoted!=settings)throw new IllegalArgumentException("Настройки изменились. Откройте меню заново.");var g=settings.groups().get(group);if(g==null||!g.options().containsKey(mode))throw new IllegalArgumentException("Неизвестная политика или режим");String reason=unavailable(id,g,g.options().get(mode));if(!reason.isEmpty()&&!mode.equals(g.standard()))throw new IllegalArgumentException(reason);var next=SelectionPolicy.choose(repository.get(id),g,mode,System.currentTimeMillis(),settings.cooldownMillis(),revision,p.getUniqueId(),p.getName(),admin,reason.isEmpty());presets.check(next);return next;}
    public void offer(Player p,UUID town,String group,String mode,boolean admin){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {long revision=repository.get(town).revision();proposed(p,town,group,mode,revision,settings,admin);presets.clear(p.getUniqueId());offers.put(p.getUniqueId(),new Offer(town,group,mode,revision,settings,System.currentTimeMillis()+60000,admin));} }
    public Offer offer(Player p){ try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {var o=offers.get(p.getUniqueId());if(o==null||o.expires()<System.currentTimeMillis()){offers.remove(p.getUniqueId());return null;}return o;} }
    public java.util.concurrent.CompletionStage<UUID> confirm(Player p)throws Exception{ try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {primary();if(presets.hasOffer(p))return presets.confirm(p);var o=offer(p);if(o==null)throw new IllegalArgumentException("Нет действующего выбора. Повторите choose.");offers.remove(p.getUniqueId());return choose(p,o.town(),o.group(),o.mode(),o.revision(),o.settings(),o.admin());} }
    @Override public double effect(UUID town,String key){ try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {double value=0;for(var g:settings.groups().values()){var p=active(town,g);if(p!=null)value+=p.effect(key);}return PolicyEffects.bound(value,key.equals("happiness")?-40:-.5,key.equals("happiness")?40:.5,0);} }
    private double multiplier(UUID town,String project,boolean cost){BigDecimal value=BigDecimal.ONE;for(var g:settings.groups().values()){var p=active(town,g);if(p!=null&&p.targets(project,cost))value=value.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(p.effect(cost?"upkeep":"production"))));}return PolicyEffects.bound(value.doubleValue(),cost?1:.25,cost?3:2,1);}
    @Override public double productionMultiplier(UUID town,String project){ try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {return multiplier(town,project,false);} }
    @Override public double upkeepMultiplier(UUID town,String project){ try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {return multiplier(town,project,true);} }
    @Override public double taxMultiplier(UUID town){ try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {return 1+effect(town,"tax");} }
    @Override public boolean tariffManaged(UUID town){ try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {var p=active(town,settings.groups().get("tariffs"));return p!=null&&p.effects().containsKey("tariff");} }
    @Override public double tariff(UUID town,double manual,double maximum){ try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {var p=active(town,settings.groups().get("tariffs"));return PolicyEffects.bound(p!=null&&p.effects().containsKey("tariff")?p.effect("tariff"):manual,0,PolicyEffects.bound(maximum,0,100,20),manual);} }
    @Override public boolean importsAllowed(UUID buyer,UUID seller,boolean sameNation){ try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) {var d=directory;if(buyer==null||seller==null||!d.towns().containsKey(buyer)||!d.towns().containsKey(seller))return false;var g=settings.groups().get("imports");if(!g.enabled())return PolicyEffects.imports("open",buyer,seller,sameNation);var p=selected(buyer,g);if(p==null)return false;if(!p.id().equals(g.standard())&&!unavailable(buyer,g,p).isEmpty())return false;return PolicyEffects.imports(p.id(),buyer,seller,sameNation);} }
    @Override public Optional<PoliciesSnapshot> policies(UUID town){ try (var suiteGuard21 = ru.neverland.core.SuiteState.guard()) {var d=directory;if(town==null||!d.towns().containsKey(town))return Optional.empty();var state=repository.get(town);Map<String,String> status=new HashMap<>();for(var g:settings.groups().values()){var option=g.options().get(state.mode(g));String reason=requirements(town,g,option,acknowledged(town,g,option));status.put(g.id(),!reason.isEmpty()?reason:!unavailable(town,g,option).isEmpty()?"Сохранённый режим действует; новые реформы ждут городские проекты":"Действует");}return Optional.of(new PoliciesSnapshot(town,d.towns().get(town),state,status));} }
    @Override public Optional<PoliciesSnapshot> residentPolicies(UUID resident){ try (var suiteGuard22 = ru.neverland.core.SuiteState.guard()) {return policies(directory.residents().get(resident));} }
    @Override public java.util.Collection<Map<String,Object>> presetPlans(UUID town){try(var guard=ru.neverland.core.SuiteState.guard()){return presets.plans(town);}}
    @Override public Map<String,Object> presetImpact(UUID town,String preset,String project){try(var guard=ru.neverland.core.SuiteState.guard()){return presets.impact(town,preset,project);}}

}
