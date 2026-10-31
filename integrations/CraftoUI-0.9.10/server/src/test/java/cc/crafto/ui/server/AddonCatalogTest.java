package cc.crafto.ui.server;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import static org.junit.jupiter.api.Assertions.*;
class AddonCatalogTest {
    private JsonObject bundled(){return JsonParser.parseReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("addons.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
    @Test void bundledCatalogIncludesLatestContractsAndStorage(){var c=AddonCatalog.parse(bundled());assertTrue(c.entries().get("contracts").holders().stream().anyMatch(x->x.endsWith("ContractMenuHolder")));assertFalse(c.entries().get("builds").inventoryHolders().isEmpty());assertEquals("town contracts",c.entries().get("contracts").command());}
    @Test void commandWithInjectedNewlineRejected(){var o=bundled();o.getAsJsonArray("modules").get(0).getAsJsonObject().addProperty("command","town builds\nop Other");assertThrows(IllegalArgumentException.class,()->AddonCatalog.parse(o));}
    @Test void duplicateOwnerAndUnlistedStorageRejected(){var duplicate=bundled();duplicate.getAsJsonArray("modules").add(duplicate.getAsJsonArray("modules").get(0).deepCopy());assertThrows(IllegalArgumentException.class,()->AddonCatalog.parse(duplicate));var o=bundled();o.getAsJsonArray("modules").get(0).getAsJsonObject().getAsJsonArray("inventoryHolders").add("untrusted.Fake");JsonObject invalid=o;assertThrows(IllegalArgumentException.class,()->AddonCatalog.parse(invalid));}
    @Test void extremeLayoutRejected(){var o=bundled();o.addProperty("columns",1000);assertThrows(IllegalArgumentException.class,()->AddonCatalog.parse(o));}
    @Test void marketHintIsBackwardCompatibleAndValidated(){
        var o=bundled();JsonObject market=null;for(var e:o.getAsJsonArray("modules"))if(e.getAsJsonObject().get("plugin").getAsString().equals("NeverLandTownyMarket"))market=e.getAsJsonObject();
        assertNotNull(market);String id=market.get("id").getAsString();market.remove("layout");assertEquals("market",AddonCatalog.parse(o).entries().get(id).layout());
        market.addProperty("layout","cards");assertEquals("cards",AddonCatalog.parse(o).entries().get(id).layout());market.addProperty("layout","invalid");assertThrows(IllegalArgumentException.class,()->AddonCatalog.parse(o));
    }
}
