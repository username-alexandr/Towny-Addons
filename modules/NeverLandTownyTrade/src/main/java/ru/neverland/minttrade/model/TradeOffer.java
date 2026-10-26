package ru.neverland.minttrade.model;

import java.util.UUID;

public record TradeOffer(UUID id, UUID sellerId, UUID buyerId, String exportId, long createdAt, long expiresAt) {
    public String shortId() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return id.toString().substring(0, 8); } }
}
