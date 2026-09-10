package ru.neverland.minttrade.api;

import java.util.List;
import java.util.UUID;

public interface MintTownyTradeApi {
    List<TradeSnapshot> activeRoutes(UUID townId);
    default List<SupplySnapshot> supplyContracts(UUID townId){return List.of();}
    int marketLevel(UUID townId);
    double tariffPercent(UUID townId);
}
