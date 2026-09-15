package ru.neverland.townysieges;

import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.core.ApiServices;
import ru.neverland.townysieges.api.TownySiegesApi;
import static ru.neverland.townysieges.SiegeRules.*;

public final class SiegeService implements TownySiegesApi {
    public static final NamespacedKey MOAT_SPEED=new NamespacedKey("neverlandtownysiegesplus","moat_speed");
    private final NeverLandTownySiegesPlus plugin;
    private final SiegeWarAccess war=new SiegeWarAccess();
    private final BuildsAccess builds=new BuildsAccess();
    private SiegeSettings settings;
    private record Cached(long at,Map<Fort,BuildsAccess.Building> buildings) { }
    private final Map<UUID,Cached> geometry=new HashMap<>();
    private final Map<UUID,Long> messages=new HashMap<>();
    private long lastFailure;
    private String failure="";
    public SiegeService(NeverLandTownySiegesPlus plugin,SiegeSettings settings) { this.plugin=plugin; this.settings=settings; }
    public SiegeSettings settings() { return settings; }
    public SiegeWarAccess war() { return war; }
    public void reload(SiegeSettings next) { ApiServices.primaryThread(); settings=next; geometry.clear(); pulse(); }
    public static Point point(Location l) { return new Point(l.getWorld().getUID(),l.getX(),l.getY(),l.getZ()); }
    public static Town town(UUID player) { var r=TownyAPI.getInstance().getResident(player); return r==null?null:r.getTownOrNull(); }
    public static boolean exempt(Player p) { return p.isDead() || p.getGameMode()==GameMode.SPECTATOR || p.getGameMode()==GameMode.CREATIVE || p.hasPermission("neverlandtownysiegesplus.bypass"); }
    private Map<Fort,BuildsAccess.Building> geometry(UUID town)throws Exception {
        long now=System.currentTimeMillis();var old=geometry.get(town);
        if(old!=null && now-old.at()<2000) return old.buildings();
        var next=builds.buildings(town,settings);
        if(geometry.size()>2048) geometry.clear();
        geometry.put(town,new Cached(now,next)); return next;
    }
    public record Assessment(UUID town,Map<Fort,Integer> levels,Map<Fort,Zone> zones,boolean uncertain) {
        public Assessment { levels=Map.copyOf(levels); zones=Map.copyOf(zones); }
        public static Assessment none() { return new Assessment(null,Map.of(),Map.of(),false); }
        public int near(Fort fort,Point p) { var zone=zones.get(fort); return zone!=null&&zone.contains(p)?levels.getOrDefault(fort,0):0; }
        public boolean enters(Fort fort,Point from,Point to) { return levels.getOrDefault(fort,0)>0 && zones.containsKey(fort) && zones.get(fort).enters(from,to); }
        public boolean wall(Point at) { var z=zones.get(Fort.WALL); return levels.getOrDefault(Fort.WALL,0)>0 && z!=null && z.world().equals(at.world()); }
    }
    /** Resolve affiliation first. A broken building provider never turns an unrelated visitor into an attacker. */
    public Assessment assess(Player player,Location location) {
        ApiServices.primaryThread();
        if(location==null || location.getWorld()==null || exempt(player)) return Assessment.none();
        Town target=TownyAPI.getInstance().getTown(location);
        if(target==null) return Assessment.none();
        try {
            if(!war.connect()) return Assessment.none();
            var context=war.context(target,player);
            if(!context.active() || settings.battleOnly()&&!context.battle() || !context.side().equals("ATTACKERS")) return Assessment.none();
        } catch(Exception|LinkageError e) { failed(e); return Assessment.none(); }
        try {
            Town own=town(player.getUniqueId());
            // Diplomacy owns treaty PvP vetoes. Allies are never burdened with attacker movement restrictions.
            if(builds.protectedByTreaty(own==null?null:own.getUUID(),target.getUUID())) return Assessment.none();
            var found=geometry(target.getUUID());var levels=new EnumMap<Fort,Integer>(Fort.class);var zones=new EnumMap<Fort,Zone>(Fort.class);
            for(var entry:found.entrySet()) { levels.put(entry.getKey(),builds.level(target.getUUID(),entry.getKey(),entry.getValue())); zones.put(entry.getKey(),entry.getValue().zone()); }
            failure="";return new Assessment(target.getUUID(),levels,zones,false);
        } catch(Exception|LinkageError e) { failed(e);return new Assessment(target.getUUID(),Map.of(),Map.of(),true); }
    }
    public void failed(Throwable error) {
        failure=error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();
        if(System.currentTimeMillis()-lastFailure>60000) { lastFailure=System.currentTimeMillis(); plugin.getLogger().log(java.util.logging.Level.WARNING,"Осадная интеграция временно недоступна",error); }
    }
    public void tell(Player p,String text) { long now=System.currentTimeMillis();if(now-messages.getOrDefault(p.getUniqueId(),0L)<3000)return;messages.put(p.getUniqueId(),now);p.sendMessage(ru.neverland.core.MenuStyle.decode("&6[Осада] &f"+text)); }
    public double defense(Player attacker,Player defender,Location at) {
        var a=assess(attacker,at);if(a.town()==null)return 0;if(a.uncertain())return -1;
        try {
            var target=TownyAPI.getInstance().getTown(a.town());var own=town(defender.getUniqueId());
            if(own!=target && !war.context(target,defender).side().equals("DEFENDERS"))return 0;
            Point p=point(at);int wall=a.near(Fort.WALL,p),tower=Math.max(a.near(Fort.TOWER,p),a.near(Fort.KEEP,p));
            if(wall==0&&tower==0)return 0;
            return SiegeRules.reduction(wall,tower,builds.army(a.town()),settings.wallReduction(),settings.towerReduction(),settings.defenseCap());
        } catch(Exception|LinkageError e) { failed(e); return -1; }
    }
    public void slow(Player p,double amount) {
        var attribute=p.getAttribute(Attribute.MOVEMENT_SPEED);if(attribute==null)return;
        var old=attribute.getModifier(MOAT_SPEED);
        if(old!=null && (amount<=0 || Math.abs(old.getAmount()+amount)>1e-9)) attribute.removeModifier(old);
        if(amount>0 && attribute.getModifier(MOAT_SPEED)==null) attribute.addTransientModifier(new AttributeModifier(MOAT_SPEED,-amount,AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }
    public void pulse() {
        ApiServices.primaryThread();war.connect();
        for(Player p:Bukkit.getOnlinePlayers()) try {
            var at=p.getLocation();var a=assess(p,at);Point pos=point(at);
            double amount=SiegeRules.moat(a.near(Fort.MOAT,pos),settings.moatRate(),settings.moatCap());slow(p,amount);
            if(amount>0) { p.setSprinting(false);p.setSwimming(false); }
            if(p.isGliding() && (a.near(Fort.TOWER,pos)>0 || a.near(Fort.KEEP,pos)>0)) { p.setGliding(false);tell(p,"Дозорная башня не даёт использовать элитры в этой зоне."); }
        } catch(Exception|LinkageError e) { failed(e);slow(p,0); }
        messages.entrySet().removeIf(e->System.currentTimeMillis()-e.getValue()>60000);
        geometry.entrySet().removeIf(e->System.currentTimeMillis()-e.getValue().at()>60000);
    }
    public void leave(Player p) { slow(p,0);messages.remove(p.getUniqueId()); }
    public void stop() { for(Player p:Bukkit.getOnlinePlayers())leave(p);geometry.clear(); }
    @Override public boolean healthy() { ApiServices.primaryThread();return war.connect() && war.state()==SiegeWarAccess.State.READY && failure.isEmpty(); }
    @Override public Map<String,String> warStatus() { ApiServices.primaryThread();war.connect();return Map.of("state",war.state().name(),"detail",war.detail(),"dependencyError",failure,"testedVersion","3.6.2"); }
    @Override public Collection<Map<String,Object>> defenses(UUID town) {
        ApiServices.primaryThread();if(town==null||TownyAPI.getInstance().getTown(town)==null)return List.of();
        try {
            var found=geometry(town);var result=new ArrayList<Map<String,Object>>();
            for(Fort f:Fort.values()) { var b=found.get(f);int level=builds.level(town,f,b);var row=new LinkedHashMap<String,Object>();row.put("project",f.project);row.put("title",f.title);row.put("completed",b==null?0:b.completed());row.put("activeLevel",level);row.put("located",b!=null);if(b!=null){var z=b.zone();row.put("world",z.world());row.put("minX",z.x1());row.put("minY",z.y1());row.put("minZ",z.z1());row.put("maxX",z.x2());row.put("maxY",z.y2());row.put("maxZ",z.z2());}result.add(Map.copyOf(row)); }
            return List.copyOf(result);
        } catch(Exception|LinkageError e) { failed(e);throw new IllegalStateException("Не удалось прочитать оборону города",e); }
    }
    @Override public Map<String,Object> restrictions(UUID player,UUID world,double x,double y,double z) {
        ApiServices.primaryThread();if(player==null||world==null)return Map.of();new Point(world,x,y,z);
        var p=Bukkit.getPlayer(player);var w=Bukkit.getWorld(world);if(p==null||w==null)return Map.of();
        var at=new Location(w,x,y,z);var a=assess(p,at);if(a.town()==null)return Map.of();Point pos=point(at);
        return Map.of("town",a.town(),"uncertain",a.uncertain(),"wall",a.wall(pos),"gate",a.near(Fort.GATE,pos)>0,"moatSlow",SiegeRules.moat(a.near(Fort.MOAT,pos),settings.moatRate(),settings.moatCap()),"tower",a.near(Fort.TOWER,pos)>0||a.near(Fort.KEEP,pos)>0,"port",a.near(Fort.PORT,pos)>0);
    }
}
