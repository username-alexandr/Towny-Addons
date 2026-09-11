package ru.neverland.townycompanies;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ContractsBridge {
    private Object call(String method,Class<?>[] types,Object... args) {
        var plugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyContracts");
        if(plugin==null||!plugin.isEnabled())throw new IllegalStateException("Городские контракты недоступны");
        try {Object api=plugin.getClass().getMethod("companyContracts").invoke(plugin);return api.getClass().getMethod(method,types).invoke(api,args);}
        catch(ReflectiveOperationException ex){throw new IllegalStateException("Требуется NeverLandTownyContracts 0.2.0 или новее",ex);}
    }
    @SuppressWarnings("unchecked") public List<Map<String,Object>> offers(UUID town) {return (List<Map<String,Object>>)call("companyOffers",new Class<?>[]{UUID.class},town);}
    public int count(UUID company) {return ((Number)call("companyActiveCount",new Class<?>[]{UUID.class},company)).intValue();}
    public boolean take(Player player,UUID company,UUID contract) {return Boolean.TRUE.equals(call("takeCompanyContract",new Class<?>[]{Player.class,UUID.class,UUID.class},player,company,contract));}
    public boolean release(Player player,UUID company,UUID contract) {return Boolean.TRUE.equals(call("releaseCompanyContract",new Class<?>[]{Player.class,UUID.class,UUID.class},player,company,contract));}
}
