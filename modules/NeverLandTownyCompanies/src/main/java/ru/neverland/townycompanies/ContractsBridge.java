package ru.neverland.townycompanies;
import java.util.*;
import org.bukkit.entity.Player;

public final class ContractsBridge {
    private Object call(String method,Class<?>[] types,Object... args) {
        try {return ru.neverland.core.ApiServices.call("NeverLandTownyContracts","ru.neverland.mintcontracts.api.MintTownyContractsApi",method,types,args);}
        catch(ReflectiveOperationException|RuntimeException|LinkageError ex){throw new IllegalStateException("Публичный API городских контрактов недоступен",ex);}
    }
    @SuppressWarnings("unchecked") public List<Map<String,Object>> offers(UUID town) {return (List<Map<String,Object>>)call("companyOffers",new Class<?>[]{UUID.class},town);}
    public int count(UUID company) {return ((Number)call("companyActiveCount",new Class<?>[]{UUID.class},company)).intValue();}
    public boolean take(Player player,UUID company,UUID contract) {return Boolean.TRUE.equals(call("takeCompanyContract",new Class<?>[]{Player.class,UUID.class,UUID.class},player,company,contract));}
    public boolean release(Player player,UUID company,UUID contract) {return Boolean.TRUE.equals(call("releaseCompanyContract",new Class<?>[]{Player.class,UUID.class,UUID.class},player,company,contract));}
}
