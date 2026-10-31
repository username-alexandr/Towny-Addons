package cc.crafto.ui.server;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static cc.crafto.ui.Protocol.*;
import static org.junit.jupiter.api.Assertions.*;

class TownNavigationTest {
    @Test void creationIsHiddenForMembersAndNeverRequiresAnExistingTown(){
        var action=object("type","commands","module","Towny","form","Towny/create-town","requiresNoTown",true,"permission","towny.command.town.new","requiredPlugin","Towny");
        assertFalse(NavigationPolicy.requiresTown(action));assertFalse(NavigationPolicy.townMismatch(action,false));assertTrue(NavigationPolicy.townMismatch(action,true));
        assertEquals("",ActionAvailability.reason(action,false,p->true,p->true));assertFalse(ActionAvailability.reason(action,true,p->true,p->true).isBlank());
        assertFalse(ActionAvailability.reason(action,false,p->false,p->true).isBlank());assertFalse(ActionAvailability.reason(action,false,p->true,p->false).isBlank());
        assertEquals("command-form:Towny/create-town",NavigationPolicy.destination(action));assertNotEquals("client:commands",NavigationPolicy.destination(action));
    }
    @Test void existingTownMenusAndCommandsAreRecognizedWithoutConfigRewrite(){
        for(String id:List.of("town","society","development","security")){
            assertTrue(NavigationPolicy.requiresTown(object("id",id,"components",new JsonArray())));
            assertTrue(NavigationPolicy.requiresTown(object("type","open","menu",id)));
        }
        for(String command:List.of("town inv","town builds","town contracts","town trade","townyjobs","townyquests","townyachievements","governance council","townydiplomacy","/neverlandtownybuilds:town inv","TOWN BUILDS list","governance council member"))
            assertTrue(NavigationPolicy.requiresTown(object("type","player-command","command",command)),command);
        assertTrue(NavigationPolicy.requiresTown(object("type","city")));
    }
    @Test void personalFeaturesAndTownInvitationsStayAvailable(){
        for(String command:List.of("orders","orders mine","townymarket","townycompanies","camp","archaeology","town invite accept","town create NewTown"))
            assertFalse(NavigationPolicy.requiresTown(object("type","player-command","command",command)),command);
        assertFalse(NavigationPolicy.requiresTown(object("type","open","menu","economy")));
    }
    @Test void customMenusCanDeclareTownRequirementExplicitly(){
        assertTrue(NavigationPolicy.requiresTown(object("id","custom","requiresTown",true)));
        assertFalse(NavigationPolicy.requiresTown(object("id","town","components",new JsonArray(),"requiresTown",false)));
    }
    @Test void directoryMergesDestinationsAndRetainsFavoritesAndSearchTerms(){
        var index=new NavigationDirectory();var action=object("type","player-command","command","orders","requiredPlugin","Contracts","closeBefore",true);
        index.add("link:orders","Личные заказы","награды",NavigationPolicy.destination(action));
        index.add("button:economy/orders","Доска","задания",NavigationPolicy.destination(action));
        index.add("button:hub/orders","Заказы","добыча",NavigationPolicy.destination(action));
        assertEquals(1,index.entries.size());var row=index.entries.get(0).getAsJsonObject();assertEquals("link:orders",str(row,"id",""));
        assertEquals(2,row.getAsJsonArray("aliases").size());assertTrue(str(row,"keywords","").contains("награды"));assertTrue(str(row,"keywords","").contains("добыча"));
        assertDoesNotThrow(()->encode(Kind.DIRECTORY,object("entries",index.entries,"townMember",false)));
    }
    @Test void differentCommandsOwnersAndNonNavigationActionsAreNotMerged(){
        var base=object("type","player-command","command","orders","requiredPlugin","A","closeBefore",true);
        var changed=base.deepCopy();changed.addProperty("command","orders mine");assertNotEquals(NavigationPolicy.destination(base),NavigationPolicy.destination(changed));
        changed=base.deepCopy();changed.addProperty("requiredPlugin","B");assertNotEquals(NavigationPolicy.destination(base),NavigationPolicy.destination(changed));
        changed=base.deepCopy();changed.addProperty("closeBefore",false);assertEquals("",NavigationPolicy.destination(changed));
        assertEquals("",NavigationPolicy.destination(object("type","message")));
    }
    @Test void fixedClientDestinationsAreDistinctAndMergeOnlyTheirOwnAliases(){
        var destinations=new HashSet<String>();
        for(String type:List.of("settings","commands","close","refresh")){
            String key=NavigationPolicy.destination(object("type",type));assertFalse(key.isBlank());assertTrue(destinations.add(key));
            var index=new NavigationDirectory();index.add("first",type,"",key);index.add("alias",type,"",key);assertEquals(1,index.entries.size());
        }
        assertEquals("",NavigationPolicy.destination(object("type","external-handler","handler","same-name")));
    }
}
