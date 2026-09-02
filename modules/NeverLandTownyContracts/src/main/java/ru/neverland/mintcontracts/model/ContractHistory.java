package ru.neverland.mintcontracts.model;

import java.util.UUID;
public record ContractHistory(UUID contractId, String templateId, long endedAt, int progress, int goal,
                              double paid, double refunded, ContractStatus status) {}
