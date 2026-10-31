package cc.crafto.ui.server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class CityBuildSchemaTest {
    public static final class StyleApi {
        public static int baseArchitecture(int value){return switch(value){case 1006->6;case 2004->4;case 70003->3;case 1007->7;default->throw new IllegalArgumentException("Unknown style");};}
    }
    final UUID town=UUID.randomUUID();
    @Test void currentSnapshotDoesNotDependOnPluginVersion(){
        var yaml=new YamlConfiguration();yaml.set("towns."+town+".levels.forge",2);yaml.set("addon-version","0.8.28");
        assertEquals(2,CityBuildSchema.town(yaml,town).getInt("levels.forge"));
        assertNull(CityBuildSchema.town(yaml,UUID.randomUUID()));
    }
    @Test void malformedSnapshotIsNotReportedAsNoConstruction(){
        var yaml=new YamlConfiguration();yaml.set("towns", "broken");assertThrows(IllegalArgumentException.class,()->CityBuildSchema.town(yaml,town));
        final var wrongSites=new YamlConfiguration();wrongSites.set("towns."+town+".construction",42);
        assertThrows(IllegalArgumentException.class,()->CityBuildSchema.town(wrongSites,town));
    }
    @Test void oldArchitectureIsPreservedAndUnknownArchitectureRejected(){
        var site=new YamlConfiguration();assertEquals(1,CityBuildSchema.architecture(site,6));
        for(int version:new int[]{1,2,3,4,5,6}){site.set("architecture-version",version);assertEquals(version,CityBuildSchema.architecture(site,6));}
        site.set("architecture-version",7);assertThrows(IllegalArgumentException.class,()->CityBuildSchema.architecture(site,6));
    }
    @Test void styledVersionsUseTheAddonDecoderAndPreserveTheCompleteSavedId(){
        var site=new YamlConfiguration();for(int version:new int[]{1006,2004}){site.set("architecture-version",version);assertEquals(version,CityBuildSchema.architecture(site,6,StyleApi.class));}
        site.set("architecture-version",1007);assertThrows(IllegalArgumentException.class,()->CityBuildSchema.architecture(site,6,StyleApi.class));
    }
    @Test void unknownStyleCannotFallBackToAnUnstyledBuilding(){
        var site=new YamlConfiguration();for(int version:new int[]{1000,2000,3006,-1}){site.set("architecture-version",version);assertThrows(IllegalArgumentException.class,()->CityBuildSchema.architecture(site,6,StyleApi.class));}
    }
    @Test void unknownEncodedStylesAreRejectedWithoutACompatibleApi(){
        var site=new YamlConfiguration();site.set("architecture-version",3006);assertThrows(IllegalArgumentException.class,()->CityBuildSchema.architecture(site,6));
        site.set("architecture-version","1006");assertThrows(IllegalArgumentException.class,()->CityBuildSchema.architecture(site,6,StyleApi.class));
    }
    @Test void themedAndPinnedArchitecturesPreserveTheirIdentity(){
        var site=new YamlConfiguration();
        for(int version:new int[]{1001,1006,2001,2006}){site.set("architecture-version",version);assertEquals(version,CityBuildSchema.architecture(site,6));}
        site.set("blueprint-set","a".repeat(64));
        for(int version:new int[]{10001,10006,11006,12006}){site.set("architecture-version",version);assertEquals(version,CityBuildSchema.architecture(site,6));}
        for(int version:new int[]{6,3006,10007,13006,20006}){site.set("architecture-version",version);assertThrows(IllegalArgumentException.class,()->CityBuildSchema.architecture(site,6));}
    }
    @Test void damagedPinsNeverFallBackToProceduralGeometry(){
        var site=new YamlConfiguration();site.set("architecture-version",11006);
        for(Object pin:new Object[]{"","bad",42,"A".repeat(64)}){site.set("blueprint-set",pin);assertThrows(IllegalArgumentException.class,()->CityBuildSchema.architecture(site,6));}
        site.set("blueprint-set",null);assertThrows(IllegalArgumentException.class,()->CityBuildSchema.architecture(site,6));
    }
}
