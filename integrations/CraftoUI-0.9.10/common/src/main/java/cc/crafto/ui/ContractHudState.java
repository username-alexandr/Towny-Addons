package cc.crafto.ui;

import com.google.gson.*;
import java.util.*;

/** Bounded display-only state. It never authorizes progress, inventory changes or rewards. */
public record ContractHudState(List<Entry> entries) {
    public static final int MAX_ENTRIES=20;
    public enum Status { ACTIVE, READY, PENDING, HELD, REVIEW, BLOCKED }
    public record Entry(String id,String title,int progress,int goal,boolean inventory,Status status,String targetKey,String kind) {
        public Entry(String id,String title,int progress,int goal,boolean inventory,Status status){this(id,title,progress,goal,inventory,status,"","");}
        public Entry {
            UUID.fromString(id);
            if(title==null||title.isBlank()||title.length()>160||title.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid title");
            if(goal<1||progress<0||progress>goal)throw new IllegalArgumentException("Invalid progress");
            Objects.requireNonNull(status);
            if(targetKey==null||(!targetKey.isEmpty()&&!targetKey.matches("(?:item|block|entity)\\.[a-z0-9_.-]{1,180}")))throw new IllegalArgumentException("Invalid target");
            if(kind==null||!Set.of("","DELIVERY","MINING","HUNTING","FISHING").contains(kind))throw new IllegalArgumentException("Invalid target kind");
        }
    }
    public ContractHudState { entries=List.copyOf(entries);if(entries.size()>MAX_ENTRIES||entries.stream().map(Entry::id).distinct().count()!=entries.size())throw new IllegalArgumentException("Invalid entries"); }
    public JsonObject json(){return Protocol.object("entries",entries);}
    public static ContractHudState parse(JsonObject body){
        JsonArray rows=body.getAsJsonArray("entries");
        if(rows==null||rows.size()>MAX_ENTRIES)throw new IllegalArgumentException("Invalid entries");
        List<Entry> values=new ArrayList<>();
        for(JsonElement element:rows){
            JsonObject r=element.getAsJsonObject();
            values.add(new Entry(r.get("id").getAsString(),r.get("title").getAsString(),exactInt(r,"progress"),exactInt(r,"goal"),r.get("inventory").getAsBoolean(),Status.valueOf(r.get("status").getAsString()),Protocol.str(r,"targetKey",""),Protocol.str(r,"kind","")));
        }
        return new ContractHudState(values);
    }
    private static int exactInt(JsonObject row,String key){return row.get(key).getAsBigDecimal().intValueExact();}
}
