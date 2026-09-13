package ru.neverland.mintevents.service;

import java.util.Locale;
import java.util.Set;

/** A conservative allowlist: no inventories, block entities, doors, beds or custom note blocks. */
public final class FireMaterials {
    private static final Set<String> WOOD=Set.of("OAK","SPRUCE","BIRCH","JUNGLE","ACACIA","DARK_OAK","MANGROVE","CHERRY","PALE_OAK","BAMBOO");
    private static final Set<String> SHAPES=Set.of("PLANKS","STAIRS","SLAB","FENCE","FENCE_GATE","TRAPDOOR","PRESSURE_PLATE","BUTTON","LOG","WOOD");
    private static final Set<String> COLORS=Set.of("WHITE","ORANGE","MAGENTA","LIGHT_BLUE","YELLOW","LIME","PINK","GRAY","LIGHT_GRAY","CYAN","PURPLE","BLUE","BROWN","GREEN","RED","BLACK");
    private FireMaterials() { }
    public static boolean combustible(String material) {
        if(material==null)return false;
        String name=material.toUpperCase(Locale.ROOT);
        if(name.equals("BOOKSHELF")||name.equals("CRAFTING_TABLE")||name.equals("LADDER")||name.equals("BAMBOO_MOSAIC")||name.equals("BAMBOO_MOSAIC_STAIRS")||name.equals("BAMBOO_MOSAIC_SLAB"))return true;
        if(name.endsWith("_WOOL"))return COLORS.contains(name.substring(0,name.length()-5));
        if(name.endsWith("_CARPET"))return COLORS.contains(name.substring(0,name.length()-7));
        if(name.equals("BAMBOO_BLOCK")||name.equals("STRIPPED_BAMBOO_BLOCK"))return true;
        if(name.startsWith("STRIPPED_")&&!name.endsWith("_LOG")&&!name.endsWith("_WOOD"))return false;
        if(name.startsWith("STRIPPED_"))name=name.substring(9);
        if(name.equals("BAMBOO_LOG")||name.equals("BAMBOO_WOOD"))return false;
        for(String wood:WOOD)if(name.startsWith(wood+"_")&&SHAPES.contains(name.substring(wood.length()+1)))return true;
        return false;
    }
    public static boolean structuralMarker(String name) {
        return combustible(name)&&!name.endsWith("_LOG")&&!name.endsWith("_WOOD");
    }
}
