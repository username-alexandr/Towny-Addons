package cc.crafto.ui.server;

import cc.crafto.ui.*;
import com.google.gson.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static cc.crafto.ui.Protocol.*;

final class ConfigLoader {
    record Catalog(Map<String,JsonObject> menus,Map<String,JsonObject> themes,Map<String,JsonObject> actions, AddonCatalog addons, Map<String,JsonObject> quickLinks, CommandCatalog commands) {}
    static Catalog load(Path root) throws Exception {
        Map<String,JsonObject> menus=directory(root.resolve("menus"));
        Map<String,JsonObject> themes=directory(root.resolve("themes"));
        JsonObject actionsRoot=read(root.resolve("actions.json"));
        Map<String,JsonObject> actions=new LinkedHashMap<>();
        for(var e:actionsRoot.entrySet()) {
            MenuSchema.requireId(e.getKey()); JsonObject a=e.getValue().getAsJsonObject();
            String type=str(a,"type","");
            if(!Set.of("open","close","message","refresh","settings","commands","city","civic","player-command","proxy").contains(type)) throw new IllegalArgumentException("Unknown action type: "+type);
            if(type.equals("civic")&&!Set.of("planner","tree","city","materials","inbox","map","goals","treasury","history").contains(str(a,"route","")))throw new IllegalArgumentException("Invalid civic entry route");
            if(type.equals("commands")&&a.has("form")&&!str(a,"form","").matches("[A-Za-z0-9_-]{1,80}/[a-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid command form destination");
            if(type.equals("open")&&!menus.containsKey(str(a,"menu","")))throw new IllegalArgumentException("Unknown menu in action: "+e.getKey());
            if(type.equals("player-command")&&(str(a,"command","").isBlank()||str(a,"permission","").isBlank())) throw new IllegalArgumentException("Command action needs command and permission");
            if(type.equals("proxy")&&!str(a,"server","").matches("[a-zA-Z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid proxy server");
            actions.put(e.getKey(),a);
        }
        actions.putIfAbsent("ui:city",object("type","city","permission","craftoui.city"));
        actions.putIfAbsent("ui:planner",object("type","civic","route","planner","permission","craftoui.city","requiredPlugin","NeverLandTownyBuilds","requiresTown",true,"discoverable",true));
        actions.putIfAbsent("ui:tree",object("type","civic","route","tree","permission","craftoui.city","requiredPlugin","NeverLandTownyBuilds","requiresTown",true,"discoverable",true));
        actions.putIfAbsent("ui:create-town",object("type","commands","module","Towny","form","Towny/create-town","permission","towny.command.town.new","requiredPlugin","Towny","requiresNoTown",true,"discoverable",true));
        AddonCatalog addons=Files.exists(root.resolve("addons.json"))?AddonCatalog.parse(read(root.resolve("addons.json"))):AddonCatalog.empty();
        for(var e:addons.entries().values()){
            if(actions.putIfAbsent("addons:"+e.id(),object("type","player-command","command",e.command(),"permission",e.permission(),"requiredPlugin",e.plugin(),"closeBefore",true))!=null)throw new IllegalArgumentException("Reserved addon action: "+e.id());
        }
        for(String name:menus.keySet())actions.putIfAbsent("hub:"+name,object("type","open","menu",name));
        if(menus.containsKey("hub")&&object("type","open","menu","main").equals(actions.get("main")))actions.put("main",object("type","open","menu","hub"));
        for(JsonObject t:themes.values())validateTheme(t);
        if(addons.enabled()&&!themes.containsKey(addons.theme()))throw new IllegalArgumentException("Missing addon theme");
        for(JsonObject m:menus.values()) {
            MenuSchema.validate(m);
            if(!themes.containsKey(str(m,"theme","classic")))throw new IllegalArgumentException("Missing theme in "+str(m,"id",""));
            MenuSchema.visit(m.getAsJsonArray("components"),n->{
                String a=str(n,"action","");
                // External actions use an explicit namespace; they are disabled until their owner registers them.
                if(!a.isBlank()&&!a.contains(":")&&!actions.containsKey(a))throw new IllegalArgumentException("Missing action: "+a);
            });
        }
        if(!menus.containsKey("main"))throw new IllegalArgumentException("main menu is required");
        Map<String,JsonObject> links=new LinkedHashMap<>();
        if(Files.exists(root.resolve("quick-actions.json"))){
            var source=read(root.resolve("quick-actions.json"));
            if(source.size()>64)throw new IllegalArgumentException("Too many quick actions");
            for(var e:source.entrySet()){
                MenuSchema.requireId(e.getKey());var value=e.getValue().getAsJsonObject();
                if(str(value,"label","").isBlank()||str(value,"label","").length()>100||str(value,"keywords","").length()>200)throw new IllegalArgumentException("Invalid quick action label");
                if(!str(value,"command","").matches("[a-zA-Z0-9_:.-]+(?: [a-zA-Z0-9_.-]+)*")||str(value,"permission","").isBlank()||str(value,"command","").length()>160)throw new IllegalArgumentException("Quick action needs a fixed command and permission");
                var action=value.deepCopy();action.addProperty("type","player-command");action.addProperty("closeBefore",true);links.put(e.getKey(),action);
                // The same validated quick action can be placed in a configured menu.
                if(actions.putIfAbsent("quick:"+e.getKey(),action)!=null)throw new IllegalArgumentException("Reserved quick action: "+e.getKey());
            }
        }
        return new Catalog(Map.copyOf(menus),Map.copyOf(themes),Map.copyOf(actions),addons,Map.copyOf(links),Files.exists(root.resolve("command-ui.json"))?CommandCatalog.parse(read(root.resolve("command-ui.json"))):CommandCatalog.empty());
    }
    static void validateTheme(JsonObject t) {
        validateTree(t,0); MenuSchema.requireId(str(t,"id",""));
        for(String k:List.of("background","panel","button","hover","border","text","muted","accent","disabled","shade"))
            if(t.has(k)&&!str(t,k,"").matches("#[0-9a-fA-F]{8}"))throw new IllegalArgumentException("Theme color must be #AARRGGBB: "+k);
    }
    static Map<String,JsonObject> directory(Path dir) throws Exception {
        Map<String,JsonObject> out=new LinkedHashMap<>();
        try(var stream=Files.list(dir)) {
            var files=stream.filter(p->p.toString().matches(".*\\.(json|yml|yaml)$")).sorted().toList();
            if(files.size()>128)throw new IllegalArgumentException("Too many config files");
            for(Path p:files){JsonObject obj=read(p);String id=str(obj,"id","");MenuSchema.requireId(id);if(out.putIfAbsent(id,obj)!=null)throw new IllegalArgumentException("Duplicate id: "+id);}
        }
        return out;
    }
    static JsonObject read(Path p)throws Exception {
        if(Files.size(p)>262144)throw new IllegalArgumentException("Config too large: "+p.getFileName());
        String raw=Files.readString(p,StandardCharsets.UTF_8);
        JsonObject o;
        if(p.toString().endsWith(".json"))o=JsonParser.parseString(raw).getAsJsonObject();
        else {YamlConfiguration y=new YamlConfiguration();y.loadFromString(raw);o=JSON.toJsonTree(normalize(y)).getAsJsonObject();}
        validateTree(o,0);return o;
    }
    private static Object normalize(Object o) {
        if(o instanceof ConfigurationSection s){Map<String,Object> m=new LinkedHashMap<>();s.getValues(false).forEach((k,v)->m.put(k,normalize(v)));return m;}
        if(o instanceof Map<?,?> m){Map<String,Object> out=new LinkedHashMap<>();m.forEach((k,v)->out.put(k.toString(),normalize(v)));return out;}
        if(o instanceof List<?> l)return l.stream().map(ConfigLoader::normalize).toList();
        return o;
    }
}
