package ru.neverland.minttrade.model;

import java.util.UUID;

public record TradeOffer(UUID id, UUID sellerId, UUID buyerId, String exportId, long createdAt, long expiresAt) {
    public String shortId() { return id.toString().substring(0, 8); }
}
