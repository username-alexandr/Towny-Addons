package cc.crafto.ui.server;

import cc.crafto.ui.MenuSchema;
import com.google.gson.*;
import java.util.*;
import static cc.crafto.ui.Protocol.*;

/** An explicit allowlist of plugin-owned inventory classes. No reflection into plugin state. */
record AddonCatalog(boolean enabled, String theme, int columns, int width, int height, Map<String,Entry> entries) {
    record Entry(String id,String plugin,String label,String command,String permission,Set<String> holders,Set<String> inventoryHolders,List<String> inventoryTitleWords,String layout,Map<String,String> holderPermissions) {
        String holderPermission(String holder){return holderPermissions.getOrDefault(holder,permission);}
    }
    static AddonCatalog empty(){return new AddonCatalog(false,"dark",3,620,420,Map.of());}
    static AddonCatalog parse(JsonObject o){
        validateTree(o,0);
        Map<String,Entry> entries=new LinkedHashMap<>();
        JsonArray list=o.getAsJsonArray("modules");if(list==null||list.size()>64)throw new IllegalArgumentException("Invalid addon list");
        Set<String> owners=new HashSet<>();
        for(JsonElement element:list){
            JsonObject e=element.getAsJsonObject();String id=str(e,"id","");MenuSchema.requireId(id);
            String plugin=str(e,"plugin","");if(!plugin.matches("[A-Za-z0-9_]{1,80}"))throw new IllegalArgumentException("Invalid addon plugin");
            if(!owners.add(plugin))throw new IllegalArgumentException("Duplicate addon plugin: "+plugin);
            String command=str(e,"command","");
            if(!command.matches("[a-zA-Z0-9_:.-]+(?: [a-zA-Z0-9_.-]+)*")||command.length()>160)throw new IllegalArgumentException("Invalid addon command");
            String permission=str(e,"permission","");if(permission.isBlank())throw new IllegalArgumentException("Addon permission required");
            String layout=str(e,"layout",plugin.equals("NeverLandTownyMarket")?"market":"cards");
            if(!Set.of("market","cards").contains(layout))throw new IllegalArgumentException("Unknown addon layout");
            Set<String> holders=classes(e,"holders"),inventory=classes(e,"inventoryHolders");
            if(!holders.containsAll(inventory))throw new IllegalArgumentException("Inventory holder is not allowlisted");
            Map<String,String> holderPermissions=new HashMap<>();
            if(e.has("holderPermissions"))for(var rule:e.getAsJsonObject("holderPermissions").entrySet()){
                if(!holders.contains(rule.getKey())||rule.getValue().getAsString().isBlank())throw new IllegalArgumentException("Invalid holder permission");
                holderPermissions.put(rule.getKey(),rule.getValue().getAsString());
            }
            if(entries.putIfAbsent(id,new Entry(id,plugin,str(e,"label",id),command,permission,holders,inventory,words(e),layout,Map.copyOf(holderPermissions)))!=null)throw new IllegalArgumentException("Duplicate addon id");
        }
        int columns=integer(o,"columns",3),width=integer(o,"width",620),height=integer(o,"height",420);
        if(columns<1||columns>4||width<480||width>900||height<320||height>650)throw new IllegalArgumentException("Addon layout out of range");
        return new AddonCatalog(bool(o,"enabled",true),str(o,"theme","dark"),columns,width,height,Map.copyOf(entries));
    }
    private static List<String> words(JsonObject e){
        if(!e.has("inventoryTitleWords"))return List.of();var out=new ArrayList<String>();
        for(JsonElement v:e.getAsJsonArray("inventoryTitleWords")){String w=v.getAsString().strip().toLowerCase(Locale.ROOT);if(w.length()<3||w.length()>80)throw new IllegalArgumentException("Invalid inventory title word");out.add(w);}return List.copyOf(out);
    }
    private static Set<String> classes(JsonObject e,String key){
        Set<String> out=new HashSet<>();if(!e.has(key))return Set.of();
        for(JsonElement value:e.getAsJsonArray(key)){String n=value.getAsString();if(!n.matches("[a-zA-Z_$][a-zA-Z0-9_.$]{1,200}"))throw new IllegalArgumentException("Invalid holder class");out.add(n);}
        return Set.copyOf(out);
    }
}
