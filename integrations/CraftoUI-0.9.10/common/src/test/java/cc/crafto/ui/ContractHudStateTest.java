package cc.crafto.ui;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ContractHudStateTest {
    private ContractHudState.Entry entry(){return new ContractHudState.Entry(UUID.randomUUID().toString(),"Безопасность дорог",7,16,false,ContractHudState.Status.ACTIVE);}
    @Test void roundTripPreservesProgress(){var state=new ContractHudState(List.of(entry()));assertEquals(state,ContractHudState.parse(Protocol.decode(Protocol.encode(Protocol.Kind.CONTRACTS,state.json())).body()));}
    @Test void rejectsExcessProgress(){assertThrows(IllegalArgumentException.class,()->new ContractHudState.Entry(UUID.randomUUID().toString(),"Заказ",17,16,false,ContractHudState.Status.ACTIVE));}
    @Test void rejectsDuplicateIds(){var entry=entry();assertThrows(IllegalArgumentException.class,()->new ContractHudState(List.of(entry,entry)));}
    @Test void rejectsFractionAndOverflow(){var body=new ContractHudState(List.of(entry())).json();var row=body.getAsJsonArray("entries").get(0).getAsJsonObject();row.addProperty("goal",1.5);assertThrows(ArithmeticException.class,()->ContractHudState.parse(body));row.addProperty("goal",4294967296L);assertThrows(ArithmeticException.class,()->ContractHudState.parse(body));}
    @Test void legacyPacketOrdinalsRemainStable(){assertEquals(8,Protocol.Kind.NATIVE_MENU.ordinal());assertEquals(9,Protocol.Kind.CONTRACTS.ordinal());assertEquals(10,Protocol.Kind.BACK.ordinal());}
    @Test void targetAndActivitySurviveWireRoundTrip(){
        for(String kind:List.of("DELIVERY","MINING","HUNTING","FISHING")){
            var entry=new ContractHudState.Entry(UUID.randomUUID().toString(),"Контракт",3,16,false,ContractHudState.Status.ACTIVE,"entity.minecraft.drowned",kind);
            var state=new ContractHudState(List.of(entry));assertEquals(state,ContractHudState.parse(Protocol.decode(Protocol.encode(Protocol.Kind.CONTRACTS,state.json())).body()));
        }
    }
    @Test void acceptsOldServerWithoutTargetMetadata(){
        var old=entry();var json=new ContractHudState(List.of(old)).json();var row=json.getAsJsonArray("entries").get(0).getAsJsonObject();row.remove("targetKey");row.remove("kind");
        assertEquals(old,ContractHudState.parse(json).entries().getFirst());
    }
    @Test void rejectsUnrelatedTranslationKeysAndUnknownActivities(){
        assertThrows(IllegalArgumentException.class,()->new ContractHudState.Entry(UUID.randomUUID().toString(),"Контракт",0,1,false,ContractHudState.Status.ACTIVE,"chat.type.text","DELIVERY"));
        assertThrows(IllegalArgumentException.class,()->new ContractHudState.Entry(UUID.randomUUID().toString(),"Контракт",0,1,false,ContractHudState.Status.ACTIVE,"item.minecraft.honey_bottle","COMMAND"));
    }
}
