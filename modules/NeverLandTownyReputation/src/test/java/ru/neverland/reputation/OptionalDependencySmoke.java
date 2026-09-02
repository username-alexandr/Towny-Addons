package ru.neverland.reputation;

public final class OptionalDependencySmoke {
    public static void main(String[] args) throws Exception {
        Class.forName("ru.neverland.reputation.TownyReputation", false, OptionalDependencySmoke.class.getClassLoader());
        Class.forName("ru.neverland.reputation.integration.PlaceholderHook", false, OptionalDependencySmoke.class.getClassLoader());
    }
}
