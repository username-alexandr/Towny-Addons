package ru.neverland.townymarket;
import java.util.UUID;
import static ru.neverland.townymarket.MarketData.*;
/** Market-specific types retain their names; the durable purchase protocol is shared with Builds. */
public final class MarketPayments {
    public interface Store extends ru.neverland.core.PurchaseSaga.Store<Order> {}
    public interface Gateway extends ru.neverland.core.PurchaseSaga.Gateway<Order> {}
    private MarketPayments() {}
    public static void advance(UUID id,long now,long retry,Store store,Gateway gateway)throws Exception { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {ru.neverland.core.PurchaseSaga.advance(id,now,retry,store,gateway);} }
    public static Order resolve(Order order,String decision,long now) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return ru.neverland.core.PurchaseSaga.resolve(order,decision,now);} }
    public static String stockNote(String status) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return ru.neverland.core.PurchaseSaga.stockNote(status);} }
}
