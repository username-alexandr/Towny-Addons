package ru.neverland.core;

import java.util.UUID;

/** Disabling ecology explicitly removes its penalties. A broken live API suspends its consumer. */
public final class EnvironmentAccess {
    private EnvironmentAccess() { }
    public static double happiness(UUID town) throws ReflectiveOperationException {
        var c = ApiServices.connect("NeverLandTownyEnvironment", "ru.neverland.townyenvironment.api.TownyEnvironmentApi", 1, "happiness");
        if (neutral(c)) return 0;
        return number(c.invoke("happiness", new Class<?>[]{UUID.class}, town), -30, 0);
    }
    public static double agriculture(UUID town, String building) throws ReflectiveOperationException {
        var c = ApiServices.connect("NeverLandTownyEnvironment", "ru.neverland.townyenvironment.api.TownyEnvironmentApi", 1, "agricultureMultiplier");
        if (neutral(c)) return 1;
        return number(c.invoke("agricultureMultiplier", new Class<?>[]{UUID.class, String.class}, town, building), .2, 1);
    }
    private static boolean neutral(ApiServices.Connection c) { return c.state() == ApiServices.State.NOT_INSTALLED || c.state() == ApiServices.State.DISABLED; }
    private static double number(Object value, double min, double max) {
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < min - 1e-9 || n.doubleValue() > max)
            throw new IllegalStateException("Неверный экологический коэффициент");
        return Math.max(min, ((Number)value).doubleValue());
    }
}
