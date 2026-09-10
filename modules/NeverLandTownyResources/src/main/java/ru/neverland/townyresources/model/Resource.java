package ru.neverland.townyresources.model;
import java.util.Locale;
public enum Resource {
    WOOD("Древесина", "OAK_LOG"), STONE("Камень", "STONE"), METAL("Металл", "IRON_INGOT"),
    FOOD("Продовольствие", "BREAD"), WATER("Вода", "WATER_BUCKET"), MATERIALS("Стройматериалы", "BRICKS"),
    KNOWLEDGE("Знания", "BOOK"), INFLUENCE("Влияние", "NETHER_STAR");
    public final String title, icon;
    Resource(String title, String icon) { this.title=title; this.icon=icon; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public static Resource parse(String text) {
        for (var r:values()) if(r.id().equalsIgnoreCase(text)||r.title.equalsIgnoreCase(text)) return r;
        throw new IllegalArgumentException("Неизвестный стратегический ресурс: "+text);
    }
}
