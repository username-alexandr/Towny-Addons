package ru.neverland.mintexpeditions;
public final class LinkageTest {
    public static void main(String[] args) throws Exception {
        Class.forName("ru.neverland.mintexpeditions.MintTownyExpeditions", false, LinkageTest.class.getClassLoader());
        Class.forName("ru.neverland.mintexpeditions.integration.CampFacade", false, LinkageTest.class.getClassLoader());
        System.out.println("Linkage OK without PlaceholderAPI and ItemsAdder");
    }
}
