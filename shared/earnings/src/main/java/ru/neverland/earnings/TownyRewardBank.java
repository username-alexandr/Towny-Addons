package ru.neverland.earnings;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.object.economy.*;
import ru.neverland.core.*;

/** Use Towny's selected adapter so personal and town balances share one provider. */
public final class TownyRewardBank implements RewardLedger.Bank {
    private final JavaPlugin plugin;
    public TownyRewardBank(JavaPlugin plugin){this.plugin=plugin;}
    public boolean ready(UUID player){return TownyEconomyHandler.isActive()&&providerReady()&&TownyAPI.getInstance().getResident(player)!=null;}
    private boolean providerReady(){
        if(!TownyEconomyHandler.getType().name().contains("VAULT"))return true;
        var vault=Bukkit.getPluginManager().getPlugin("Vault");if(vault==null||!vault.isEnabled())return false;
        for(Class<?> type:Bukkit.getServicesManager().getKnownServices())if(type.getName().equals("net.milkbowl.vault.economy.Economy")||type.getName().equals("net.milkbowl.vault2.economy.Economy")){
            var registration=Bukkit.getServicesManager().getRegistration(type);if(registration==null||!registration.getPlugin().isEnabled())continue;
            try{if(Boolean.TRUE.equals(type.getMethod("isEnabled").invoke(registration.getProvider())))return true;}catch(ReflectiveOperationException ignored){}
        }
        return false;
    }
    public boolean deposit(RewardLedger.Entry e){
        if(!ready(e.player()))throw new IllegalStateException("Towny economy disconnected");
        var resident=TownyAPI.getInstance().getResident(e.player());if(resident==null)throw new IllegalStateException("Resident unavailable");
        Account account=resident.getAccount();String token="[nlt-income:"+e.id()+"]";
        var proof=new PaymentEvidence(account,true,token,e.cents());
        var observer=new AccountObserver(){public void withdrew(Account a,double n,String why){}public void deposited(Account a,double n,String why){proof.observe(a,true,n,why);}};
        synchronized(account){account.addObserver(observer);try{
            boolean result;try{result=account.deposit(e.cents()/100.0,token+" "+e.source());}catch(RuntimeException ex){if(!proof.observed())throw ex;result=true;}
            boolean paid=proof.result(result);
            AuditTrail.record(plugin,"deposit",e.id().toString(),(e.source().startsWith("orders:")?"PUBLIC_ORDER_REWARD":"JOB_SALARY"),paid?"PAID":"REVIEW",AuditRecord.Party.unknown(),new AuditRecord.Party("SERVER","server","Государство",""),AuditTrail.player(e.player()),"",0,AuditTrail.cents(e.cents()),e.source());
            return paid;
        }finally{account.removeObserver(observer);}}
    }
    public List<String> diagnostics(){
        List<String> lines=new ArrayList<>();var vault=Bukkit.getPluginManager().getPlugin("Vault");
        lines.add("Towny economy active="+TownyEconomyHandler.isActive()+"; adapter="+TownyEconomyHandler.getType());
        lines.add("Vault="+(vault==null?"отсутствует":vault.getPluginMeta().getVersion()+" enabled="+vault.isEnabled()));
        boolean found=false;for(Class<?> type:Bukkit.getServicesManager().getKnownServices())if(type.getName().equals("net.milkbowl.vault.economy.Economy")||type.getName().equals("net.milkbowl.vault2.economy.Economy")){
            found=true;
            var registration=Bukkit.getServicesManager().getRegistration(type);lines.add("Vault provider="+(registration==null?"нет":registration.getProvider().getClass().getName()+" from "+registration.getPlugin().getName()));
        }
        if(!found)lines.add("Зарегистрированный Vault/Vault2 провайдер отсутствует.");
        if(!TownyEconomyHandler.isActive()||!providerReady())lines.add("Платежи ожидают подключения экономики. Vault сам по себе не хранит баланс.");return lines;
    }
}
