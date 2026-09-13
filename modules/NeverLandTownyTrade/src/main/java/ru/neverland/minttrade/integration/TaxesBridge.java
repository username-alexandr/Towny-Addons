package ru.neverland.minttrade.integration;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.UUID;
import ru.neverland.core.ApiServices;
import ru.neverland.core.DiplomacyAccess;

public final class TaxesBridge {
    private final JavaPlugin owner;
    public TaxesBridge(JavaPlugin owner){this.owner=owner;}
    public boolean available(){return plugin()!=null;}
    public boolean tradeBlocked(UUID first,UUID second){return supplyRestriction(first,second)!=null;}
    public String supplyRestriction(UUID first,UUID second){
        if(DiplomacyAccess.tradeBlocked(first,second))return "Торговля закрыта дипломатией или реестр недоступен";
        if(owner.getServer().getPluginManager().getPlugin("NeverLandTownyTaxes")==null&&owner.getServer().getPluginManager().getPlugin("TaxyTowny")==null)return null;
        Object value=invoke("isTradeBlocked",first,second);
        return value instanceof Boolean flag?(flag?"Торговля запрещена санкциями":null):"Система санкций временно недоступна";
    }
    public double preferenceMultiplier(UUID first,UUID second){Object value=invoke("tradePreferenceMultiplier",first,second);return value instanceof Number n&&Double.isFinite(n.doubleValue())?Math.max(0,Math.min(1,n.doubleValue())):1;}
    private Object invoke(String method,UUID first,UUID second){
        Plugin plugin=plugin();if(plugin==null)return null;
        try{
            if(plugin.getName().equals("NeverLandTownyTaxes"))return ApiServices.call("NeverLandTownyTaxes","ru.neverland.townytaxes.api.NeverLandTownyTaxesApi",method,new Class<?>[]{UUID.class,UUID.class},first,second);
            return plugin.getClass().getMethod(method,UUID.class,UUID.class).invoke(plugin,first,second);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return null;}
    }
    private Plugin plugin(){Plugin value=owner.getServer().getPluginManager().getPlugin("NeverLandTownyTaxes");if(value==null)value=owner.getServer().getPluginManager().getPlugin("TaxyTowny");return value!=null&&value.isEnabled()?value:null;}
}
