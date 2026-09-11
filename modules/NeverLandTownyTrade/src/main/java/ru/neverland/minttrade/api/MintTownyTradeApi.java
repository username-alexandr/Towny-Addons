package ru.neverland.minttrade.api;

import java.util.List;
import java.util.UUID;

public interface MintTownyTradeApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("activeRoutes", "marketLevel", "supplyContracts", "tariffPercent"); }
    List<TradeSnapshot> activeRoutes(UUID townId);
    default List<SupplySnapshot> supplyContracts(UUID townId){return List.of();}
    int marketLevel(UUID townId);
    double tariffPercent(UUID townId);
}
