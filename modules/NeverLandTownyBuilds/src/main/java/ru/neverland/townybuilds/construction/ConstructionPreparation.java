package ru.neverland.townybuilds.construction;

import java.util.List;
import java.util.UUID;

public record ConstructionPreparation(Status status, UUID townId, ConstructionSite site,
                                      BlueprintPlan plan, List<String> details) {
    public enum Status {
        READY,
        ALREADY_ACTIVE,
        NO_GROUND_TARGET,
        OUTSIDE_TOWN,
        UNEVEN_GROUND,
        OBSTRUCTED,
        UNKNOWN_BLUEPRINT
    }

    public static ConstructionPreparation failed(Status status, String detail) {
        return new ConstructionPreparation(status, null, null, null,
                detail == null || detail.isBlank() ? List.of() : List.of(detail));
    }
}
