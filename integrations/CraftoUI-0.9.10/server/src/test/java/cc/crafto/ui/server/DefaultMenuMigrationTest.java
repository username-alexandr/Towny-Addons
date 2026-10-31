package cc.crafto.ui.server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class DefaultMenuMigrationTest {
    @TempDir Path root;
    private byte[] resource(String name)throws Exception{try(var in=getClass().getClassLoader().getResourceAsStream(name)){return in.readAllBytes();}}
    private Path install(String name,byte[] data)throws Exception{Path target=root.resolve("menus/"+name);Files.createDirectories(target.getParent());Files.write(target,data);return target;}
    @Test void untouchedDefaultsUpgradeWithBackupAndAreIdempotent()throws Exception{
        byte[] old=resource("migrations/0.5.0/menus/profile.json");Path target=install("profile.json",old);
        DefaultMenuMigration.apply(root);assertArrayEquals(resource("menus/profile.json"),Files.readAllBytes(target));assertArrayEquals(old,Files.readAllBytes(root.resolve("backups/0.5.0/menus/profile.json")));
        DefaultMenuMigration.apply(root);assertArrayEquals(resource("menus/profile.json"),Files.readAllBytes(target));
        var profile=com.google.gson.JsonParser.parseString(Files.readString(target)).getAsJsonObject();assertEquals(330,profile.get("height").getAsInt());assertEquals("dark",profile.get("theme").getAsString());
    }
    @Test void customizedMenuIsPreserved()throws Exception{
        byte[] custom=new String(resource("migrations/0.5.0/menus/profile.json"),StandardCharsets.UTF_8).replace("classic","custom").getBytes(StandardCharsets.UTF_8);Path target=install("profile.json",custom);
        DefaultMenuMigration.apply(root);assertArrayEquals(custom,Files.readAllBytes(target));assertFalse(Files.exists(root.resolve("backups")));
    }
    @Test void windowsLineEndingsAreRecognized()throws Exception{
        Path target=install("help.yml",new String(resource("migrations/0.5.0/menus/help.yml"),StandardCharsets.UTF_8).replace("\n","\r\n").getBytes(StandardCharsets.UTF_8));DefaultMenuMigration.apply(root);assertTrue(Files.readString(target).contains("theme: dark"));
    }
    @Test void economyUpgradeAddsOrdersAndKeepsBackup()throws Exception{
        byte[] old=resource("migrations/0.5.1/menus/economy.json");Path target=install("economy.json",old);
        DefaultMenuMigration.apply(root);
        assertArrayEquals(resource("menus/economy.json"),Files.readAllBytes(target));
        assertArrayEquals(old,Files.readAllBytes(root.resolve("backups/0.5.1/menus/economy.json")));
        assertTrue(Files.readString(target).contains("quick:orders"));
        DefaultMenuMigration.apply(root);
        assertArrayEquals(old,Files.readAllBytes(root.resolve("backups/0.5.1/menus/economy.json")));
    }
    @Test void customizedEconomyIsNotOverwritten()throws Exception{
        byte[] custom=new String(resource("migrations/0.5.1/menus/economy.json"),StandardCharsets.UTF_8).replace("Заработок","Мой заработок").getBytes(StandardCharsets.UTF_8);
        Path target=install("economy.json",custom);DefaultMenuMigration.apply(root);
        assertArrayEquals(custom,Files.readAllBytes(target));assertFalse(Files.exists(root.resolve("backups")));
    }
    @Test void townShortcutUpgradeKeepsOriginalAndRunsOnce()throws Exception{
        byte[] old=resource("migrations/0.6.0/menus/town.json");Path target=install("town.json",old);
        DefaultMenuMigration.apply(root);DefaultMenuMigration.apply(root);
        assertArrayEquals(resource("menus/town.json"),Files.readAllBytes(target));
        assertArrayEquals(old,Files.readAllBytes(root.resolve("backups/0.6.0/menus/town.json")));
    }
    @Test void installedTownAndDevelopmentMenusGainTreeWithoutLosingCustomContent()throws Exception{
        for(String name:new String[]{"town.json","development.json"}){
            var menu=com.google.gson.JsonParser.parseString(new String(resource("menus/"+name),StandardCharsets.UTF_8)).getAsJsonObject();
            var grid=cc.crafto.ui.MenuSchema.find(menu,"modules");
            grid.getAsJsonArray("children").asList().removeIf(e->e.getAsJsonObject().get("id").getAsString().equals("city-tree"));
            menu.addProperty("title","Наше развитие");
            Path target=install(name,menu.toString().getBytes(StandardCharsets.UTF_8));byte[] previous=Files.readAllBytes(target);
            DefaultMenuMigration.apply(root);
            var current=com.google.gson.JsonParser.parseString(Files.readString(target)).getAsJsonObject();
            assertEquals("Наше развитие",current.get("title").getAsString());
            assertEquals("ui:tree",cc.crafto.ui.MenuSchema.find(current,"city-tree").get("action").getAsString());
            assertArrayEquals(previous,Files.readAllBytes(root.resolve("backups/0.9.10/menus/"+name)));
            byte[] once=Files.readAllBytes(target);DefaultMenuMigration.apply(root);assertArrayEquals(once,Files.readAllBytes(target));
        }
    }
    @Test void customizedTownIsPreserved()throws Exception{
        byte[] custom=new String(resource("migrations/0.6.0/menus/town.json"),StandardCharsets.UTF_8).replace("Постройки, ресурсы и развитие","Наш город").getBytes(StandardCharsets.UTF_8);
        Path target=install("town.json",custom);DefaultMenuMigration.apply(root);
        assertArrayEquals(custom,Files.readAllBytes(target));assertFalse(Files.exists(root.resolve("backups")));
    }
    @Test void placementUpgradePreservesCustomizedCatalogAndBacksUpOnce()throws Exception{
        var original=com.google.gson.JsonParser.parseString(new String(resource("migrations/0.7.2/addons.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        original.addProperty("theme","our-theme");original.getAsJsonArray("modules").get(0).getAsJsonObject().addProperty("label","Наши постройки");
        Path target=root.resolve("addons.json");Files.writeString(target,original.toString());byte[] before=Files.readAllBytes(target);
        DefaultMenuMigration.apply(root);byte[] after=Files.readAllBytes(target);DefaultMenuMigration.apply(root);assertArrayEquals(after,Files.readAllBytes(target));
        assertArrayEquals(before,Files.readAllBytes(root.resolve("backups/0.7.2/addons.json")));
        var changed=com.google.gson.JsonParser.parseString(Files.readString(target)).getAsJsonObject();
        assertEquals("our-theme",changed.get("theme").getAsString());assertEquals("Наши постройки",changed.getAsJsonArray("modules").get(0).getAsJsonObject().get("label").getAsString());
        assertTrue(changed.toString().contains("PlacementService$PlacementMenu"));
    }
    @Test void placementMigrationDoesNotEnableAnExcludedBuildsMenu()throws Exception{
        Path target=root.resolve("addons.json");String custom="{\"enabled\":false,\"modules\":[{\"plugin\":\"NeverLandTownyBuilds\",\"holders\":[]}]}";
        Files.writeString(target,custom);DefaultMenuMigration.apply(root);assertEquals(custom,Files.readString(target));
    }
    @Test void minimalDefaultsUpgradeFrom0813WithBackupsAndPreserveSelectedTheme()throws Exception{
        var old=com.google.gson.JsonParser.parseString(new String(resource("migrations/0.8.13/menus/hub.json"),StandardCharsets.UTF_8)).getAsJsonObject();old.addProperty("theme","classic");
        Path target=install("hub.json",old.toString().getBytes(StandardCharsets.UTF_8));byte[] before=Files.readAllBytes(target);
        Path theme=root.resolve("themes/dark.json");Files.createDirectories(theme.getParent());byte[] themeBefore=resource("migrations/0.8.13/themes/dark.json");Files.write(theme,themeBefore);
        Path actions=root.resolve("actions.json");Files.writeString(actions,"{\"custom\":{\"type\":\"message\",\"message\":\"Keep me\"}}");
        DefaultMenuMigration.apply(root);byte[] after=Files.readAllBytes(target);DefaultMenuMigration.apply(root);assertArrayEquals(after,Files.readAllBytes(target));
        var current=com.google.gson.JsonParser.parseString(Files.readString(target)).getAsJsonObject();assertEquals("classic",current.get("theme").getAsString());assertTrue(current.get("responsive").getAsBoolean());assertTrue(current.toString().contains("Все команды"));assertFalse(current.toString().contains("Вернуться в игру"));
        assertArrayEquals(before,Files.readAllBytes(root.resolve("backups/0.8.13/menus/hub.json")));assertArrayEquals(themeBefore,Files.readAllBytes(root.resolve("backups/0.8.13/themes/dark.json")));assertArrayEquals(resource("themes/dark.json"),Files.readAllBytes(theme));
        var updatedActions=com.google.gson.JsonParser.parseString(Files.readString(actions)).getAsJsonObject();assertTrue(updatedActions.has("custom"));assertEquals("commands",updatedActions.getAsJsonObject("commands").get("type").getAsString());
    }
    @Test void customized0813LayoutsThemesAndCommandHandlersArePreserved()throws Exception{
        var custom=com.google.gson.JsonParser.parseString(new String(resource("migrations/0.8.13/menus/hub.json"),StandardCharsets.UTF_8)).getAsJsonObject();custom.addProperty("width",710);Path menu=install("hub.json",custom.toString().getBytes(StandardCharsets.UTF_8));byte[] before=Files.readAllBytes(menu);
        Path theme=root.resolve("themes/dark.json");Files.createDirectories(theme.getParent());Files.writeString(theme,"{\"background\":\"#FF112233\"}");String originalTheme=Files.readString(theme);
        Path actions=root.resolve("actions.json");Files.writeString(actions,"{\"commands\":{\"type\":\"message\",\"message\":\"Custom handler\"}}");String originalActions=Files.readString(actions);
        for(var value:custom.getAsJsonArray("components"))if(value.getAsJsonObject().has("children"))value.getAsJsonObject().getAsJsonArray("children").asList().removeIf(e->java.util.Set.of("quick:orders","hub:society").contains(cc.crafto.ui.Protocol.str(e.getAsJsonObject(),"action","")));
        DefaultMenuMigration.apply(root);assertEquals(custom,com.google.gson.JsonParser.parseString(Files.readString(menu)));assertArrayEquals(before,Files.readAllBytes(root.resolve("backups/0.9.1/menus/hub.json")));
        assertEquals(originalTheme,Files.readString(theme));assertEquals(originalActions,Files.readString(actions));assertFalse(Files.exists(root.resolve("backups/0.8.13")));
    }
    @Test void denseProfileAndHelpUpgradeFrom0816WithBackups()throws Exception{
        for(String name:new String[]{"profile.json","help.yml"}){
            byte[] old=resource("migrations/0.8.16/menus/"+name);Path target=install(name,old);DefaultMenuMigration.apply(root);assertArrayEquals(resource("menus/"+name),Files.readAllBytes(target));assertArrayEquals(old,Files.readAllBytes(root.resolve("backups/0.8.16/menus/"+name)));
        }
    }
    @Test void helpCaptionIsReplacedOnlyForUnmodifiedInstalledFile()throws Exception{
        byte[] old=resource("migrations/0.9.5/menus/help.yml");Path target=install("help.yml",old);
        DefaultMenuMigration.apply(root);assertArrayEquals(resource("menus/help.yml"),Files.readAllBytes(target));
        assertArrayEquals(old,Files.readAllBytes(root.resolve("backups/0.9.5/menus/help.yml")));
    }
    @Test void installedHelpGainsHoverExplanationsWithoutOverwritingCustomMenus()throws Exception{
        byte[] old=resource("migrations/0.9.6/menus/help.yml");Path target=install("help.yml",old);
        DefaultMenuMigration.apply(root);
        assertArrayEquals(resource("menus/help.yml"),Files.readAllBytes(target));
        assertArrayEquals(old,Files.readAllBytes(root.resolve("backups/0.9.6/menus/help.yml")));
        DefaultMenuMigration.apply(root);
        assertArrayEquals(old,Files.readAllBytes(root.resolve("backups/0.9.6/menus/help.yml")));
        byte[] custom=new String(old,StandardCharsets.UTF_8).replace("Где найти нужное","Моя справка").getBytes(StandardCharsets.UTF_8);
        Path customRoot=root.resolve("custom");Path customTarget=customRoot.resolve("menus/help.yml");Files.createDirectories(customTarget.getParent());Files.write(customTarget,custom);
        DefaultMenuMigration.apply(customRoot);assertArrayEquals(custom,Files.readAllBytes(customTarget));
    }
    @Test void shippedExampleHelpWithOldHoverClaimAlsoUpgrades()throws Exception{
        byte[] old=resource("migrations/0.9.6-config/menus/help.yml");Path target=install("help.yml",old);
        DefaultMenuMigration.apply(root);
        assertArrayEquals(resource("menus/help.yml"),Files.readAllBytes(target));
        assertArrayEquals(old,Files.readAllBytes(root.resolve("backups/0.9.6-config/menus/help.yml")));
    }
}
