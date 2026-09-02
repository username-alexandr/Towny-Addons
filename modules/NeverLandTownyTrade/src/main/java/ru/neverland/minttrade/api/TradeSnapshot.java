package ru.neverland.minttrade.api;

import java.util.UUID;

public record TradeSnapshot(UUID caravanId, UUID sellerTownId, UUID buyerTownId, String exportId,
                            int cargo, double price, long arrivalTime, double progress, int campStops) {}
