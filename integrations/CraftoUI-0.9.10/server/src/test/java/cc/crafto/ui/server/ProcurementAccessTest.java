package cc.crafto.ui.server;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProcurementAccessTest {
    public static class Provider {
        int version=1;Set<String> capabilities=Set.of("offers","quote","confirm","status","cancel","requests");
        CompletableFuture<Object> pending=new CompletableFuture<>();int calls;UUID last;
        public int apiVersion(){return version;}public Set<String> capabilities(){return capabilities;}
        public CompletionStage<Object> confirm(Player p,UUID id){calls++;last=id;return pending;}
        public CompletionStage<Object> status(Player p,UUID id){last=id;return pending;}
    }
    @Test void financialCallIsIssuedOnceAndOnlyForTheSavedId()throws Exception{var p=new Provider();UUID id=UUID.randomUUID();var result=ProcurementAccess.invoke(p,Provider.class,"confirm",null,id).toCompletableFuture();assertEquals(1,p.calls);assertEquals(id,p.last);assertFalse(result.isDone());p.pending.complete("ack");assertEquals("ack",result.join());assertEquals(1,p.calls);}
    @Test void incompatibleVersionAndMissingMethodsNeverCallProvider(){var p=new Provider();p.version=2;assertThrows(IllegalArgumentException.class,()->ProcurementAccess.invoke(p,Provider.class,"confirm",null,UUID.randomUUID()));p.version=1;p.capabilities=Set.of("confirm");assertThrows(IllegalArgumentException.class,()->ProcurementAccess.invoke(p,Provider.class,"confirm",null,UUID.randomUUID()));assertEquals(0,p.calls);}
    @Test void rejectsArbitraryMethodNames(){var p=new Provider();assertThrows(IllegalArgumentException.class,()->ProcurementAccess.invoke(p,Provider.class,"deleteTown",null,UUID.randomUUID()));assertEquals(0,p.calls);}
    @Test void timeoutOrCancellationOfUiDoesNotCancelPayment()throws Exception{var p=new Provider();var ui=ProcurementAccess.invoke(p,Provider.class,"confirm",null,UUID.randomUUID()).toCompletableFuture();ui.completeExceptionally(new TimeoutException());assertFalse(p.pending.isDone());p.pending.complete("paid");assertEquals("paid",p.pending.join());assertThrows(CompletionException.class,ui::join);assertEquals(1,p.calls);}
    @Test void providerFailurePreservesHumanExplanation()throws Exception{var p=new Provider();var ui=ProcurementAccess.invoke(p,Provider.class,"confirm",null,UUID.randomUUID()).toCompletableFuture();p.pending.completeExceptionally(new CompletionException(new IllegalArgumentException("Мэр изменился")));var error=assertThrows(CompletionException.class,ui::join);assertEquals("Мэр изменился",error.getCause().getMessage());assertEquals(1,p.calls);}
}
