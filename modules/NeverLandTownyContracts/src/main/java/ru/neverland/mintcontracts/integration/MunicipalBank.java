package ru.neverland.mintcontracts.integration;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.object.economy.*;
import ru.neverland.integration.TreasuryAccess;
import ru.neverland.mintcontracts.model.MunicipalPayment;
import ru.neverland.mintcontracts.service.MunicipalPayments;

public final class MunicipalBank implements MunicipalPayments.Bank {
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final java.util.Set<java.util.UUID> submitting=java.util.concurrent.ConcurrentHashMap.newKeySet();
    public MunicipalBank(org.bukkit.plugin.java.JavaPlugin plugin){this.plugin=plugin;}
    @Override public boolean asynchronous(MunicipalPayment p){return p.kind()==MunicipalPayment.Kind.RESERVE&&org.bukkit.Bukkit.getPluginManager().getPlugin("NeverLandTownyTreasuryPlus")!=null;}
    private static String reason(MunicipalPayment p){return "[contracts:"+p.id()+"] Муниципальный контракт";}
    @Override @SuppressWarnings("unchecked") public MunicipalPayments.Outcome poll(MunicipalPayment p)throws Exception{
        if(p.phase()!=MunicipalPayment.Phase.TREASURY||p.kind()!=MunicipalPayment.Kind.RESERVE)throw new IllegalArgumentException("Не подготовлен резерв казны");
        var api=ru.neverland.core.ApiServices.require("NeverLandTownyTreasuryPlus","ru.neverland.townytreasury.api.TownyTreasuryApi","requestDebit","debitStatus");
        var proof=(java.util.Map<String,Object>)api.invoke("debitStatus",new Class<?>[]{java.util.UUID.class},p.id());String status=String.valueOf(proof.get("status"));
        if(status.equals("ABSENT")){if(!p.id().toString().equals(String.valueOf(proof.get("operation"))))throw new IllegalArgumentException("Неверный ID ответа казны");if(submitting.add(p.id()))try{var future=(java.util.concurrent.CompletionStage<?>)api.invoke("requestDebit",new Class<?>[]{java.util.UUID.class,java.util.UUID.class,String.class,String.class,long.class,String.class,java.util.UUID.class,String.class},p.id(),p.town(),"infrastructure","contracts",p.cents(),reason(p),p.actor(),plugin.getName());future.whenComplete((v,e)->submitting.remove(p.id()));}catch(Exception error){submitting.remove(p.id());throw error;}return MunicipalPayments.Outcome.WAITING;}
        return proof(p,proof);
    }
    public static MunicipalPayments.Outcome proof(MunicipalPayment p,java.util.Map<String,Object> proof){
        long cents;try{cents=new java.math.BigDecimal(String.valueOf(proof.get("cents"))).longValueExact();}catch(RuntimeException invalid){throw new IllegalArgumentException("Неверная сумма квитанции казны");}
        if(!p.id().toString().equals(String.valueOf(proof.get("operation")))||!p.town().toString().equals(String.valueOf(proof.get("town")))||cents!=p.cents()||!"infrastructure".equals(proof.get("category"))||!"contracts".equals(proof.get("source"))||!"NeverLandTownyContracts".equals(proof.get("owner"))||!p.actor().toString().equals(String.valueOf(proof.get("actor")))||!reason(p).equals(proof.get("reason"))||!"DEBIT".equals(proof.get("kind")))throw new IllegalArgumentException("Квитанция казны не совпадает с резервом контракта");
        return switch(String.valueOf(proof.get("status"))){case "PAID"->Boolean.TRUE.equals(proof.get("backingAvailable"))?MunicipalPayments.Outcome.PAID:MunicipalPayments.Outcome.REVIEW;case "REJECTED"->MunicipalPayments.Outcome.REJECTED;case "PREPARED","INVOKING"->MunicipalPayments.Outcome.WAITING;case "REVIEW"->MunicipalPayments.Outcome.REVIEW;default->throw new IllegalArgumentException("Неизвестная квитанция казны");};
    }
    public boolean transfer(MunicipalPayment p){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        if(asynchronous(p))throw new IllegalStateException("Для установленной казны нужен подтверждаемый резерв");
        if(!TownyEconomyHandler.isActive())return false;
        boolean reserve=p.kind()==MunicipalPayment.Kind.RESERVE,player=p.kind()==MunicipalPayment.Kind.REWARD;
        var town=player?null:TownyAPI.getInstance().getTown(p.town());var resident=player?TownyAPI.getInstance().getResident(p.account()):null;
        if(player?resident==null:town==null)return false;
        Account account=player?resident.getAccount():town.getAccount();String token="[contracts:"+p.id()+"]";var evidence=new PaymentEvidence(account,!reserve,token,p.cents());
        var observer=new AccountObserver(){public void withdrew(Account a,double n,String why){ evidence.observe(a,false,n,why); }public void deposited(Account a,double n,String why){ evidence.observe(a,true,n,why); }};
        synchronized(account){account.addObserver(observer);try{boolean result;double amount=p.cents()/100.0;String why=reason(p);
            try {result=reserve?account.withdraw(amount,why):player?account.deposit(amount,why):TreasuryAccess.deposit(town,"infrastructure","contracts",true,amount,why);}
            catch(RuntimeException ex){if(evidence.observed())return true;throw ex;}return evidence.result(result);
        }finally{account.removeObserver(observer);}}
    } }
}
