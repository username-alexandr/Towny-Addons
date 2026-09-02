package ru.neverland.minttrade;

import ru.neverland.minttrade.model.RoutePoint;
import ru.neverland.minttrade.util.TradeMath;

import java.util.UUID;

public final class TradeMathSmoke {
    public static void main(String[] args) {
        UUID world = UUID.randomUUID();
        RoutePoint a = new RoutePoint(world, "world", 0, 64, 0, RoutePoint.Kind.TOWN, "A", 0, null);
        RoutePoint camp = new RoutePoint(world, "world", 30, 64, 40, RoutePoint.Kind.CAMP, "Camp", 2, UUID.randomUUID());
        RoutePoint b = new RoutePoint(world, "world", 60, 64, 80, RoutePoint.Kind.TOWN, "B", 0, null);
        if (Math.round(a.distance(camp) + camp.distance(b)) != 100) throw new AssertionError("route distance");
        long now = 1_000_000L;
        if (Math.abs(TradeMath.progress(now, now + 100_000, now + 25_000) - 0.25) > 0.0001) throw new AssertionError("progress");
        if (TradeMath.tariff(1234.56, 7.5) != 92.59) throw new AssertionError("tariff rounding");
        if (TradeMath.progress(now, now + 100_000, now - 1) != 0) throw new AssertionError("lower clamp");
        if (TradeMath.progress(now, now + 100_000, now + 200_000) != 1) throw new AssertionError("upper clamp");
        System.out.println("TradeMathSmoke OK");
    }
}
