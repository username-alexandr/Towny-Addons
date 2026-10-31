package cc.crafto.ui;
import java.util.*;

/** Session-only presentation state. No item snapshots, commands, permissions or confirmation tokens. */
public final class MenuViewMemory {
    public record Position(int page,int selected,int scroll,Map<String,Double> lists){public Position{lists=Map.copyOf(lists);}}
    private final LinkedHashMap<String,Position> entries=new LinkedHashMap<>();
    public Position get(String key){return entries.getOrDefault(key,new Position(0,-1,0,Map.of()));}
    public void put(String key,int page,int selected,int scroll,Map<String,Double> lists){
        if(key.isEmpty()||key.length()>600)return;entries.remove(key);
        entries.put(key,new Position(Math.max(0,page),selected,Math.max(0,scroll),lists));
        while(entries.size()>64)entries.remove(entries.keySet().iterator().next());
    }
    public void clear(){entries.clear();}
}
