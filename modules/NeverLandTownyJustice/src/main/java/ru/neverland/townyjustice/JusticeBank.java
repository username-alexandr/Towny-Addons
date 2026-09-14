package ru.neverland.townyjustice;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.economy.*;
import ru.neverland.core.*;
import ru.neverland.integration.TreasuryAccess;
/** Towny account evidence resolves a lost bank reply; no raw Vault bypass of municipal budgets. */
public final class JusticeBank implements JusticePayments.Bank {
    public boolean debit(JusticePayment p){return transfer(p,false);}public boolean credit(JusticePayment p){return transfer(p,true);}
    private boolean transfer(JusticePayment p,boolean incoming){ApiServices.primaryThread();if(!TownyEconomyHandler.isActive())return false;
        boolean city=p.kind()==JusticePayment.Kind.BOUNTY_FUND||incoming&&p.kind()!=JusticePayment.Kind.BOUNTY_PAY;var town=city?TownyAPI.getInstance().getTown(p.town()):null;var resident=city?null:TownyAPI.getInstance().getResident(p.resident());if(town==null&&resident==null)return false;Account account=city?town.getAccount():resident.getAccount();String token="[justice:"+p.id()+"]";var evidence=new PaymentEvidence(account,incoming,token,p.amount());
        var observer=new AccountObserver(){public void withdrew(Account a,double n,String why){evidence.observe(a,false,n,why);}public void deposited(Account a,double n,String why){evidence.observe(a,true,n,why);}};
        synchronized(account){account.addObserver(observer);try{boolean result;double amount=p.amount()/100.0;String reason=token+" Суд и розыск";
            try{result=city?(incoming?TreasuryAccess.deposit(town,p.kind()==JusticePayment.Kind.BOUNTY_REFUND?"army":"free","justice",p.kind()==JusticePayment.Kind.BOUNTY_REFUND,amount,reason):TreasuryAccess.withdraw(town,"army","justice",amount,reason)):(incoming?account.deposit(amount,reason):account.withdraw(amount,reason));}
            catch(RuntimeException ex){if(evidence.observed())return true;throw ex;}return evidence.result(result);
        }finally{account.removeObserver(observer);}}
    }
}
