package cc.crafto.ui.server;

import cc.crafto.ui.*;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static cc.crafto.ui.Protocol.*;

class MenuCategoriesTest {
    @TempDir Path root;
    JsonObject resource(String name)throws Exception{
        try(var in=getClass().getResourceAsStream("/"+name)){
            assertNotNull(in,name);return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    List<JsonObject> buttons(JsonObject menu){
        var result=new ArrayList<JsonObject>();MenuSchema.visit(menu.getAsJsonArray("components"),n->{if(str(n,"type","").equals("button"))result.add(n);});return result;
    }
    @Test void personalOrdersHaveOneCategoryAndSettingsHaveTheirOwnAction()throws Exception{
        for(String id:List.of("hub","main")){
            var entries=buttons(resource("menus/"+id+".json"));
            assertTrue(entries.stream().noneMatch(n->str(n,"action","").equals("quick:orders")));
            assertTrue(entries.stream().noneMatch(n->str(n,"action","").equals("hub:society")));
            assertEquals(List.of("settings"),entries.stream().filter(n->str(n,"id","").equals("settings")).map(n->str(n,"action","")).toList());
        }
        assertEquals(1,buttons(resource("menus/economy.json")).stream().filter(n->str(n,"action","").equals("quick:orders")).count());
        assertTrue(buttons(resource("menus/profile.json")).stream().noneMatch(n->str(n,"action","").equals("quick:history")));
        assertEquals(1,buttons(resource("menus/town.json")).stream().filter(n->str(n,"action","").equals("hub:society")).count());
        var actions=resource("actions.json");assertEquals("client:settings",NavigationPolicy.destination(actions.getAsJsonObject("settings")));
    }
    @Test void bothReleasedHomeMenusUpgradeAndKeepThemeAndExactBackup()throws Exception{
        for(String id:List.of("hub","main")){
            var old=resource("migrations/0.9.1/menus/"+id+".json");old.addProperty("theme","classic");
            var file=root.resolve("menus/"+id+".json");Files.createDirectories(file.getParent());Files.writeString(file,old.toString());byte[] original=Files.readAllBytes(file);
            DefaultMenuMigration.apply(root);byte[] current=Files.readAllBytes(file);DefaultMenuMigration.apply(root);
            assertArrayEquals(current,Files.readAllBytes(file));assertArrayEquals(original,Files.readAllBytes(root.resolve("backups/0.9.1/menus/"+id+".json")));
            var expected=resource("menus/"+id+".json");expected.addProperty("theme","classic");assertEquals(expected,JsonParser.parseString(Files.readString(file)));
        }
    }
    @Test void installed093MenusLoseOnlyCrossCategoryShortcuts()throws Exception{
        for(String id:List.of("hub","main","profile")){
            byte[] old;try(var in=getClass().getResourceAsStream("/migrations/0.9.3/menus/"+id+".json")){old=in.readAllBytes();}
            var file=root.resolve("menus/"+id+".json");Files.createDirectories(file.getParent());Files.write(file,old);
        }
        DefaultMenuMigration.apply(root);
        for(String id:List.of("hub","main","profile")){
            var file=root.resolve("menus/"+id+".json");assertEquals(resource("menus/"+id+".json"),ConfigLoader.read(file));
            assertTrue(Files.isRegularFile(root.resolve("backups/0.9.3/menus/"+id+".json")));
            byte[] once=Files.readAllBytes(file);DefaultMenuMigration.apply(root);assertArrayEquals(once,Files.readAllBytes(file));
        }
    }
    @Test void customizedHomeLosesOnlyTheDuplicateAndEconomyIsUntouched()throws Exception{
        var old=resource("migrations/0.9.1/menus/hub.json");old.addProperty("width",710);old.addProperty("title","Наше меню");
        var menu=root.resolve("menus/hub.json");Files.createDirectories(menu.getParent());Files.writeString(menu,old.toString());
        var trade=root.resolve("menus/economy.json");Files.writeString(trade,resource("menus/economy.json").toString());byte[] tradeBefore=Files.readAllBytes(trade);
        var expected=old.deepCopy();for(var node:expected.getAsJsonArray("components"))if(node.getAsJsonObject().has("children"))node.getAsJsonObject().getAsJsonArray("children").asList().removeIf(e->Set.of("quick:orders","hub:society").contains(str(e.getAsJsonObject(),"action","")));
        DefaultMenuMigration.apply(root);byte[] first=Files.readAllBytes(menu);DefaultMenuMigration.apply(root);
        assertEquals(expected,JsonParser.parseString(Files.readString(menu)));assertArrayEquals(first,Files.readAllBytes(menu));assertArrayEquals(tradeBefore,Files.readAllBytes(trade));
    }
}
