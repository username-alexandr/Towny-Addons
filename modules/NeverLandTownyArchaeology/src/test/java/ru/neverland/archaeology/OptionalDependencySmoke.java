package ru.neverland.archaeology;

public final class OptionalDependencySmoke {
    public static void main(String[] args) throws Exception { Class.forName("ru.neverland.archaeology.TownyArchaeology", false, OptionalDependencySmoke.class.getClassLoader()); Class.forName("ru.neverland.archaeology.integration.PlaceholderHook", false, OptionalDependencySmoke.class.getClassLoader()); }
}
