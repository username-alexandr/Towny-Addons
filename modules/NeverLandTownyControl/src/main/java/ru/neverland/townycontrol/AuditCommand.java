package ru.neverland.townycontrol;

import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;

/** Disk search/export runs off the server tick. Bukkit identity resolution runs on the main thread. */
public final class AuditCommand implements CommandExecutor,TabCompleter {
    private final JavaPlugin plugin;private final Semaphore jobs=new Semaphore(2);
    public AuditCommand(JavaPlugin plugin){this.plugin=plugin;var c=plugin.getCommand("nltaudit");c.setExecutor(this);c.setTabCompleter(this);}
    void page(AuditQuery query,int size,java.util.function.BiConsumer<AuditReader.Page,Exception> callback) {
        if(!jobs.tryAcquire())throw new IllegalStateException("Уже выполняются две проверки журнала; повторите позже");
        try { plugin.getServer().getScheduler().runTaskAsynchronously(plugin,()->{
            AuditReader.Page page=null;Exception failure=null;
            try{page=AuditReader.page(plugin.getDataFolder().toPath().getParent(),query,query.page(),size);}
            catch(Exception ex){failure=ex;plugin.getLogger().log(java.util.logging.Level.WARNING,"Чтение аудита для меню",ex);}
            finally{jobs.release();}
            var result=page;var error=failure;
            if(plugin.isEnabled())plugin.getServer().getScheduler().runTask(plugin,()->callback.accept(result,error));
        }); } catch(RuntimeException ex){jobs.release();throw ex;}
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!AdminAccess.has(sender,AdminAccess.AUDIT)){sender.sendMessage("§cНет прав на аудит.");return true;}
        try{
            String action=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
            if(action.equals("help")){sender.sendMessage("§b/nltaudit search|export [town=город|UUID] [player=ник|UUID] [from=UUID] [to=UUID] [category=категория] [kind=тип] [module=аддон] [outcome=результат] [id=UUID] [since=YYYY-MM-DD] [until=YYYY-MM-DD] [intercity=true] [text=строка] [page=1]");sender.sendMessage("§7/nltaudit status. Даты UTC; направления сделки — движение товара, money — её цена. BANK_LEG и BANK_TRANSFER — отдельные уровни свидетельств, их суммы нельзя складывать.");return true;}
            if(!Set.of("search","export","status").contains(action))throw new IllegalArgumentException("Действия: search, export, status, help");
            if(action.equals("export")&&!AdminAccess.has(sender,AdminAccess.EXPORT))throw new IllegalArgumentException("Нет права экспорта аудита");
            var parsed=AuditQuery.parse(Arrays.copyOfRange(args,1,args.length));var filters=new LinkedHashMap<>(parsed.filters());
            for(String k:List.of("town","player","from","to"))if(filters.containsKey(k)){String v=filters.get(k);try{filters.put(k,UUID.fromString(v).toString());}catch(IllegalArgumentException ex){if(k.equals("player")){var r=TownyAPI.getInstance().getResident(v);if(r==null)throw new IllegalArgumentException("Игрок не найден; для удалённого используйте UUID");filters.put(k,r.getUUID().toString());}else{var t=TownyAPI.getInstance().getTown(v);if(t==null)throw new IllegalArgumentException("Город не найден; для удалённого используйте UUID");filters.put(k,t.getUUID().toString());}}}
            var query=new AuditQuery(filters,parsed.page());if(!jobs.tryAcquire())throw new IllegalStateException("Уже выполняются две проверки журнала; повторите позже");
            sender.sendMessage("§7Читаю журнал…");
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin,()->{List<String> reply;try{reply=read(action,query);}catch(Exception ex){reply=List.of("§cАудит не прочитан полностью: "+ex.getMessage()+". Результат не выдан как полный.");plugin.getLogger().log(java.util.logging.Level.WARNING,"Чтение аудита",ex);}finally{jobs.release();}var messages=reply;if(plugin.isEnabled())plugin.getServer().getScheduler().runTask(plugin,()->{if(AdminAccess.has(sender,AdminAccess.AUDIT)&&(!action.equals("export")||AdminAccess.has(sender,AdminAccess.EXPORT)))messages.forEach(sender::sendMessage);});});
        }catch(Exception ex){sender.sendMessage("§c"+ex.getMessage());}return true;
    }
    private List<String> read(String action,AuditQuery query)throws Exception {
        Path root=plugin.getDataFolder().toPath().getParent();List<Path> dirs=AuditReader.directories(root);
        var order=Comparator.comparingLong(AuditRecord::at).thenComparing(AuditRecord::id);var latest=new PriorityQueue<AuditRecord>(order);long[] count={0},total={0};
        Path temporary=null,export=null;java.io.BufferedWriter writer=null;
        try{
            if(action.equals("export")){Path exports=plugin.getDataFolder().toPath().resolve("audit-exports");Files.createDirectories(exports);export=exports.resolve("audit-"+UUID.randomUUID()+".csv");temporary=export.resolveSibling(export.getFileName()+".tmp");writer=Files.newBufferedWriter(temporary,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);writer.write("schema,id,observed_at_ms,module,kind,outcome,operation,actor_type,actor_id,actor_name,actor_town,from_type,from_id,from_name,from_town,to_type,to_id,to_name,to_town,asset,quantity,money,details\n");}
            final var out=writer;
            for(Path dir:dirs)AuditJournal.scan(dir,r->{total[0]++;if(!query.test(r))return;count[0]++;if(out!=null){if(count[0]>100000)throw new IllegalArgumentException("Больше 100000 записей: сузьте период или фильтры");try{out.write(String.join(",",r.fields().stream().map(AuditJournal::csv).toList()));out.newLine();}catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}}else if(!action.equals("status")){latest.add(r);if(latest.size()>query.page()*10)latest.remove();}});
            if(writer!=null){writer.close();writer=null;Files.move(temporary,export,StandardCopyOption.ATOMIC_MOVE);return List.of("§aCSV: "+export+"; записей: "+count[0]+"; время UTC.");}
            if(action.equals("status"))return List.of("§aЖурналы прочитаны, контрольные суммы проверены: "+dirs.size()+"; записей: "+total[0]+".","§7Ведутся с установки обновления. Ошибки записи: ищите AUDIT GAP в консоли; нативные банковские операции записываются, пока Control включён.");
            var result=new ArrayList<String>();result.add("§bАудит: "+count[0]+" записей; страница "+query.page());var rows=latest.stream().sorted(order.reversed()).skip((query.page()-1L)*10).toList();for(var r:rows){String asset=r.asset().isEmpty()?"":r.quantity()+"×"+r.asset().split(":",2)[0];result.add("§f"+Instant.ofEpochMilli(r.at())+" §e"+r.kind()+"/"+r.outcome()+" §f"+clean(r.from().name())+" → "+clean(r.to().name())+" | "+asset+" | "+r.money()+" | actor="+clean(r.actor().name()));result.add("§7id="+r.id()+" operation="+r.operation()+" "+clean(r.details()));}return result;
        }finally{if(writer!=null)writer.close();if(temporary!=null)Files.deleteIfExists(temporary);}
    }
    private static String clean(String value){return value.replaceAll("[\\p{Cntrl}§]"," ").substring(0,Math.min(300,value.replaceAll("[\\p{Cntrl}§]"," ").length()));}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){if(!AdminAccess.has(sender,AdminAccess.AUDIT))return List.of();String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return (args.length<=1?List.of("search","export","status","help"):List.of("town=","player=","from=","to=","category=","kind=","module=","outcome=","id=","since=","until=","intercity=true","text=","page=")).stream().filter(s->s.startsWith(prefix)).filter(s->!s.equals("export")||AdminAccess.has(sender,AdminAccess.EXPORT)).toList();}
}
