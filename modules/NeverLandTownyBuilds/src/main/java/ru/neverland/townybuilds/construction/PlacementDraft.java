package ru.neverland.townybuilds.construction;
import java.util.UUID;
/** A reusable reference to the town's resource fund, never a bearer token for money or resources. */
public record PlacementDraft(UUID id, int level,String blueprintSet) {
    public PlacementDraft(UUID id,int level){this(id,level,"");}
    public PlacementDraft { if(id==null||level<1||level>5||blueprintSet==null||!blueprintSet.isEmpty()&&!blueprintSet.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Повреждён чертёж размещения"); }
    public static PlacementDraft create(int level){return new PlacementDraft(UUID.randomUUID(),level);}
    public static PlacementDraft create(int level,String pin){return new PlacementDraft(UUID.randomUUID(),level,pin);}
}
