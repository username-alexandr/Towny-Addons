package cc.crafto.ui;
import java.util.*;

/** Presentation only. Names and colors match Contracts' OrderRarity; unknown stays unknown. */
public enum OrderRarityStyle {
    COMMON("Обычный",0xFFFFFFFF), UNCOMMON("Необычный",0xFF55FF55),
    RARE("Редкий",0xFF5555FF), EPIC("Эпический",0xFFFF55FF), LEGENDARY("Легендарный",0xFFFFAA00);
    public final String label;public final int color;
    OrderRarityStyle(String label,int color){this.label=label;this.color=color;}
    public static OrderRarityStyle parse(String value){try{return valueOf(value.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException|NullPointerException e){return null;}}
    public static OrderRarityStyle lore(String line){
        String value=line.replaceAll("(?i)§[0-9a-fk-orx]","").strip();
        if(!value.matches("(?iu)^(редкость|rarity)\\s*:.*"))return null;
        value=value.substring(value.indexOf(':')+1).strip();
        for(var rarity:values())if(value.equalsIgnoreCase(rarity.label)||value.equalsIgnoreCase(rarity.name()))return rarity;return null;
    }
    public static int rank(String value){var r=parse(value);return r==null?-1:r.ordinal();}
    public String icon(String type){
        String[] items=switch(type){
            case "HUNTING"->new String[]{"wooden_sword","stone_sword","iron_sword","diamond_sword","netherite_sword"};
            case "MINING"->new String[]{"wooden_pickaxe","stone_pickaxe","iron_pickaxe","diamond_pickaxe","netherite_pickaxe"};
            case "DELIVERY"->new String[]{"white_shulker_box","lime_shulker_box","light_blue_shulker_box","purple_shulker_box","orange_shulker_box"};
            case "FISHING"->new String[]{"cod","salmon","pufferfish","tropical_fish","heart_of_the_sea"};
            default->null;
        };return items==null?"":"minecraft:"+items[ordinal()];
    }
    public boolean glint(){return this==EPIC||this==LEGENDARY;}
}
