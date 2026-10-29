package ru.neverland.townymarket.personal;
import java.util.*;
import org.bukkit.Bukkit;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.economy.*;
import ru.neverland.core.PaymentEvidence;

/** Real personal Towny accounts, never a server payout. Ambiguous outcomes stay pending for review. */
public class LimitBank {
    public boolean ready(UUID owner){return TownyEconomyHandler.isActive()&&TownyEconomyHandler.activeAdapter()!=null&&TownyAPI.getInstance().getResident(owner)!=null;}
    public boolean move(UUID owner,UUID receipt,long cents,boolean incoming){
        if(!ready(owner))throw new IllegalStateException("Экономика Towny временно недоступна");var account=TownyAPI.getInstance().getResident(owner).getAccount();String token="[personal-market:"+receipt+"]";var evidence=new PaymentEvidence(account,incoming,token,cents);
        var observer=new AccountObserver(){public void withdrew(Account a,double n,String reason){evidence.observe(a,false,n,reason);}public void deposited(Account a,double n,String reason){evidence.observe(a,true,n,reason);}};
        synchronized(account){double before=TownyEconomyHandler.getBalance(account);if(!Double.isFinite(before))throw new IllegalStateException("Банк не сообщил достоверный баланс");account.addObserver(observer);try{boolean result;try{result=incoming?account.deposit(cents/100.0,token+" Получение средств личного рынка"):account.withdraw(cents/100.0,token+" Резерв личной заявки");}catch(RuntimeException error){if(evidence.observed())return true;throw error;}if(!result&&!evidence.observed()){double after=TownyEconomyHandler.getBalance(account);if(!Double.isFinite(after)||Math.abs(after-before)>0.000001)throw new IllegalStateException("Банк изменил баланс без подтверждения; требуется сверка");}return evidence.result(result);}finally{account.removeObserver(observer);}}
    }
}
