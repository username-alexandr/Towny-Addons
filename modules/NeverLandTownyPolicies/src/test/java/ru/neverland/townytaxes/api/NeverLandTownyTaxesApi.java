package ru.neverland.townytaxes.api;
import java.util.UUID;
public interface NeverLandTownyTaxesApi extends ru.neverland.core.ApiContract {default java.util.Set<String> capabilities(){return java.util.Set.of("municipalTaxConfigured");}boolean municipalTaxConfigured(UUID town);}
