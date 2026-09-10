package ru.neverland.townyresources.model;
import java.util.*;
public record Reservation(UUID town, Map<Resource,Long> amounts, Status status) {
    public enum Status { HELD, CONSUMED, RELEASED }
    public Reservation { Objects.requireNonNull(town); Objects.requireNonNull(status); amounts=Amounts.copy(amounts); }
    public Reservation finish(boolean consume) { return new Reservation(town,amounts,consume?Status.CONSUMED:Status.RELEASED); }
}
