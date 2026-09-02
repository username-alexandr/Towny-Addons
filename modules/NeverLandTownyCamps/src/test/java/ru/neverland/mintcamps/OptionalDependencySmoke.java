package ru.neverland.mintcamps;

public final class OptionalDependencySmoke {
    public static void main(String[] args) throws Exception {
        Class<?> main = Class.forName("ru.neverland.mintcamps.MintTownyCamps", false,
                OptionalDependencySmoke.class.getClassLoader());
        if (!"ru.neverland.mintcamps.MintTownyCamps".equals(main.getName())) {
            throw new AssertionError("Главный класс не загружен");
        }
        System.out.println("main-class-without-optional-plugins=OK");
    }
}
