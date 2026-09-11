package ru.neverland.townytaxes.api;

import java.util.UUID;

public interface NeverLandTownyTaxesApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("isTradeBlocked", "municipalTaxConfigured", "serverReserve", "taxDebt", "tradePreferenceMultiplier"); }
    boolean isTradeBlocked(UUID firstTown, UUID secondTown);
    double tradePreferenceMultiplier(UUID firstTown, UUID secondTown);
    double taxDebt(UUID subjectId);
    double serverReserve();
    boolean municipalTaxConfigured(UUID town);
}
