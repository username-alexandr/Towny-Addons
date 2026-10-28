package ru.neverland.townysieges;
import java.time.*;
/** Daily local window; equal endpoints mean all day. Tests use actual zone rules. */
public record RaidWindow(int start,int end) {
    public RaidWindow{if(start<0||start>=1440||end<0||end>=1440)throw new IllegalArgumentException("Время должно быть в пределах суток");}
    public static int minute(String text){var time=LocalTime.parse(text);if(time.getSecond()!=0||time.getNano()!=0)throw new IllegalArgumentException("Укажите часы и минуты: ЧЧ:ММ");return time.getHour()*60+time.getMinute();}
    public int minutes(){return start==end?1440:Math.floorMod(end-start,1440);}
    public String describe(){return start==end?"Круглосуточно":time(start)+"–"+time(end);}
    private static String time(int n){return String.format(java.util.Locale.ROOT,"%02d:%02d",n/60,n%60);}
    public long closes(long now,ZoneId zone){
        if(start==end)return Long.MAX_VALUE;var at=Instant.ofEpochMilli(now).atZone(zone);LocalDate day=at.toLocalDate();
        if(at.getHour()*60+at.getMinute()<start)day=day.minusDays(1);
        var open=day.atTime(start/60,start%60).atZone(zone).toInstant();var close=day.plusDays(end<=start?1:0).atTime(end/60,end%60).atZone(zone).toInstant();
        return !Instant.ofEpochMilli(now).isBefore(open)&&Instant.ofEpochMilli(now).isBefore(close)?close.toEpochMilli():-1;
    }
    public boolean fits(long now,long duration,ZoneId zone){if(duration<1)throw new IllegalArgumentException("Неверная длительность");long close=closes(now,zone);return close>now&&duration<=close-now;}
}
