package ru.neverland.townybuilds.construction;

import java.util.*;
import org.bukkit.Material;

/** Theme is part of the saved architecture identity, so old sites and repairs retain their palette. */
public enum BuildingStyle {
    MEDIEVAL("medieval","Средневековье",0,Material.STONE_BRICKS),
    MODERN("modern","Современный",1000,Material.QUARTZ_BLOCK),
    FANTASY("fantasy","Фэнтези",2000,Material.AMETHYST_BLOCK);
    private final String id,title;private final int prefix;private final Material icon;
    BuildingStyle(String id,String title,int prefix,Material icon){this.id=id;this.title=title;this.prefix=prefix;this.icon=icon;}
    public String id(){return id;}public String title(){return title;}public Material icon(){return icon;}
    public int architecture(int base){if(base<1||base>=1000)throw new IllegalArgumentException("Неверная базовая версия чертежа");return prefix+base;}
    public static BuildingStyle parse(String id){for(var style:values())if(style.id.equalsIgnoreCase(id)||style.title.equalsIgnoreCase(id))return style;throw new IllegalArgumentException("Выберите: Средневековье, Современный или Фэнтези");}
    public static BuildingStyle ofArchitecture(int version){int local=version>=10000?version-10000:version;return switch(local/1000){case 0->MEDIEVAL;case 1->MODERN;case 2->FANTASY;default->throw new IllegalArgumentException("Неизвестный стиль чертежа");};}
    public static int baseArchitecture(int version){ofArchitecture(version);int base=version%1000;if(base<1)throw new IllegalArgumentException("Неверная версия чертежа");return base;}
    public BlueprintPlan apply(BlueprintPlan source){
        if(source==null||this==MEDIEVAL)return source;var blocks=new LinkedHashMap<BlockOffset,BlueprintBlock>();
        source.blocks().forEach((at,b)->blocks.put(at,new BlueprintBlock(material(b.material()),b.role(),b.stage(),b.facing(),b.axis(),b.half(),b.hinge())));
        return new BlueprintPlan(source.projectId(),source.level(),source.stageName(),blocks);
    }
    /** Shape families and functional blocks are preserved; changes are visual, never a new resource price. */
    private final Map<Material,Material> substitutions=new java.util.concurrent.ConcurrentHashMap<>();
    public Material material(Material source){return this==MEDIEVAL?source:substitutions.computeIfAbsent(source,this::mapped);}
    private Material mapped(Material source){
        String name=source.name();boolean modern=this==MODERN;
        var woods=List.of("OAK","SPRUCE","BIRCH","JUNGLE","ACACIA","DARK_OAK","MANGROVE","CHERRY","PALE_OAK","CRIMSON","WARPED","BAMBOO");
        for(String wood:woods){
            if(name.equals(wood+"_PLANKS")||name.equals("BAMBOO_MOSAIC"))return modern?Material.WHITE_CONCRETE:Material.CHERRY_PLANKS;
            if(name.equals(wood+"_LOG")||name.equals(wood+"_STEM")||name.equals("STRIPPED_"+wood+"_LOG")||name.equals("STRIPPED_"+wood+"_STEM"))return modern?Material.QUARTZ_PILLAR:Material.STRIPPED_CHERRY_LOG;
            if(name.equals(wood+"_WOOD")||name.equals(wood+"_HYPHAE")||name.equals("STRIPPED_"+wood+"_WOOD")||name.equals("STRIPPED_"+wood+"_HYPHAE"))return modern?Material.QUARTZ_PILLAR:Material.STRIPPED_CHERRY_WOOD;
            if(name.equals(wood+"_STAIRS")||name.equals("BAMBOO_MOSAIC_STAIRS"))return modern?Material.SMOOTH_QUARTZ_STAIRS:Material.PURPUR_STAIRS;
            if(name.equals(wood+"_SLAB")||name.equals("BAMBOO_MOSAIC_SLAB"))return modern?Material.SMOOTH_QUARTZ_SLAB:Material.PURPUR_SLAB;
            if(name.equals(wood+"_FENCE"))return modern?Material.BIRCH_FENCE:Material.CHERRY_FENCE;
            if(name.equals(wood+"_FENCE_GATE"))return modern?Material.BIRCH_FENCE_GATE:Material.CHERRY_FENCE_GATE;
            if(name.equals(wood+"_DOOR"))return modern?Material.BIRCH_DOOR:Material.CHERRY_DOOR;
            if(name.equals(wood+"_TRAPDOOR"))return modern?Material.BIRCH_TRAPDOOR:Material.CHERRY_TRAPDOOR;
        }
        return switch(source){
            case STONE,COBBLESTONE,MOSSY_COBBLESTONE,STONE_BRICKS,MOSSY_STONE_BRICKS,CRACKED_STONE_BRICKS,CHISELED_STONE_BRICKS,ANDESITE,POLISHED_ANDESITE,TUFF,POLISHED_TUFF,TUFF_BRICKS->modern?Material.LIGHT_GRAY_CONCRETE:Material.END_STONE_BRICKS;
            case DIORITE,POLISHED_DIORITE,BRICKS,SMOOTH_STONE,SANDSTONE,SMOOTH_SANDSTONE,CUT_SANDSTONE->modern?Material.WHITE_CONCRETE:Material.CALCITE;
            case COBBLESTONE_STAIRS,MOSSY_COBBLESTONE_STAIRS,STONE_STAIRS,STONE_BRICK_STAIRS,MOSSY_STONE_BRICK_STAIRS,BRICK_STAIRS,ANDESITE_STAIRS,POLISHED_ANDESITE_STAIRS->modern?Material.POLISHED_ANDESITE_STAIRS:Material.QUARTZ_STAIRS;
            case COBBLESTONE_SLAB,MOSSY_COBBLESTONE_SLAB,STONE_SLAB,STONE_BRICK_SLAB,MOSSY_STONE_BRICK_SLAB,BRICK_SLAB,ANDESITE_SLAB,POLISHED_ANDESITE_SLAB,SMOOTH_STONE_SLAB->modern?Material.POLISHED_ANDESITE_SLAB:Material.QUARTZ_SLAB;
            case GLASS,WHITE_STAINED_GLASS->modern?Material.LIGHT_BLUE_STAINED_GLASS:Material.PURPLE_STAINED_GLASS;
            case GLASS_PANE,WHITE_STAINED_GLASS_PANE->modern?Material.LIGHT_BLUE_STAINED_GLASS_PANE:Material.PURPLE_STAINED_GLASS_PANE;
            case GLOWSTONE->Material.SEA_LANTERN;
            case RED_NETHER_BRICKS,NETHER_BRICKS->modern?Material.POLISHED_DEEPSLATE:Material.DARK_PRISMARINE;
            case RED_NETHER_BRICK_STAIRS,NETHER_BRICK_STAIRS->modern?Material.POLISHED_DEEPSLATE_STAIRS:Material.DARK_PRISMARINE_STAIRS;
            case RED_NETHER_BRICK_SLAB,NETHER_BRICK_SLAB->modern?Material.POLISHED_DEEPSLATE_SLAB:Material.DARK_PRISMARINE_SLAB;
            default->source;
        };
    }
}
