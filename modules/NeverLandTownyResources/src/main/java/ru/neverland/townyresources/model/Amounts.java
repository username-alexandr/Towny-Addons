package ru.neverland.townyresources.model;
import java.math.*;
import java.util.*;
/** Fixed-point thousandths: no floating-point balance drift. */
public final class Amounts {
    public static final long SCALE=1000, MAX=1_000_000_000_000L, FLOW_MAX=MAX*10000;
    private Amounts() {}
    public static long parse(Object raw) {
        try { long n=new BigDecimal(String.valueOf(raw).replace(',', '.')).multiply(BigDecimal.valueOf(SCALE)).longValueExact(); return valid(n); }
        catch(ArithmeticException|NumberFormatException ex) { throw new IllegalArgumentException("Количество: 0..1 000 000 000, максимум три знака после запятой"); }
    }
    public static long valid(long n) { if(n<0||n>MAX) throw new IllegalArgumentException("Количество вне допустимого диапазона"); return n; }
    public static long multiply(long n,int count) { if(count<0)throw new IllegalArgumentException("Отрицательный множитель");valid(n);return count==0?0:n>MAX/count?MAX:n*count; }
    public static long bonus(long n,double multiplier) {
        if(!Double.isFinite(multiplier)) multiplier=1;
        return Math.min(MAX,BigDecimal.valueOf(n).multiply(BigDecimal.valueOf(Math.max(1,Math.min(3,multiplier)))).setScale(0,RoundingMode.DOWN).longValueExact());
    }
    public static Map<Resource,Long> copy(Map<Resource,Long> input) {
        var result=new EnumMap<Resource,Long>(Resource.class);
        for(var r:Resource.values()) result.put(r,valid(input.getOrDefault(r,0L)));
        return Collections.unmodifiableMap(result);
    }
    public static Map<Resource,Long> flows(Map<Resource,Long> input) {
        var result=new EnumMap<Resource,Long>(Resource.class);
        for(var r:Resource.values()){long n=input.getOrDefault(r,0L);if(n<0||n>FLOW_MAX)throw new IllegalArgumentException("Поток ресурсов вне допустимого диапазона");result.put(r,n);}
        return Collections.unmodifiableMap(result);
    }
    public static EnumMap<Resource,Long> mutable(Map<Resource,Long> input) { var out=new EnumMap<Resource,Long>(Resource.class);out.putAll(copy(input));return out; }
    public static String decimal(long n) { return BigDecimal.valueOf(n,3).stripTrailingZeros().toPlainString(); }
    public static String display(long n) { var f=java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));f.setMaximumFractionDigits(3);return f.format(BigDecimal.valueOf(n,3)); }
    public static long add(long a,long b) { return Math.min(MAX,Math.addExact(a,b)); }
}
