package ru.neverland.townypolicies.service;
import java.math.BigDecimal;
import java.util.Map;
public final class EffectLabels {
    private EffectLabels(){}
    private static final Map<String,String> LABELS=Map.of("tax","Городские сборы","tariff","Транзитная пошлина","trade_time","Время торгового маршрута","army","Лимит солдат","production","Выпуск производств","upkeep","Содержание зданий","happiness","Довольство");
    public static String value(String key,double value){boolean points=key.equals("happiness"),rate=key.equals("tariff");double n=points||rate?value:value*100;return LABELS.getOrDefault(key,"Эффект")+": "+(!rate&&n>0?"+":"")+BigDecimal.valueOf(n).stripTrailingZeros().toPlainString()+(points?" п.":"%");}
}
