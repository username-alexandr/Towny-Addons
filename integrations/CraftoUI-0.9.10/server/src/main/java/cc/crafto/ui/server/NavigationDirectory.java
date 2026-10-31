package cc.crafto.ui.server;

import com.google.gson.*;
import java.util.*;
import static cc.crafto.ui.Protocol.*;

/** A single visible entry per destination, retaining old favorite IDs as aliases. */
final class NavigationDirectory {
    final JsonArray entries=new JsonArray();
    private final Map<String,JsonObject> destinations=new LinkedHashMap<>();
    void add(String id,String label,String keywords,String destination){
        JsonObject existing=destination.isBlank()?null:destinations.get(destination);
        if(existing!=null){
            JsonArray aliases=existing.getAsJsonArray("aliases");JsonPrimitive alias=new JsonPrimitive(id);
            if(!str(existing,"id","").equals(id)&&!aliases.contains(alias))aliases.add(alias);
            String combined=str(existing,"keywords","")+" "+label+" "+keywords;
            existing.addProperty("keywords",combined.substring(0,Math.min(800,combined.length())));return;
        }
        JsonObject node=object("id",id,"label",label,"keywords",keywords);node.add("aliases",new JsonArray());
        entries.add(node);if(!destination.isBlank())destinations.put(destination,node);
    }
}
