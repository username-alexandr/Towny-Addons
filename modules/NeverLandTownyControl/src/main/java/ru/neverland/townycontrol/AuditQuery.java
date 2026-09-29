package ru.neverland.townycontrol;

import ru.neverland.core.AuditRecord;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;

public record AuditQuery(Map<String,String> filters,int page) implements Predicate<AuditRecord> {
    public AuditQuery {filters=Map.copyOf(filters);if(page<1||page>100)throw new IllegalArgumentException("Страница: 1–100");}
    public static AuditQuery parse(String[] args){var map=new LinkedHashMap<String,String>();int page=1;for(String raw:args){int split=raw.indexOf('=');if(split<1)throw new IllegalArgumentException("Фильтр: ключ=значение");String k=raw.substring(0,split).toLowerCase(Locale.ROOT),v=raw.substring(split+1);if(v.isBlank())throw new IllegalArgumentException("Пустой фильтр");if(k.equals("page")){page=Integer.parseInt(v);continue;}if(!Set.of("town","player","from","to","category","kind","module","outcome","id","since","until","intercity","text").contains(k))throw new IllegalArgumentException("Неизвестный фильтр: "+k);if(map.putIfAbsent(k,v)!=null)throw new IllegalArgumentException("Фильтр указан дважды: "+k);if(k.equals("category"))AuditCategory.valueOf(v.toUpperCase(Locale.ROOT));if(k.equals("since")||k.equals("until"))date(v);if(k.equals("intercity")&&!Set.of("true","false").contains(v))throw new IllegalArgumentException("intercity=true|false");}if(map.containsKey("since")&&map.containsKey("until")&&map.get("since").compareTo(map.get("until"))>0)throw new IllegalArgumentException("Начало позже конца периода");return new AuditQuery(map,page);}
    private static LocalDate date(String text){try{return LocalDate.parse(text);}catch(java.time.DateTimeException ex){throw new IllegalArgumentException("Дата должна иметь формат YYYY-MM-DD: "+text);}}
    @Override public boolean test(AuditRecord r){for(var e:filters.entrySet()){String v=e.getValue();boolean match=switch(e.getKey()){
        case "town"->r.town(v);case "player"->r.player(v);case "from"->r.from().id().equals(v)||r.from().town().equals(v);case "to"->r.to().id().equals(v)||r.to().town().equals(v);
        case "category"->AuditCategory.valueOf(v.toUpperCase(Locale.ROOT)).test(r);case "kind"->r.kind().equalsIgnoreCase(v);case "module"->r.module().equalsIgnoreCase(v)||r.module().equalsIgnoreCase("NeverLandTowny"+v);case "outcome"->r.outcome().equalsIgnoreCase(v);
        case "id"->r.id().equalsIgnoreCase(v)||r.operation().equalsIgnoreCase(v);
        case "since"->r.at()>=LocalDate.parse(v).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();case "until"->r.at()<LocalDate.parse(v).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        case "intercity"->r.intercity()==Boolean.parseBoolean(v);case "text"->String.join(" ",r.fields()).toLowerCase(Locale.ROOT).contains(v.toLowerCase(Locale.ROOT));default->false;};if(!match)return false;}return true;}
}
