package cc.crafto.ui.server;

import org.bukkit.event.*;
import org.bukkit.event.server.*;
import org.bukkit.plugin.Plugin;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One bounded async read for all opted-in clients, never one disk read per tick/player. */
final class ContractHudBridge implements Listener {
    private final CraftoUIPlugin plugin;
    private volatile Path source;
    private volatile String addonVersion="";
    record Status(boolean ready,String message) {}
    private volatile Status status=new Status(false,"Контракты: NeverLandTownyContracts не установлен или выключен.");
    private final AtomicBoolean busy=new AtomicBoolean();
    private long warned;
    ContractHudBridge(CraftoUIPlugin plugin){
        this.plugin=plugin;
        bind(plugin.getServer().getPluginManager().getPlugin("NeverLandTownyContracts"));
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin,t->poll(),1,2,TimeUnit.SECONDS);
    }
    private void bind(Plugin addon){
        if(addon==null||!addon.isEnabled())return;
        if(!"NeverLandTownyContracts".equals(addon.getName()))return;
        String version=addon.getDescription().getVersion();
        addonVersion=version;
        // Compatibility is validated by the persisted schema, not the release number.
        status=new Status(false,"Контракты "+version+": ожидается чтение заданий.");
        source=addon.getDataFolder().toPath();
        plugin.getLogger().info("Contract HUD adapter connected: NeverLandTownyContracts "+version);
    }
    Status status(){return status;}
    @EventHandler public void enabled(PluginEnableEvent e){bind(e.getPlugin());}
    @EventHandler public void disabled(PluginDisableEvent e){if("NeverLandTownyContracts".equals(e.getPlugin().getName())){source=null;status=new Status(false,"Контракты: NeverLandTownyContracts выключен.");}}
    private void poll(){
        if(!busy.compareAndSet(false,true))return;
        try{
            Set<UUID> audience=plugin.contractHudAudience();if(audience.isEmpty())return;
            Path captured=source;
            Map<UUID,List<PublicOrderReader.Order>> state=captured==null?Map.of():PublicOrderReader.read(captured,audience);
            if(!Objects.equals(source,captured))state=Map.of();
            else if(captured!=null)status=new Status(true,"Контракты "+addonVersion+": данные читаются.");
            plugin.contractHudUpdate(state);
        }catch(Exception e){
            status=new Status(false,"Контракты: не удалось прочитать задания; подробности в журнале сервера.");
            plugin.contractHudUpdate(Map.of());
            long now=System.nanoTime();if(warned==0||now-warned>TimeUnit.MINUTES.toNanos(1)){warned=now;plugin.getLogger().warning("Contract HUD snapshot unavailable: "+e.getClass().getSimpleName()+": "+e.getMessage());}
        }finally{busy.set(false);}
    }
}
