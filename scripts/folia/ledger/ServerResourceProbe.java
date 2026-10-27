package ru.neverland.validation;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcontracts.api.ServerMaterialsApi;
import ru.neverland.mintcontracts.orders.ServerMaterials;

/** Marked disposable fixture only: test the live SQLite worker and registered construction API. */
public final class ServerResourceProbe extends JavaPlugin {
    private final List<String> checks=new CopyOnWriteArrayList<>();
    private final String run=System.getProperty("neverland.probe.run","legacy");
    private Path proof;private long before;private boolean done;
    private void check(boolean value,String label){checks.add((value?"PASS ":"FAIL ")+label);if(!value)throw new AssertionError(label);}
    private Object field(Object value,String key)throws Exception{var f=value.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(value);}
    private UUID id(String suffix){return UUID.nameUUIDFromBytes((run+suffix).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    @Override public void onEnable(){
        if(!Files.exists(Path.of("ALLOW_DISPOSABLE_SERVER_RESOURCE_PROBE"))||!getServer().getIp().equals("127.0.0.1")){getServer().getPluginManager().disablePlugin(this);return;}
        proof=getDataFolder().toPath().resolve("runs").resolve(run);
        // LedgerProbe holds SuiteState on a worker at this time. No blocking ledger acquisition here.
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{try{start();}catch(Throwable error){finish(error);}},100);
    }
    private void start()throws Exception{
        var plugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyContracts");check(plugin!=null&&plugin.isEnabled(),"Contracts enabled on live Folia");
        var api=Bukkit.getServicesManager().load(ServerMaterialsApi.class);check(api!=null&&api.capabilities().containsAll(Set.of("balances","reserve","consume","release")),"construction material API is registered with required capabilities");
        var orders=field(plugin,"publicOrders");var db=(ServerMaterials)field(orders,"materials");
        check(db.available(),"Paper bundled SQLite driver opens real server-materials.db");
        var catalogue=YamlConfiguration.loadConfiguration(Path.of("plugins/NeverLandTownyContracts/orders.yml").toFile());
        check(catalogue.getInt("catalog-version")==5&&catalogue.contains("templates.build_oak_log_common")&&catalogue.contains("templates.build_black_stained_glass_legendary")&&catalogue.contains("templates.build_pale_oak_trapdoor_common"),"installed v3 catalogue gains construction deliveries");
        check(catalogue.getInt("limits.player-24h")==5000&&catalogue.getInt("limits.server-24h")==500000,"custom player/server payout caps survive real catalogue migration");
        var previous=YamlConfiguration.loadConfiguration(getDataFolder().toPath().resolve("previous.yml").toFile());
        var delivery=new ServerMaterials.Delivery(id("delivery"),id("player"),"FixturePlayer","fixture_building","OAK_LOG",64,100,"COMMON",System.currentTimeMillis());
        api.balances().thenCompose(values->{before=values.getOrDefault("OAK_LOG",0L);if(previous.contains("available"))check(before==previous.getLong("available"),"reserved and consumed stock survives full Folia restart");return db.prepare(delivery);})
            .thenCompose(d->db.confirm(d.id(),"disposable fixture supplies test inventory proof"))
            .thenCompose(d->db.settled(d.id())) // No bank receipt exists: this fixture exercises materials only.
            .thenCompose(v->api.balances()).thenCompose(values->{check(values.get("OAK_LOG")==before+64,"confirmed delivery becomes live available stock once");return api.reserve(id("spent"),"fixture_spawn",Map.of("OAK_LOG",16L),"fixture","server spawn stage");})
            .thenCompose(status->{check(status.equals("RESERVED"),"construction reserves resources asynchronously");return api.reserve(id("spent"),"fixture_spawn",Map.of("OAK_LOG",16L),"fixture","server spawn stage");})
            .thenCompose(status->api.consume(id("spent"),"fixture","server stage completed"))
            .thenCompose(status->{check(status.equals("SPENT"),"completed project consumes reserve");return api.release(id("spent"),"fixture","invalid spent refund").handle((r,error)->{check(error!=null,"spent project cannot create a resource refund");return null;});})
            .thenCompose(v->api.reserve(id("returned"),"fixture_spawn",Map.of("OAK_LOG",8L),"fixture","cancelled server stage"))
            .thenCompose(status->api.release(id("returned"),"fixture","server stage cancelled"))
            .thenCompose(status->api.release(id("returned"),"fixture","server stage cancelled"))
            .thenCompose(status->api.balances()).whenComplete((values,error)->{
                if(error!=null){finish(error);return;}
                try{check(values.get("OAK_LOG")==before+48,"duplicate reserve/return calls preserve final stock exactly");
                    var y=new YamlConfiguration();y.set("available",values.get("OAK_LOG"));Files.createDirectories(getDataFolder().toPath());y.save(getDataFolder().toPath().resolve("previous.yml").toFile());finish(null);
                }catch(Throwable ex){finish(ex);}
            });
    }
    private synchronized void finish(Throwable error){if(done)return;done=true;
        if(error!=null){checks.add("FAIL "+error);getLogger().log(java.util.logging.Level.SEVERE,"SERVER_RESOURCE_PROBE_FAIL",error);}
        try{Files.createDirectories(proof);Files.write(proof.resolve("checks.txt"),checks);Files.writeString(proof.resolve("result.txt"),"run_id="+run+"\nfailed="+checks.stream().filter(x->x.startsWith("FAIL")).count()+"\nchecks="+checks.size()+"\n");}
        catch(Exception ex){getLogger().log(java.util.logging.Level.SEVERE,"Cannot write server resource proof",ex);}
    }
    @Override public void onDisable(){if(proof!=null&&!done)finish(new IllegalStateException("server stopped before material API checks completed"));}
}
