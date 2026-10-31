package cc.crafto.ui.server;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static cc.crafto.ui.Protocol.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerExperienceTest {
    @TempDir Path root;
    @Test void reasonsDistinguishRoleTownAndUnavailableModule(){
        assertEquals("",ActionAvailability.reason(object("permission","allowed"),true,x->true,x->true));
        assertTrue(ActionAvailability.reason(object("permission","mayor"),true,x->false,x->true).contains("роли"));
        assertTrue(ActionAvailability.reason(object("requiredPlugin","Builds"),true,x->true,x->false).contains("выключен"));
        assertTrue(ActionAvailability.reason(object("requiresTown",true),false,x->true,x->true).contains("Вступите"));
        assertEquals("Дождитесь голосования",ActionAvailability.reason(object("enabled",false,"unavailableReason","Дождитесь голосования"),true,x->true,x->true));
        assertFalse(ActionAvailability.reason(null,true,x->true,x->true).isEmpty());
    }
    @Test void destructiveActionsAreNotClassifiedAsNavigation(){
        assertNull(NativeMenuPresentation.role("Отменить строительство"));
        assertNull(NativeMenuPresentation.role("Удалить город"));
        assertNull(NativeMenuPresentation.role("Разрешить"));
        assertEquals("confirm",NativeMenuPresentation.role("Подтвердить сдачу"));
        assertEquals("back",NativeMenuPresentation.role("Назад"));
    }
    @Test void migrationAddsPersonalHistoryWithoutOpeningAdminMenu()throws Exception{
        for(String file:new String[]{"addons.json","quick-actions.json","menus/economy.json","menus/profile.json"}){
            Path target=root.resolve(file);Files.createDirectories(target.getParent());
            try(var input=getClass().getClassLoader().getResourceAsStream("migrations/0.8.3/"+file)){Files.write(target,input.readAllBytes());}
        }
        DefaultMenuMigration.apply(root);var catalog=AddonCatalog.parse(JsonParser.parseString(Files.readString(root.resolve("addons.json"))).getAsJsonObject());
        var control=catalog.entries().get("control");
        assertEquals("neverlandtownycontrol.admin",control.holderPermission("ru.neverland.townycontrol.AdminMenu$Screen"));
        assertEquals("neverlandtownycontrol.history",control.holderPermission("ru.neverland.townycontrol.PersonalHistoryMenu$View"));
        assertTrue(catalog.entries().get("builds").holders().contains("ru.neverland.townybuilds.gui.MenuManager$ContributionHolder"));
        assertTrue(Files.readString(root.resolve("menus/economy.json")).contains("quick:history"));
        byte[] before=Files.readAllBytes(root.resolve("addons.json"));DefaultMenuMigration.apply(root);assertArrayEquals(before,Files.readAllBytes(root.resolve("addons.json")));
        assertTrue(Files.isRegularFile(root.resolve("backups/0.8.3/addons.json")));
    }
}
