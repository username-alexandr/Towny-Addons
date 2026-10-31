package cc.crafto.ui.client;
import cc.crafto.ui.MenuSchema;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static cc.crafto.ui.Protocol.*;
/** Bundled fallback does not override server colours; resource packs may explicitly override them. */
final class UiTheme {
    static JsonObject resolve(JsonObject server,boolean forceDark){
        JsonObject out=server.deepCopy();
        try {
            if(forceDark||out.isEmpty())try(var in=UiTheme.class.getResourceAsStream("/assets/craftoui/defaults/dark.json")){
                if(in!=null)merge(out,JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject());
            }
            String id=forceDark?"dark":str(out,"id","dark");MenuSchema.requireId(id);
            var resource=Minecraft.getInstance().getResourceManager().getResource(Identifier.parse("craftoui:themes/"+id+".json"));
            if(resource.isPresent())try(var reader=resource.get().openAsReader()){merge(out,JsonParser.parseReader(reader).getAsJsonObject());}
        }catch(Exception e){CraftoUIClient.LOG.warn("Theme override ignored: {}",e.toString());}
        return out;
    }
    private static void merge(JsonObject into,JsonObject from){validateTree(from,0);from.entrySet().forEach(e->into.add(e.getKey(),e.getValue()));}
    static int color(JsonObject theme,String key){
        String fallback=switch(key){case "text"->"#FFF1F4FA";case "muted"->"#FFADB6C7";case "accent"->"#FF54D797";case "shade"->"#A0000000";default->"#FF343B4A";};
        try{return (int)Long.parseLong(str(theme,key,fallback).substring(1),16);}catch(Exception e){return (int)Long.parseLong(fallback.substring(1),16);}
    }
}
