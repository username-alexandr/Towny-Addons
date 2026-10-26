package ru.neverland.mintcontracts.integration;
import java.util.UUID;

/** Optional, versioned service lookup avoids a circular plugin load order. */
public final class CompaniesBridge {
    private Object call(String method,Class<?>[] types,Object... args) {
        try {return ru.neverland.core.ApiServices.call("NeverLandTownyCompanies","ru.neverland.townycompanies.api.CompaniesApi",method,types,args);}
        catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return null;}
    }
    public boolean allow(String method,UUID actor,UUID company,UUID town){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return Boolean.TRUE.equals(call(method,new Class<?>[]{UUID.class,UUID.class,UUID.class},actor,company,town));} }
    public String name(UUID company){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {Object name=call("companyName",new Class<?>[]{UUID.class},company);return name instanceof String s?s:"Компания "+company.toString().substring(0,8);} }
    public boolean settle(UUID contract,UUID company,UUID town,long payout,long refund){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return Boolean.TRUE.equals(call("settleEscrow",new Class<?>[]{UUID.class,UUID.class,UUID.class,long.class,long.class},contract,company,town,payout,refund));} }
}
