package ru.neverland.minttrade.service;

import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.minttrade.api.MintTownyTradeApi;
import ru.neverland.minttrade.api.TradeSnapshot;
import ru.neverland.minttrade.integration.TownyHook;

import java.util.List;
import java.util.UUID;

public final class TradeApiService implements MintTownyTradeApi {
    private final TownyHook towny; private final TradeService trade;
    public TradeApiService(TownyHook towny, TradeService trade) { this.towny = towny; this.trade = trade; }
    @Override public List<TradeSnapshot> activeRoutes(UUID townId) { return trade.repository().caravans().stream()
            .filter(value -> value.sellerId().equals(townId) || value.buyerId().equals(townId))
            .map(value -> new TradeSnapshot(value.id(), value.sellerId(), value.buyerId(), value.exportId(), value.totalCargo(),
                    value.basePrice(), value.arrivesAt(), value.progress(System.currentTimeMillis()), value.campStops())).toList(); }
    @Override public int marketLevel(UUID townId) { Town town = towny.town(townId); return trade.marketLevel(town); }
    @Override public double tariffPercent(UUID townId) { Town town = towny.town(townId); return town == null ? 0 : trade.tariff(town); }
}
