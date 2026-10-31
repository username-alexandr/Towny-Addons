package cc.crafto.ui.server;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.util.*;import cc.crafto.ui.Protocol;
class CivicViewTest {
    @Test void rarityAndRewardRemainStructuredAndUnavailableOffersAreNotActions(){
        var v=new CivicView();v.rows.add(new CivicView.Row("Улов","Рыбалка","item.minecraft.cod",new CivicView.Action("offer","server-id"),"Уже принят").order("EPIC","125.00","Рыбалка","FISHING"));
        var actions=new HashMap<String,CivicView.Action>();var payload=v.json("token",actions,0,6);var row=payload.getAsJsonArray("rows").get(0).getAsJsonObject();
        assertEquals("EPIC",row.get("rarity").getAsString());assertEquals("125.00",row.get("reward").getAsString());assertTrue(actions.isEmpty());
        assertEquals("minecraft:tropical_fish",row.get("icon").getAsString());assertEquals("Рыбалка",row.get("kind").getAsString());
        assertDoesNotThrow(()->Protocol.decode(Protocol.encode(Protocol.Kind.CIVIC,payload)));
    }
    @Test void disabledRowsHaveNoExecutableServerKey(){var v=new CivicView();v.rows.add(new CivicView.Row("Удалить","","",new CivicView.Action("delete","server-id"),"Нужна должность мэра"));var actions=new HashMap<String,CivicView.Action>();var payload=v.json("token",actions,0,6);assertTrue(actions.isEmpty());assertEquals("",payload.getAsJsonArray("rows").get(0).getAsJsonObject().get("action").getAsString());}
    @Test void pageExposesOnlyDisplayedServerActions(){var v=new CivicView();for(int i=0;i<30;i++)v.rows.add(new CivicView.Row("Задание "+i,"Подробности",new CivicView.Action("contract","server-id-"+i)));var actions=new HashMap<String,CivicView.Action>();var payload=v.json("token",actions,1,6);assertEquals(5,payload.get("pages").getAsInt());assertEquals(6,actions.size());assertFalse(actions.containsKey("a0"));assertEquals("server-id-6",actions.get("a6").id());assertDoesNotThrow(()->Protocol.decode(Protocol.encode(Protocol.Kind.CIVIC,payload)));}
}
