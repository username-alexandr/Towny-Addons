package ru.neverland.townytaxes.model;

import java.math.*;
import java.util.*;

/** One frozen calculation for preview, withdrawal, receipt and administrator reconciliation. */
public record TaxCalculation(double raw,double citizenship,double municipal,double sanctions,double discount,long cents) {
    public static final long MAX_CENTS=9_000_000_000_000L;
    public TaxCalculation {
        for(double n:new double[]{raw,citizenship,municipal,sanctions,discount})if(!Double.isFinite(n))throw new IllegalArgumentException("Данные расчёта налогов временно недоступны");
        if(raw<0||citizenship<0||citizenship>10||municipal<.5||municipal>1.5||sanctions<0||sanctions>1000||discount<0||discount>1||cents<0||cents>MAX_CENTS)throw new IllegalArgumentException("Сумма или коэффициент налога вне допустимых границ");
        if(cents!=amount(raw,citizenship,municipal,sanctions,discount))throw new IllegalArgumentException("Итог не соответствует сохранённой формуле налога");
    }
    public static TaxCalculation calculate(double raw,double citizenship,double municipal,double sanctions,double discount){
        double bounded=ru.neverland.integration.PolicyEffects.bound(municipal,.5,1.5,1);
        return new TaxCalculation(raw,citizenship,bounded,sanctions,discount,amount(raw,citizenship,bounded,sanctions,discount));
    }
    private static long amount(double raw,double citizenship,double municipal,double sanctions,double discount){
        try{return BigDecimal.valueOf(raw).multiply(BigDecimal.valueOf(citizenship)).multiply(BigDecimal.valueOf(municipal)).multiply(BigDecimal.valueOf(sanctions)).multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(discount))).movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact();}
        catch(NumberFormatException|ArithmeticException error){throw new IllegalArgumentException("Не удалось точно рассчитать налог",error);}
    }
    public double money(){return cents/100.0;}
    public Map<String,Object> values(){return Map.of("raw",raw,"citizenship",citizenship,"municipal",municipal,"sanctions",sanctions,"discount",discount,"cents",cents);}
    private static String number(double n){return BigDecimal.valueOf(n).stripTrailingZeros().toPlainString();}
    public String describe(){return "База налога: "+number(raw)+"; статус жителя ×"+number(citizenship)+"; указы ×"+number(municipal)+"; санкции ×"+number(sanctions)+"; скидка "+number(discount*100)+"%; итог "+BigDecimal.valueOf(cents,2).toPlainString();}
}
