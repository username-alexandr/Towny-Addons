package cc.crafto.ui.server;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AddonMenuMigrationTest {
    @TempDir Path root;
    private JsonObject old()throws Exception{try(var in=getClass().getClassLoader().getResourceAsStream("migrations/0.8.1/addons.json")){return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}}
    private JsonObject module(JsonObject config,String id){for(var e:config.getAsJsonArray("modules"))if(e.getAsJsonObject().get("id").getAsString().equals(id))return e.getAsJsonObject();throw new AssertionError(id);}
    private Path install(JsonObject value)throws Exception{Path file=root.resolve("addons.json");Files.writeString(file,value.toString());return file;}
    @Test void previousReleaseGetsEveryNewMenuAndKeepsExactBackup()throws Exception{
        Path file=install(old());byte[] before=Files.readAllBytes(file);DefaultMenuMigration.apply(root);
        assertArrayEquals(before,Files.readAllBytes(root.resolve("backups/0.8.1/addons.json")));
        var catalog=AddonCatalog.parse(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
        assertTrue(catalog.entries().get("council").holders().containsAll(Set.of("ru.neverland.townycouncil.AccessMenus$View","ru.neverland.townycouncil.MinisterMenus$View")));
        assertTrue(catalog.entries().get("events").holders().contains("ru.neverland.mintevents.gui.FireRepairMenus$Screen"));
        assertTrue(catalog.entries().get("builds").holders().contains("ru.neverland.townybuilds.construction.ConstructionCancelMenu$View"));
        assertTrue(catalog.entries().get("districts").holders().contains("ru.neverland.townydistricts.gui.MobFarmMenu$View"));
        byte[] after=Files.readAllBytes(file);DefaultMenuMigration.apply(root);assertArrayEquals(after,Files.readAllBytes(file));assertArrayEquals(before,Files.readAllBytes(root.resolve("backups/0.8.1/addons.json")));
    }
    @Test void customizedCatalogChangesOnlyHolderLists()throws Exception{
        var original=old();original.addProperty("enabled",false);original.addProperty("theme","our-theme");original.addProperty("width",780);
        var council=module(original,"council");council.addProperty("label","Наш совет");council.addProperty("command","custom:council");council.addProperty("permission","our.council");
        var permissions=new JsonObject();permissions.addProperty("ru.neverland.townycouncil.CouncilMenus$Holder","our.special");council.add("holderPermissions",permissions);
        Path file=install(original);DefaultMenuMigration.apply(root);var next=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        for(var value:original.getAsJsonArray("modules")){
            var was=value.getAsJsonObject();String id=was.get("id").getAsString();var updated=module(next,id);
            if(was.has("holderPermissions"))for(var permission:was.getAsJsonObject("holderPermissions").entrySet())assertEquals(permission.getValue(),updated.getAsJsonObject("holderPermissions").get(permission.getKey()));
            updated.add("holders",was.get("holders").deepCopy());
            if(was.has("holderPermissions"))updated.add("holderPermissions",was.get("holderPermissions").deepCopy());else updated.remove("holderPermissions");
        }
        assertEquals(original,next);
    }
    @Test void excludedModulesAndExcludedAnchorStayExcluded()throws Exception{
        var original=old();var list=original.getAsJsonArray("modules");list.remove(module(original,"events"));
        module(original,"council").add("holders",new JsonArray());Path file=install(original);DefaultMenuMigration.apply(root);
        var c=AddonCatalog.parse(JsonParser.parseString(Files.readString(file)).getAsJsonObject());assertFalse(c.entries().containsKey("events"));assertTrue(c.entries().get("council").holders().isEmpty());
    }
    @Test void holderNameUnderDifferentPluginDoesNotGrantRecognition()throws Exception{
        var original=old();module(original,"council").addProperty("plugin","OtherPlugin");Path file=install(original);DefaultMenuMigration.apply(root);
        var c=AddonCatalog.parse(JsonParser.parseString(Files.readString(file)).getAsJsonObject());assertEquals(Set.of("ru.neverland.townycouncil.CouncilMenus$Holder"),c.entries().get("council").holders());
    }
    @Test void malformedCatalogIsLeftForNormalConfigErrorHandling()throws Exception{
        Path file=root.resolve("addons.json");String broken="{\"modules\": [";Files.writeString(file,broken);DefaultMenuMigration.apply(root);assertEquals(broken,Files.readString(file));
    }
    @Test void newMenusAreButtonsAndOnlySpatialFarmMapUsesThemedSlots(){
        String owner="NeverLandTownyDistricts",holder="ru.neverland.townydistricts.gui.MobFarmMenu$View";
        assertTrue(AddonBridge.preserveSlotLayout(owner,holder,"Город • защита от мобов"));
        assertFalse(AddonBridge.preserveSlotLayout(owner,holder,"Город • зоны ферм"));
        assertFalse(AddonBridge.preserveSlotLayout(owner,holder,"Разрешить мобов здесь?"));
        assertFalse(AddonBridge.preserveSlotLayout("OtherPlugin",holder,"Город • защита от мобов"));
    }
}
