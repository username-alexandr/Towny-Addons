package cc.crafto.ui.server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CitySummaryTest {
    @TempDir Path dir;
    private CitySnapshot snapshot(String town,int plots,int level,boolean known,long at){return new CitySnapshot(town,"Город","","0",plots,8,8-plots,2,List.of(),List.of(),List.of(),Map.of("town_hall",level),Map.of("town_hall","Ратуша"),known,true,0,at);}
    @Test void firstVisitDoesNotInventHistory(){var changes=CitySnapshot.changes(null,snapshot("a",1,2,true,1));assertEquals(1,changes.size());assertTrue(changes.getFirst().contains("Первый вход"));}
    @Test void levelIncreaseAndTerritoryAreReported(){var changes=CitySnapshot.changes(snapshot("a",1,1,true,1),snapshot("a",3,2,true,2));assertTrue(changes.stream().anyMatch(s->s.contains("Ратуша")&&s.contains("2")));assertTrue(changes.stream().anyMatch(s->s.contains("1 → 3")));assertTrue(changes.stream().anyMatch(s->s.contains("7 → 5")));}
    @Test void switchingTownsNeverImpliesConstruction(){var changes=CitySnapshot.changes(snapshot("a",1,0,true,1),snapshot("b",8,4,true,2));assertEquals(1,changes.size());assertTrue(changes.getFirst().contains("теперь"));}
    @Test void unavailableBuildDataNeverCreatesFalseCompletions(){assertFalse(CitySnapshot.changes(snapshot("a",1,0,false,1),snapshot("a",1,4,true,2)).stream().anyMatch(s->s.contains("Завершена")));}
    @Test void unchangedStateIsExplicit(){assertEquals(List.of("Изменений города с прошлого посещения не обнаружено."),CitySnapshot.changes(snapshot("a",1,1,true,1),snapshot("a",1,1,true,2)));}
    @Test void checkpointSurvivesRestartAndRejectsOlderWrite()throws Exception{UUID id=UUID.randomUUID();var store=new CitySummaryStore(dir);store.write(id,snapshot("a",2,2,true,20));new CitySummaryStore(dir).write(id,snapshot("a",1,1,true,10));assertEquals(2,new CitySummaryStore(dir).read(id).claimed());assertNull(store.read(UUID.randomUUID()));}
    @Test void wrongSchemaIsRejected()throws Exception{UUID id=UUID.randomUUID();Files.writeString(dir.resolve(id+".json"),"{\"schema\":999}");assertThrows(IllegalArgumentException.class,()->new CitySummaryStore(dir).read(id));}
    @Test void formattingIsRemovedAndBounded(){assertEquals("Ратуша",CitySnapshot.clean("&#ABCDEF&lРатуша"));assertEquals(160,CitySnapshot.clean("x".repeat(200)).length());}
}
