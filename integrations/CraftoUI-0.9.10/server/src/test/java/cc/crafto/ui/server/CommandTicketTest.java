package cc.crafto.ui.server;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class CommandTicketTest {
    @Test void replayDoesNotExecuteTwice(){var t=new CommandTicket<String>();String token=t.issue("fixed args",100);assertEquals("fixed args",t.consume(token,200));assertNull(t.consume(token,201));}
    @Test void wrongConnectionTokenDoesNotConsumeValidTicket(){var a=new CommandTicket<String>();var b=new CommandTicket<String>();String token=a.issue("a",100);b.issue("b",100);assertNull(b.consume(token,101));assertEquals("a",a.consume(token,101));}
    @Test void expiresInThirtySeconds(){var t=new CommandTicket<String>();String token=t.issue("old",0);assertNull(t.consume(token,TimeUnit.SECONDS.toNanos(31)));}
    @Test void newReviewInvalidatesPrevious(){var t=new CommandTicket<String>();String first=t.issue("first",0),second=t.issue("second",1);assertNull(t.consume(first,2));assertEquals("second",t.consume(second,2));}
    @Test void reloadOrDisconnectInvalidates(){var t=new CommandTicket<String>();String token=t.issue("old",0);t.clear();assertNull(t.consume(token,1));}
}
