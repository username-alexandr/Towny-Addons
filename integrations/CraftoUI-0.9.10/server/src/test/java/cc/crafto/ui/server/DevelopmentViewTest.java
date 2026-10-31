package cc.crafto.ui.server;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DevelopmentViewTest {
    private final UUID town=UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private Map<String,Object> sample(){
        var row=new LinkedHashMap<String,Object>();row.put("schema",1);row.put("town",town.toString());row.put("catalog",List.of(Map.of("id","town_hall","name","Ратуша","current",0,"maximum",5)));
        row.put("steps",List.of(Map.of("project","town_hall","name","Ратуша","level",1,"cents",10000L,"bonusBlocks",5,"notices",List.of("Нужно содержание"))));row.put("cents",10000L);row.put("bonusBlocks",5);
        row.put("supply",List.of(Map.of("key","OAK_LOG","name","Дубовое бревно","material","OAK_LOG","custom",false,"required",272L,"donated",64L,"stored",128L,"missing",80L)));
        row.put("finalLevels",Map.of("town_hall",1));row.put("known",true);row.put("stockKnown",true);row.put("notices",List.of());return row;
    }
    @Test void materialCountIsSeparateFromAnyItemStack(){var view=DevelopmentView.decode(sample(),town,false);assertEquals(272,view.supplies().getFirst().required());assertEquals(80,view.supplies().getFirst().missing());assertEquals(10000,view.cents());}
    @Test void refusesAnotherTown(){assertThrows(IllegalArgumentException.class,()->DevelopmentView.decode(sample(),UUID.randomUUID(),false));}
    @Test void refusesInconsistentMoney(){var value=sample();value.put("cents",1L);assertThrows(IllegalArgumentException.class,()->DevelopmentView.decode(value,town,false));}
    @Test void refusesRepeatedLevel(){var value=sample();value.put("steps",List.of(((List<?>)value.get("steps")).getFirst(),((List<?>)value.get("steps")).getFirst()));assertThrows(IllegalArgumentException.class,()->DevelopmentView.decode(value,town,false));}
    @Test void refusesOverstatedSupply(){var value=sample();var item=new HashMap<String,Object>((Map<String,Object>)((List<?>)value.get("supply")).getFirst());item.put("stored",129L);value.put("supply",List.of(item));assertThrows(IllegalArgumentException.class,()->DevelopmentView.decode(value,town,false));}
    @Test void rejectsOversizedCatalog(){var value=sample();value.put("catalog",Collections.nCopies(129,((List<?>)value.get("catalog")).getFirst()));assertThrows(IllegalArgumentException.class,()->DevelopmentView.decode(value,town,true));}
    @Test void catalogWorksWithoutASelectedGoal(){var value=sample();value.remove("steps");value.remove("supply");assertEquals("Ратуша",DevelopmentView.decode(value,town,true).catalog().getFirst().name());}
    @Test void suppliesAreInformationalAndActionsAreServerOwned(){var view=DevelopmentView.decode(sample(),town,false);var menu=new CivicView();view.populate(menu,"planner-supply","",Map.of("town_hall",1));var actions=new HashMap<String,CivicView.Action>();menu.json("token",actions,0,12);assertTrue(actions.isEmpty());assertTrue(menu.rows.getFirst().detail().contains("272"));}
    @Test void plannerDoesNotPublishOrCharge(){var view=DevelopmentView.decode(sample(),town,false);var menu=new CivicView();view.populate(menu,"planner","",Map.of("town_hall",1));assertTrue(menu.rows.stream().filter(r->r.action()!=null).noneMatch(r->r.action().kind().startsWith("review-")||r.action().kind().equals("command")));}
    @Test void failedPlanCanBeClearedWithoutChangingBuildings(){var menu=new CivicView();DevelopmentView.unavailable("Каталог изменён").populate(menu,"planner","",Map.of("town_hall",1));assertEquals("plan-clear",menu.rows.getLast().action().kind());}
    @Test void unknownStockIsNeverPresentedAsConfirmedZero(){var value=sample();value.put("stockKnown",false);value.put("known",false);var menu=new CivicView();DevelopmentView.decode(value,town,false).populate(menu,"planner-supply","",Map.of("town_hall",1));assertTrue(menu.rows.getFirst().detail().contains("склад: уточняется"));}
}
