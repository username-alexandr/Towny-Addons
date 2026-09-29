package ru.neverland.mintespionage.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintespionage.model.OperationDefinition;
import ru.neverland.mintespionage.util.ColorUtil;

import java.text.DecimalFormat;

public final class EconomyService {
    private final JavaPlugin plugin;private final DecimalFormat format=new DecimalFormat("#,##0.##");
    public EconomyService(JavaPlugin plugin){this.plugin=plugin;}
    public double balance(Town town){ synchronized (ru.neverland.core.SuiteState.lock()) {try{return town==null?0:town.getAccount().getHoldingBalance();}catch(RuntimeException exception){return 0;}} }
    public boolean withdrawOperation(Town town,OperationDefinition definition,String target){ synchronized (ru.neverland.core.SuiteState.lock()) {
        String reason=plugin.getConfig().getString("economy.operation-reason","Разведывательная операция").replace("%operation%",ColorUtil.strip(definition.name())).replace("%target%",target);
        return withdraw(town,definition.cost(),reason);
    } }
    public boolean withdrawUpgrade(Town town,double amount,boolean network){ synchronized (ru.neverland.core.SuiteState.lock()) {return withdraw(town,amount,plugin.getConfig().getString(network?"economy.network-upgrade-reason":"economy.defense-upgrade-reason","Развитие разведки"));} }
    public boolean withdraw(Town town,double amount,String reason){ synchronized (ru.neverland.core.SuiteState.lock()) {
        if(amount<=0)return true;try{return town!=null&&ru.neverland.integration.TreasuryAccess.canSpend(town,"army",amount)&&ru.neverland.integration.TreasuryAccess.withdraw(town,"army","espionage",amount,ColorUtil.strip(reason));}catch(RuntimeException exception){return false;}
    } }
    public String format(double value){ synchronized (ru.neverland.core.SuiteState.lock()) {return format.format(value);} }
}
