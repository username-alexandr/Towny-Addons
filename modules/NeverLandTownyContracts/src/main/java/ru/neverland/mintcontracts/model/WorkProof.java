package ru.neverland.mintcontracts.model;
import java.util.Objects;
import java.util.UUID;
public record WorkProof(UUID actor,long placedAt) {
    public WorkProof{Objects.requireNonNull(actor);if(placedAt<0)throw new IllegalArgumentException("Некорректное время проверки");}
}
