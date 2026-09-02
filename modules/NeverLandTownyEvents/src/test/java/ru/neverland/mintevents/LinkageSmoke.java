package ru.neverland.mintevents;

public final class LinkageSmoke {
    public static void main(String[] args) throws Exception {
        Class.forName("ru.neverland.mintevents.MintTownyEvents", false, LinkageSmoke.class.getClassLoader());
        Class.forName("ru.neverland.mintevents.service.EventService", false, LinkageSmoke.class.getClassLoader());
        System.out.println("LinkageSmoke OK without PlaceholderAPI");
    }
}
