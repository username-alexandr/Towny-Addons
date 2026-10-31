package cc.crafto.ui;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static cc.crafto.ui.Protocol.*;
class MenuSchemaTest {
    private JsonObject menu(){JsonArray nodes=new JsonArray();nodes.add(object("id","profile","type","button","text","Профиль","x",20,"y",30,"w",200,"h",40));return object("id","main","width",560,"height",460,"components",nodes);}
    @Test void acceptsMenuAndFindsNestedNodes(){JsonObject m=menu();MenuSchema.validate(m);assertNotNull(MenuSchema.find(m,"profile"));assertNull(MenuSchema.find(m,"missing"));}
    @Test void duplicateIdsRejected(){JsonObject m=menu();m.getAsJsonArray("components").add(m.getAsJsonArray("components").get(0).deepCopy());assertThrows(IllegalArgumentException.class,()->MenuSchema.validate(m));}
    @Test void remoteOrTraversalAssetsRejected(){assertThrows(IllegalArgumentException.class,()->MenuSchema.requireResource("https://example.com/x"));assertThrows(IllegalArgumentException.class,()->MenuSchema.requireResource("craftoui:../secret"));MenuSchema.requireResource("craftoui:textures/gui/card.png");}
    @Test void unsupportedComponentAndNegativeSizeRejected(){JsonObject m=menu(),n=m.getAsJsonArray("components").get(0).getAsJsonObject();n.addProperty("type","javascript");assertThrows(IllegalArgumentException.class,()->MenuSchema.validate(m));n.addProperty("type","button");n.addProperty("w",-1);assertThrows(IllegalArgumentException.class,()->MenuSchema.validate(m));}
    @Test void placeholderIsNotAnExpressionOrRecursiveTemplate(){assertEquals("Имя: Александр; —; ${balance}",MenuSchema.bind("Имя: ${player.name}; ${missing}; ${injected}",Map.of("player.name","Александр","injected","${balance}")));}
    @Test void invalidResponsiveRolesAreRejectedBeforeSending(){var m=menu();var n=m.getAsJsonArray("components").get(0).getAsJsonObject();for(String role:new String[]{"content","aside","unknown"}){n.addProperty("layoutRole",role);assertThrows(IllegalArgumentException.class,()->MenuSchema.validate(m));}n.addProperty("layoutRole","actions");assertDoesNotThrow(()->MenuSchema.validate(m));}
}
