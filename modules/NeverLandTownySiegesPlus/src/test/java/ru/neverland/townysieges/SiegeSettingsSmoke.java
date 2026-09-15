package ru.neverland.townysieges;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;

public final class SiegeSettingsSmoke {
    static void bad(YamlConfiguration y,String key,Object value){Object previous=y.get(key);y.set(key,value);boolean denied=false;try{SiegeSettings.load(y);}catch(IllegalArgumentException expected){denied=true;}y.set(key,previous);if(!denied)throw new AssertionError("invalid config accepted: "+key);}
    public static void main(String[] args)throws Exception{
        var y=new YamlConfiguration();try(var in=SiegeSettingsSmoke.class.getResourceAsStream("/config.yml")){y.load(new InputStreamReader(in,StandardCharsets.UTF_8));}
        var good=SiegeSettings.load(y);if(good.radii().size()!=6||good.defenseCap()!=.4||!good.battleOnly())throw new AssertionError("packaged defaults");
        bad(y,"radius.port_fort",-1);bad(y,"radius.watchtower",Double.NaN);bad(y,"radius.fortress_wall",1000000);
        bad(y,"damage.maximum-reduction",1);bad(y,"moat.maximum-slow",1);bad(y,"battle-sessions-only","true");bad(y,"ground-height",0);
        try{good.radii().clear();throw new AssertionError("mutable settings");}catch(UnsupportedOperationException expected){}
        System.out.println("SiegeSettingsSmoke PASS");
    }
}
