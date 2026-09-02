package ru.neverland.minttrade.model;

import java.util.UUID;

public record TradeHistory(UUID caravanId, UUID sellerId, UUID buyerId, String exportId,
                           int amount, double price, double tariffs, long completedAt, CaravanStatus status) {}
