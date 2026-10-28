package ru.neverland.townyupkeep.model;

import java.util.*;

/** The same billing boundary for town savings and treasury forecasts. No spending or server-time advancement. */
public final class BillingPreview {
    private BillingPreview(){}
    public static long charges(long due,int period,int horizon){
        if(due<0||period<60||period>604800||horizon<1||horizon>604800)throw new IllegalArgumentException("Некорректный срок прогноза");
        return due>=horizon?0:1+(horizon-1-due)/period;
    }
    public static Map<String,Object> summary(Collection<Map<String,Object>> rows,Set<String> stop){
        var seen=new HashSet<String>();long before=0,after=0;boolean known=true;var oldResources=new TreeMap<String,Long>();var newResources=new TreeMap<String,Long>();
        for(var row:rows){String id=(String)row.get("project");if(!seen.add(id))throw new IllegalArgumentException("Повтор здания в прогнозе");long money=((Number)row.get("cents")).longValue();if(money<0)throw new IllegalArgumentException("Отрицательный прогноз");before=Math.addExact(before,money);if(!stop.contains(id))after=Math.addExact(after,money);known&=Boolean.TRUE.equals(row.get("known"));
            var resources=(Map<?,?>)row.get("resources");for(var e:resources.entrySet()){String resource=(String)e.getKey();long n=((Number)e.getValue()).longValue();if(n<0||!Cost.RESOURCES.contains(resource))throw new IllegalArgumentException("Неверный ресурс прогноза");oldResources.merge(resource,n,Math::addExact);if(!stop.contains(id))newResources.merge(resource,n,Math::addExact);}
        }
        if(!seen.containsAll(stop))throw new IllegalArgumentException("В плане остановки есть неизвестное здание");
        return Map.of("beforeCents",before,"afterCents",after,"savingCents",before-after,"beforeResources",Map.copyOf(oldResources),"afterResources",Map.copyOf(newResources),"known",known);
    }
}
