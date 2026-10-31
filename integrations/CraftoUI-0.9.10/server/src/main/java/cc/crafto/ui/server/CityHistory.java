package cc.crafto.ui.server;

import org.bukkit.plugin.Plugin;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static cc.crafto.ui.server.PublicMethods.*;

/** Read-only, optional Chronicles API. Its entries() method copies the repository under SuiteState.lock;
 * it does not access players/worlds or perform disk I/O. Called from the async snapshot task. */
final class CityHistory {
    static final String PLUGIN="NeverLandTownyChronicles";
    static final int MAX_ENTRIES=10_000;
    record Entry(String id,long timestamp,String category,String title,List<String> details,String actor,String source) {
        String date(){try{return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Moscow")).format(Instant.ofEpochMilli(timestamp))+" МСК";}catch(DateTimeException e){return "Дата неизвестна";}}
        String subtitle(){return date()+" · "+category+(actor.isBlank()?"":" · "+actor);}
    }
    record Result(List<Entry> entries,String problem,boolean limited){
        static Result unavailable(String reason){return new Result(List.of(),reason,false);}
    }
    static boolean contributionsAvailable(Plugin addon){
        if(addon==null||!addon.isEnabled())return false;
        try{Class.forName("ru.neverland.townychronicles.service.ContributionsMenu",false,addon.getClass().getClassLoader());return true;}
        catch(ClassNotFoundException|LinkageError unsupported){return false;}
    }
    static Result read(Plugin addon,UUID town){
        if(town==null)return Result.unavailable("Сначала вступите в город.");
        if(addon==null||!addon.isEnabled())return Result.unavailable("Летопись недоступна: Chronicles не установлен или выключен.");
        try{
            Class<?> type=Class.forName("ru.neverland.townychronicles.api.TownyChroniclesApi",true,addon.getClass().getClassLoader());
            Object service=addon.getServer().getServicesManager().load(type);
            if(service==null)return Result.unavailable("Chronicles не зарегистрировал API летописи.");
            Object version=call(service,"apiVersion"),caps=call(service,"capabilities");
            if(!(version instanceof Integer n)||n!=1||!(caps instanceof Set<?> set)||!set.contains("entries"))return Result.unavailable("Версия API Chronicles не поддерживает чтение летописи.");
            Object result=type.getMethod("entries",UUID.class).invoke(service,town);
            if(!(result instanceof List<?> entries))throw new IllegalArgumentException("entries must be a list");
            return convert(entries,town);
        }catch(Exception|LinkageError e){return Result.unavailable("Не удалось прочитать летопись Chronicles. Проверьте состояние аддона.");}
    }
    static Result convert(List<?> records,UUID town)throws ReflectiveOperationException{
        // Never silently present a damaged source as an empty history.
        List<Entry> entries=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(Object record:records){
            if(!town.equals(call(record,"townId")))continue;
            String id=UUID.fromString(call(record,"id").toString()).toString();if(!ids.add(id))continue;
            Object rawDetails=call(record,"details");if(!(rawDetails instanceof List<?> details))throw new IllegalArgumentException("invalid details");
            List<String> text=new ArrayList<>();int remaining=6000;boolean cut=false;
            for(Object value:details){String line=clean(value,1000);if(text.size()>=64||line.length()>remaining){cut=true;break;}text.add(line);remaining-=line.length();}
            if(cut)text.add("Запись слишком длинная. Полный текст доступен в меню Chronicles.");
            entries.add(new Entry(id,((Number)call(record,"timestamp")).longValue(),category(call(record,"category").toString()),clean(call(record,"title"),400),List.copyOf(text),clean(call(record,"actor"),120),clean(call(record,"source"),120)));
        }
        entries.sort(Comparator.comparingLong(Entry::timestamp).reversed().thenComparing(Entry::id));
        return new Result(List.copyOf(entries.subList(0,Math.min(MAX_ENTRIES,entries.size()))),"",entries.size()>MAX_ENTRIES);
    }
    private static String clean(Object value,int max){String text=CivicData.clean(value==null?"":value.toString());return text.substring(0,Math.min(max,text.length()));}
    private static String category(String value){return switch(value){
        case "FOUNDING"->"Основание";case "MAYOR"->"Мэры";case "NATION"->"Нации";case "WAR"->"Войны";case "WONDER"->"Чудеса Света";case "ACHIEVEMENT"->"Достижения";case "TERRITORY"->"Территория";case "DISSOLUTION"->"Исчезновение";case "CUSTOM"->"Особые события";default->clean(value,80);
    };}
}
