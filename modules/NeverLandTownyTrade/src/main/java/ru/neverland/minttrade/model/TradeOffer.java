package ru.neverland.minttrade.model;

import java.util.UUID;

public record TradeOffer(UUID id, UUID sellerId, UUID buyerId, String exportId, long createdAt, long expiresAt) {
    public String shortId() { synchronized (ru.neverland.core.SuiteState.lock()) { return id.toString().substring(0, 8); } }
}
