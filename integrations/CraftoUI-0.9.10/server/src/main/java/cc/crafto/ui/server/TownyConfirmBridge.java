package cc.crafto.ui.server;
import org.bukkit.command.*;import org.bukkit.entity.Player;import org.bukkit.event.*;
import java.util.*;import java.util.concurrent.*;
import static cc.crafto.ui.server.PublicMethods.*;
/** Public Towny confirmation events preserve the exact original transaction and cancellable checks. */
final class TownyConfirmBridge implements Listener {
    record Pending(String token,Object confirmation,long until){}
    private final CraftoUIPlugin plugin;private final TownyCivic towny;
    private final Map<UUID,Pending> pending=new ConcurrentHashMap<>();private final Map<UUID,Object> executing=new ConcurrentHashMap<>();
    TownyConfirmBridge(CraftoUIPlugin plugin,TownyCivic towny){this.plugin=plugin;this.towny=towny;}
    @SuppressWarnings("unchecked") void start(){
        var addon=towny.addon("Towny");if(addon==null)return;
        try{for(String type:List.of("Send","Cancel","Confirm")){
            Class<? extends Event> event=(Class<? extends Event>)Class.forName("com.palmergames.bukkit.towny.confirmations.event.Confirmation"+type+"Event",true,addon.getClass().getClassLoader());
            plugin.getServer().getPluginManager().registerEvent(event,this,EventPriority.HIGHEST,(listener,e)->{try{observe(type,e);}catch(Exception ex){plugin.getLogger().warning("Towny confirmation unavailable: "+ex.getClass().getSimpleName());}},plugin,true);
        }}catch(Exception e){plugin.getLogger().warning("Towny confirmation events unavailable");}
    }
    private void observe(String type,Event e)throws Exception{
        Object sender=call(e,"getSender");if(!(sender instanceof Player p))return;Object confirmation=call(e,"getConfirmation");UUID id=p.getUniqueId();
        if(type.equals("Confirm")&&executing.containsKey(id)&&executing.get(id)!=confirmation){if(e instanceof Cancellable cancel)cancel.setCancelled(true);return;}
        if(type.equals("Send")){int duration=((Number)call(confirmation,"getDuration")).intValue();Pending value=new Pending(UUID.randomUUID().toString(),confirmation,System.nanoTime()+TimeUnit.SECONDS.toNanos(Math.max(1,duration)));pending.put(id,value);
            p.getScheduler().run(plugin,task->plugin.resumeTownCreation(p,value),null);
        }
        else pending.computeIfPresent(id,(key,old)->old.confirmation()==confirmation?null:old);
    }
    Pending get(Player p){Pending value=pending.get(p.getUniqueId());if(value!=null&&System.nanoTime()>value.until()){pending.remove(p.getUniqueId(),value);return null;}return value;}
    String title(Player p,Pending value)throws Exception{return CivicData.clean(call(call(value.confirmation(),"getTitle"),"forLocale",CommandSender.class,p).toString());}
    void execute(Player p,String token,boolean accept)throws Exception{
        Pending value=get(p);if(value==null||!value.token().equals(token))throw new IllegalArgumentException("Подтверждение Towny изменилось или истекло");
        String name=call(value.confirmation(),accept?"getConfirmCommand":"getCancelCommand").toString().replaceFirst("^/","");
        if(!name.matches("[a-zA-Z0-9_-]{1,40}"))throw new IllegalArgumentException("Это подтверждение доступно через исходную команду Towny");
        Command command=plugin.getServer().getCommandMap().getCommand(name);
        String expected="com.palmergames.bukkit.towny.command.commandobjects."+(accept?"ConfirmCommand":"CancelCommand");
        if(command==null||!command.getClass().getName().equals(expected)||!command.testPermissionSilent(p))throw new IllegalArgumentException("Команда подтверждения Towny недоступна");
        var event=new org.bukkit.event.player.PlayerCommandPreprocessEvent(p,"/"+name);plugin.getServer().getPluginManager().callEvent(event);
        if(event.isCancelled()||!event.getMessage().equals("/"+name)||get(p)!=value)throw new IllegalArgumentException("Подтверждение изменено или отменено обработчиком сервера");
        executing.put(p.getUniqueId(),value.confirmation());try{command.execute(p,name,new String[0]);}finally{executing.remove(p.getUniqueId());}
    }
    void departed(UUID player){pending.remove(player);executing.remove(player);}
}
