package ru.neverland.townytaxes.api;

import java.util.UUID;
import java.util.*;

public interface NeverLandTownyTaxesApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("isTradeBlocked", "municipalTaxConfigured", "serverReserve", "taxDebt", "tradePreferenceMultiplier", "taxPreview", "taxReceipts", "taxPayments"); }
    boolean isTradeBlocked(UUID firstTown, UUID secondTown);
    double tradePreferenceMultiplier(UUID firstTown, UUID secondTown);
    double taxDebt(UUID subjectId);
    double serverReserve();
    boolean municipalTaxConfigured(UUID town);
    default Collection<Map<String,Object>> taxPreview(UUID player,long incomeCents,long transactionCents){throw new UnsupportedOperationException();}
    default Collection<Map<String,Object>> taxReceipts(UUID subject){throw new UnsupportedOperationException();}
    default Collection<Map<String,Object>> taxPayments(){throw new UnsupportedOperationException();}
}
