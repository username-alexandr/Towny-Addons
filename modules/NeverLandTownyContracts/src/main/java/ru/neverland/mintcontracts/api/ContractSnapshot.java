package ru.neverland.mintcontracts.api;

import java.util.UUID;
public record ContractSnapshot(UUID id, UUID townId, String templateId, String name, String type,
                               int progress, int goal, double escrow, long expiresAt) {}
