package cc.crafto.ui.server;
import cc.crafto.ui.Protocol;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import com.google.gson.*;
/** Upgrades unmodified defaults, with an on-disk backup before replacement. */
final class DefaultMenuMigration {
    static void apply(Path root)throws IOException{
        apply(root,"0.5.0",List.of("profile.json","help.yml","main.json"));
        apply(root,"0.5.1",List.of("economy.json"));
        apply(root,"0.6.0",List.of("town.json"));
        for(String file:List.of("menus/main.json","menus/hub.json","menus/town.json","menus/economy.json","menus/adventures.json","menus/society.json","menus/management.json","menus/help.yml","addons.json","quick-actions.json","command-ui.json"))
            upgrade(root,"0.7.1",file);
        upgrade(root,"0.7.2","addons.json");
        addPlacementMenu(root);
        addCurrentAddonMenus(root);
        addQuickLinks(root);
        addPlayerMenus(root);
        for(String file:List.of("menus/profile.json","menus/economy.json"))upgrade(root,"0.8.3",file);
        for(String id:List.of("hub","main","town","economy","adventures","society","management","development","security","profile"))upgrade(root,"0.8.13","menus/"+id+".json");
        for(String id:List.of("dark","classic"))upgrade(root,"0.8.13","themes/"+id+".json");
        for(String id:List.of("hub","main","town","economy","adventures","society","management","development","security","profile"))upgrade(root,"0.8.16","menus/"+id+".json");
        upgrade(root,"0.8.16","menus/help.yml");
        for(String name:List.of("addons.json","command-ui.json","quick-actions.json","menus/development.json","menus/security.json","menus/economy.json"))upgrade(root,"0.8.19",name);
        for(String name:List.of("addons.json","command-ui.json","quick-actions.json","menus/development.json"))upgrade(root,"0.8.20",name);
        for(String name:List.of("menus/hub.json","menus/main.json","command-ui.json"))upgrade(root,"0.9.0",name);
        for(String name:List.of("menus/hub.json","menus/main.json"))upgrade(root,"0.9.1",name);
        removeHomeOrders(root);
        for(String name:List.of("hub","main","profile"))upgrade(root,"0.9.3","menus/"+name+".json");
        separateCategories(root);
        upgrade(root,"0.9.5","menus/help.yml");
        upgrade(root,"0.9.6","menus/help.yml");
        upgrade(root,"0.9.6-config","menus/help.yml");
        AddonSuiteMigration.apply(root);
        addCommandAction(root);
        addTreeButtons(root);
    }
    /** Remove only the known duplicate shortcut, including from a customized home layout. */
    private static void removeHomeOrders(Path root)throws IOException{
        for(String name:List.of("menus/hub.json","menus/main.json")){
            Path file=root.resolve(name);if(!Files.isRegularFile(file)||Files.size(file)>262144)continue;
            try{
                var value=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if(value.has("components")&&removeOrders(value.getAsJsonArray("components")))replace(root,file,"0.9.1",name,json(value));
            }catch(JsonParseException|IllegalStateException ignored){/* Report invalid configurations without replacing them. */}
        }
    }
    private static boolean removeOrders(JsonArray nodes){
        boolean changed=false;
        for(int i=nodes.size()-1;i>=0;i--){
            if(!nodes.get(i).isJsonObject())continue;var node=nodes.get(i).getAsJsonObject();
            if(Protocol.str(node,"type","").equals("button")&&Protocol.str(node,"action","").equals("quick:orders")){nodes.remove(i);changed=true;}
            else if(node.has("children")&&node.get("children").isJsonArray())changed|=removeOrders(node.getAsJsonArray("children"));
        }
        return changed;
    }
    /** Remove only the duplicated stock shortcuts; leave all other custom menu data intact. */
    private static void separateCategories(Path root)throws IOException{
        for(String name:List.of("menus/hub.json","menus/main.json","menus/profile.json")){
            Path file=root.resolve(name);if(!Files.isRegularFile(file)||Files.size(file)>262144)continue;
            String action=name.equals("menus/profile.json")?"quick:history":"hub:society";
            String id=name.equals("menus/profile.json")?"history":"society";
            try{var value=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if(value.has("components")&&removeShortcut(value.getAsJsonArray("components"),id,action))replace(root,file,"0.9.3",name,json(value));
            }catch(JsonParseException|IllegalStateException ignored){/* Loader reports invalid user configuration. */}
        }
    }
    private static boolean removeShortcut(JsonArray nodes,String id,String action){
        boolean changed=false;
        for(int i=nodes.size()-1;i>=0;i--){
            if(!nodes.get(i).isJsonObject())continue;var node=nodes.get(i).getAsJsonObject();
            if(Protocol.str(node,"id","").equals(id)&&Protocol.str(node,"action","").equals(action)){nodes.remove(i);changed=true;}
            else if(node.has("children")&&node.get("children").isJsonArray())changed|=removeShortcut(node.getAsJsonArray("children"),id,action);
        }return changed;
    }
    private static void addCommandAction(Path root)throws IOException{
        Path target=root.resolve("actions.json");if(!Files.isRegularFile(target)||Files.size(target)>262144)return;
        try{var value=JsonParser.parseString(Files.readString(target)).getAsJsonObject();
            if(!value.has("commands")){value.add("commands",JsonParser.parseString(normal(resource("actions.json"))).getAsJsonObject().get("commands"));replace(root,target,"0.8.13","actions.json",json(value));}
        }catch(JsonParseException|IllegalStateException ignored){}
    }
    /** Expose the existing progression screen in installed menus without resetting player customizations. */
    private static void addTreeButtons(Path root)throws IOException{
        for(String name:List.of("menus/town.json","menus/development.json")){
            Path target=root.resolve(name);
            if(!Files.isRegularFile(target)||Files.size(target)>262144)continue;
            try{
                var actual=JsonParser.parseString(Files.readString(target)).getAsJsonObject();
                var defaults=JsonParser.parseString(normal(resource(name))).getAsJsonObject();
                if(!actual.has("components")||!defaults.has("components"))continue;
                String anchor=name.equals("menus/town.json")?"city-overview":"planner";
                if(cc.crafto.ui.MenuSchema.find(actual,anchor)==null)continue;
                if(containsAction(actual.getAsJsonArray("components"),"ui:tree"))continue;
                var button=cc.crafto.ui.MenuSchema.find(defaults,"city-tree");
                if(button==null)continue;
                for(var element:actual.getAsJsonArray("components")){
                    if(!element.isJsonObject())continue;
                    var grid=element.getAsJsonObject();
                    if(!Protocol.str(grid,"id","").equals("modules")||!grid.has("children")||!grid.get("children").isJsonArray())continue;
                    var old=grid.getAsJsonArray("children");var children=new JsonArray();children.add(button.deepCopy());old.forEach(children::add);
                    grid.add("children",children);
                    int columns=Math.max(1,Protocol.integer(grid,"columns",3)),rows=(children.size()+columns-1)/columns;
                    int needed=rows*Protocol.integer(grid,"rowHeight",38)+Math.max(0,rows-1)*Protocol.integer(grid,"gap",6);
                    int y=Protocol.integer(grid,"y",80);
                    if(needed>Protocol.integer(grid,"h",0))grid.addProperty("h",needed);
                    if(y+needed+40>Protocol.integer(actual,"height",0))actual.addProperty("height",y+needed+40);
                    replace(root,target,"0.9.10",name,json(actual));break;
                }
            }catch(JsonParseException|IllegalStateException ignored){/* ConfigLoader reports invalid user configuration. */}
        }
    }
    private static boolean containsAction(JsonArray nodes,String action){
        for(var element:nodes){
            if(!element.isJsonObject())continue;var node=element.getAsJsonObject();
            if(Protocol.str(node,"action","").equals(action))return true;
            if(node.has("children")&&node.get("children").isJsonArray()&&containsAction(node.getAsJsonArray("children"),action))return true;
        }
        return false;
    }
    private static void apply(Path root,String version,List<String> names)throws IOException{
        for(String name:names)upgrade(root,version,"menus/"+name);
    }
    private static void upgrade(Path root,String version,String name)throws IOException{
            Path target=root.resolve(name);
            if(!Files.isRegularFile(target)||Files.size(target)>262144)return;
            byte[] current=Files.readAllBytes(target),old=resource("migrations/"+version+"/"+name);
            if(same(current,resource(name),name))return;
            if(!same(current,old,name)){
                byte[] themed=(version.equals("0.7.1")||version.equals("0.7.2")||version.equals("0.8.3")||version.equals("0.8.13")||version.equals("0.8.16")||version.equals("0.8.19")||version.equals("0.8.20")||version.equals("0.9.0")||version.equals("0.9.1")||version.equals("0.9.3"))?themeOnlyUpgrade(current,old,resource(name),name):null;
                if(themed!=null){replace(root,target,version,name,themed);return;}
                if(version.equals("0.7.1")&&!same(current,resource(name),name)){
                    Path example=root.resolve("updates/0.7.2").resolve(name);Files.createDirectories(example.getParent());Files.write(example,resource(name));
                }
                return;
            }
            replace(root,target,version,name,resource(name));
    }
    static void replace(Path root,Path target,String version,String name,byte[] bytes)throws IOException{
            Path backup=root.resolve("backups/"+version).resolve(name);Files.createDirectories(backup.getParent());
            if(!Files.exists(backup))Files.copy(target,backup);
            Path temp=Files.createTempFile(target.getParent(),".craftoui-migrate-",".tmp");
            try{
                Files.write(temp,bytes);
                try{Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
            }finally{Files.deleteIfExists(temp);}
    }
    private static boolean same(byte[] left,byte[] right,String name){
        if(name.endsWith(".json"))try{return JsonParser.parseString(normal(left)).equals(JsonParser.parseString(normal(right)));}catch(JsonParseException ignored){}
        return normal(left).equals(normal(right));
    }
    private static byte[] themeOnlyUpgrade(byte[] current,byte[] old,byte[] latest,String name){
        if(!name.endsWith(".json"))return null;
        try{
            var actual=JsonParser.parseString(normal(current)).getAsJsonObject();var previous=JsonParser.parseString(normal(old)).getAsJsonObject();
            var next=JsonParser.parseString(normal(latest)).getAsJsonObject();var stripped=actual.deepCopy();
            List<String> keys=name.equals("addons.json")?List.of("theme","columns","width","height"):name.startsWith("menus/")?List.of("theme"):List.of();
            for(String key:keys){stripped.remove(key);previous.remove(key);}
            if(keys.isEmpty()||!stripped.equals(previous))return null;
            for(String key:keys){if(actual.has(key))next.add(key,actual.get(key));else next.remove(key);}
            return (new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(next)+"\n").getBytes(StandardCharsets.UTF_8);
        }catch(JsonParseException|IllegalStateException e){return null;}
    }
    private static void addQuickLinks(Path root)throws IOException{
        Path target=root.resolve("quick-actions.json");if(!Files.isRegularFile(target)||Files.size(target)>262144)return;
        try{
            JsonObject current=JsonParser.parseString(Files.readString(target)).getAsJsonObject();
            JsonObject defaults=JsonParser.parseString(normal(resource("quick-actions.json"))).getAsJsonObject();boolean changed=false;
            // Only new named shortcuts are added; existing commands and permissions stay untouched.
            for(String id:List.of("governance-council"))if(!current.has(id)){current.add(id,defaults.get(id));changed=true;}
            if(changed)replace(root,target,"0.7.1","quick-actions.json",(new GsonBuilder().setPrettyPrinting().create().toJson(current)+"\n").getBytes(StandardCharsets.UTF_8));
        }catch(JsonParseException|IllegalStateException ignored){/* ConfigLoader reports malformed data without replacing it. */}
    }
    private static void addPlacementMenu(Path root)throws IOException{
        Path target=root.resolve("addons.json");if(!Files.isRegularFile(target)||Files.size(target)>262144)return;
        try{
            var current=JsonParser.parseString(Files.readString(target)).getAsJsonObject();boolean changed=false;
            if(!current.has("modules")||!current.get("modules").isJsonArray())return;
            for(var value:current.getAsJsonArray("modules")){
                if(!value.isJsonObject())continue;var entry=value.getAsJsonObject();
                if(!entry.has("plugin")||!entry.get("plugin").getAsString().equals("NeverLandTownyBuilds")||!entry.has("holders")||!entry.get("holders").isJsonArray())continue;
                var holders=entry.getAsJsonArray("holders");var detail=new JsonPrimitive("ru.neverland.townybuilds.gui.MenuManager$DetailsHolder");
                var placement=new JsonPrimitive("ru.neverland.townybuilds.construction.PlacementService$PlacementMenu");
                if(holders.contains(detail)&&!holders.contains(placement)){holders.add(placement);changed=true;}
            }
            if(changed)replace(root,target,"0.7.2","addons.json",(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(current)+"\n").getBytes(StandardCharsets.UTF_8));
        }catch(JsonParseException|IllegalStateException ignored){/* Invalid user config is reported by ConfigLoader. */}
    }
    private static String normal(byte[] value){return new String(value,StandardCharsets.UTF_8).replace("\r\n","\n");}
    private record MenuExtension(String plugin,String anchor,List<String> added){}
    private static void addCurrentAddonMenus(Path root)throws IOException{
        Path target=root.resolve("addons.json");if(!Files.isRegularFile(target)||Files.size(target)>262144)return;
        var extensions=List.of(
            new MenuExtension("NeverLandTownyCouncil","ru.neverland.townycouncil.CouncilMenus$Holder",List.of("ru.neverland.townycouncil.AccessMenus$View","ru.neverland.townycouncil.MinisterMenus$View")),
            new MenuExtension("NeverLandTownyBuilds","ru.neverland.townybuilds.gui.MenuManager$DetailsHolder",List.of("ru.neverland.townybuilds.construction.ConstructionCancelMenu$View")),
            new MenuExtension("NeverLandTownyDistricts","ru.neverland.townydistricts.gui.DistrictMenu$View",List.of("ru.neverland.townydistricts.gui.MobFarmMenu$View")),
            new MenuExtension("NeverLandTownyEvents","ru.neverland.mintevents.gui.EventMenuHolder",List.of("ru.neverland.mintevents.gui.FireRepairMenus$Screen")));
        try{
            var current=JsonParser.parseString(Files.readString(target)).getAsJsonObject();boolean changed=false;
            if(!current.has("modules")||!current.get("modules").isJsonArray())return;
            for(var value:current.getAsJsonArray("modules")){
                if(!value.isJsonObject())continue;var entry=value.getAsJsonObject();
                if(!entry.has("plugin")||!entry.get("plugin").isJsonPrimitive()||!entry.has("holders")||!entry.get("holders").isJsonArray())continue;
                var holders=entry.getAsJsonArray("holders");
                for(var extension:extensions)if(extension.plugin().equals(entry.get("plugin").getAsString())&&holders.contains(new JsonPrimitive(extension.anchor())))
                    for(String added:extension.added())if(!holders.contains(new JsonPrimitive(added))){holders.add(added);changed=true;}
            }
            if(changed)replace(root,target,"0.8.1","addons.json",(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(current)+"\n").getBytes(StandardCharsets.UTF_8));
        }catch(JsonParseException|IllegalStateException ignored){/* ConfigLoader reports malformed data; do not replace it. */}
    }
    private static void addPlayerMenus(Path root)throws IOException{
        Path file=root.resolve("addons.json");
        if(Files.isRegularFile(file)&&Files.size(file)<=262144)try{
            var current=JsonParser.parseString(Files.readString(file)).getAsJsonObject();boolean changed=false;
            if(current.has("modules")&&current.get("modules").isJsonArray())for(var value:current.getAsJsonArray("modules")){
                if(!value.isJsonObject())continue;var entry=value.getAsJsonObject();
                if(!entry.has("plugin")||!entry.get("plugin").isJsonPrimitive()||!entry.has("holders")||!entry.get("holders").isJsonArray())continue;
                var holders=entry.getAsJsonArray("holders");String owner=entry.get("plugin").getAsString(),added="";
                if(owner.equals("NeverLandTownyBuilds")&&holders.contains(new JsonPrimitive("ru.neverland.townybuilds.gui.MenuManager$DetailsHolder")))added="ru.neverland.townybuilds.gui.MenuManager$ContributionHolder";
                if(owner.equals("NeverLandTownyControl")&&holders.contains(new JsonPrimitive("ru.neverland.townycontrol.AdminMenu$Screen")))added="ru.neverland.townycontrol.PersonalHistoryMenu$View";
                if(added.isEmpty())continue;
                if(!holders.contains(new JsonPrimitive(added))){holders.add(added);changed=true;}
                if(owner.equals("NeverLandTownyControl")){
                    if(!entry.has("holderPermissions")){entry.add("holderPermissions",new JsonObject());changed=true;}
                    if(entry.get("holderPermissions").isJsonObject()&&!entry.getAsJsonObject("holderPermissions").has(added)){entry.getAsJsonObject("holderPermissions").addProperty(added,"neverlandtownycontrol.history");changed=true;}
                }
            }
            if(changed)replace(root,file,"0.8.3","addons.json",json(current));
        }catch(JsonParseException|IllegalStateException ignored){}
        file=root.resolve("quick-actions.json");
        if(Files.isRegularFile(file)&&Files.size(file)<=262144)try{
            var current=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if(!current.has("history")){current.add("history",JsonParser.parseString(new String(resource("quick-actions.json"),StandardCharsets.UTF_8)).getAsJsonObject().get("history"));replace(root,file,"0.8.3","quick-actions.json",json(current));}
        }catch(JsonParseException|IllegalStateException ignored){}
    }
    private static byte[] json(JsonObject value){return (new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(value)+"\n").getBytes(StandardCharsets.UTF_8);}
    private static byte[] resource(String name)throws IOException{try(var in=DefaultMenuMigration.class.getClassLoader().getResourceAsStream(name)){if(in==null)throw new FileNotFoundException(name);return in.readAllBytes();}}
}
