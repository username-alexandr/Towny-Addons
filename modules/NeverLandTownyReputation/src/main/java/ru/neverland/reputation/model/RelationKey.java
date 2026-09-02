package ru.neverland.reputation.model;

import java.util.UUID;

public record RelationKey(ReputationScope scope, UUID first, UUID second) {
    public RelationKey {
        if (scope == null || first == null || second == null) throw new IllegalArgumentException("Пустая сторона репутации");
        if (scope != ReputationScope.PLAYER && first.toString().compareTo(second.toString()) > 0) {
            UUID swap = first; first = second; second = swap;
        }
    }
    public static RelationKey of(ReputationScope scope, UUID first, UUID second) { return new RelationKey(scope, first, second); }
    public String storageKey() { return first + (scope == ReputationScope.PLAYER ? ">" : "~") + second; }
    public boolean involves(UUID id) { return first.equals(id) || second.equals(id); }
    public UUID other(UUID id) { return first.equals(id) ? second : first; }
}
