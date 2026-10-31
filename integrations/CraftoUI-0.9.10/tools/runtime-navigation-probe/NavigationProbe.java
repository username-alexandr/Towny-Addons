package cc.crafto.test;
import cc.crafto.ui.*;
import com.google.gson.*;
import io.papermc.paper.threadedregions.scheduler.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import static cc.crafto.ui.Protocol.*;

/** TEST ONLY: simulated players, genuine Folia, Contracts, inventory and CraftoUI protocol. */
public final class NavigationProbe extends JavaPlugin {
    private JavaPlugin ui;private Actor actor,vanilla;private Object connection;private final List<String> checks=new ArrayList<>();
    private final Deque<Step> steps=new ArrayDeque<>();private int commands;private String consumedDirectory="";
    @FunctionalInterface interface Step{void run()throws Exception;}
    static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String name,Object v)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,v);}
    static Object call(Object o,String name,Object...a)throws Exception{for(var m:o.getClass().getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==a.length){m.setAccessible(true);return m.invoke(o,a);}throw new NoSuchMethodException(name);}
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks.add("PASS "+label);getLogger().info("PASS "+label);}
    @Override public void onEnable(){
        if(!Boolean.getBoolean("craftoui.navigationProbe")||!getServer().getIp().equals("127.0.0.1")||!Files.exists(Path.of("ALLOW_DISPOSABLE_NAVIGATION_TEST"))){getServer().getPluginManager().disablePlugin(this);return;}
        getServer().getGlobalRegionScheduler().runDelayed(this,t->{try{setup();next();}catch(Throwable e){finish(e);}},80);
    }
    void setup()throws Exception{
        ui=(JavaPlugin)getServer().getPluginManager().getPlugin("CraftoUI");check(ui!=null&&ui.isEnabled(),"CraftoUI starts on genuine Folia");
        check(getServer().getPluginManager().isPluginEnabled("NeverLandTownyContracts"),"genuine Contracts enabled");
        actor=new Actor("NavigationMod");connection=connect(actor,true);set(connection,"civic",true); // reproduces the old intercept condition
        vanilla=new Actor("NavigationVanilla");connect(vanilla,false);
        steps.add(()->call(ui,"openNow",connection,"hub"));
        steps.add(()->click("orders",false));
        steps.add(()->{board(actor);check(actor.commands.equals(List.of("orders")),"home button invokes exact /orders");check(actor.last(Kind.NATIVE_MENU)!=null,"Contracts board receives native UI presentation hint");check(actor.last(Kind.CIVIC)==null,"orders does not switch to civic picker");send(Kind.BACK,object("navigation",field(connection,"navigationToken")));});
        steps.add(()->{check(str(actor.last(Kind.OPEN).body().getAsJsonObject("screen"),"id","").equals("hub"),"Back returns from orders to originating home");check(actor.commands.size()==1,"Back does not replay /orders");call(ui,"openNow",connection,"economy");});
        steps.add(()->click("orders",false));
        steps.add(()->{board(actor);check(actor.commands.size()==2,"economy button invokes the same real board");actor.deny.add("neverland.orders.use");call(ui,"openNow",connection,"economy");});
        steps.add(()->{commands=actor.commands.size();click("orders",false);});
        steps.add(()->{check(actor.commands.size()==commands,"denied order permission blocks forged button packet");actor.deny.clear();click("orders",true);});
        steps.add(()->{check(actor.commands.size()==commands,"wrong session token cannot launch order");call(ui,"directory",connection);});
        steps.add(()->{
            var directory=actor.last(Kind.DIRECTORY).body();boolean orders=false,legacy=false;
            for(var entry:directory.getAsJsonArray("entries")){var e=entry.getAsJsonObject();if(str(e,"id","").equals("link:orders"))orders=str(e,"label","").equals("Личные заказы");if(str(e,"id","").equals("button:economy/treasuryplus"))legacy=true;}
            check(orders,"search exposes the same orders shortcut");
            // Treasury provider is absent on this minimal fixture: unavailable actions stay absent.
            check(!legacy,"search excludes unavailable optional plugin");
            consumedDirectory=str(directory,"token","");send(Kind.LAUNCH,object("token",consumedDirectory,"id","link:orders"));
        });
        steps.add(()->{board(actor);check(actor.commands.size()==commands+1,"search launches real /orders board");send(Kind.LAUNCH,object("token",consumedDirectory,"id","link:orders"));});
        steps.add(()->{check(actor.commands.size()==commands+1,"search launch token is consumed");var event=new PlayerCommandPreprocessEvent(actor.player,"/orders");getServer().getPluginManager().callEvent(event);check(!event.isCancelled(),"typed /orders is not intercepted with civic support");actor.player.performCommand("orders");});
        steps.add(()->{board(actor);var event=new PlayerCommandPreprocessEvent(vanilla.player,"/orders");getServer().getPluginManager().callEvent(event);check(!event.isCancelled(),"typed /orders stays available without mod");vanilla.player.performCommand("orders");});
        steps.add(()->{board(vanilla);check(vanilla.messages.isEmpty(),"client without handshake receives no custom payload");check(actor.scheduled>0,"navigation requests owning-player scheduler");finish(null);});
    }
    @SuppressWarnings("unchecked") Object connect(Actor a,boolean ready)throws Exception{
        call(ui,"join",new PlayerJoinEvent(a.player,Component.empty()));Object c=((Map<UUID,?>)field(ui,"connections")).get(a.id);
        for(String n:List.of("ready","nativeMenus","navigation","personal"))set(c,n,ready);return c;
    }
    void click(String component,boolean forged)throws Exception{Object s=field(connection,"session");send(Kind.ACTION,object("session",forged?"forged":field(s,"token"),"revision",field(s,"revision"),"sequence",((Long)field(s,"sequence"))+1,"component",component));}
    void send(Kind kind,JsonObject body)throws Exception{call(ui,"receive",actor.player,encode(kind,body));}
    void board(Actor a){check(a.top.getHolder()!=null&&a.top.getHolder().getClass().getName().equals("ru.neverland.mintcontracts.orders.PublicOrders$Board"),a.name+" opened genuine PublicOrders.Board");}
    void next(){if(steps.isEmpty())return;getServer().getGlobalRegionScheduler().runDelayed(this,t->{try{steps.remove().run();next();}catch(Throwable e){finish(e);}},6);}
    void finish(Throwable e){try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("checks.txt"),String.join("\n",checks)+"\n");if(e==null)Files.writeString(getDataFolder().toPath().resolve("passed.txt"),"PASS "+checks.size()+" assertions\n");else{getLogger().log(java.util.logging.Level.SEVERE,"NAVIGATION_PROBE_FAILED",e);Files.writeString(getDataFolder().toPath().resolve("failed.txt"),e.toString());}}catch(Exception failure){failure.printStackTrace();}finally{getServer().shutdown();}}
    final class Actor {
        final String name;final UUID id;Player player;InventoryView view;Inventory top=Bukkit.createInventory(null,9);final Inventory bottom=Bukkit.createInventory(null,36);
        final Set<String> deny=new HashSet<>();final List<String> commands=new ArrayList<>();final List<Protocol.Message> messages=new ArrayList<>();int scheduled;
        @SuppressWarnings("unchecked") Actor(String name){this.name=name;id=UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            EntityScheduler scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class<?>[]{EntityScheduler.class},(proxy,m,a)->{
                scheduled++;if(m.getName().equals("run"))return Bukkit.getGlobalRegionScheduler().run((org.bukkit.plugin.Plugin)a[0],(Consumer<ScheduledTask>)a[1]);
                if(m.getName().equals("runDelayed"))return Bukkit.getGlobalRegionScheduler().runDelayed((org.bukkit.plugin.Plugin)a[0],(Consumer<ScheduledTask>)a[1],(Long)a[3]);
                if(m.getName().equals("runAtFixedRate"))return Bukkit.getGlobalRegionScheduler().runAtFixedRate((org.bukkit.plugin.Plugin)a[0],(Consumer<ScheduledTask>)a[1],(Long)a[3],(Long)a[4]);
                throw new UnsupportedOperationException(m.getName());
            });
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,m,a)->switch(m.getName()){
                case "getUniqueId"->id;case "getName"->name;case "getServer"->Bukkit.getServer();case "isOnline"->true;case "isOp","isDead"->false;
                case "hasPermission"->!deny.contains(a[0].toString())&&!a[0].toString().startsWith("craftoui.admin");case "getScheduler"->scheduler;
                case "getOpenInventory"->view;case "getItemOnCursor"->new ItemStack(Material.AIR);case "getWorld"->Bukkit.getWorlds().getFirst();case "getLocation"->new Location(Bukkit.getWorlds().getFirst(),0,64,0);
                case "getGameMode"->GameMode.SURVIVAL;case "getLevel"->0;case "getExp"->0f;case "getHealth"->20d;case "getFoodLevel"->20;
                case "openInventory"->{top=(Inventory)a[0];Bukkit.getPluginManager().callEvent(new InventoryOpenEvent(view));yield view;}
                case "closeInventory"->{Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(view));top=Bukkit.createInventory(null,9);yield null;}
                case "performCommand"->{String command=(String)a[0];commands.add(command);String[] parts=command.split(" ");var cmd=Bukkit.getPluginCommand(parts[0]);if(cmd==null)throw new IllegalArgumentException(command);yield cmd.execute(player,parts[0],Arrays.copyOfRange(parts,1,parts.length));}
                case "sendPluginMessage"->{if(CHANNEL.equals(a[1]))messages.add(decode((byte[])a[2]));yield null;}
                case "sendMessage"->null;case "hashCode"->id.hashCode();case "equals"->proxy==a[0];case "toString"->"Simulated navigation player "+name;default->null;
            });
            view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(proxy,m,a)->switch(m.getName()){
                case "getTopInventory"->top;case "getBottomInventory"->bottom;case "getPlayer"->player;case "getType"->InventoryType.CHEST;
                case "getTitle","getOriginalTitle"->"Navigation fixture";case "title"->Component.text("Navigation fixture");default->null;
            });
        }
        Protocol.Message last(Kind kind){for(int i=messages.size()-1;i>=0;i--)if(messages.get(i).kind()==kind)return messages.get(i);return null;}
    }
}
