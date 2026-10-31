package cc.crafto.ui;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TownUiTest {
    @Test void townlessTabsRetainInvitationsAndPersonalOrders(){
        assertEquals(List.of("inbox","orders"),TownUi.tabs(false));
        for(String route:TownUi.tabs(false))assertFalse(TownUi.requiresTown(route));
        for(String route:List.of("city","materials","map","goals","build","contract","history","history-entry","plot","treasury"))assertTrue(TownUi.requiresTown(route));
        for(String route:List.of("invite-town","invite-company","offer","order","mine"))assertFalse(TownUi.requiresTown(route));
    }
    @Test void memberTabsAppearOnceAndReturnWhenJoining(){
        assertEquals(5,TownUi.tabs(true).size());assertEquals(5,new HashSet<>(TownUi.tabs(true)).size());assertFalse(TownUi.tabs(true).contains("orders"));
        assertTrue(TownUi.tabs(true).contains("city"));assertFalse(TownUi.tabs(false).contains("city"));
    }
    @Test void mergingFavoritesKeepsOrderAndUnavailableFavorites(){
        var p=new PersonalPreferences();p.favorites=new ArrayList<>(List.of("button:hub/orders","link:orders","temporarily-hidden-city","button:town/warehouse"));
        assertTrue(p.remapFavorites(Map.of("button:hub/orders","link:orders","button:town/warehouse","link:warehouse")));
        assertEquals(List.of("link:orders","temporarily-hidden-city","link:warehouse"),p.favorites);
        assertFalse(p.remapFavorites(Map.of("button:hub/orders","link:orders")));
    }
}
