package ru.neverland.townyideologies.reforms;

import java.util.UUID;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.economy.*;
import ru.neverland.core.PaymentEvidence;
import ru.neverland.integration.TreasuryAccess;

/** A token-specific Towny account observer is required for a successful non-idempotent debit. */
public class ReformBank {
    public boolean ready(UUID town){try(var guard=ru.neverland.core.SuiteState.guard()){return TownyEconomyHandler.isActive()&&TownyEconomyHandler.activeAdapter()!=null&&TownyAPI.getInstance().getTown(town)!=null;}}
    public boolean pay(UUID townId,UUID receipt,long cents){
        final com.palmergames.bukkit.towny.object.Town town;try(var guard=ru.neverland.core.SuiteState.guard()){if(!ready(townId))throw new IllegalStateException("Экономика Towny пока недоступна");town=TownyAPI.getInstance().getTown(townId);}double amount=cents/100.0;
        if(!TreasuryAccess.canSpend(town,"social",amount))return false;
        final Account account;try(var guard=ru.neverland.core.SuiteState.guard()){account=town.getAccount();}String token="[ideology-reform:"+receipt+"]";var evidence=new PaymentEvidence(account,false,token,cents);
        var observer=new AccountObserver(){public void withdrew(Account a,double n,String reason){evidence.observe(a,false,n,reason);}public void deposited(Account a,double n,String reason){}};
        try(var guard=ru.neverland.core.SuiteState.guard()){synchronized(account){account.addObserver(observer);}}
        try{try{TreasuryAccess.withdraw(town,"social","ideology",amount,token+" Завершение реформы");}catch(RuntimeException error){if(evidence.observed())return true;throw error;}
            // Once the non-idempotent bank call started, neither its boolean nor a balance
            // comparison proves that no money moved. Keep an unobserved attempt pending.
            if(!evidence.observed())throw new IllegalStateException("Банк не подтвердил квитанцию списания; нужна сверка");return true;
        }finally{try(var guard=ru.neverland.core.SuiteState.guard()){synchronized(account){account.removeObserver(observer);}}}
    }
}
