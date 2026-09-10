package ru.neverland.townydistricts.model;
import java.util.*;
public enum DistrictType {
    RESIDENTIAL("Жилой","RED_BED"), INDUSTRIAL("Промышленный","BLAST_FURNACE"),
    COMMERCIAL("Торговый","EMERALD"), MILITARY("Военный","IRON_SWORD"),
    PORT("Портовый","OAK_BOAT"), AGRICULTURAL("Сельскохозяйственный","WHEAT"),
    ADMINISTRATIVE("Административный","BELL");
    public final String title,icon;
    DistrictType(String title,String icon){this.title=title;this.icon=icon;}
    public String id(){return name().toLowerCase(Locale.ROOT);}
    public static DistrictType parse(String raw){
        for(var type:values()) if(type.id().equalsIgnoreCase(raw)||type.title.equalsIgnoreCase(raw))return type;
        throw new IllegalArgumentException("Неизвестный тип района");
    }
}
