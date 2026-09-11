package ru.neverland.mintcontracts.integration;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.object.economy.*;
import ru.neverland.integration.TreasuryAccess;
import ru.neverland.mintcontracts.model.MunicipalPayment;
import ru.neverland.mintcontracts.service.MunicipalPayments;

public final class MunicipalBank implements MunicipalPayments.Bank {
    public boolean transfer(MunicipalPayment p){
        if(!TownyEconomyHandler.isActive())return false;
        boolean reserve=p.kind()==MunicipalPayment.Kind.RESERVE,player=p.kind()==MunicipalPayment.Kind.REWARD;
        var town=player?null:TownyAPI.getInstance().getTown(p.town());var resident=player?TownyAPI.getInstance().getResident(p.account()):null;
        if(player?resident==null:town==null)return false;
        Account account=player?resident.getAccount():town.getAccount();String token="[contracts:"+p.id()+"]";var evidence=new PaymentEvidence(account,!reserve,token,p.cents());
        var observer=new AccountObserver(){public void withdrew(Account a,double n,String why){evidence.observe(a,false,n,why);}public void deposited(Account a,double n,String why){evidence.observe(a,true,n,why);}};
        synchronized(account){account.addObserver(observer);try{boolean result;double amount=p.cents()/100.0;String why=token+" Муниципальный контракт";
            try {result=reserve?TreasuryAccess.withdraw(town,"infrastructure","contracts",amount,why):player?account.deposit(amount,why):TreasuryAccess.deposit(town,"infrastructure","contracts",true,amount,why);}
            catch(RuntimeException ex){if(evidence.observed())return true;throw ex;}return evidence.result(result);
        }finally{account.removeObserver(observer);}}
    }
}
