package ru.neverland.core;

import java.util.UUID;

/** Optional integration. An installed but unhealthy provider never grants privileges or charges taxes. */
public final class CitizensAccess {
    private static final String PLUGIN = "NeverLandTownyCitizens";
    private static final String API = "ru.neverland.townycitizens.api.TownyCitizensApi";
    private CitizensAccess() { }
    public static boolean available() {
        var c = ApiServices.connect(PLUGIN, API, 1, "healthy", "allows", "taxMultiplier");
        if (c.state() == ApiServices.State.NOT_INSTALLED) return true;
        if (!c.ready()) return false;
        try { return Boolean.TRUE.equals(c.invoke("healthy", new Class<?>[0])); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ex) { return false; }
    }
    public static boolean allows(UUID town, UUID player, String right) {
        var c = ApiServices.connect(PLUGIN, API, 1, "allows");
        if (c.state() == ApiServices.State.NOT_INSTALLED) return true;
        if (!c.ready()) return false;
        try { return Boolean.TRUE.equals(c.invoke("allows", new Class<?>[]{UUID.class, UUID.class, String.class}, town, player, right)); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ex) { return false; }
    }
    public static double tax(UUID town, UUID player) {
        var c = ApiServices.connect(PLUGIN, API, 1, "taxMultiplier");
        if (c.state() == ApiServices.State.NOT_INSTALLED) return 1;
        if (!c.ready()) return Double.NaN;
        try {
            Object value = c.invoke("taxMultiplier", new Class<?>[]{UUID.class, UUID.class}, town, player);
            return value instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() >= 0
                    && n.doubleValue() <= 10 ? n.doubleValue() : Double.NaN;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) { return Double.NaN; }
    }
}
