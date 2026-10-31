package cc.crafto.ui.server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.concurrent.*;
import static cc.crafto.ui.server.PublicMethods.*;
final class CityDataBridge {
    record Access(String townId,String name,int claimed,int limit,int remaining,int residents,boolean builds,boolean contracts,String status){}
    record Result(CitySnapshot current,List<String> summary){}
    private final CraftoUIPlugin plugin;private final AddonFiles files=new AddonFiles();private final CityBuildReader builds;private final CitySummaryStore summaries;private long warned;
    CityDataBridge(CraftoUIPlugin plugin){this.plugin=plugin;builds=new CityBuildReader(plugin,files);summaries=new CitySummaryStore(plugin.getDataFolder().toPath().resolve("activity"));}
    private Plugin addon(String name){Plugin p=plugin.getServer().getPluginManager().getPlugin(name);return p!=null&&p.isEnabled()?p:null;}
    Access capture(Player player){
        boolean canBuild=player.hasPermission("neverlandtownybuilds.use"),canContracts=player.hasPermission("mintcontracts.use");
        // Contracts' own public command gate is checked again when launched; this only controls display.

        Plugin towny=addon("Towny");if(towny==null)return new Access("","Город недоступен",0,0,0,0,false,false,"Towny не установлен или выключен.");
        try{
            Object api=staticCall(towny.getClass().getClassLoader(),"com.palmergames.bukkit.towny.TownyAPI","getInstance");
            Object resident=call(api,"getResident",UUID.class,player.getUniqueId()),town=resident==null?null:call(resident,"getTownOrNull");
            if(town==null)return new Access("","Нет города",0,0,0,0,false,canContracts,"Вы пока не состоите в городе.");
            int limit=((Number)call(town,"getMaxTownBlocks")).intValue();boolean unlimited=(boolean)call(town,"hasUnlimitedClaims")||limit==Integer.MAX_VALUE||limit<0;
            return new Access(call(town,"getUUID").toString(),CitySnapshot.clean(call(town,"getName").toString()),((Number)call(town,"getNumTownBlocks")).intValue(),unlimited?-1:limit,unlimited?-1:Math.max(0,((Number)call(town,"availableTownBlocks")).intValue()),((Number)call(town,"getNumResidents")).intValue(),canBuild,canContracts,"Лимит территории не отменяет стоимость захвата и правила Towny.");
        }catch(Exception e){return new Access("","Город недоступен",0,0,0,0,false,false,"Не удалось получить данные Towny.");}
    }
    CompletableFuture<Result> load(UUID player,Access access,boolean compare){
        var result=new CompletableFuture<Result>();
        plugin.getServer().getAsyncScheduler().runNow(plugin,task->{
            try{
                String balance=balance(access);List<CitySnapshot.Row> contracts=new ArrayList<>();boolean contractKnown=false;double pending=0;
                Plugin addon=addon("NeverLandTownyContracts");
                try{if(access.contracts()&&addon!=null){
                    var yaml=files.read(addon.getDataFolder().toPath().resolve("contract-data.yml"));
                    var snapshot=CityContractReader.read(yaml,access.townId(),player);
                    contracts.addAll(snapshot.rows());pending=snapshot.pending();contractKnown=true;
                }else contracts.add(new CitySnapshot.Row(access.contracts()?"Аддон городских заданий сейчас недоступен.":"Нет доступа к городским заданиям."));
                }catch(Exception e){contracts.clear();contracts.add(new CitySnapshot.Row("Не удалось получить данные городских заданий."));contractKnown=false;pending=0;warnContracts(addon,e);}
                var rows=List.copyOf(contracts);final boolean known=contractKnown;final double reward=pending;
                CompletableFuture<CityBuildReader.Result> buildResult=access.townId().isEmpty()||!access.builds()?CompletableFuture.completedFuture(CityBuildReader.unavailable("Постройки недоступны.")):builds.read(addon("NeverLandTownyBuilds"),UUID.fromString(access.townId()));
                buildResult.whenComplete((b,error)->{
                    if(error!=null){result.completeExceptionally(error);return;}
                    // Completion may originate from a region scan; persistence always hops back to async.
                    plugin.getServer().getAsyncScheduler().runNow(plugin,t->{try{
                        CitySnapshot current=new CitySnapshot(access.townId(),access.name(),access.status(),balance,access.claimed(),access.limit(),access.remaining(),access.residents(),b.builds(),b.resources(),rows,b.levels(),b.names(),b.known(),known,reward,System.currentTimeMillis());
                        List<String> summary=List.of();
                        if(compare){try{summary=CitySnapshot.changes(summaries.read(player),current);}catch(Exception e){summary=List.of("Предыдущая сводка недоступна. Новая история начинается сейчас.");}}
                        summaries.write(player,current);result.complete(new Result(current,summary));
                    }catch(Exception e){result.completeExceptionally(e);}});
                });
            }catch(Exception e){result.completeExceptionally(e);}
        });return result;
    }
    private synchronized void warnContracts(Plugin addon,Exception error){
        long now=System.nanoTime();if(warned!=0&&now-warned<TimeUnit.MINUTES.toNanos(1))return;warned=now;
        plugin.getLogger().warning("City contracts snapshot unavailable ("+(addon==null?"missing addon":addon.getDescription().getVersion())+"): "+error.getClass().getSimpleName()+": "+error.getMessage());
    }
    private String balance(Access access){
        if(access.townId().isEmpty())return "—";
        try{
            Plugin towny=addon("Towny");if(towny==null)return "Недоступно";ClassLoader loader=towny.getClass().getClassLoader();
            if(!(boolean)staticCall(loader,"com.palmergames.bukkit.towny.TownyEconomyHandler","isActive"))return "Экономика недоступна";
            if(!(boolean)staticCall(loader,"com.palmergames.bukkit.towny.TownySettings","isEconomyAsync"))return "Баланс доступен в меню казны";
            Object api=staticCall(loader,"com.palmergames.bukkit.towny.TownyAPI","getInstance"),town=call(api,"getTown",UUID.class,UUID.fromString(access.townId()));
            Object account=call(town,"getAccount");
            boolean async=(boolean)staticCall(loader,"com.palmergames.bukkit.towny.TownySettings","isEconomyAsync");
            double value=((Number)(async?call(account,"getHoldingBalance"):call(account,"getCachedBalance",boolean.class,false))).doubleValue();
            return Double.isFinite(value)?String.format(Locale.ROOT,"%.2f%s",value,async?"":" (кэш Towny)"):"Недоступно";
        }catch(Exception e){return "Баланс недоступен";}
    }
    void departed(UUID player,CitySnapshot state){plugin.getServer().getAsyncScheduler().runNow(plugin,t->{try{summaries.write(player,state);}catch(Exception e){plugin.getLogger().warning("Cannot save city activity snapshot");}});}
}
