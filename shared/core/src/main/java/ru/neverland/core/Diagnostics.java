package ru.neverland.core;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
/** Read provider-local snapshots, including when shared code is loaded in separate classloaders. */
public final class Diagnostics {
    private Diagnostics() {}
    public static void show(CommandSender sender) {
        Set<String> lines=new TreeSet<>();
        collect(new ApiContract(){},ApiContract.class,lines);
        for(Class<?> contract:Bukkit.getServicesManager().getKnownServices()) {
            try {contract.getMethod("apiVersion");Object service=Bukkit.getServicesManager().load(contract);if(service!=null)collect(service,contract,lines);}
            catch(NoSuchMethodException ignored) {}
        }
        sender.sendMessage("NeverLand: состояние интеграций и синхронных записей (с запуска сервера)");
        for(String line:lines)sender.sendMessage(line);
    }
    private static void collect(Object service,Class<?> contract,Set<String> lines) {
        try {
            if(contract.getMethod("integrationDiagnostics").invoke(service) instanceof List<?> list)for(Object value:list)if(value instanceof Map<?,?> m)lines.add("API "+m.get("plugin")+" / "+m.get("contract")+": "+m.get("state")+" "+m.get("detail"));
            if(contract.getMethod("storageMetrics").invoke(service) instanceof List<?> list)for(Object value:list)if(value instanceof Map<?,?> m){long writes=((Number)m.get("writes")).longValue(),failures=((Number)m.get("failures")).longValue();double average=((Number)m.get("totalNanos")).doubleValue()/Math.max(1,writes+failures)/1_000_000.0,max=((Number)m.get("maximumNanos")).doubleValue()/1_000_000.0;lines.add(String.format(Locale.ROOT,"Файл %s: записей %d, ошибок %d, среднее %.2f мс, максимум %.2f мс, байт %s",m.get("file"),writes,failures,average,max,m.get("bytes")));}
        } catch(ReflectiveOperationException|RuntimeException ex){lines.add("Диагностика "+contract.getName()+": "+ex.getClass().getSimpleName());}
    }
}
