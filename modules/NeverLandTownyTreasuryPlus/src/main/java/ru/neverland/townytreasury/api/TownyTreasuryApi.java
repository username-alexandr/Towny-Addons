package ru.neverland.townytreasury.api;
import java.util.*;
import ru.neverland.townytreasury.model.CityLedger;
/** Money and budget writes require the server thread. No extra economy account is created. */
public interface TownyTreasuryApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("canSpend", "spend", "treasury"); }
    boolean canSpend(UUID town,String category,double amount);
    boolean spend(UUID town,String category,String source,double amount,String reason);
    Optional<CityLedger> treasury(UUID town);
}
