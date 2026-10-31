package cc.crafto.test;
import org.bukkit.*;import org.bukkit.block.BlockFace;import org.bukkit.command.*;import org.bukkit.entity.Player;import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.*;import com.palmergames.bukkit.towny.*;import com.palmergames.bukkit.towny.object.*;import com.palmergames.bukkit.towny.object.inviteobjects.*;import com.palmergames.bukkit.towny.invites.InviteHandler;
import ru.neverland.townycompanies.*;import ru.neverland.townybuilds.data.*;import ru.neverland.townybuilds.construction.*;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.function.*;
import static cc.crafto.ui.Protocol.*;import static ru.neverland.townycompanies.CompanyData.*;
/** DISPOSABLE TEST ONLY. Reflection is confined to test setup and internal protocol assertions. */
public final class CivicProbe extends JavaPlugin implements org.bukkit.event.Listener {
    private int checks;private final List<String> report=new ArrayList<>();
    private static UUID id(String name){return UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private static Object invoke(Object o,String n,Object...args)throws Exception{for(var m:o.getClass().getDeclaredMethods())if(m.getName().equals(n)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(o,args);}throw new NoSuchMethodException(n);}
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;report.add("PASS "+label);getLogger().info("PASS "+label);}
    private void save(){try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("checks.txt"),String.join("\n",report)+"\n");}catch(Exception e){throw new RuntimeException(e);}}
    private void failure(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"CIVIC_PROBE_FAILED",e);save();}
    private CompanyService companies(){return (CompanyService)((NeverLandTownyCompanies)getServer().getPluginManager().getPlugin("NeverLandTownyCompanies")).companies();}
    @Override public void onEnable(){
        if(!Files.isRegularFile(Path.of("ALLOW_CRAFTO_UI_DISPOSABLE_TEST"))||!getServer().getIp().equals("127.0.0.1")){getServer().getPluginManager().disablePlugin(this);return;}
        if(Files.isRegularFile(Path.of("AUTO_CIVIC_INVITATION_TEST")))getServer().getPluginManager().registerEvents(this,this);
        getCommand("civicprobe").setExecutor((sender,cmd,label,args)->{if(sender instanceof Player p&&p.isOp())p.getScheduler().run(this,t->{try{switch(args.length==0?"check":args[0]){case "setup"->setup(p);case "invite"->invite(p);case "checks"->protocol(p);default->checkCompany(p);}save();}catch(Throwable e){failure(e);}},null);return true;});
    }
    @org.bukkit.event.EventHandler public void joined(org.bukkit.event.player.PlayerJoinEvent event){
        Player p=event.getPlayer();if(!List.of("UiDecline","UiInvite","UiVisitor").contains(p.getName()))return;
        p.getScheduler().runDelayed(this,t->{try{autoInvitation(p);}catch(Exception e){failure(e);}},null,120);
    }
    private void autoInvitation(Player p)throws Exception{
        Wire wire=new Wire(p);boolean decline=p.getName().equals("UiDecline"),town=p.getName().equals("UiVisitor");
        wire.call("open",object("route","inbox"))
        .thenCompose(inbox->wire.call("act",object("action",wire.action(inbox,town?"Город":"Компания"))))
        .thenCompose(detail->wire.call("act",object("action",wire.action(detail,decline?"Отклонить":"Принять"))))
        .thenCompose(review->{check(str(review,"op","").equals("review"),p.getName()+" invitation consequence preview");return wire.call("confirm",object("confirmation",str(review,"confirmation","")));})
        .thenAccept(done->{check(str(done,"op","").equals("view"),p.getName()+" confirmation returned current view: "+done);
            if(town)check(TownyAPI.getInstance().getResident(p.getUniqueId()).getTownOrNull()!=null,"Towny invite changed real resident membership");
            else{Company c=companies().ledger.membership(id("CraftoRenderUI"));check(!c.invitations().containsKey(p.getUniqueId()),p.getName()+" own invitation removed from real ledger");if(!decline)check(c.members().containsKey(p.getUniqueId()),"company join changed real membership");}
            check(done.getAsJsonArray("rows").toString().contains("Приглашений нет"),p.getName()+" UI snapshot immediately drops used invitation");
            save();getLogger().info("AUTO_PASS "+p.getName());
        }).exceptionally(e->{failure(e);return null;});
    }
    private void setup(Player p)throws Exception{
        var u=TownyUniverse.getInstance();Resident actor=TownyAPI.getInstance().getResident(p.getUniqueId());Town town=actor.getTownOrNull();
        if(town==null){UUID townId=id("UiCivicTown");u.newTownInternal("UiCivicTown",townId);town=TownyAPI.getInstance().getTown(townId);actor.setTown(town);town.setMayor(actor);actor.save();
            var pos=p.getLocation();int size=TownySettings.getTownBlockSize();var claim=new TownBlock(Math.floorDiv(pos.getBlockX(),size),Math.floorDiv(pos.getBlockZ(),size),u.getWorld(p.getWorld().getName()));u.addTownBlock(claim);claim.setTown(town);town.setHomeBlock(claim);claim.save();town.setSpawn(pos);town.save();}
        for(String name:List.of("UiInvite","UiDecline","UiVisitor")){Resident r=TownyAPI.getInstance().getResident(id(name));if(r==null)r=u.getDataSource().newResident(name,id(name));if(!name.equals("UiVisitor")&&r.getTownOrNull()==null)r.setTown(town);r.save();}
        var service=companies();if(service.ledger.membership(p.getUniqueId())==null)service.create(p,CompanyData.Kind.SHOP,"Мастерская города");invite(p);
        var builds=getServer().getPluginManager().getPlugin("NeverLandTownyBuilds");var store=(DataStore)field(builds,"dataStore");var pos=p.getLocation();
        var data=store.town(town.getUUID());if(data.constructionSite("warehouse")==null)data.setConstructionSite(new ConstructionSite("warehouse",p.getWorld().getUID(),pos.getBlockX()+3,pos.getBlockY(),pos.getBlockZ()+3,BlockFace.NORTH,0,1,1,true));store.markDirty();store.saveOrThrow();
        p.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.OAK_PLANKS,32),new org.bukkit.inventory.ItemStack(Material.COBBLESTONE,64),new org.bukkit.inventory.ItemStack(Material.STONE,64));
        check(true,"real Towny town, construction and company fixtures created on entity scheduler");p.sendMessage("Civic fixture ready");
    }
    private void invite(Player p)throws Exception{
        var service=companies();service.invite(p,id("UiInvite"));service.invite(p,id("UiDecline"));Town town=TownyAPI.getInstance().getResident(p.getUniqueId()).getTownOrNull();var r=TownyAPI.getInstance().getResident(id("UiVisitor"));
        if(r.getTownOrNull()==null&&r.getReceivedInvites().isEmpty()){var invite=new PlayerJoinTownInvite(p,r,town);r.newReceivedInvite(invite);town.newSentInvite(invite);InviteHandler.addInvite(invite);}check(true,"real town and company invitations created");
    }
    private void checkCompany(Player p){var service=companies();Company c=service.ledger.membership(id("CraftoRenderUI"));check(c!=null,"fixture company exists");check(!c.invitations().containsKey(id("UiDecline")),"declining removes only own company invitation");check(c.members().containsKey(id("UiInvite")),"company invitation accepted through real handler");check(TownyAPI.getInstance().getResident(id("UiVisitor")).getTownOrNull()!=null,"town invitation accepted through Towny");save();}
    class Wire {
        Object work,state;Player p;int seq;String token="";
        Wire(Player p)throws Exception{this.p=p;Object ui=getServer().getPluginManager().getPlugin("CraftoUI");work=field(ui,"civicWorkbench");Class<?> type=Class.forName("cc.crafto.ui.server.CivicWorkbench$State",true,ui.getClass().getClassLoader());var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);state=ctor.newInstance();}
        CompletableFuture<JsonObject> call(String op,JsonObject data){var f=new CompletableFuture<JsonObject>();data.addProperty("op",op);data.addProperty("token",token);data.addProperty("request",++seq);data.addProperty("size",12);
            Consumer<JsonObject> receive=b->{if(!str(b,"op","").equals("notification")){if(b.has("token"))token=str(b,"token","");f.complete(b);}};
            try{invoke(work,"handle",p,state,data,receive);}catch(Exception e){f.completeExceptionally(e);}return f.orTimeout(12,TimeUnit.SECONDS);
        }
        String action(JsonObject b,String title){for(var e:b.getAsJsonArray("rows")){var row=e.getAsJsonObject();if(str(row,"title","").contains(title))return str(row,"action","");}throw new IllegalArgumentException("No row "+title+" in "+b);}
    }
    private void protocol(Player p)throws Exception{
        var wire=new Wire(p);final String[] accepted={""};final String[] ticket={""};
        wire.call("open",object("route","map")).thenCompose(map->{check(map.has("map")&&map.getAsJsonObject("map").getAsJsonArray("cells").size()==81,"real Towny map has 81 selectable cells");return wire.call("open",object("route","orders"));})
        .thenCompose(board->{check(str(board,"op","").equals("view")&&board.getAsJsonArray("rows").size()>1,"real daily orders visible");var rows=board.getAsJsonArray("rows");return wire.call("act",object("action",str(rows.get(1).getAsJsonObject(),"action","")));})
        .thenCompose(detail->{check(str(detail,"route","").equals("offer"),"server-selected order detail");return wire.call("act",object("action",wire.action(detail,"Принять")));})
        .thenCompose(review->{check(str(review,"op","").equals("review"),"accept shows consequences before mutation");ticket[0]=str(review,"confirmation","");return wire.call("confirm",object("confirmation",ticket[0],"id","forged-template"));})
        .thenCompose(mine->{check(str(mine,"route","").equals("mine")&&mine.getAsJsonArray("rows").size()>0,"accepted real order, client-supplied template ignored");accepted[0]=wire.action(mine,"");return wire.call("confirm",object("confirmation",ticket[0]));})
        .thenCompose(replay->{check(str(replay,"op","").equals("error"),"confirmation replay rejected");return wire.call("open",object("route","mine"));})
        .thenCompose(mine->wire.call("act",object("action",wire.action(mine,""))))
        .thenCompose(detail->wire.call("act",object("action",wire.action(detail,"Отменить"))))
        .thenCompose(review->{check(str(review,"op","").equals("review")&&review.getAsJsonArray("lines").toString().contains("Возврат: 0"),"cancellation review returned");ticket[0]=str(review,"confirmation","");return wire.call("page",object("page",0));})
        .thenCompose(cancelled->wire.call("confirm",object("confirmation",ticket[0])))
        .thenCompose(replay->{check(str(replay,"op","").equals("error"),"leaving review invalidates confirmation");return wire.call("open",object("route","mine"));})
        .thenCompose(mine->wire.call("act",object("action",wire.action(mine,""))))
        .thenCompose(detail->wire.call("act",object("action",wire.action(detail,"Отменить"))))
        .thenCompose(review->wire.call("confirm",object("confirmation",str(review,"confirmation",""))))
        .thenCompose(done->{check(str(done,"route","").equals("orders"),"real order cancellation completed");return wire.call("open",object("route","materials"));})
        .thenCompose(materials->{check(str(materials,"route","").equals("materials"),"materials page reads real construction/inventory");return wire.call("act",object("action","forged-server-action"));})
        .thenAccept(forged->{check(str(forged,"op","").equals("error"),"unregistered action rejected");save();p.sendMessage("CIVIC PROBE PASSED "+checks);})
        .exceptionally(e->{failure(e);return null;});
    }
}
