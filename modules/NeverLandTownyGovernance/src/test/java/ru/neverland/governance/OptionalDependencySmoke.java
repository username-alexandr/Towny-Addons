package ru.neverland.governance;

public final class OptionalDependencySmoke {
    public static void main(String[] args) throws Exception {
        Class<?> main = Class.forName("ru.neverland.governance.TownyGovernance", false, OptionalDependencySmoke.class.getClassLoader());
        if (!main.getName().equals("ru.neverland.governance.TownyGovernance")) throw new AssertionError("Главный класс не загружен");
        System.out.println("Main class without optional plugins: OK");
    }
}
