package cc.crafto.ui;

import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;
import static cc.crafto.ui.Protocol.*;

/** Validated declarative UI; no downloaded classes, scripts, HTML or URLs. */
public final class MenuSchema {
    private MenuSchema() {}
    public static final Set<String> TYPES = Set.of("button","text","image","avatar","progress","panel","grid","list","notification","dialog");
    public static void validate(JsonObject menu) {
        validateTree(menu, 0);
        requireId(str(menu,"id",""));
        dimension(menu,"width",240,1600); dimension(menu,"height",160,1200);
        if (!menu.has("components") || !menu.get("components").isJsonArray()) throw new IllegalArgumentException("components must be an array");
        Set<String> ids = new HashSet<>();
        visit(menu.getAsJsonArray("components"), node -> {
            String id = str(node,"id",""); requireId(id);
            if (!ids.add(id) || ids.size()>128) throw new IllegalArgumentException("Duplicate component or too many components: "+id);
            if (!TYPES.contains(str(node,"type",""))) throw new IllegalArgumentException("Unknown component type: "+id);
            if(node.has("layoutRole")){
                String type=str(node,"type",""),role=str(node,"layoutRole","");
                boolean valid=switch(role){
                    case "aside"->type.equals("panel")&&node.has("children")&&node.get("children").isJsonArray();
                    case "content"->Set.of("grid","list").contains(type)&&node.has("children")&&node.get("children").isJsonArray();
                    case "description"->Set.of("text","notification").contains(type);
                    case "actions"->type.equals("button");
                    default->false;
                };
                if(!valid)throw new IllegalArgumentException("Invalid layoutRole: "+id);
            }
            for (String k: List.of("x","y","w","h","gap","rowHeight","columns","minCellWidth")) {
                if (node.has(k)) { double n=node.get(k).getAsDouble(); if(!Double.isFinite(n)||n<0||n>1600) throw new IllegalArgumentException("Invalid dimension: "+id+"."+k); }
            }
            if(node.has("columns") && integer(node,"columns",1)<1) throw new IllegalArgumentException("columns must be positive");
            if(node.has("icon")) requireResource(str(node,"icon",""));
            if(node.has("texture")) requireResource(str(node,"texture",""));
            if(node.has("action")) requireId(str(node,"action",""));
            if(node.has("directoryId")) requireId(str(node,"directoryId",""));
        });
        encode(Kind.OPEN, object("screen",menu));
    }
    private static void dimension(JsonObject m,String k,int min,int max) {
        int n=integer(m,k,0); if(n<min||n>max) throw new IllegalArgumentException(k+" out of range");
    }
    public static void requireId(String id) {
        if (!id.matches("[a-z0-9_][a-z0-9_.:/-]{0,95}") || id.contains("..")) throw new IllegalArgumentException("Invalid id: "+id);
    }
    public static void requireResource(String id) {
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_][a-z0-9_./-]*") || id.contains("..") || id.contains("//")) throw new IllegalArgumentException("Invalid resource: "+id);
    }
    public static void visit(JsonArray nodes, Consumer<JsonObject> consumer) {
        for(JsonElement e:nodes) {
            JsonObject n=e.getAsJsonObject(); consumer.accept(n);
            if(n.has("children")) visit(n.getAsJsonArray("children"),consumer);
        }
    }
    public static JsonObject find(JsonObject menu,String id) {
        final JsonObject[] found={null}; visit(menu.getAsJsonArray("components"),n->{if(str(n,"id","").equals(id))found[0]=n;}); return found[0];
    }
    public static String bind(String text,Map<String,String> data) {
        var m=java.util.regex.Pattern.compile("\\$\\{([a-zA-Z0-9_.:-]+)}").matcher(text);
        StringBuilder b=new StringBuilder();
        while(m.find()) m.appendReplacement(b,java.util.regex.Matcher.quoteReplacement(data.getOrDefault(m.group(1),"—")));
        m.appendTail(b); return b.length()>4096?b.substring(0,4096):b.toString();
    }
}
