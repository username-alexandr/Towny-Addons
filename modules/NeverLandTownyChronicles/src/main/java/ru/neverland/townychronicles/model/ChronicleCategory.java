package ru.neverland.townychronicles.model;

import org.bukkit.Material;
import java.util.Locale;

public enum ChronicleCategory {
    FOUNDING("Основание",Material.BELL,"&#FFD45A"),MAYOR("Мэры",Material.PLAYER_HEAD,"&#65B8FF"),NATION("Нации",Material.WHITE_BANNER,"&#C58CFF"),
    WAR("Войны",Material.IRON_SWORD,"&#FF6666"),WONDER("Чудеса Света",Material.NETHER_STAR,"&#FFDD55"),ACHIEVEMENT("Достижения",Material.TOTEM_OF_UNDYING,"&#55FF99"),
    TERRITORY("Территория",Material.MAP,"&#63E6BE"),CUSTOM("Особые события",Material.WRITABLE_BOOK,"&#FFFFFF"),DISSOLUTION("Исчезновение",Material.WITHER_ROSE,"&#777777");
    private final String display;private final Material icon;private final String color;ChronicleCategory(String display,Material icon,String color){this.display=display;this.icon=icon;this.color=color;}
    public String display(){return display;}public Material icon(){return icon;}public String color(){return color;}
    public static ChronicleCategory parse(String input){if(input==null)return null;String value=input.toUpperCase(Locale.ROOT);return switch(value){case "FOUNDATION","ОСНОВАНИЕ"->FOUNDING;case "MAYORS","МЭР","МЭРЫ"->MAYOR;case "NATIONS","НАЦИЯ","НАЦИИ"->NATION;case "WARS","ВОЙНА","ВОЙНЫ"->WAR;case "WONDERS","ЧУДО","ЧУДЕСА"->WONDER;case "ACHIEVEMENTS","ДОСТИЖЕНИЕ","ДОСТИЖЕНИЯ"->ACHIEVEMENT;case "LAND","ТЕРРИТОРИЯ"->TERRITORY;case "РАСПАД"->DISSOLUTION;default->tryValue(value);};}
    private static ChronicleCategory tryValue(String value){try{return valueOf(value);}catch(IllegalArgumentException exception){return null;}}
}
