package ru.neverland.mintexpeditions.model;
import java.util.UUID;
public record ExpeditionHistory(UUID id, UUID leaderId, String definitionId, int participants,
                                int objectives, int kills, ExpeditionStatus status, long endedAt) {}
