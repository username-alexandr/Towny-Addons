package ru.neverland.governance.gui;

import java.util.List;
import java.util.UUID;
import ru.neverland.governance.model.OfficeDefinition;
import ru.neverland.governance.model.OfficeHolder;

/** The confirmation describes a specific council state; changed seats require a fresh review. */
public record OfficeConfirmation(UUID town, UUID actor, UUID resident, boolean dismiss,
                                 OfficeDefinition office, List<OfficeHolder> holders, long createdAt) {
    public OfficeConfirmation { holders = List.copyOf(holders); }
    public boolean current(UUID town, UUID actor, OfficeDefinition office, List<OfficeHolder> holders, long now) {
        return this.town.equals(town) && this.actor.equals(actor) && this.office.equals(office)
                && this.holders.equals(holders) && now >= createdAt && now - createdAt < 60_000_000_000L;
    }
}
