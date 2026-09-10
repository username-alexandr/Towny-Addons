package ru.neverland.townytreasury.api;
import java.util.*;
import ru.neverland.townytreasury.model.CityLedger;
/** Money and budget writes require the server thread. No extra economy account is created. */
public interface TownyTreasuryApi {
    boolean canSpend(UUID town,String category,double amount);
    boolean spend(UUID town,String category,String source,double amount,String reason);
    Optional<CityLedger> treasury(UUID town);
}
