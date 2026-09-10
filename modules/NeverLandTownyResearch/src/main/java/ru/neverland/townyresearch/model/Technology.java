package ru.neverland.townyresearch.model;
import java.util.*;
public record Technology(String id,String name,String icon,String description,boolean enabled,List<Level> levels) {
    public static final Set<String> IDS=Set.of("irrigation","walls","fast_caravans","medicine","navigation","metallurgy","alchemy");
    public Technology{if(!IDS.contains(id)||name==null||name.isBlank()||icon==null||icon.isBlank()||description==null)throw new IllegalArgumentException("Неверная технология");levels=List.copyOf(levels);if(levels.isEmpty()||levels.size()>5)throw new IllegalArgumentException("Уровней: 1..5");}
    public record Level(long knowledge,int seconds,double bonus,Map<String,Integer> buildings,Map<String,Integer> requires){
        public Level{if(knowledge<1||knowledge>1_000_000_000_000L||seconds<5||seconds>604800||!Double.isFinite(bonus)||bonus<0||bonus>0.5)throw new IllegalArgumentException("Некорректная цена, время или бонус технологии");buildings=checked(buildings);requires=checked(requires);if(buildings.isEmpty())throw new IllegalArgumentException("Укажите научные здания");}
    }
    public static Map<String,Integer> checked(Map<String,Integer> input){var out=Map.copyOf(input);for(var e:out.entrySet()){validId(e.getKey());if(e.getValue()<1||e.getValue()>5)throw new IllegalArgumentException("Уровень требования: 1..5");}return out;}
    public static void validId(String id){if(id==null||!id.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException("Неверный ID");}
}
