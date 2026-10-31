package cc.crafto.ui.server;
import cc.crafto.ui.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ConfigLoaderTest {
    @TempDir Path root;
    void copy()throws Exception{for(String n:new String[]{"actions.json","menus/main.json","menus/profile.json","menus/help.yml","themes/classic.json","themes/dark.json"}){Path p=root.resolve(n);Files.createDirectories(p.getParent());try(var in=getClass().getClassLoader().getResourceAsStream(n)){assertNotNull(in);Files.copy(in,p);}}}
    @Test void bundledJsonAndYamlAreValidAndFitNetworkEnvelope()throws Exception{copy();var c=ConfigLoader.load(root);assertEquals(3,c.menus().size());assertEquals(2,c.themes().size());for(var m:c.menus().values())assertTrue(Protocol.encode(Protocol.Kind.OPEN,Protocol.object("screen",m)).length<Protocol.MAX_BYTES);}
    @Test void completeHubLoadsWithOptionalAddons()throws Exception{copy();for(String n:new String[]{"addons.json","menus/hub.json","menus/town.json","menus/economy.json","menus/adventures.json","menus/society.json","menus/management.json"}){try(var in=getClass().getClassLoader().getResourceAsStream(n)){Files.copy(in,root.resolve(n));}}var c=ConfigLoader.load(root);assertTrue(c.menus().containsKey("economy"));assertEquals("city",c.actions().get("ui:city").get("type").getAsString());assertEquals("craftoui.city",c.actions().get("ui:city").get("permission").getAsString());assertEquals("ui:city",MenuSchema.find(c.menus().get("town"),"city-overview").get("action").getAsString());assertEquals("town contracts",c.actions().get("addons:contracts").get("command").getAsString());for(var m:c.menus().values())assertTrue(Protocol.encode(Protocol.Kind.OPEN,Protocol.object("screen",m)).length<Protocol.MAX_BYTES);}
    @Test void plannerNavigationUsesAllowlistedRouteAndChecksTownMembership()throws Exception{
        copy();var action=ConfigLoader.load(root).actions().get("ui:planner");assertEquals("civic",action.get("type").getAsString());assertEquals("planner",action.get("route").getAsString());assertTrue(NavigationPolicy.requiresTown(action));
        assertFalse(ActionAvailability.reason(action,false,p->true,p->true).isBlank());
        assertFalse(ActionAvailability.reason(action,true,p->true,p->false).isBlank());
        assertEquals("civic:planner",NavigationPolicy.destination(action));
        Files.writeString(root.resolve("actions.json"),"{\"unsafe\":{\"type\":\"civic\",\"route\":\"procurement-order\"}}");assertThrows(IllegalArgumentException.class,()->ConfigLoader.load(root));
    }
    @Test void cityTreeIsReachableFromBothTownMenusWithTownAndBuildsChecks()throws Exception{
        copy();for(String n:new String[]{"menus/town.json","menus/development.json"})try(var in=getClass().getClassLoader().getResourceAsStream(n)){Files.copy(in,root.resolve(n));}
        var catalog=ConfigLoader.load(root);var action=catalog.actions().get("ui:tree");
        assertEquals("tree",action.get("route").getAsString());
        assertEquals("NeverLandTownyBuilds",action.get("requiredPlugin").getAsString());
        assertTrue(NavigationPolicy.requiresTown(action));
        assertFalse(ActionAvailability.reason(action,false,p->true,p->true).isBlank());
        assertEquals("civic:tree",NavigationPolicy.destination(action));
        assertEquals("ui:tree",MenuSchema.find(catalog.menus().get("town"),"city-tree").get("action").getAsString());
        assertEquals("ui:tree",MenuSchema.find(catalog.menus().get("development"),"city-tree").get("action").getAsString());
    }
    @Test void brokenReloadDoesNotProducePartialCatalog()throws Exception{copy();var original=ConfigLoader.load(root);Files.writeString(root.resolve("menus/main.json"),"{bad");assertThrows(Exception.class,()->ConfigLoader.load(root));assertEquals(3,original.menus().size());}
    @Test void badThemeAndUnknownMenuActionFail()throws Exception{copy();Files.writeString(root.resolve("actions.json"),"{\"go\":{\"type\":\"open\",\"menu\":\"missing\"}}");assertThrows(IllegalArgumentException.class,()->ConfigLoader.load(root));}
    @Test void quickLinksRequireFixedCommandAndPermission()throws Exception{copy();var p=root.resolve("quick-actions.json");Files.writeString(p,"{\"bad\":{\"label\":\"Bad\",\"command\":\"op name; stop\",\"permission\":\"test\"}}");assertThrows(IllegalArgumentException.class,()->ConfigLoader.load(root));Files.writeString(p,"{\"bad\":{\"label\":\"Bad\",\"command\":\"orders\"}}");assertThrows(IllegalArgumentException.class,()->ConfigLoader.load(root));}
    @Test void bundledQuickLinksAreOptionalAndValid()throws Exception{copy();assertTrue(ConfigLoader.load(root).quickLinks().isEmpty());try(var in=getClass().getClassLoader().getResourceAsStream("quick-actions.json")){Files.copy(in,root.resolve("quick-actions.json"));}var c=ConfigLoader.load(root);assertEquals("town inv",c.quickLinks().get("warehouse").get("command").getAsString());assertEquals(30,c.quickLinks().size());}
    @Test void economyOrdersUsesSamePermissionCheckedCommandAsSearch()throws Exception{
        copy();for(String n:new String[]{"quick-actions.json","menus/economy.json"})try(var in=getClass().getClassLoader().getResourceAsStream(n)){Files.copy(in,root.resolve(n));}
        var c=ConfigLoader.load(root);var action=c.actions().get("quick:orders");
        assertEquals(c.quickLinks().get("orders"),action);
        assertEquals("orders",action.get("command").getAsString());
        assertEquals("neverland.orders.use",action.get("permission").getAsString());
        assertEquals("NeverLandTownyContracts",action.get("requiredPlugin").getAsString());
        assertTrue(action.get("closeBefore").getAsBoolean());
        var node=MenuSchema.find(c.menus().get("economy"),"orders");
        assertEquals("quick:orders",node.get("action").getAsString());
    }
    @Test void quickActionNamespaceCannotBeShadowed()throws Exception{
        copy();try(var in=getClass().getClassLoader().getResourceAsStream("quick-actions.json")){Files.copy(in,root.resolve("quick-actions.json"));}
        Files.writeString(root.resolve("actions.json"),"{\"quick:orders\":{\"type\":\"close\"}}");
        assertThrows(IllegalArgumentException.class,()->ConfigLoader.load(root));
    }
}
