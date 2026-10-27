package ru.neverland.townysieges;

import java.math.*;
import java.util.*;
import org.bukkit.Bukkit;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.economy.*;
import ru.neverland.core.PaymentEvidence;

/** Towny's selected provider, receipt observer and no automatic retry after an unknown outcome. */
public class LootBank {
    public boolean ready(UUID defender,UUID attacker){
        if(!TownyEconomyHandler.isActive()||TownyAPI.getInstance().getTown(defender)==null||TownyAPI.getInstance().getTown(attacker)==null)return false;
        if(!TownyEconomyHandler.getType().name().contains("VAULT"))return true;
        var vault=Bukkit.getPluginManager().getPlugin("Vault");if(vault==null||!vault.isEnabled())return false;
        for(Class<?> type:Bukkit.getServicesManager().getKnownServices())if(Set.of("net.milkbowl.vault.economy.Economy","net.milkbowl.vault2.economy.Economy").contains(type.getName())){
            var registration=Bukkit.getServicesManager().getRegistration(type);if(registration!=null&&registration.getPlugin().isEnabled())try{if(Boolean.TRUE.equals(type.getMethod("isEnabled").invoke(registration.getProvider())))return true;}catch(ReflectiveOperationException ignored){}
        }return false;
    }
    public long balance(UUID town){double balance=TownyAPI.getInstance().getTown(town).getAccount().getHoldingBalance();if(!Double.isFinite(balance)||balance<0)throw new IllegalStateException("Некорректный баланс казны");return BigDecimal.valueOf(balance).movePointRight(2).setScale(0,RoundingMode.DOWN).longValueExact();}
    public boolean mutate(RaidLootStore.Loot loot,boolean incoming){
        if(!ready(loot.defender(),loot.attacker()))throw new IllegalStateException("Экономика Towny/Vault недоступна");
        var account=TownyAPI.getInstance().getTown(incoming?loot.attacker():loot.defender()).getAccount();String token="[raid-loot:"+loot.id()+":"+(incoming?"credit":"debit")+"]";
        var evidence=new PaymentEvidence(account,incoming,token,loot.cents());
        var observer=new AccountObserver(){public void deposited(Account source,double amount,String reason){evidence.observe(source,true,amount,reason);}public void withdrew(Account source,double amount,String reason){evidence.observe(source,false,amount,reason);}};
        synchronized(account){account.addObserver(observer);try{
            boolean result;String reason=(incoming?"[NLT|income|free|raid] ":"[NLT|spend|free|raid] ")+token+" Захват кафедры ратуши";
            try{result=incoming?account.deposit(loot.cents()/100.0,reason):account.withdraw(loot.cents()/100.0,reason);}catch(RuntimeException failure){if(evidence.observed())return true;throw failure;}
            return evidence.result(result);
        }finally{account.removeObserver(observer);}}
    }
}
