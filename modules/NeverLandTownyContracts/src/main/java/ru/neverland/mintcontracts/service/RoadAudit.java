package ru.neverland.mintcontracts.service;
import ru.neverland.mintcontracts.model.*;
import java.util.*;

/** Unknown/unloaded cells never satisfy a road. Existing paving must remain intact too. */
public final class RoadAudit {
    public enum Cell { UNKNOWN, INVALID, VALID }
    public interface Inspector { Cell inspect(int x,int y,int z); }
    public record Result(Map<UUID,Integer> contributions,Set<String> invalidProofs,boolean complete) {}
    public static Result inspect(WorkArea area,Map<String,WorkProof> proofs,long now,long stableMillis,Inspector inspector){
        Map<UUID,Integer> counts=new LinkedHashMap<>();Set<String> invalid=new HashSet<>();boolean complete=true;
        for(int x=area.minX();x<=area.maxX();x++)for(int z=area.minZ();z<=area.maxZ();z++){
            String key=WorkArea.key(x,z);Cell cell=inspector.inspect(x,area.y(),z);WorkProof proof=proofs.get(key);
            if(cell==Cell.INVALID&&proof!=null)invalid.add(key);
            if(cell!=Cell.VALID){complete=false;continue;}
            if(area.required().contains(key)){
                if(proof==null||now<proof.placedAt()||now-proof.placedAt()<stableMillis){complete=false;continue;}
                counts.merge(proof.actor(),1,Integer::sum);
            }
        }
        return new Result(Map.copyOf(counts),Set.copyOf(invalid),complete&&counts.values().stream().mapToInt(Integer::intValue).sum()==area.required().size());
    }
}
