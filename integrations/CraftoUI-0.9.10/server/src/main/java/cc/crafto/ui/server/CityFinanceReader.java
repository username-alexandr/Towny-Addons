package cc.crafto.ui.server;

import org.bukkit.plugin.Plugin;
import java.util.*;
import static cc.crafto.ui.server.PublicMethods.*;

/** Called only from AsyncScheduler. Reads immutable TreasuryPlus snapshots; never calls sync/spend/economy. */
final class CityFinanceReader {
    static final String API="ru.neverland.townytreasury.api.TownyTreasuryApi";
    static CityFinance read(Plugin treasury,UUID town,long now){
        if(town==null)return CityFinance.unavailable("Сначала вступите в город");
        if(treasury==null||!treasury.isEnabled())return CityFinance.unavailable("Аддон казны недоступен");
        try{
            if(frozen(treasury))return CityFinance.unavailable("Учёт казны приостановлен администратором");
            Class<?> api=Class.forName(API,false,treasury.getClass().getClassLoader());
            var registration=treasury.getServer().getServicesManager().getRegistration(api);
            if(registration==null||registration.getPlugin()!=treasury)return CityFinance.unavailable("API казны недоступен");
            var result=readProvider(registration.getProvider(),town,now);
            return treasury.isEnabled()&&!frozen(treasury)?result:CityFinance.unavailable("Учёт казны приостановлен");
        }catch(Exception|LinkageError e){return CityFinance.unavailable("Не удалось прочитать финансовый отчёт. Обновите экран.");}
    }
    private static boolean frozen(Plugin treasury)throws ReflectiveOperationException{
        try{return (boolean)Class.forName("ru.neverland.core.SuiteState",false,treasury.getClass().getClassLoader())
                .getMethod("frozen",String.class).invoke(null,treasury.getName());}
        catch(ClassNotFoundException|NoSuchMethodException ignored){return false;}
    }
    static CityFinance readProvider(Object service,UUID town,long now){
        try{
            if((boolean)call(service,"fault"))return CityFinance.unavailable("Учёт казны приостановлен. Нужна проверка администратора.");
            Object value=call(service,"treasury",UUID.class,town);
            if(!(value instanceof Optional<?> result))throw new IllegalArgumentException("Unsupported treasury API");
            if(result.isEmpty())return CityFinance.unavailable("Для этого города финансовый учёт ещё не начат");
            CityFinance snapshot=decode(result.get(),now);
            if((boolean)call(service,"fault"))return CityFinance.unavailable("Учёт казны приостановлен. Нужна проверка администратора.");
            return snapshot;
        }catch(Exception|LinkageError e){return CityFinance.unavailable("Финансовые данные недоступны или имеют неподдерживаемый формат");}
    }
    static CityFinance decode(Object ledger,long now)throws ReflectiveOperationException{
        long started=number(call(ledger,"started"));
        if(started<0||started>now)throw new IllegalArgumentException("Invalid accounting start");
        Object raw=call(ledger,"weeks");
        if(!(raw instanceof Map<?,?> weeks)||weeks.size()>13)throw new IllegalArgumentException("Invalid weekly reports");
        String key=CityFinance.weekAt(now);Object week=weeks.get(key);
        Map<String,Long> income=week==null?Map.of():amounts(call(week,"income"));
        Map<String,Long> expenses=week==null?Map.of():amounts(call(week,"expenses"));
        long refunds=week==null?0:number(call(week,"refunds")),adjustments=week==null?0:number(call(week,"adjustments"));
        return new CityFinance(true,"",key,Math.max(started,CityFinance.startOfWeek(now)),now,
                sum(income),sum(expenses),refunds,adjustments,call(ledger,"pending")!=null,expenses);
    }
    private static long number(Object value){
        if(!(value instanceof Long)&&!(value instanceof Integer))throw new IllegalArgumentException("Expected integer cents");
        return ((Number)value).longValue();
    }
    private static Map<String,Long> amounts(Object raw){
        if(!(raw instanceof Map<?,?> values)||values.size()>1024)throw new IllegalArgumentException("Invalid ledger amounts");
        var out=new TreeMap<String,Long>();
        for(var entry:values.entrySet()){
            if(!(entry.getKey() instanceof String key)||!key.matches("[a-z0-9_/-]{1,64}"))throw new IllegalArgumentException("Invalid ledger source");
            long amount=number(entry.getValue());if(amount<0)throw new IllegalArgumentException("Negative ledger flow");out.put(key,amount);
        }
        return Map.copyOf(out);
    }
    private static long sum(Map<String,Long> values){long total=0;for(long n:values.values())total=Math.addExact(total,n);return total;}
}
