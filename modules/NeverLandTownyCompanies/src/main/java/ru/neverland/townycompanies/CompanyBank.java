package ru.neverland.townycompanies;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.economy.Account;
import com.palmergames.bukkit.towny.object.economy.AccountObserver;
import ru.neverland.integration.TreasuryAccess;
import static ru.neverland.townycompanies.CompanyData.*;

public final class CompanyBank implements CompanyLedger.Bank {
    @Override public boolean transfer(Payment p) {
        if(!com.palmergames.bukkit.towny.TownyEconomyHandler.isActive())return false;
        boolean city=p.purpose()==Purpose.TAX||p.purpose()==Purpose.REFUND;
        var town=city?TownyAPI.getInstance().getTown(p.account()):null;
        var resident=city?null:TownyAPI.getInstance().getResident(p.account());
        if(town==null&&resident==null)return false;
        Account account=city?town.getAccount():resident.getAccount();boolean incoming=p.purpose()!=Purpose.DEPOSIT;
        String token="[companies:"+p.id()+"]";var evidence=new PaymentEvidence(account,incoming,token,p.amount());
        var observer=new AccountObserver(){
            public void withdrew(Account source,double amount,String reason){evidence.observe(source,false,amount,reason);}
            public void deposited(Account source,double amount,String reason){evidence.observe(source,true,amount,reason);}
        };
        synchronized(account) {
            account.addObserver(observer);
            try {
                String reason=token+" Предприятия города";double amount=p.amount()/100.0;boolean result;
                try {result=city?TreasuryAccess.deposit(town,p.purpose()==Purpose.REFUND?"infrastructure":"free",p.purpose()==Purpose.REFUND?"contracts":"tax",p.purpose()==Purpose.REFUND,amount,reason)
                            :incoming?account.deposit(amount,reason):account.withdraw(amount,reason);}
                catch(RuntimeException ex){if(evidence.observed())return true;throw ex;}
                return evidence.result(result);
            } finally {account.removeObserver(observer);}
        }
    }
}
