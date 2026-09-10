package ru.neverland.minttrade.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.minttrade.integration.BuildBridge;
import ru.neverland.minttrade.integration.CampsBridge;
import ru.neverland.minttrade.integration.TownyHook;
import ru.neverland.minttrade.model.RoutePoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RoutePlanner {
    public record RoutePlan(List<RoutePoint> points, List<UUID> transitTowns, double distance,
                            long durationMillis, double delayChance) {}
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final BuildBridge builds;
    private final CampsBridge camps;
    public RoutePlanner(JavaPlugin plugin, TownyHook towny, BuildBridge builds, CampsBridge camps) {
        this.plugin = plugin; this.towny = towny; this.builds = builds; this.camps = camps;
    }
    public RoutePlan plan(Town seller, Town buyer) {
        Location from = seller == null ? null : seller.getSpawnOrNull(), to = buyer == null ? null : buyer.getSpawnOrNull();
        if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return null;
        RoutePoint start = townPoint(from, seller), end = townPoint(to, buyer);
        List<Candidate> candidates = new ArrayList<>();
        double corridor = plugin.getConfig().getDouble("camps.route-corridor", 160);
        Set<UUID> parties = Set.of(seller.getUUID(), buyer.getUUID());
        for (CampsBridge.CampStop stop : camps.activeStops()) {
            RoutePoint point = stop.point();
            if (!parties.contains(stop.ownerTownId()) || !point.worldId().equals(start.worldId())) continue;
            Projection projection = projection(start, end, point);
            if (projection.t > 0.05 && projection.t < 0.95 && projection.distance <= corridor)
                candidates.add(new Candidate(point, projection.t));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::t));
        int maxStops = Math.max(0, plugin.getConfig().getInt("camps.maximum-stops", 3));
        List<RoutePoint> points = new ArrayList<>(); points.add(start);
        candidates.stream().limit(maxStops).forEach(value -> points.add(value.point())); points.add(end);
        double distance = 0; for (int i = 1; i < points.size(); i++) distance += points.get(i - 1).distance(points.get(i));
        int sellerMarket = builds.marketLevel(seller.getUUID()), buyerMarket = builds.marketLevel(buyer.getUUID());
        int colossi = builds.projectLevel(seller.getUUID(), "rhodes_colossus")
                + builds.projectLevel(buyer.getUUID(), "rhodes_colossus");
        int palaces = builds.projectLevel(seller.getUUID(), "crystal_palace")
                + builds.projectLevel(buyer.getUUID(), "crystal_palace");
        double bonus = (sellerMarket + buyerMarket) * plugin.getConfig().getDouble("routes.market-speed-bonus-per-level", 0.03);
        bonus += colossi * plugin.getConfig().getDouble("routes.colossus-speed-bonus", 0.10);
        bonus += palaces * plugin.getConfig().getDouble("routes.crystal-palace-speed-bonus", 0.06);
        bonus += points.stream().filter(value -> value.kind() == RoutePoint.Kind.CAMP).mapToInt(RoutePoint::level).sum()
                * plugin.getConfig().getDouble("camps.speed-bonus-per-level", 0.04);
        bonus = Math.min(plugin.getConfig().getDouble("routes.maximum-speed-bonus", 0.45), Math.max(0, bonus));
        double researchSpeed=Math.max(ru.neverland.integration.ResearchBonuses.bonus(seller.getUUID(),"fast_caravans"),ru.neverland.integration.ResearchBonuses.bonus(buyer.getUUID(),"fast_caravans"));
        double navigation=Math.max(ru.neverland.integration.ResearchBonuses.bonus(seller.getUUID(),"navigation"),ru.neverland.integration.ResearchBonuses.bonus(buyer.getUUID(),"navigation"));
        double specializationSpeed=Math.max(ru.neverland.integration.SpecializationAccess.bonus(seller.getUUID(),"trade_speed"),ru.neverland.integration.SpecializationAccess.bonus(buyer.getUUID(),"trade_speed"));
        double specializationDelay=Math.max(ru.neverland.integration.SpecializationAccess.bonus(seller.getUUID(),"trade_delay"),ru.neverland.integration.SpecializationAccess.bonus(buyer.getUUID(),"trade_delay"));
        double minutes = distance / Math.max(1, plugin.getConfig().getDouble("routes.blocks-per-minute", 160));
        minutes = ru.neverland.integration.ResearchEffects.minutes(minutes*(1-bonus)*(1-specializationSpeed),plugin.getConfig().getDouble("routes.minimum-minutes",3),plugin.getConfig().getDouble("routes.maximum-minutes",120),researchSpeed);
        double chance = plugin.getConfig().getDouble("routes.delay.base-chance", 0.20)
                - (sellerMarket + buyerMarket) * plugin.getConfig().getDouble("routes.delay.market-reduction-per-level", 0.015)
                - colossi * plugin.getConfig().getDouble("routes.delay.colossus-reduction", 0.05)
                - palaces * plugin.getConfig().getDouble("routes.delay.crystal-palace-reduction", 0.03)
                - points.stream().filter(value -> value.kind() == RoutePoint.Kind.CAMP).mapToInt(RoutePoint::level).sum()
                * plugin.getConfig().getDouble("routes.delay.camp-reduction-per-level", 0.025);
        chance = ru.neverland.integration.ResearchEffects.delay(chance-specializationDelay,plugin.getConfig().getDouble("routes.delay.minimum-chance",0.02),navigation);
        return new RoutePlan(List.copyOf(points), transit(points, seller.getUUID(), buyer.getUUID()), distance,
                Math.max(1000, Math.round(minutes * 60_000)), chance);
    }
    private List<UUID> transit(List<RoutePoint> points, UUID seller, UUID buyer) {
        Set<UUID> result = new LinkedHashSet<>();
        double spacing = Math.max(8, plugin.getConfig().getDouble("tariffs.route-sample-spacing", 32));
        for (int index = 1; index < points.size(); index++) {
            RoutePoint a = points.get(index - 1), b = points.get(index); World world = Bukkit.getWorld(a.worldId());
            if (world == null) continue; int steps = Math.max(1, (int) Math.ceil(a.distance(b) / spacing));
            for (int step = 1; step < steps; step++) {
                double t = (double) step / steps;
                Town town = towny.townAt(new Location(world, a.x() + (b.x() - a.x()) * t, 64, a.z() + (b.z() - a.z()) * t));
                if (town != null && !town.getUUID().equals(seller) && !town.getUUID().equals(buyer)) result.add(town.getUUID());
            }
        }
        return List.copyOf(result);
    }
    private RoutePoint townPoint(Location location, Town town) {
        return new RoutePoint(location.getWorld().getUID(), location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                RoutePoint.Kind.TOWN, town.getName(), 0, null);
    }
    private Projection projection(RoutePoint a, RoutePoint b, RoutePoint p) {
        double dx = b.x() - a.x(), dz = b.z() - a.z(), length = dx * dx + dz * dz;
        double t = length <= 0 ? 0 : ((p.x() - a.x()) * dx + (p.z() - a.z()) * dz) / length;
        double x = a.x() + dx * t, z = a.z() + dz * t;
        return new Projection(t, Math.hypot(p.x() - x, p.z() - z));
    }
    private record Candidate(RoutePoint point, double t) {}
    private record Projection(double t, double distance) {}
}
