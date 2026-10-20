import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.*;

/** TEST ONLY. Never distribute this provider in production plugins/. */
public final class EconomyFixture extends JavaPlugin implements InvocationHandler {
    public volatile boolean healthy=true;
    private final Map<String,Double> balances=new HashMap<>();
    @Override public void onEnable(){
        Economy proxy=(Economy)Proxy.newProxyInstance(Economy.class.getClassLoader(),new Class<?>[]{Economy.class},this);
        getServer().getServicesManager().register(Economy.class,proxy,this,ServicePriority.Highest);
        getLogger().warning("DISPOSABLE ECONOMY FIXTURE: balances are test-only");
    }
    @Override public synchronized Object invoke(Object proxy,Method method,Object[] args){
        String name=method.getName();Object first=args==null||args.length==0?null:args[0];String key=first instanceof OfflinePlayer p?p.getName():String.valueOf(first);
        double balance=balances.getOrDefault(key,0.0),amount=0; if(args!=null)for(Object a:args)if(a instanceof Double n)amount=n;
        return switch(name){
            case "getName","toString" -> "NeverLand disposable fixture";
            case "isEnabled" -> healthy;
            case "hasAccount" -> true;
            case "hasBankSupport" -> false;
            case "fractionalDigits" -> 2;
            case "format" -> String.format(java.util.Locale.ROOT,"%.2f",amount);
            case "currencyNamePlural","currencyNameSingular" -> "test coins";
            case "getBalance" -> balance;
            case "has" -> balance>=amount;
            case "createPlayerAccount" -> {balances.putIfAbsent(key,0.0);yield true;}
            case "depositPlayer","withdrawPlayer" -> {
                boolean success=amount>=0&&(name.equals("depositPlayer")||balance>=amount);
                if(success){balance+=name.equals("depositPlayer")?amount:-amount;balances.put(key,balance);}
                yield new EconomyResponse(amount,balance,success?EconomyResponse.ResponseType.SUCCESS:EconomyResponse.ResponseType.FAILURE,success?null:"insufficient test funds");
            }
            case "getBanks" -> List.of();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy==first;
            default -> new EconomyResponse(0,balance,EconomyResponse.ResponseType.NOT_IMPLEMENTED,"test fixture");
        };
    }
}
