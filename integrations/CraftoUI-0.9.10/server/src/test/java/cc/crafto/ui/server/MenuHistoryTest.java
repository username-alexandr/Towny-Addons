package cc.crafto.ui.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuHistoryTest {
    @Test void returnsToActualPreviousMenuAcrossKinds(){
        var history=new MenuHistory<String>();
        history.visit("screen:hub","hub");history.visit("screen:economy","economy");history.visit("native:orders","orders");
        assertEquals("economy",history.previous());assertEquals("economy",history.back());assertEquals("hub",history.back());
        assertNull(history.previous());assertNull(history.back());assertNull(history.current());
    }
    @Test void returningAncestorReplacesItWithFreshInstanceAndDoesNotLoop(){
        var history=new MenuHistory<String>();
        history.visit("hub","hub");history.visit("orders","old board");history.visit("detail","detail");
        history.visit("orders","refreshed board");
        assertEquals("refreshed board",history.current());assertEquals("hub",history.previous());assertEquals("hub",history.back());
    }
    @Test void refreshDoesNotBecomeAnotherBackStep(){
        var history=new MenuHistory<Integer>();history.visit("hub",1);history.visit("profile",2);history.visit("profile",3);
        assertEquals(3,history.current());assertEquals(1,history.back());
    }
    @Test void boundsRetainedInventoriesAndClearsOnSessionEnd(){
        var history=new MenuHistory<Integer>();for(int i=0;i<100;i++)history.visit("menu:"+i,i);
        int steps=0;while(history.current()!=null){steps++;history.back();}assertEquals(32,steps);
        history.visit("new",1);history.clear();assertNull(history.current());assertNull(history.previous());
    }
}
