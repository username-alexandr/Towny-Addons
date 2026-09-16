package ru.neverland.townyseasons;

import java.io.IOException;
import java.util.*;
import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.ApiServices;
import ru.neverland.townyseasons.api.TownySeasonsApi;

public final class SeasonService implements TownySeasonsApi {
    private final JavaPlugin plugin;
    private final SeasonRepository repository;
    private final RealisticSeasonsAccess external=new RealisticSeasonsAccess();
    private final Map<UUID,String> observed=new HashMap<>();
    private final Map<UUID,String> failures=new HashMap<>();
    private SeasonSettings settings;
    public SeasonService(JavaPlugin plugin,SeasonRepository repository,SeasonSettings settings) {
        this.plugin=plugin;this.repository=repository;this.settings=settings;
    }
    public SeasonSettings settings() { return settings; }
    public void reload(SeasonSettings next) { ApiServices.primaryThread();settings=next;observed.clear();failures.clear(); }
    public void override(UUID world,Season season) throws IOException {
        ApiServices.primaryThread();World w=world(world);
        if(!enabled(w))throw new IllegalArgumentException("Сезоны отключены в этом мире");
        repository.set(world,season);pulse();
    }
    @Override public boolean healthy() { ApiServices.primaryThread();return repository.healthy(); }
    private World world(UUID id) {
        if(id==null)throw new IllegalArgumentException("Мир не задан");
        World w=Bukkit.getWorld(id);if(w==null)throw new IllegalArgumentException("Мир не загружен");return w;
    }
    private boolean enabled(World w) { return w.getEnvironment()==World.Environment.NORMAL&&(settings.worlds().isEmpty()||settings.worlds().contains(w.getName())); }
    public UUID townWorld(UUID id) {
        ApiServices.primaryThread();var town=id==null?null:TownyAPI.getInstance().getTown(id);
        if(town==null)throw new IllegalArgumentException("Город не найден");
        var home=town.getHomeBlockOrNull();
        if(home==null)throw new IllegalStateException("У города нет домашнего участка");
        var w=Bukkit.getWorld(home.getWorld().getName());
        if(w==null)throw new IllegalStateException("Мир города не загружен");return w.getUID();
    }
    @Override public Map<String,Object> calendar(UUID worldId) {
        ApiServices.primaryThread();World w=world(worldId);
        if(!healthy())throw new IllegalStateException("Ошибка хранения календаря");
        boolean active=enabled(w);Season season=null;long year=0,remaining=0;int day=0;
        String source=active?settings.mode().name():"DISABLED";
        if(active) {
            season=repository.overrides().get(worldId);
            if(season!=null)source="MANUAL";
            else if(settings.mode()==SeasonSettings.Mode.REALISTIC_SEASONS)season=external.read(w);
            else {
                boolean real=settings.mode()==SeasonSettings.Mode.CUSTOM;
                long elapsed=real?Math.max(0,System.currentTimeMillis()-repository.epoch()):Math.max(0,w.getGameTime());
                var date=SeasonClock.at(elapsed,real?settings.dayMillis():24000,settings.days(),settings.first());
                season=date.season();year=date.year();day=date.day();remaining=date.remaining();
            }
        }
        var out=new LinkedHashMap<String,Object>();out.put("world",worldId);out.put("worldName",w.getName());out.put("enabled",active);
        out.put("season",season==null?"NONE":season.name());out.put("title",season==null?"Без сезонов":season.title);
        out.put("source",source);out.put("year",year);out.put("day",day);out.put("daysPerSeason",settings.days());
        out.put("remaining",remaining);out.put("remainingUnit",source.equals("MINECRAFT")?"TICKS":source.equals("CUSTOM")?"MILLISECONDS":"UNKNOWN");
        var effect=season==null?new SeasonSettings.Effects(Map.of(),Map.of()):settings.effects().get(season);
        out.put("production",effect.production());out.put("eventWeights",effect.events());return Map.copyOf(out);
    }
    @Override public Map<String,Object> townCalendar(UUID town) { return calendar(townWorld(town)); }
    @Override public double productionMultiplier(UUID town,String building) {
        Objects.requireNonNull(building);return coefficient(townCalendar(town),"production",building.toLowerCase(Locale.ROOT));
    }
    @Override public double eventWeight(UUID town,String eventMode) {
        Objects.requireNonNull(eventMode);return coefficient(townCalendar(town),"eventWeights",eventMode.toLowerCase(Locale.ROOT));
    }
    private double coefficient(Map<String,Object> view,String key,String id) { Object value=((Map<?,?>)view.get(key)).get(id);return value==null?1:((Number)value).doubleValue(); }
    public void pulse() {
        for(World w:Bukkit.getWorlds())try {
            var view=calendar(w.getUID());String current=(String)view.get("season");String old=observed.put(w.getUID(),current);failures.remove(w.getUID());
            if(old!=null&&!old.equals(current)&&settings.announce())for(var player:w.getPlayers())
                player.sendMessage(ru.neverland.core.MenuStyle.decode("&6[Сезоны] &f"+view.get("title")+". Производство и риски: &e/t seasons"));
        } catch(RuntimeException|LinkageError e) {
            String error=e.getMessage();if(!Objects.equals(failures.put(w.getUID(),error),error))plugin.getLogger().warning(w.getName()+": "+error);
        }
    }
    public String providerStatus() { return external.status(); }
}
