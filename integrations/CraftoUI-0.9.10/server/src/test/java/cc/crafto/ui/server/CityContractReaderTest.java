package cc.crafto.ui.server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CityContractReaderTest {
    private final UUID player=UUID.randomUUID();
    private YamlConfiguration fixture(){var y=new YamlConfiguration();y.set("schema",3);y.set("towns.a.active.one.terms.name","&aПоставка камня");y.set("towns.a.active.one.goal",64);y.set("towns.a.active.one.progress",16);y.set("towns.b.active.two.terms.name","Чужой город");y.set("towns.b.active.two.goal",10);y.set("towns.b.active.two.progress",2);y.set("pending.players."+player,25.5);return y;}
    @Test void ownTownAndOwnRewardsOnly(){var y=fixture();var r=CityContractReader.read(y,"a",player);assertEquals(25.5,r.pending());assertEquals(List.of(new CitySnapshot.Row("Поставка камня — 16 / 64")),r.rows());assertEquals(0,CityContractReader.read(y,"a",UUID.randomUUID()).pending());}
    @Test void cancelledOrdersAreNotShown(){var y=fixture();y.set("towns.a.active.one.admin-cancelled",true);assertEquals("Активных городских заданий нет.",CityContractReader.read(y,"a",player).rows().getFirst().text());}
    @Test void pendingSettlementIsNotClaimableProgress(){var y=fixture();y.set("towns.a.active.one.settlement-status","PENDING");assertTrue(CityContractReader.read(y,"a",player).rows().getFirst().text().endsWith("расчёт награды"));}
    @Test void unknownSchemaAndInvalidValuesAreRejected(){var y=fixture();y.set("schema",999);assertThrows(IllegalArgumentException.class,()->CityContractReader.read(y,"a",player));y.set("schema",3);y.set("pending.players."+player,Double.NaN);assertThrows(IllegalArgumentException.class,()->CityContractReader.read(y,"a",player));}
    @Test void malformedSectionsAreNotReportedAsEmptyOrZero(){var y=fixture();y.set("towns.a.active","broken");assertThrows(IllegalArgumentException.class,()->CityContractReader.read(y,"a",player));var money=fixture();money.set("pending.players."+player,"broken");assertThrows(IllegalArgumentException.class,()->CityContractReader.read(money,"a",player));}
    private void payment(YamlConfiguration y,String id,UUID owner,String kind,String phase,long cents){String path="payments."+id+".";y.set(path+"account",owner.toString());y.set(path+"kind",kind);y.set(path+"phase",phase);y.set(path+"cents",cents);}
    @Test void journalRewardsMatchAddonPendingPlayerAndExcludeOtherMoney(){
        var y=fixture();payment(y,"ready",player,"REWARD","READY",1000);payment(y,"pending",player,"REWARD","PENDING",2025);
        payment(y,"done",player,"REWARD","DONE",9999);payment(y,"rejected",player,"REWARD","REJECTED",9999);
        payment(y,"refund",player,"REFUND","READY",9999);payment(y,"reserve",player,"RESERVE","READY",9999);
        payment(y,"other",UUID.randomUUID(),"REWARD","READY",9999);
        assertEquals(55.75,CityContractReader.read(y,"a",player).pending());
    }
    @Test void malformedJournalDoesNotProduceMisleadingReward(){
        var y=fixture();payment(y,"ready",player,"REWARD","UNKNOWN",1000);assertThrows(IllegalArgumentException.class,()->CityContractReader.read(y,"a",player));
        y.set("payments.ready.phase","READY");y.set("payments.ready.cents",10.5);assertThrows(IllegalArgumentException.class,()->CityContractReader.read(y,"a",player));
        y.set("payments.ready.cents",-1);assertThrows(IllegalArgumentException.class,()->CityContractReader.read(y,"a",player));
    }
}
