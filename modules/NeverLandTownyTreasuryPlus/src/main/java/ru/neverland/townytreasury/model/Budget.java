package ru.neverland.townytreasury.model;
import java.util.*;
public enum Budget {
    CONSTRUCTION("construction","Строительство","BRICKS"),ARMY("army","Армия","IRON_SWORD"),INFRASTRUCTURE("infrastructure","Инфраструктура","RAIL"),SOCIAL("social","Социальные расходы","BREAD"),FREE("free","Свободный остаток","CHEST");
    private final String id,name,icon;
    Budget(String id,String name,String icon){this.id=id;this.name=name;this.icon=icon;}
    public String id(){return id;}public String title(){return name;}public String icon(){return icon;}
    public static Budget parse(String id){return Arrays.stream(values()).filter(b->b.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Статья: construction, army, infrastructure, social или free"));}
    public static Map<Budget,Long> amounts(Map<Budget,Long> input){var out=new EnumMap<Budget,Long>(Budget.class);for(var b:values())out.put(b,Money.positive(input.getOrDefault(b,0L)));return Collections.unmodifiableMap(out);}
    public static Map<Budget,Integer> shares(Map<Budget,Integer> input){var out=new EnumMap<Budget,Integer>(Budget.class);int sum=0;for(var b:values()){int n=input.getOrDefault(b,0);if(n<0||n>100)throw new IllegalArgumentException("Доли: 0–100%");out.put(b,n);sum+=n;}if(sum!=100)throw new IllegalArgumentException("Сумма долей должна быть 100%");return Collections.unmodifiableMap(out);}
}
