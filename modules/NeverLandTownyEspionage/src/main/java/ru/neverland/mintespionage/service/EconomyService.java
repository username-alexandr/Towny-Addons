package ru.neverland.mintespionage.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintespionage.model.OperationDefinition;
import ru.neverland.mintespionage.util.ColorUtil;

import java.text.DecimalFormat;

public final class EconomyService {
    private final JavaPlugin plugin;private final DecimalFormat format=new DecimalFormat("#,##0.##");
    public EconomyService(JavaPlugin plugin){this.plugin=plugin;}
    public double balance(Town town){try{return town==null?0:town.getAccount().getHoldingBalance();}catch(RuntimeException exception){return 0;}}
    public boolean withdrawOperation(Town town,OperationDefinition definition,String target){
        String reason=plugin.getConfig().getString("economy.operation-reason","Разведывательная операция").replace("%operation%",ColorUtil.strip(definition.name())).replace("%target%",target);
        return withdraw(town,definition.cost(),reason);
    }
    public boolean withdrawUpgrade(Town town,double amount,boolean network){return withdraw(town,amount,plugin.getConfig().getString(network?"economy.network-upgrade-reason":"economy.defense-upgrade-reason","Развитие разведки"));}
    public boolean withdraw(Town town,double amount,String reason){
        if(amount<=0)return true;try{return town!=null&&town.getAccount().canPayFromHoldings(amount)&&town.getAccount().withdraw(amount,ColorUtil.strip(reason));}catch(RuntimeException exception){return false;}
    }
    public String format(double value){return format.format(value);}
}
