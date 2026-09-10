package ru.neverland.townytreasury.model;
import java.math.*;
public final class Money {
    public static final long MAX=9_000_000_000_000_000L;
    private Money(){}
    public static long valid(long value){if(value < -MAX || value > MAX)throw new IllegalArgumentException("Сумма выходит за допустимый предел");return value;}
    public static long positive(long value){valid(value);if(value<0)throw new IllegalArgumentException("Отрицательная сумма");return value;}
    public static long cents(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Некорректная сумма");return parse(Double.toString(value));}
    public static long parse(String value){return valid(new BigDecimal(value.replace(',','.')).movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact());}
    public static String format(long cents){return BigDecimal.valueOf(cents,2).toPlainString();}
    public static long add(long a,long b){return valid(Math.addExact(a,b));}
}
