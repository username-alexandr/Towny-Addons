package cc.crafto.test;
import cc.crafto.ui.*;
import com.google.gson.*;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.*;
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

/** Disposable test only. Real Towny membership and CraftoUI; explicit player/scheduler doubles. */
public final class CityDashboardProbe extends JavaPlugin {
    JavaPlugin ui;Actor actor;Object connection;Resident resident;Town town;JsonObject directory;String savedToken;
    final List<String> checks=new ArrayList<>();final Deque<Step> steps=new ArrayDeque<>();
    @FunctionalInterface interface Step{void run()throws Exception;}
    static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String n,Object v)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static Object call(Object o,String n,Object...a)throws Exception{for(var m:o.getClass().getDeclaredMethods())if(m.getName().equals(n)&&m.getParameterCount()==a.length){m.setAccessible(true);return m.invoke(o,a);}throw new NoSuchMethodException(n);}
    void check(boolean b,String name){if(!b)throw new AssertionError(name);checks.add("PASS "+name);getLogger().info("PASS "+name);}
    @Override public void onEnable(){
        if(!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_CITY_UI_TEST"))||!getServer().getIp().equals("127.0.0.1")){getServer().getPluginManager().disablePlugin(this);return;}
        getServer().getGlobalRegionScheduler().runDelayed(this,t->{try{setup();next();}catch(Throwable e){finish(e);}},60);
    }
    @SuppressWarnings("unchecked") void setup()throws Exception{
        ui=(JavaPlugin)Bukkit.getPluginManager().getPlugin("CraftoUI");check(ui.isEnabled()&&ui.getDescription().getVersion().equals("0.8.5"),"CraftoUI 0.8.5 starts on real Folia");
        actor=new Actor("TownUiTest"+Integer.toHexString((int)System.nanoTime()));call(ui,"join",new PlayerJoinEvent(actor.player,Component.empty()));connection=((Map<UUID,?>)field(ui,"connections")).get(actor.id);
        for(String n:List.of("ready","personal","navigation","cityScreen","civic"))set(connection,n,true);
        // Inject an alias in the live configuration to reproduce repeated destination buttons.
        var catalog=field(ui,"catalog");var menus=(Map<String,JsonObject>)call(catalog,"menus");var hub=menus.get("hub");var duplicate=MenuSchema.find(hub,"town").deepCopy();duplicate.addProperty("id","town-duplicate");MenuSchema.find(hub,"sections").getAsJsonArray("children").add(duplicate);
        steps.add(()->{call(ui,"openNow",connection,"hub");var body=actor.last(Kind.OPEN).body();check(!bool(body,"townMember",true),"unregistered player is townless even with all city permissions");check(count(body.getAsJsonObject("screen").getAsJsonArray("components"),"town")==0,"townless home hides every city alias");check(MenuSchema.find(body.getAsJsonObject("screen"),"society")==null,"townless home hides residents and government");check(MenuSchema.find(body.getAsJsonObject("screen"),"orders")!=null,"personal orders remain visible without a town");check(MenuSchema.find(body.getAsJsonObject("screen"),"economy")!=null,"work and trading remain visible");
            Object session=field(connection,"session");call(ui,"openNow",connection,"town");check(session==field(connection,"session"),"direct private menu request is denied without a town");directory();check(!hasEntry("menu:town")&&!hasEntry("link:warehouse"),"townless search omits city and warehouse");});
        steps.add(()->{var universe=TownyUniverse.getInstance();UUID tid=UUID.randomUUID();universe.newTownInternal("UiTown"+Integer.toHexString((int)System.nanoTime()),tid);town=universe.getTown(tid);var mayor=universe.getDataSource().newResident("UiMayor"+Integer.toHexString((int)System.nanoTime()),UUID.randomUUID());mayor.setTown(town);town.setMayor(mayor);resident=universe.getDataSource().newResident(actor.name,actor.id);resident.setTown(town);
            call(ui,"update",connection,field(connection,"session"));var patch=actor.last(Kind.PATCH).body();check(bool(patch,"townMember",false),"joining Towny updates open menu without reconnect");check(count(patch.getAsJsonArray("components"),"town")==1,"city destination appears exactly once despite duplicate configuration");check(count(patch.getAsJsonArray("components"),"society")==1,"government destination appears once");
            directory();check(hasEntry("menu:town")&&hasEntry("link:warehouse"),"joining enables city search entries");long warehouse=0;for(var row:directory.getAsJsonArray("entries")){var e=row.getAsJsonObject();if(str(e,"label","").equals("Склад города")){warehouse++;check(e.getAsJsonArray("aliases").contains(new JsonPrimitive("button:town/warehouse")),"warehouse favorite alias is retained");}}check(warehouse==1,"quick link and section button yield one warehouse entry");savedToken=str(directory,"token","");directory();check(savedToken.equals(str(directory,"token","")),"unchanged search refresh preserves in-flight click token");});
        steps.add(()->{resident.removeTown();send(Kind.LAUNCH,object("token",savedToken,"id","link:warehouse"));check(actor.commands.isEmpty(),"previous search token cannot execute warehouse after leaving town");Object s=field(connection,"session");send(Kind.ACTION,object("session",field(s,"token"),"revision",field(s,"revision"),"sequence",((Long)field(s,"sequence"))+1,"component","town"));check(field(connection,"session")==s,"forged hidden city button is denied after leaving");
            call(ui,"update",connection,s);var patch=actor.last(Kind.PATCH).body();check(!bool(patch,"townMember",true)&&count(patch.getAsJsonArray("components"),"town")==0,"leaving removes city buttons from current screen");
            for(int i=0;i<3;i++){resident.setTown(town);call(ui,"update",connection,s);check(count(actor.last(Kind.PATCH).body().getAsJsonArray("components"),"town")==1,"repeat join "+i+" adds no duplicates");resident.removeTown();call(ui,"update",connection,s);check(count(actor.last(Kind.PATCH).body().getAsJsonArray("components"),"town")==0,"repeat leave "+i+" removes city aliases");}
            directory();check(!hasEntry("menu:town"),"search refresh removes city after leaving");send(Kind.CIVIC,object("op","open","route","city","request",41,"size",6));});
        steps.add(()->{var civic=actor.last(Kind.CIVIC).body();check(str(civic,"op","").equals("view")&&str(civic,"route","").equals("inbox"),"townless civic city request opens invitations instead of city controls");check(!bool(civic,"townMember",true),"civic response supplies current townless state");check(actor.scheduled>0,"UI requests player scheduler");});
        steps.add(()->{resident.setTown(town);send(Kind.CIVIC,object("op","open","route","city","cityDashboard",true,"request",50,"size",3));});
        steps.add(()->{var v=cv();check(v.has("city"),"new client receives structured city dashboard");check(v.getAsJsonObject("city").getAsJsonArray("metrics").get(1).getAsJsonObject().get("value").getAsInt()==2,"dashboard residents come from real Towny");check(v.getAsJsonObject("city").getAsJsonArray("shortcuts").size()==7,"seven distinct server-owned city shortcuts");check(!bool(v,"canBack",true),"root city has no fake back history");
            var links=v.getAsJsonObject("city").getAsJsonArray("shortcuts");check(str(links.get(5).getAsJsonObject(),"action","x").isEmpty(),"missing Chronicles shortcut cannot execute");check(!str(links.get(5).getAsJsonObject(),"reason","").isEmpty(),"missing history explains unavailability");request("act",object("action",str(links.get(3).getAsJsonObject(),"action","")));});
        steps.add(()->{var v=cv();check(str(v,"route","").equals("map")&&v.has("map"),"map shortcut opens real Towny map");check(bool(v,"canBack",false),"map carries a previous-screen back target");request("back",new JsonObject());});
        steps.add(()->{check(str(cv(),"route","").equals("city"),"back from map returns to city dashboard");request("section",object("section","builds"));});
        steps.add(()->{check(str(cv().getAsJsonObject("city"),"section","").equals("builds"),"building section is selected on server");request("act",object("action",str(cv().getAsJsonObject("city").getAsJsonArray("shortcuts").get(6).getAsJsonObject(),"action","")));});
        steps.add(()->{check(str(cv(),"route","").equals("inbox")&&bool(cv(),"canBack",false),"invitations retain return to city");request("back",new JsonObject());});
        steps.add(()->{check(str(cv().getAsJsonObject("city"),"section","").equals("builds"),"back preserves selected city section");request("section",object("section","untrusted"));});
        steps.add(()->{check(str(cv(),"op","").equals("error"),"unknown section is rejected");send(Kind.CIVIC,object("op","open","route","city","request",90,"size",3));});
        steps.add(()->{check(!cv().has("city")&&str(cv(),"route","").equals("city"),"old client retains legacy city view");send(Kind.CIVIC,object("op","open","route","city","cityDashboard",true,"request",91,"size",3));});
        steps.add(()->{resident.removeTown();request("act",object("action","c3"));});
        steps.add(()->{check(str(cv(),"route","").equals("inbox")&&!cv().has("city"),"leaving city blocks stale dashboard action and hides city data");finish(null);});
    }
    int requestNumber=60;
    JsonObject cv(){return actor.last(Kind.CIVIC).body();}
    void request(String op,JsonObject b)throws Exception{b.addProperty("op",op);b.addProperty("token",str(cv(),"token",""));b.addProperty("request",++requestNumber);b.addProperty("size",3);send(Kind.CIVIC,b);}
    void directory()throws Exception{set(connection,"directoryRequestAt",0L);call(ui,"directory",connection);directory=actor.last(Kind.DIRECTORY).body();}
    boolean hasEntry(String id){for(var row:directory.getAsJsonArray("entries"))if(str(row.getAsJsonObject(),"id","").equals(id))return true;return false;}
    int count(JsonArray nodes,String id){int count=0;for(var value:nodes){var n=value.getAsJsonObject();if(str(n,"id","").equals(id)||id.equals("town")&&str(n,"id","").equals("town-duplicate"))count++;if(n.has("children"))count+=count(n.getAsJsonArray("children"),id);}return count;}
    void send(Kind kind,JsonObject body)throws Exception{call(ui,"receive",actor.player,encode(kind,body));}
    void next(){if(!steps.isEmpty())getServer().getGlobalRegionScheduler().runDelayed(this,t->{try{steps.remove().run();next();}catch(Throwable e){finish(e);}},20);}
    void finish(Throwable e){try{Files.createDirectories(getDataFolder().toPath());Files.write(getDataFolder().toPath().resolve("checks.txt"),checks);if(e==null)Files.writeString(getDataFolder().toPath().resolve("passed.txt"),"PASS "+checks.size()+" checks\n");else{var trace=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(trace));Files.writeString(getDataFolder().toPath().resolve("failed.txt"),trace.toString());getLogger().log(java.util.logging.Level.SEVERE,"TOWN_UI_FAILED",e);}}catch(Exception failure){failure.printStackTrace();}finally{getServer().shutdown();}}
    final class Actor {
        final String name;final UUID id;Player player;InventoryView view;PlayerInventory inventory;Inventory top=Bukkit.createInventory(null,9);final Inventory bottom=Bukkit.createInventory(null,36);
        final Set<String> deny=new HashSet<>();final List<String> commands=new ArrayList<>();final List<Protocol.Message> messages=new ArrayList<>();int scheduled;
        @SuppressWarnings("unchecked") Actor(String name){this.name=name;id=UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            EntityScheduler scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class<?>[]{EntityScheduler.class},(proxy,m,a)->{
                scheduled++;if(m.getName().equals("run"))return Bukkit.getGlobalRegionScheduler().run((org.bukkit.plugin.Plugin)a[0],(Consumer<ScheduledTask>)a[1]);
                if(m.getName().equals("runDelayed"))return Bukkit.getGlobalRegionScheduler().runDelayed((org.bukkit.plugin.Plugin)a[0],(Consumer<ScheduledTask>)a[1],(Long)a[3]);
                if(m.getName().equals("runAtFixedRate"))return Bukkit.getGlobalRegionScheduler().runAtFixedRate((org.bukkit.plugin.Plugin)a[0],(Consumer<ScheduledTask>)a[1],(Long)a[3],(Long)a[4]);
                throw new UnsupportedOperationException(m.getName());
            });
            inventory=(PlayerInventory)Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),new Class<?>[]{PlayerInventory.class},(proxy,m,a)->switch(m.getName()){
                case "getContents","getStorageContents"->new ItemStack[36];case "getItemInMainHand","getItemInOffHand"->new ItemStack(Material.AIR);case "getHeldItemSlot"->0;default->null;});
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,m,a)->switch(m.getName()){
                case "getUniqueId"->id;case "getName"->name;case "getServer"->Bukkit.getServer();case "isOnline"->true;case "isOp","isDead"->false;
                case "hasPermission"->!deny.contains(a[0].toString())&&!a[0].toString().startsWith("craftoui.admin");case "getScheduler"->scheduler;
                case "getOpenInventory"->view;case "getItemOnCursor"->new ItemStack(Material.AIR);case "getWorld"->Bukkit.getWorlds().getFirst();case "getLocation"->new Location(Bukkit.getWorlds().getFirst(),0,64,0);
                case "getInventory"->inventory;case "getGameMode"->GameMode.SURVIVAL;case "getLevel"->0;case "getExp"->0f;case "getHealth"->20d;case "getFoodLevel"->20;
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
