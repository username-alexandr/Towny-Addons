package ru.neverland.townysieges;
import java.util.UUID;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.economy.*;
import ru.neverland.core.PaymentEvidence;
import ru.neverland.integration.TreasuryAccess;
public class RepairBank extends LootBank {
    public boolean allowed(UUID town,long cents){return ready(town,town)&&balance(town)>=cents&&TreasuryAccess.canSpend(TownyAPI.getInstance().getTown(town),"construction",cents/100.0);}
    public boolean withdraw(RaidRepairsStore.Job job){
        var town=TownyAPI.getInstance().getTown(job.town());var account=town.getAccount();String token="[raid-repair:"+job.id()+":debit]";
        var evidence=new PaymentEvidence(account,false,token,job.cents());var observer=new AccountObserver(){public void deposited(Account source,double amount,String reason){}public void withdrew(Account source,double amount,String reason){evidence.observe(source,false,amount,reason);}};
        synchronized(account){account.addObserver(observer);try{boolean result;try{result=TreasuryAccess.withdraw(town,"construction","raid-repair",job.cents()/100.0,token+" Ремонт "+job.title());}catch(RuntimeException error){if(evidence.observed())return true;throw error;}return evidence.result(result);}finally{account.removeObserver(observer);}}
    }
}
