package cc.crafto.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActionReplyGateTest {
    @Test void staleCityReplyCannotOpenCityAfterSettingsClick(){
        var gate=new ActionReplyGate();long city=gate.sent("city"),settings=gate.sent("client:settings");
        assertFalse(gate.accept(city,"city"));
        assertTrue(gate.accept(settings,"settings"));
        assertFalse(gate.accept(settings,"settings"));
    }
    @Test void mismatchedDestinationAndRepeatedReplyAreRejected(){
        var gate=new ActionReplyGate();long settings=gate.sent("client:settings");
        assertFalse(gate.accept(settings,"city"));
        assertFalse(gate.accept(settings,"settings"));
        assertTrue(gate.accept(gate.sent("city"),"city"));
    }
}
