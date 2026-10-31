package cc.crafto.ui.server;

import cc.crafto.ui.Protocol;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class CityFinanceTest {
    static final long NOW=Instant.parse("2026-10-28T12:00:00Z").toEpochMilli();
    static final UUID TOWN=UUID.randomUUID();
    public record Week(Map<String,?> income,Map<String,?> expenses,long refunds,long adjustments,Map<String,Long> categories){}
    public record Ledger(long started,Map<String,Week> weeks,Object pending){}
    public static final class Provider {
        final Ledger data;boolean broken;int reads;UUID requested;
        Provider(Ledger data){this.data=data;}
        public boolean fault(){return broken;}
        public Optional<Ledger> treasury(UUID town){reads++;requested=town;return Optional.ofNullable(data);}
        public void sync(){throw new AssertionError("Must never read the economy account");}
        public void spend(){throw new AssertionError("Must never mutate the economy account");}
    }
    Week week(long income,long expense,long refunds,long adjustments){return new Week(Map.of("tax",income),Map.of("upkeep",expense),refunds,adjustments,Map.of("infrastructure",expense));}
    Ledger ledger(Week week,Object pending){return new Ledger(NOW-10*86400000L,Map.of(CityFinance.weekAt(NOW),week),pending);}
    CityFinance read(Week week){return CityFinanceReader.readProvider(new Provider(ledger(week,null)),TOWN,NOW);}

    @Test void actualCashFlowsIncludeRefundsExactlyOnceAndDoNotDoubleCountCategories(){
        var f=read(week(200000,150000,5000,0));assertTrue(f.known());assertEquals(205000,f.received());assertEquals(150000,f.expenses());assertEquals(55000,f.net());assertEquals("positive",f.tone());assertEquals("Казна в плюсе",f.status());
        var view=new CivicView();f.addDetails(view);assertTrue(view.rows.stream().anyMatch(r->r.detail().contains("1 500,00")));
    }
    @Test void deficitAndFlatWeekHaveDistinctStatuses(){var f=read(week(100,500,0,0));assertEquals(-400,f.net());assertEquals("negative",f.tone());assertEquals("Казна в минусе",f.status());assertEquals("Без изменения",read(week(0,0,0,0)).status());}
    @Test void weekBoundaryIsMondayUtcNotClientTimezone(){long before=Instant.parse("2026-10-25T23:59:59.999Z").toEpochMilli(),after=before+1;assertEquals("2026-10-19",CityFinance.weekAt(before));assertEquals("2026-10-26",CityFinance.weekAt(after));assertEquals(after,CityFinance.startOfWeek(after));}
    @Test void absentCurrentWeekMeansNoFlowsOnlyWhenLedgerExists(){var f=CityFinanceReader.readProvider(new Provider(new Ledger(NOW-864000000L,Map.of("2026-10-19",week(999,500,0,0)),null)),TOWN,NOW);assertTrue(f.known());assertEquals(0,f.net());assertEquals(CityFinance.startOfWeek(NOW),f.since());}
    @Test void absentLedgerOrPluginDoesNotBecomeZeroIncome(){assertFalse(CityFinanceReader.readProvider(new Provider(null),TOWN,NOW).known());var f=CityFinanceReader.read(null,TOWN,NOW);assertFalse(f.known());assertEquals("—",f.dashboard().getAsJsonArray("metrics").get(0).getAsJsonObject().get("value").getAsString());}
    @Test void partialWeekStartsWhenAccountingStarted(){long started=NOW-60000;var f=CityFinanceReader.readProvider(new Provider(new Ledger(started,Map.of(),null)),TOWN,NOW);assertTrue(f.known());assertEquals(started,f.since());assertTrue(f.explanation().contains("28.10.2026 11:59 UTC"));}
    @Test void failedOrIncompatibleProviderFailsClosed(){var p=new Provider(ledger(week(500,100,0,0),null));p.broken=true;assertFalse(CityFinanceReader.readProvider(p,TOWN,NOW).known());assertEquals(0,p.reads);assertFalse(CityFinanceReader.readProvider(new Object(),TOWN,NOW).known());}
    @Test void adjustmentDoesNotMasqueradeAsProfitOrExpense(){var f=read(week(500,100,0,2000));assertEquals(400,f.net());assertEquals(2000,f.adjustments());assertEquals("warning",f.tone());assertEquals("Нужна сверка",f.status());assertTrue(f.explanation().contains("+20,00"));}
    @Test void pendingPaymentCannotShowConfidentSurplus(){var f=CityFinanceReader.readProvider(new Provider(ledger(week(500,100,0,0),new Object())),TOWN,NOW);assertTrue(f.known());assertEquals("warning",f.tone());assertTrue(f.explanation().contains("незавершённое списание"));}
    @Test void negativeFractionalAndOverflowAmountsAreRejected(){
        for(Object bad:List.of(-1L,0.5,Double.NaN,"100")){
            var badWeek=new Week(Map.of("tax",bad),Map.of(),0,0,Map.of());assertFalse(read(badWeek).known());
        }
        var overflow=new Week(Map.of("tax",Long.MAX_VALUE,"other",1L),Map.of(),0,0,Map.of());assertFalse(read(overflow).known());
        assertFalse(read(week(Long.MAX_VALUE,0,1,0)).known());assertFalse(read(week(100,0,-1,0)).known());
    }
    @Test void dataFromFutureIsRejected(){assertFalse(CityFinanceReader.readProvider(new Provider(new Ledger(NOW+1,Map.of(),null)),TOWN,NOW).known());}
    @Test void apiReceivesOnlyServerCapturedTownAndIsReadOnly(){var p=new Provider(ledger(week(100,10,0,0),null));assertTrue(CityFinanceReader.readProvider(p,TOWN,NOW).known());assertEquals(TOWN,p.requested);assertEquals(1,p.reads);}
    @Test void deniedFinanceDoesNotLeakAmountsOrRegisterAction(){var v=new CivicView();v.city=new com.google.gson.JsonObject();CityDashboardView.finances(v,read(week(987654321,1234567,0,0)),"Нужно право просмотра казны");var actions=new HashMap<String,CivicView.Action>();var payload=v.json("server",actions,0,6);String wire=payload.toString();assertFalse(wire.contains("9 876 543"));assertFalse(wire.contains("12 345"));assertFalse(actions.containsKey("cf"));assertFalse(payload.getAsJsonObject("city").getAsJsonObject("finance").get("known").getAsBoolean());}
    @Test void financeCardsUseServerActionAndFitProtocol(){var v=new CivicView();v.city=new com.google.gson.JsonObject();CityDashboardView.finances(v,read(week(200000,150000,5000,0)),"");var actions=new HashMap<String,CivicView.Action>();var payload=v.json("server",actions,0,6);assertEquals(new CivicView.Action("route","treasury"),actions.get("cf"));assertEquals("cf",payload.getAsJsonObject("city").getAsJsonObject("finance").get("action").getAsString());assertDoesNotThrow(()->Protocol.decode(Protocol.encode(Protocol.Kind.CIVIC,payload)));}
    @Test void manySourcesStayBoundedWithoutLosingTotal(){var expenses=new HashMap<String,Long>();for(int i=0;i<100;i++)expenses.put("source_"+i,100L);var f=read(new Week(Map.of(),expenses,0,0,Map.of()));var v=new CivicView();f.addDetails(v);assertEquals(10000,f.expenses());assertEquals(4,v.rows.size());assertEquals("Расход: Прочие списания",v.rows.getLast().title());assertEquals("100,00",v.rows.getLast().detail());assertFalse(v.rows.toString().contains("source_"));}
    @Test void centsAndSignAreNotRoundedThroughDouble(){assertEquals("9 007 199 254 740,99",CityFinance.money(900719925474099L));assertEquals("+0,01",CityFinance.signed(1));assertEquals("-0,01",CityFinance.signed(-1));}
}
