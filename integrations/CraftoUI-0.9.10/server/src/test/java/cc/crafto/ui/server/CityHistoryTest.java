package cc.crafto.ui.server;
import org.junit.jupiter.api.Test;
import java.util.*;
import cc.crafto.ui.Protocol;
import static org.junit.jupiter.api.Assertions.*;

public class CityHistoryTest {
    private static final UUID TOWN=UUID.randomUUID();
    public record Source(UUID id,UUID townId,String category,long timestamp,String title,List<String> details,String actor,String source){}
    private Source entry(UUID town,long time){return new Source(UUID.randomUUID(),town,"TERRITORY",time,"&aРасширение города",List.of("Захвачено 4 участка"),"Мэр","Towny");}
    @Test void keepsOnlyCurrentTownAndSortsNewestFirst()throws Exception{
        var old=entry(TOWN,1);var newest=entry(TOWN,3);var other=entry(UUID.randomUUID(),9);
        var result=CityHistory.convert(List.of(old,other,newest),TOWN);
        assertEquals(List.of(newest.id().toString(),old.id().toString()),result.entries().stream().map(CityHistory.Entry::id).toList());
    }
    @Test void preservesRealFieldsAndCyrillic()throws Exception{
        var e=CityHistory.convert(List.of(entry(TOWN,0)),TOWN).entries().getFirst();
        assertEquals("Расширение города",e.title());assertEquals("Территория",e.category());assertEquals("Мэр",e.actor());assertEquals("Towny",e.source());assertEquals(List.of("Захвачено 4 участка"),e.details());assertEquals("01.01.1970 03:00 МСК",e.date());
    }
    @Test void duplicateIdsAppearOnce()throws Exception{var e=entry(TOWN,3);assertEquals(1,CityHistory.convert(List.of(e,e),TOWN).entries().size());}
    @Test void emptyHistoryIsDifferentFromMissingAddon()throws Exception{
        assertTrue(CityHistory.convert(List.of(),TOWN).problem().isEmpty());assertFalse(CityHistory.read(null,TOWN).problem().isEmpty());
    }
    @Test void missingTownDoesNotReadAnyAddon(){assertTrue(CityHistory.read(null,null).problem().contains("вступите"));}
    @Test void unknownCategoriesRemainReadable()throws Exception{
        var e=new Source(UUID.randomUUID(),TOWN,"DIPLOMACY",0,"Союз",List.of(),null,null);
        var result=CityHistory.convert(List.of(e),TOWN).entries().getFirst();assertEquals("DIPLOMACY",result.category());assertEquals("",result.actor());assertEquals("",result.source());
    }
    @Test void longDetailsRemainWithinProtocolBudgetAndExplainTruncation()throws Exception{
        var raw=new Source(UUID.randomUUID(),TOWN,"CUSTOM",0,"Я".repeat(600),Collections.nCopies(100,"Я".repeat(1000)),"Мэр","Аддон");
        var e=CityHistory.convert(List.of(raw),TOWN).entries().getFirst();assertTrue(e.details().getLast().contains("слишком длинная"));
        var v=new CivicView();v.document=true;v.title=e.title();v.lines.add(e.subtitle());v.lines.addAll(e.details());
        var data=v.json("token",new HashMap<>(),0,12);assertTrue(data.get("document").getAsBoolean());assertDoesNotThrow(()->Protocol.decode(Protocol.encode(Protocol.Kind.CIVIC,data)));
    }
    @Test void allSavedPagesAreAccessibleBeyond128Entries()throws Exception{
        var source=new ArrayList<Source>();for(int i=0;i<301;i++)source.add(entry(TOWN,i));var result=CityHistory.convert(source,TOWN);assertEquals(301,result.entries().size());
        var view=new CivicView();for(var e:result.entries())view.rows.add(new CivicView.Row(e.title(),e.subtitle(),new CivicView.Action("history-entry",e.id())));
        var actions=new HashMap<String,CivicView.Action>();var page=view.json("token",actions,50,6);assertEquals(51,page.get("pages").getAsInt());assertEquals(1,actions.size());assertEquals(source.getFirst().id().toString(),actions.get("a300").id());
    }
    @Test void malformedRecordsAreNotReportedAsEmptyHistory(){assertThrows(ReflectiveOperationException.class,()->CityHistory.convert(List.of(new Object()),TOWN));}
}
