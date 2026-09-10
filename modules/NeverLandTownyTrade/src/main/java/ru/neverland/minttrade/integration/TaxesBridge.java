package ru.neverland.minttrade.integration;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

public final class TaxesBridge {
    private final JavaPlugin owner;
    public TaxesBridge(JavaPlugin owner){this.owner=owner;}
    public boolean available(){return plugin()!=null;}
    public boolean tradeBlocked(UUID first,UUID second){Object value=invoke("isTradeBlocked",first,second);return value instanceof Boolean flag&&flag;}
    /** Recurring orders must not evade an installed sanctions system during its failure. */
    public String supplyRestriction(UUID first,UUID second){
        if(owner.getServer().getPluginManager().getPlugin("NeverLandTownyTaxes")==null&&owner.getServer().getPluginManager().getPlugin("TaxyTowny")==null)return null;
        Object value=invoke("isTradeBlocked",first,second);
        return value instanceof Boolean flag?(flag?"Торговля запрещена санкциями":null):"Система санкций временно недоступна";
    }
    public double preferenceMultiplier(UUID first,UUID second){Object value=invoke("tradePreferenceMultiplier",first,second);return value instanceof Number number?Math.max(0,Math.min(1,number.doubleValue())):1;}
    private Object invoke(String name,UUID first,UUID second){Plugin plugin=plugin();if(plugin==null)return null;try{Method method=plugin.getClass().getMethod(name,UUID.class,UUID.class);return method.invoke(plugin,first,second);}catch(ReflectiveOperationException|LinkageError error){owner.getLogger().warning("Интеграция NeverLandTownyTaxes недоступна: "+error.getMessage());return null;}}
    private Plugin plugin(){Plugin value=owner.getServer().getPluginManager().getPlugin("NeverLandTownyTaxes");if(value==null)value=owner.getServer().getPluginManager().getPlugin("TaxyTowny");return value!=null&&value.isEnabled()?value:null;}
}
