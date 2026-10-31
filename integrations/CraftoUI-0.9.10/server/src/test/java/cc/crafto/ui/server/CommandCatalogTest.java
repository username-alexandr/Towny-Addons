package cc.crafto.ui.server;
import cc.crafto.ui.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class CommandCatalogTest {
    JsonObject stock()throws Exception{try(var in=getClass().getResourceAsStream("/command-ui.json")){return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}}
    @Test void stockFormsFitProtocolAndIncludePersonnel()throws Exception{var c=CommandCatalog.parse(stock());assertEquals(41,c.modules().size());assertEquals(103,c.modules().values().stream().mapToInt(m->m.forms().size()).sum());for(var m:c.modules().values())for(var f:m.forms())assertTrue(Protocol.encode(Protocol.Kind.COMMANDS,Protocol.object("op","form","form",f.json())).length<Protocol.MAX_BYTES);assertTrue(c.modules().get("NeverLandTownyCouncil").forms().stream().anyMatch(f->f.prefix().equals("appoint")));}
    @Test void duplicateOwnersFailReload()throws Exception{var j=stock();j.getAsJsonArray("modules").add(j.getAsJsonArray("modules").get(0).deepCopy());assertThrows(IllegalArgumentException.class,()->CommandCatalog.parse(j));}
    @Test void rootRestrictionCannotBeBypassedByAForm()throws Exception{var j=stock();var m=j.getAsJsonArray("modules").get(0).getAsJsonObject();m.add("roots",Protocol.JSON.toJsonTree(java.util.List.of("safe")));m.getAsJsonArray("forms").add(Protocol.object("id","bad","label","Bad","root","other","fields",java.util.List.of()));assertThrows(IllegalArgumentException.class,()->CommandCatalog.parse(j));}
    @Test void cityCreationUsesTownyRootPermissionAndOneNameField()throws Exception{
        var module=CommandCatalog.parse(stock()).modules().get("Towny");var form=module.forms().stream().filter(f->f.id().equals("create-town")).findFirst().orElseThrow();
        assertEquals("town",form.root());assertEquals("towny.command.town.new",form.permission());assertEquals("new Речной_берег",form.arguments(Protocol.object("name","Речной_берег")));
        assertThrows(IllegalArgumentException.class,()->form.arguments(Protocol.object("name","Имя с пробелом")));assertThrows(IllegalArgumentException.class,()->form.arguments(Protocol.object("name","Town\nstop")));
    }
    @Test void legacyEnglishModuleNamesAreLocalizedWithoutEditingUserConfig()throws Exception{
        var json=stock();for(var row:json.getAsJsonArray("modules")){
            var module=row.getAsJsonObject();if(module.get("plugin").getAsString().equals("NeverLandTownyStick"))module.addProperty("label","Stick");
            if(module.get("plugin").getAsString().equals("NeverLandTownyTaxes"))module.addProperty("label","Taxes");
        }
        var catalog=CommandCatalog.parse(json);
        assertEquals("Проверка предмета",catalog.modules().get("NeverLandTownyStick").label());
        assertEquals("Налоги",catalog.modules().get("NeverLandTownyTaxes").label());
    }
}
