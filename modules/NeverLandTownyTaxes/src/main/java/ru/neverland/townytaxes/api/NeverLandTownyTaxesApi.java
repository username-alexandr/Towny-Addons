package ru.neverland.townytaxes.api;

import java.util.UUID;

public interface NeverLandTownyTaxesApi {
    boolean isTradeBlocked(UUID firstTown, UUID secondTown);
    double tradePreferenceMultiplier(UUID firstTown, UUID secondTown);
    double taxDebt(UUID subjectId);
    double serverReserve();
    boolean municipalTaxConfigured(UUID town);
}
