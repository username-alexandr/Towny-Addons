package ru.neverland.townyseasons.markets;

import java.util.*;
import ru.neverland.townyseasons.Season;

/** A view of the existing calendar, never a second clock or an independent seasonal counter. */
public record CalendarWindow(UUID world,String worldName,String source,Season season,long year,int day,int days,boolean enabled) {
    public CalendarWindow {
        Objects.requireNonNull(world);Objects.requireNonNull(worldName);Objects.requireNonNull(source);
        if(year<0||day<0||days<1||days>365||day>days)throw new IllegalArgumentException("Неверная дата общего календаря");
        if(enabled&&(season==null||!Set.of("CUSTOM","MINECRAFT","MANUAL","REALISTIC_SEASONS").contains(source)))throw new IllegalArgumentException("Источник сезона недоступен");
        if(enabled&&Set.of("CUSTOM","MINECRAFT").contains(source)&&(year<1||day<1))throw new IllegalArgumentException("Неполная дата календаря");
    }
    public static CalendarWindow read(Map<String,Object> view){
        boolean enabled=Boolean.TRUE.equals(view.get("enabled"));String season=String.valueOf(view.get("season"));
        return new CalendarWindow((UUID)view.get("world"),String.valueOf(view.get("worldName")),String.valueOf(view.get("source")),enabled?Season.valueOf(season):null,
                ((Number)view.get("year")).longValue(),((Number)view.get("day")).intValue(),((Number)view.get("daysPerSeason")).intValue(),enabled);
    }
    public String key(){if(!enabled)throw new IllegalArgumentException("В этом мире сезоны отключены");return world+":"+source+":"+year+":"+season.name();}
    public Season next(){if(!enabled)throw new IllegalArgumentException("В этом мире сезоны отключены");return Season.values()[(season.ordinal()+1)%4];}
    public boolean fairOpen(int lastDays,boolean undated){if(lastDays<1||lastDays>365)throw new IllegalArgumentException("Неверная длительность ярмарки");return enabled&&(day==0?undated:day>=Math.max(1,days-lastDays+1));}
    public String title(){return !enabled?"Сезоны отключены":season.title+(year>0?" • год "+year:"");}
    public String closedReason(int lastDays,boolean undated){
        if(!enabled)return "В мире города сезонная ярмарка отключена.";
        if(day==0&&!undated)return "Источник сообщает сезон без даты; ярмарка ждёт календарь с днями.";
        if(fairOpen(lastDays,undated))return "Ярмарка открыта по общему календарю мира.";
        return "Ярмарка откроется с "+Math.max(1,days-lastDays+1)+"-го дня этого сезона.";
    }
}
