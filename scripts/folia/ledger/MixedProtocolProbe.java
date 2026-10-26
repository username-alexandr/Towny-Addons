package ru.neverland.validation;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
/** Verifies a partial update stops the new controller before writing module/player data. */
public final class MixedProtocolProbe extends JavaPlugin {
    @Override public void onEnable(){
        if(!Files.exists(Path.of("ALLOW_DISPOSABLE_LEDGER_PROBE"))||!getServer().getIp().equals("127.0.0.1")){getServer().getPluginManager().disablePlugin(this);return;}
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{
            try{
                var towny=Bukkit.getPluginManager().getPlugin("Towny");var control=Bukkit.getPluginManager().getPlugin("NeverLandTownyControl");var old=Bukkit.getPluginManager().getPlugin("NeverLandTownyChronicles");
                boolean safe=towny!=null&&towny.isEnabled()&&control!=null&&!control.isEnabled()&&old!=null
                    &&!Files.exists(control.getDataFolder().toPath().resolve("modules.yml"));
                Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("result.txt"),"failed="+(safe?0:1)+"\nPartial update rejects new controller before data mutation\n");
            }catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"Mixed protocol probe",e);}
            Bukkit.shutdown();
        },30);
    }
}
