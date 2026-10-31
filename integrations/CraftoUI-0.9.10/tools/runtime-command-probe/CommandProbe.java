package cc.crafto.test;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.*;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.townycompanies.*;
import ru.neverland.townycouncil.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static cc.crafto.ui.Protocol.*;
import static ru.neverland.townycompanies.CompanyData.*;

/** TEST ONLY. Never distributed in release/; requires an isolated loopback fixture. */
public final class CommandProbe extends JavaPlugin implements Listener {
    private boolean block;private int checks;private final List<String> report=new ArrayList<>();
    private static UUID id(String name){return UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static Object method(Object o,String name,Object...args)throws Exception{for(var m:o.getClass().getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(o,args);}throw new NoSuchMethodException(name);}
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;report.add("PASS "+label);getLogger().info("PASS "+label);}
    @Override public void onEnable(){
        if(!Files.isRegularFile(Path.of("ALLOW_CRAFTO_UI_DISPOSABLE_TEST"))||!getServer().getIp().equals("127.0.0.1")){getServer().getPluginManager().disablePlugin(this);return;}
        getServer().getPluginManager().registerEvents(this,this);
        getCommand("cuiprobe").setExecutor((sender,command,label,args)->{if(sender instanceof Player p&&p.isOp())p.getScheduler().run(this,t->{try{switch(args.length==0?"check":args[0]){case "setup"->setup(p);case "security"->security(p);case "joined"->{var c=companies().own(p);check(c.members().get(id("UiInvite"))==Role.WORKER&&!c.invitations().containsKey(id("UiInvite")),"invitation accepted through UI and native confirmation");}default->verify(p);}Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("checks.txt"),String.join("\n",report)+"\n");p.sendMessage("UI PROBE PASS "+checks);}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"UI_PROBE_FAILED",e);p.sendMessage("UI PROBE FAILED: "+e);}},null);return true;});
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void intercept(PlayerCommandPreprocessEvent e){if(block&&e.getMessage().startsWith("/townycouncil"))e.setCancelled(true);}
    private CompanyService companies()throws Exception{return (CompanyService)field(getServer().getPluginManager().getPlugin("NeverLandTownyCompanies"),"service");}
    private void setup(Player p)throws Exception{
        var u=TownyUniverse.getInstance();Resident actor=TownyAPI.getInstance().getResident(p.getUniqueId());Town town=actor.getTownOrNull();
        if(town==null){UUID townId=id("UiActionsTown");u.newTownInternal("UiActionsTown",townId);town=TownyAPI.getInstance().getTown(townId);actor.setTown(town);town.setMayor(actor);actor.save();
            var pos=p.getLocation();var claim=new TownBlock(pos.getBlockX()>>4,pos.getBlockZ()>>4,u.getWorld(p.getWorld().getName()));u.addTownBlock(claim);claim.setTown(town);town.setHomeBlock(claim);claim.save();town.setSpawn(pos);town.save();}
        for(String name:List.of("UiInvite","UiMinister","UiWorker")){Resident r=TownyAPI.getInstance().getResident(id(name));if(r==null)r=u.getDataSource().newResident(name,id(name));if(r.getTownOrNull()==null)r.setTown(town);r.save();}
        var citizens=getServer().getPluginManager().getPlugin("NeverLandTownyCitizens");if(citizens!=null&&citizens.isEnabled()){
            Object service=method(citizens,"citizens");Class<?> type=Class.forName("ru.neverland.townycitizens.model.CitizenshipStatus",true,citizens.getClass().getClassLoader());
            @SuppressWarnings({"unchecked","rawtypes"})Object status=Enum.valueOf((Class)type,"HONORARY");
            for(String name:List.of("UiInvite","UiMinister","UiWorker"))method(service,"assign",getServer().getConsoleSender(),town,id(name),status,0,"Disposable UI test");
        }
        var service=companies();if(service.ledger.membership(p.getUniqueId())==null)service.create(p,CompanyData.Kind.SHOP,"UI Test Company");
        Company c=service.own(p);var members=new LinkedHashMap<>(c.members());members.put(id("UiWorker"),Role.WORKER);service.ledger.put(c.team(c.owner(),members,c.invitations(),c.successor(),c.successorUntil()));
        check(true,"fixture town, offline residents and company created on player's scheduler");getLogger().info("FIXTURE_COMPANY "+c.id());p.sendMessage("Company ID: "+c.id());
    }
    private void verify(Player p)throws Exception{
        var c=companies().own(p);check(c.invitations().containsKey(id("UiInvite")),"custom UI company invitation persisted");
        check(c.members().get(id("UiWorker"))==Role.MANAGER,"custom UI company role persisted");
        var council=((NeverLandTownyCouncil)getServer().getPluginManager().getPlugin("NeverLandTownyCouncil")).council();
        check(id("UiMinister").equals(council.holders(c.town()).get("economy")),"custom UI minister appointment persisted");
    }
    private void security(Player p)throws Exception{
        Object ui=getServer().getPluginManager().getPlugin("CraftoUI"),work=field(ui,"commandWorkbench"),catalog=method(field(ui,"catalog"),"commands");
        Class<?> cls=Class.forName("cc.crafto.ui.server.CommandWorkbench$State",true,ui.getClass().getClassLoader());var constructor=cls.getDeclaredConstructor();constructor.setAccessible(true);Object state=constructor.newInstance();
        var replies=new ArrayList<JsonObject>();Consumer<JsonObject> send=replies::add;
        class Wire {JsonObject call(JsonObject body)throws Exception{replies.clear();method(work,"handle",p,state,catalog,body,send,(Runnable)()->{});check(!replies.isEmpty(),"response "+str(body,"op",""));return replies.getLast();}}
        Wire wire=new Wire();JsonObject opened=wire.call(object("op","open","size",24));String token=str(opened,"token","");
        int expected=0,observed=0;var modules=(Map<?,?>)method(catalog,"modules");
        var known=Collections.newSetFromMap(new IdentityHashMap<PluginCommand,Boolean>());
        for(Command raw:getServer().getCommandMap().getKnownCommands().values())if(raw instanceof PluginCommand c&&known.add(c)&&c.getPlugin().isEnabled()&&c.getPlugin().getName().startsWith("NeverLandTowny"))expected++;
        for(Object key:modules.keySet()){
            String module=key.toString();int page=0,pages=1;
            while(page<pages){var response=wire.call(object("op","list","token",token,"module",module,"page",page,"size",24));check(str(response,"op","").equals("list"),"catalog "+module+" page "+page);pages=integer(response,"pages",1);
                for(var entry:response.getAsJsonArray("entries"))if(module.startsWith("NeverLandTowny")&&str(entry.getAsJsonObject(),"id","").contains("/@"))observed++;page++;}
        }
        check(observed==expected&&observed>40,"all enabled addon command roots reachable: "+observed+"/"+expected);
        String target="NeverLandTownyCouncil/history";
        var fake=wire.call(object("op","prepare","token",token,"id","Towny/@op","values",object("args","UiInvite")));check(str(fake,"op","").equals("error"),"unregistered/foreign command rejected");
        var role=wire.call(object("op","prepare","token",token,"id","NeverLandTownyCompanies/role","values",object("player","UiWorker","role","owner")));check(str(role,"op","").equals("error"),"forged role rejected");
        var review=wire.call(object("op","prepare","token",token,"id",target,"values",new JsonObject()));String ticket=str(review,"confirmation","");check(!ticket.isEmpty(),"review returns a confirmation");
        var permission=p.addAttachment(this,"craftoui.commands",false);
        try{var denied=wire.call(object("op","confirm","token",token,"confirmation",ticket));check(str(denied,"op","").equals("error"),"permissions rechecked on confirmation");}finally{p.removeAttachment(permission);}
        block=true;try{var denied=wire.call(object("op","confirm","token",token,"confirmation",ticket));check(str(denied,"op","").equals("error"),"command preprocessing cancellation respected");}finally{block=false;}
        var replay=wire.call(object("op","confirm","token",token,"confirmation",ticket));check(str(replay,"op","").equals("error"),"cancelled confirmation consumed");
        review=wire.call(object("op","prepare","token",token,"id",target,"values",new JsonObject()));ticket=str(review,"confirmation","");
        var sent=wire.call(object("op","confirm","token",token,"confirmation",ticket,"arguments","appoint economy UiInvite"));check(str(sent,"op","").equals("dispatched"),"server-owned arguments dispatched");
        replay=wire.call(object("op","confirm","token",token,"confirmation",ticket));check(str(replay,"op","").equals("error"),"completed confirmation cannot replay");
        review=wire.call(object("op","prepare","token",token,"id",target,"values",new JsonObject()));ticket=str(review,"confirmation","");
        wire.call(object("op","cancel","token",token));
        var cancelled=wire.call(object("op","confirm","token",token,"confirmation",ticket));check(str(cancelled,"op","").equals("error"),"editing or cancelling review invalidates ticket");
        var cross=wire.call(object("op","prepare","token",UUID.randomUUID().toString(),"id",target));check(str(cross,"op","").equals("error"),"foreign session token rejected");
    }
}
