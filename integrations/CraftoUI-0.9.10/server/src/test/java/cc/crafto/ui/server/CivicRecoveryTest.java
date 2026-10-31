package cc.crafto.ui.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the same session recovery and token gate used by CivicWorkbench.handle. */
class CivicRecoveryTest {
    private CivicWorkbench.Review review(){return new CivicWorkbench.Review(new CivicView.Action("delete","server-only-id"),null,null,0,0,"fingerprint","Confirm",java.util.List.of("Consequences"));}
    private CivicWorkbench.State state(){
        var s=new CivicWorkbench.State();s.route="history-entry";s.id="server-event";s.page=2;s.token="old-view";
        s.actions.put("a0",new CivicView.Action("delete","server-only-id"));
        s.history.push(new CivicWorkbench.Route("history","",1));return s;
    }
    @Test void refreshDoesNotRequireOrReuseTheExpiredToken(){
        var s=state();assertTrue(s.recover("refresh"));assertEquals("",s.token);assertTrue(s.actions.isEmpty());
        assertEquals("history-entry",s.route);assertEquals("server-event",s.id);assertEquals(2,s.page);assertEquals(1,s.history.size());
    }
    @Test void refreshCancelsReviewsAndInFlightViews(){
        var s=state();int epoch=s.epoch;String ticket=s.ticket.issue(review(),System.nanoTime());
        assertTrue(s.recover("refresh"));assertTrue(s.epoch>epoch);assertNull(s.ticket.consume(ticket,System.nanoTime()));
    }
    @Test void backUsesOnlyTheServerHistory(){
        var s=state();assertTrue(s.recover("back"));assertEquals("history",s.route);assertEquals("",s.id);assertEquals(1,s.page);
        assertEquals("",s.token);assertTrue(s.actions.isEmpty());assertTrue(s.history.isEmpty());
    }
    @Test void rootBackHasABoundedDestination(){
        var s=state();s.history.clear();assertTrue(s.recover("back"));assertEquals("city",s.route);assertEquals("",s.id);assertEquals(0,s.page);
    }
    @Test void closeInvalidatesActionsReviewsAndPendingLoads(){
        var s=state();int epoch=s.epoch;String ticket=s.ticket.issue(review(),System.nanoTime());
        assertTrue(s.recover("close"));assertTrue(s.epoch>epoch);assertTrue(s.actions.isEmpty());assertTrue(s.history.isEmpty());
        assertEquals("",s.token);assertNull(s.ticket.consume(ticket,System.nanoTime()));assertTrue(s.recover("close"));
    }
    @Test void gameplayOperationsCannotUseTheRecoveryPath(){
        for(String op:new String[]{"act","confirm","page","filter","section","unknown"}){
            var s=state();assertFalse(s.recover(op),op);assertEquals("old-view",s.token);assertEquals(1,s.actions.size());
            assertThrows(IllegalArgumentException.class,()->s.requireToken("forged"));assertDoesNotThrow(()->s.requireToken("old-view"));
        }
    }
    @Test void emptyTokensNeverAuthorizeAnAction(){
        var s=state();s.recover("refresh");assertThrows(IllegalArgumentException.class,()->s.requireToken(""));
        assertThrows(IllegalArgumentException.class,()->s.requireToken("old-view"));
    }
    @Test void oldActionKeysCannotBeReplayedAfterRecovery(){
        var s=state();s.recover("back");s.token="new-view";assertNull(s.actions.get("a0"));
        assertThrows(IllegalArgumentException.class,()->s.requireToken("old-view"));assertDoesNotThrow(()->s.requireToken("new-view"));
    }
    @Test void personalOrdersReturnToTheirActualEntryPoint(){
        var s=state();s.open("mine",false,true);
        assertEquals("mine",s.route);assertTrue(s.nativeOrders);assertTrue(s.history.isEmpty());
        s.history.push(new CivicWorkbench.Route("mine","",2));s.route="order";s.id="order-1";
        assertTrue(s.recover("back"));assertEquals("mine",s.route);assertEquals(2,s.page);assertTrue(s.history.isEmpty());assertTrue(s.nativeOrders);
        s.open("orders",false);assertFalse(s.nativeOrders);assertTrue(s.history.isEmpty());
        s.history.push(new CivicWorkbench.Route("orders","",1));s.route="mine";
        assertTrue(s.recover("back"));assertEquals("orders",s.route);assertEquals(1,s.page);
        s.open("mine",false,true);s.recover("close");assertFalse(s.nativeOrders);
        assertThrows(IllegalArgumentException.class,()->s.open("admin",false));
    }
}
