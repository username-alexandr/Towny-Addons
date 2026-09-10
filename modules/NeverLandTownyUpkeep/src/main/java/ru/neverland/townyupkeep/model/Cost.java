package ru.neverland.townyupkeep.model;
import java.math.*;
import java.util.*;
/** Cents and resource thousandths, never floating-point ledger balances. */
public record Cost(long money, Map<String,Long> resources) {
    public static final Set<String> RESOURCES=Set.of("wood","stone","metal","food","water","materials","knowledge","influence");
    public static final long MAX=1_000_000_000_000L;
    public Cost {
        if(money<0||money>100_000_000_000L)throw new IllegalArgumentException("Стоимость деньгами вне допустимого диапазона");
        resources=Map.copyOf(resources);for(var e:resources.entrySet())if(!RESOURCES.contains(e.getKey())||e.getValue()<0||e.getValue()>MAX)throw new IllegalArgumentException("Некорректная стоимость ресурса");
    }
    public static long parse(Object value,int scale){try{return new BigDecimal(String.valueOf(value)).movePointRight(scale).longValueExact();}catch(Exception ex){throw new IllegalArgumentException("Неверное количество: "+value);}}
    public static String format(long value,int scale){return BigDecimal.valueOf(value,scale).stripTrailingZeros().toPlainString().replace('.',',');}
    public Cost multiply(int level,BigDecimal factor){if(level<1||level>5||factor.compareTo(BigDecimal.ONE)<0||factor.compareTo(BigDecimal.valueOf(20))>0)throw new IllegalArgumentException("Некорректный множитель содержания");
        BigDecimal n=factor.multiply(BigDecimal.valueOf(level));Map<String,Long> next=new HashMap<>();resources.forEach((id,v)->next.put(id,scale(v,n)));return new Cost(scale(money,n),next);}
    public Cost policy(double multiplier){double safe=ru.neverland.integration.PolicyEffects.bound(multiplier,1,3,1);BigDecimal factor=BigDecimal.valueOf(safe);Map<String,Long> next=new HashMap<>();resources.forEach((id,value)->next.put(id,scale(value,factor)));return new Cost(scale(money,factor),next);}
    private static long scale(long value,BigDecimal factor){return BigDecimal.valueOf(value).multiply(factor).setScale(0,RoundingMode.CEILING).longValueExact();}
}
