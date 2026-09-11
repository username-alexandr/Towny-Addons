package ru.neverland.mintcontracts.integration;
import org.bukkit.Bukkit;
import java.util.UUID;

/** Optional reflection bridge avoids a hard dependency or circular plugin load order. */
public final class CompaniesBridge {
    private Object call(String method,Class<?>[] types,Object... args) {
        var plugin=Bukkit.getPluginManager().getPlugin("NeverLandTownyCompanies");
        if(plugin==null||!plugin.isEnabled())return null;
        try {Object api=plugin.getClass().getMethod("companies").invoke(plugin);return api.getClass().getMethod(method,types).invoke(api,args);}
        catch(ReflectiveOperationException|RuntimeException ex){return null;}
    }
    public boolean allow(String method,UUID actor,UUID company,UUID town){return Boolean.TRUE.equals(call(method,new Class<?>[]{UUID.class,UUID.class,UUID.class},actor,company,town));}
    public String name(UUID company){Object name=call("companyName",new Class<?>[]{UUID.class},company);return name instanceof String s?s:"Компания "+company.toString().substring(0,8);}
    public boolean settle(UUID contract,UUID company,UUID town,long payout,long refund){return Boolean.TRUE.equals(call("settleEscrow",new Class<?>[]{UUID.class,UUID.class,UUID.class,long.class,long.class},contract,company,town,payout,refund));}
}
