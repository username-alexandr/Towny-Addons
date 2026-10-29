package ru.neverland.townypopulation.integration;

import java.math.BigDecimal;
import java.util.*;
import ru.neverland.core.ApiServices;
import ru.neverland.townypopulation.model.DistrictCohorts;

/** Strict primitive boundary: unknown/old district snapshots never masquerade as empty districts. */
public final class DistrictsBridge {
    public record Snapshot(boolean available,boolean pending,String issue,long sourceAt,Map<String,DistrictCohorts.District> districts,Map<String,String> assignments){
        public Snapshot{districts=Map.copyOf(districts);assignments=Map.copyOf(assignments);}
        public static Snapshot unavailable(String issue,boolean pending){return new Snapshot(false,pending,issue,0,Map.of(),Map.of());}
    }
    public Snapshot read(UUID town,long now){
        try{
            var link=ApiServices.connect("NeverLandTownyDistricts","ru.neverland.townydistricts.api.TownyDistrictsApi",1,"assessment");
            if(link.state()==ApiServices.State.NOT_INSTALLED)return new Snapshot(true,false,"",now,Map.of(),Map.of());
            if(!link.ready())return Snapshot.unavailable("Данные районов временно недоступны",false);
            return decode(link.invoke("assessment",new Class<?>[]{UUID.class},town),now);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError error){return Snapshot.unavailable("Не удалось проверить данные районов. Сообщите администрации",false);}
    }
    /** Public pure parser for contract regression tests; only JDK values cross plugin classloaders. */
    public static Snapshot decode(Object raw,long now){
        var report=map(raw,32);boolean available=bool(report,"available"),pending=bool(report,"pending");
        if(!available)return Snapshot.unavailable(pending?"Данные районов готовятся. Обновите окно через несколько секунд":"Оценка районов временно недоступна",pending);
        if(pending||integer(report,"format",2,2)!=2)throw new IllegalArgumentException("Несовместимый снимок районов");
        long source=number(report.get("sourceAt"),1,Long.MAX_VALUE),checked=number(report.get("checkedAt"),1,Long.MAX_VALUE);
        if(source>now||checked>now||now-source>60_000) return Snapshot.unavailable("Данные районов ожидают обновления",true);
        boolean home=bool(report,"homeKnown");var rows=map(report.get("rows"),128);var districts=new HashMap<String,DistrictCohorts.District>();
        for(var entry:rows.entrySet()){
            String id=key(entry.getKey());var row=map(entry.getValue(),32);if(!id.equals(string(row,"id")))throw new IllegalArgumentException("Не совпадает ID района");
            int total=integer(row,"total",1,20_000),connected=integer(row,"connected",0,total);boolean world=bool(row,"worldAvailable");
            // Buildings being unavailable makes their physical assignment unknowable, not an empty contribution.
            if(!bool(row,"buildingsAvailable"))return Snapshot.unavailable("Площадки зданий района пока недоступны",true);
            var services=new ArrayList<DistrictCohorts.Service>();for(var service:map(row.get("services"),8).entrySet()){
                var value=map(service.getValue(),8);if(integer(value,"total",1,20_000)!=total)throw new IllegalArgumentException("Площадь службы отличается от района");
                services.add(new DistrictCohorts.Service(key(service.getKey()),string(value,"title"),integer(value,"served",0,total),total,bool(value,"known")));
            }
            services.sort(Comparator.comparing(DistrictCohorts.Service::id));districts.put(id,new DistrictCohorts.District(id,string(row,"name"),string(row,"type"),total,connected,home&&world,services));
        }
        var assignments=new HashMap<String,String>();for(var entry:map(report.get("assignments"),1024).entrySet()){
            String project=key(entry.getKey()),district=key(entry.getValue());if(!project.matches("[a-z0-9_]{1,64}")||!districts.containsKey(district))throw new IllegalArgumentException("Неверное соответствие площадки району");assignments.put(project,district);
        }
        return new Snapshot(true,false,"",source,districts,assignments);
    }
    private static Map<?,?> map(Object value,int limit){if(!(value instanceof Map<?,?> map)||map.size()>limit)throw new IllegalArgumentException("Неверная карта районов");return map;}
    private static String key(Object value){if(!(value instanceof String text)||text.isBlank()||text.length()>80)throw new IllegalArgumentException("Неверное имя поля района");return text;}
    private static String string(Map<?,?> map,String field){return key(map.get(field));}
    private static boolean bool(Map<?,?> map,String field){if(!(map.get(field)instanceof Boolean flag))throw new IllegalArgumentException("Неверное состояние района");return flag;}
    private static int integer(Map<?,?> map,String field,int min,int max){return(int)number(map.get(field),min,max);}
    private static long number(Object value,long min,long max){if(!(value instanceof Number number))throw new IllegalArgumentException("Неверное число района");try{long result=new BigDecimal(number.toString()).longValueExact();if(result<min||result>max)throw new IllegalArgumentException("Число района вне диапазона");return result;}catch(ArithmeticException|NumberFormatException error){throw new IllegalArgumentException("Неверное целое число района",error);}}
}
