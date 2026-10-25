package ru.neverland.townybuilds.construction;
import java.util.UUID;
/** A reusable reference to the town's resource fund, never a bearer token for money or resources. */
public record PlacementDraft(UUID id, int level) {
    public PlacementDraft { if(id==null||level<1||level>5)throw new IllegalArgumentException("Повреждён чертёж размещения"); }
    public static PlacementDraft create(int level){return new PlacementDraft(UUID.randomUUID(),level);}
}
