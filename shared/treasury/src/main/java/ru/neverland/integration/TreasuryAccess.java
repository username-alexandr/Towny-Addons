package ru.neverland.integration;
import org.bukkit.Bukkit;
import com.palmergames.bukkit.towny.object.Town;
import java.util.UUID;
import java.lang.reflect.InvocationTargetException;
/** Optional integration. An installed but unavailable ledger cannot silently bypass budgets. */
public final class TreasuryAccess {
    private TreasuryAccess(){}
    private static Object invoke(String method,Class<?>[] signature,Object... args){
        try{var c=ru.neverland.core.ApiServices.connect("NeverLandTownyTreasuryPlus","ru.neverland.townytreasury.api.TownyTreasuryApi",1,method);return c.ready()?c.invoke(method,signature,args):false;}
        catch(InvocationTargetException ex){throw new IllegalStateException("Казна: "+ex.getCause().getMessage(),ex.getCause());}
        catch(ReflectiveOperationException|LinkageError ex){return false;}
    }
    public static boolean canSpend(Town town,String category,double amount){if(town==null||!Double.isFinite(amount)||amount<0)return false;if(Bukkit.getPluginManager().getPlugin("NeverLandTownyTreasuryPlus")==null)return town.getAccount().canPayFromHoldings(amount);return Boolean.TRUE.equals(invoke("canSpend",new Class<?>[]{UUID.class,String.class,double.class},town.getUUID(),category,amount));}
    public static boolean withdraw(Town town,String category,String source,double amount,String reason){if(town==null||!Double.isFinite(amount)||amount<0)return false;if(Bukkit.getPluginManager().getPlugin("NeverLandTownyTreasuryPlus")==null)return town.getAccount().withdraw(amount,reason);return Boolean.TRUE.equals(invoke("spend",new Class<?>[]{UUID.class,String.class,String.class,double.class,String.class},town.getUUID(),category,source,amount,reason));}
    public static boolean deposit(Town town,String category,String source,boolean refund,double amount,String reason){if(town==null||!Double.isFinite(amount)||amount<0)return false;return town.getAccount().deposit(amount,"[NLT|"+(refund?"refund":"income")+"|"+category+"|"+source+"] "+reason);}
}
