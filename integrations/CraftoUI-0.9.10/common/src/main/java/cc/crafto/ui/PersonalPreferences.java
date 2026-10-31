package cc.crafto.ui;

import java.util.*;

/** Local preferences only; never an authorization source. One profile per server and player. */
public final class PersonalPreferences {
    public boolean automatic=true, notifications=true, summary=true;
    public float x=1, y=.5f, scale=1;
    public int rows=3;
    public List<String> pinned=new ArrayList<>(), favorites=new ArrayList<>(), announced=new ArrayList<>();
    public void sanitize(){
        x=finite(x,0,1,1);y=finite(y,0,1,.5f);scale=finite(scale,.65f,1.3f,1);rows=Math.clamp(rows,1,5);
        pinned=clean(pinned,20,100);favorites=clean(favorites,32,240);announced=clean(announced,128,100);
    }
    private static float finite(float n,float min,float max,float fallback){return Float.isFinite(n)?Math.clamp(n,min,max):fallback;}
    private static List<String> clean(List<String> values,int limit,int length){return values==null?new ArrayList<>():new ArrayList<>(values.stream().filter(Objects::nonNull).filter(v->!v.isBlank()&&v.length()<=length).distinct().limit(limit).toList());}
    public List<ContractHudState.Entry> selected(List<ContractHudState.Entry> entries){
        Map<String,ContractHudState.Entry> available=new LinkedHashMap<>();entries.forEach(e->available.put(e.id(),e));
        List<ContractHudState.Entry> result=new ArrayList<>();
        for(String id:pinned){var entry=available.remove(id);if(entry!=null)result.add(entry);}
        if(automatic)result.addAll(available.values());return List.copyOf(result);
    }
    public void manual(List<ContractHudState.Entry> current){if(automatic){pinned=new ArrayList<>(selected(current).stream().map(ContractHudState.Entry::id).toList());automatic=false;}}
    public void pin(String id,List<ContractHudState.Entry> current){manual(current);pinned.removeIf(key->current.stream().noneMatch(e->e.id().equals(key)));if(!pinned.remove(id)&&pinned.size()<20)pinned.add(id);}
    public void move(String id,int direction,List<ContractHudState.Entry> current){manual(current);int i=pinned.indexOf(id),next=i+direction;if(i>=0&&next>=0&&next<pinned.size())Collections.swap(pinned,i,next);}
    public boolean favorite(String id){if(favorites.remove(id))return false;if(favorites.size()<32)favorites.add(id);return favorites.contains(id);}
    public boolean remapFavorites(Map<String,String> aliases){
        var mapped=new ArrayList<>(favorites.stream().map(id->aliases.getOrDefault(id,id)).distinct().toList());
        if(mapped.equals(favorites))return false;favorites=mapped;return true;
    }
    public boolean announce(String id){if(announced.contains(id))return false;announced.add(id);while(announced.size()>128)announced.removeFirst();return true;}
}
