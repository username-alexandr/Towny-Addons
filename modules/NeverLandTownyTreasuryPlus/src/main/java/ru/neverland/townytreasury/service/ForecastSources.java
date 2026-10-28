package ru.neverland.townytreasury.service;

import java.util.*;
import ru.neverland.core.ApiServices;
import ru.neverland.townytreasury.config.TreasurySettings;
import ru.neverland.townytreasury.model.*;

/** Public provider snapshots only. Missing/paused/incompatible data must never masquerade as zero expense. */
final class ForecastSources {
    private ForecastSources(){}
    record Result(List<TreasuryForecast.Item> items,List<String> issues){}
    static Result read(UUID town,int seconds,TreasurySettings settings){
        var items=new ArrayList<TreasuryForecast.Item>();var issues=new ArrayList<String>();
        var builds=ApiServices.connect("NeverLandTownyBuilds","ru.neverland.townybuilds.api.BuildingMaterialsApi",1,"plans");
        if(builds.ready())try{
            Object raw=builds.invoke("plans",new Class<?>[]{UUID.class},town);if(!(raw instanceof Collection<?> rows)||rows.size()>512)throw new IllegalArgumentException("Неверный список строек");
            var batch=new ArrayList<TreasuryForecast.Item>();for(Object value:rows){var row=row(value);String id=text(row,"project");boolean known=flag(row,"known");batch.add(new TreasuryForecast.Item("build/"+id,text(row,"title"),Budget.CONSTRUCTION,cents(row),known));if(!known)issues.add("Стройка "+id+": незавершённая оплата или сверка");}items.addAll(batch);
        }catch(Exception|LinkageError error){issues.add("Не удалось прочитать планы строительства");}
        else if(builds.state()!=ApiServices.State.NOT_INSTALLED)issues.add("Планы строительства временно недоступны");
        var upkeep=ApiServices.connect("NeverLandTownyUpkeep","ru.neverland.townyupkeep.api.TownyUpkeepApi",1,"plannedBills");
        if(upkeep.ready())try{
            Object raw=upkeep.invoke("plannedBills",new Class<?>[]{UUID.class,int.class},town,seconds);if(!(raw instanceof Collection<?> rows)||rows.size()>512)throw new IllegalArgumentException("Неверный список содержания");
            var batch=new ArrayList<TreasuryForecast.Item>();for(Object value:rows){var row=row(value);String id=text(row,"project");boolean known=flag(row,"known");batch.add(new TreasuryForecast.Item("upkeep/"+id,text(row,"title"),settings.category("project/"+id),cents(row),known));if(!known)issues.add("Содержание "+id+": счёт ещё обрабатывается");}items.addAll(batch);
        }catch(Exception|LinkageError error){issues.add("Не удалось прочитать счета содержания");}
        else if(upkeep.state()!=ApiServices.State.NOT_INSTALLED)issues.add("Расчёт содержания временно недоступен");
        return new Result(List.copyOf(items),List.copyOf(issues));
    }
    private static Map<?,?> row(Object value){if(!(value instanceof Map<?,?> map))throw new IllegalArgumentException("Неверная строка прогноза");return map;}
    private static String text(Map<?,?> row,String key){if(!(row.get(key) instanceof String s)||s.isBlank())throw new IllegalArgumentException("Нет поля "+key);return s;}
    private static long cents(Map<?,?> row){Object n=row.get("cents");if(!(n instanceof Long||n instanceof Integer))throw new IllegalArgumentException("Нецелая денежная сумма");return Money.positive(((Number)n).longValue());}
    private static boolean flag(Map<?,?> row,String key){if(!(row.get(key) instanceof Boolean b))throw new IllegalArgumentException("Нет признака полноты");return b;}
}
