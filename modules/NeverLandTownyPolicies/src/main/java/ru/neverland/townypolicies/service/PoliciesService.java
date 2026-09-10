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
    public PoliciesService(Plugin plugin,PoliciesRepository repository,PoliciesSettings settings){this.plugin=plugin;this.repository=repository;this.settings=settings;}
    private void primary(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Нужен основной поток сервера");}
    public PoliciesSettings settings(){return settings;}public CityPolicies state(UUID town){return repository.get(town);}
    public void start(){primary();refresh();schedule();}private void schedule(){task=Bukkit.getScheduler().runTaskTimer(plugin,this::refresh,settings.interval()*20L,settings.interval()*20L);}
    public void reload(PoliciesSettings next){primary();settings=next;offers.clear();if(task!=null)task.cancel();refresh();schedule();}
    public void stop(){if(task!=null)task.cancel();directory=new Directory(Map.of(),Map.of(),Map.of());offers.clear();}
    public void refresh(){primary();try{Map<UUID,String> towns=new HashMap<>();Map<UUID,UUID> nations=new HashMap<>(),residents=new HashMap<>();for(Town town:List.copyOf(TownyAPI.getInstance().getTowns())){towns.put(town.getUUID(),town.getName());if(town.getNationOrNull()!=null)nations.put(town.getUUID(),town.getNationOrNull().getUUID());town.getResidents().forEach(r->residents.put(r.getUUID(),town.getUUID()));}directory=new Directory(towns,nations,residents);offers.values().removeIf(o->o.expires()<System.currentTimeMillis()||!towns.containsKey(o.town()));}catch(Exception e){directory=new Directory(Map.of(),Map.of(),Map.of());plugin.getLogger().warning("Политики ожидают данные Towny: "+e.getMessage());}}
    public Town town(Player p){var r=TownyAPI.getInstance().getResident(p);return r==null?null:r.getTownOrNull();}
    public boolean manager(Player p,Town town){var r=TownyAPI.getInstance().getResident(p);return SelectionPolicy.manager(r!=null&&town.equals(r.getTownOrNull()),r!=null&&town.isMayor(r),p.hasPermission("neverlandtownypolicies.manage"));}
    private boolean taxConfigured(UUID town){try{var p=Bukkit.getPluginManager().getPlugin("NeverLandTownyTaxes");if(p==null||!p.isEnabled())return false;Class<?> type=Class.forName("ru.neverland.townytaxes.api.NeverLandTownyTaxesApi",true,p.getClass().getClassLoader());Object api=Bukkit.getServicesManager().load(type);return api!=null&&Boolean.TRUE.equals(type.getMethod("municipalTaxConfigured",UUID.class).invoke(api,town));}catch(Exception|LinkageError e){return false;}}
    public String unavailable(UUID town,PolicyGroup group,PolicyOption option){if(option==null)return "Сохранённый режим отсутствует в настройках";if(option.id().equals(group.standard()))return "";if(!group.enabled())return "Политика отключена в настройках";for(String name:option.requires()){var dependency=Bukkit.getPluginManager().getPlugin(name);if(dependency==null||!dependency.isEnabled())return "Не готов модуль "+name;String minimum=DependencyVersions.MINIMUM.get(name);if(!DependencyVersions.atLeast(dependency.getDescription().getVersion(),minimum))return "Нужен "+name+" версии "+minimum+" или новее";}if(group.id().equals("taxes")&&!taxConfigured(town))return "Нет действующего городского сбора Taxes в пользу своей казны";return "";}
    private PolicyOption selected(UUID town,PolicyGroup g){return g.options().get(repository.get(town).mode(g));}
    private PolicyOption active(UUID town,PolicyGroup g){if(town==null||!directory.towns().containsKey(town)||!g.enabled())return null;var option=selected(town,g);return unavailable(town,g,option).isEmpty()?option:null;}
    public void choose(Player p,UUID id,String group,String mode,long revision,PoliciesSettings quoted,boolean admin)throws Exception{primary();var next=proposed(p,id,group,mode,revision,quoted,admin);repository.put(id,next);offers.remove(p.getUniqueId());refresh();}
    private CityPolicies proposed(Player p,UUID id,String group,String mode,long revision,PoliciesSettings quoted,boolean admin){primary();var town=TownyAPI.getInstance().getTown(id);if(town==null)throw new IllegalArgumentException("Город не найден");if(admin?!p.hasPermission("neverlandtownypolicies.admin"):!p.hasPermission("neverlandtownypolicies.use")||!manager(p,town))throw new IllegalArgumentException("Выбор доступен мэру или уполномоченному своего города");if(quoted!=settings)throw new IllegalArgumentException("Настройки изменились. Откройте меню заново.");var g=settings.groups().get(group);if(g==null||!g.options().containsKey(mode))throw new IllegalArgumentException("Неизвестная политика или режим");String reason=unavailable(id,g,g.options().get(mode));if(!reason.isEmpty()&&!mode.equals(g.standard()))throw new IllegalArgumentException(reason);return SelectionPolicy.choose(repository.get(id),g,mode,System.currentTimeMillis(),settings.cooldownMillis(),revision,p.getUniqueId(),p.getName(),admin,reason.isEmpty());}
    public void offer(Player p,UUID town,String group,String mode,boolean admin){long revision=repository.get(town).revision();proposed(p,town,group,mode,revision,settings,admin);offers.put(p.getUniqueId(),new Offer(town,group,mode,revision,settings,System.currentTimeMillis()+60000,admin));}
    public Offer offer(Player p){var o=offers.get(p.getUniqueId());if(o==null||o.expires()<System.currentTimeMillis()){offers.remove(p.getUniqueId());return null;}return o;}
    public UUID confirm(Player p)throws Exception{primary();var o=offer(p);if(o==null)throw new IllegalArgumentException("Нет действующего выбора. Повторите choose.");offers.remove(p.getUniqueId());choose(p,o.town(),o.group(),o.mode(),o.revision(),o.settings(),o.admin());return o.town();}
    @Override public double effect(UUID town,String key){double value=0;for(var g:settings.groups().values()){var p=active(town,g);if(p!=null)value+=p.effect(key);}return PolicyEffects.bound(value,key.equals("happiness")?-40:-.5,key.equals("happiness")?40:.5,0);}
    private double multiplier(UUID town,String project,boolean cost){BigDecimal value=BigDecimal.ONE;for(var g:settings.groups().values()){var p=active(town,g);if(p!=null&&p.targets(project,cost))value=value.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(p.effect(cost?"upkeep":"production"))));}return PolicyEffects.bound(value.doubleValue(),cost?1:.25,cost?3:2,1);}
    @Override public double productionMultiplier(UUID town,String project){return multiplier(town,project,false);}
    @Override public double upkeepMultiplier(UUID town,String project){return multiplier(town,project,true);}
    @Override public double taxMultiplier(UUID town){return 1+effect(town,"tax");}
    @Override public boolean tariffManaged(UUID town){var p=active(town,settings.groups().get("tariffs"));return p!=null&&p.effects().containsKey("tariff");}
    @Override public double tariff(UUID town,double manual,double maximum){var p=active(town,settings.groups().get("tariffs"));return PolicyEffects.bound(p!=null&&p.effects().containsKey("tariff")?p.effect("tariff"):manual,0,PolicyEffects.bound(maximum,0,100,20),manual);}
    @Override public boolean importsAllowed(UUID buyer,UUID seller,boolean sameNation){var d=directory;if(buyer==null||seller==null||!d.towns().containsKey(buyer)||!d.towns().containsKey(seller))return false;var g=settings.groups().get("imports");if(!g.enabled())return PolicyEffects.imports("open",buyer,seller,sameNation);var p=selected(buyer,g);if(p==null)return false;if(!p.id().equals(g.standard())&&!unavailable(buyer,g,p).isEmpty())return false;return PolicyEffects.imports(p.id(),buyer,seller,sameNation);}
    @Override public Optional<PoliciesSnapshot> policies(UUID town){var d=directory;if(town==null||!d.towns().containsKey(town))return Optional.empty();var state=repository.get(town);Map<String,String> status=new HashMap<>();for(var g:settings.groups().values()){String reason=unavailable(town,g,g.options().get(state.mode(g)));status.put(g.id(),reason.isEmpty()?"Действует":reason);}return Optional.of(new PoliciesSnapshot(town,d.towns().get(town),state,status));}
    @Override public Optional<PoliciesSnapshot> residentPolicies(UUID resident){return policies(directory.residents().get(resident));}
}
