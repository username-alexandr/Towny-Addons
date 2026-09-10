package ru.neverland.townyspecialization.service;
import java.util.Map;
public final class EffectNames{
    private static final Map<String,String> NAMES=Map.of("production","Выпуск профильных зданий","trade_speed","Сокращение времени караванов","mob_defense","Защита жителей от мобов","research_speed","Скорость исследований","courier_speed","Скорость NPC-курьеров","trade_delay","Снижение задержек торговли","happiness","Довольство населения");
    public static String name(String id){return NAMES.getOrDefault(id,"Бонус");}
    public static String value(String id,double value){if(id.equals("happiness"))return new java.text.DecimalFormat("0.##").format(value)+" п.";String percent=Ui.percent(value);return id.equals("trade_delay")?percent.replace("%"," п.п."):percent;}
}
