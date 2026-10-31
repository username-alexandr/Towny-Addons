package cc.crafto.ui.server;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CityProgressionTest {
    @Test void distantBranchesRemainHiddenUntilARequiredBuildingAdvances(){
        var deep=new CityProgression.Node("foundry","Литейная","building","Производство","",0,5,Map.of("forge",3),Map.of());
        assertFalse(CityProgression.revealed(deep,Map.of(),Map.of()));
        assertFalse(CityProgression.revealed(deep,Map.of("forge",1),Map.of()));
        assertFalse(CityProgression.ready(deep,Map.of("forge",2),Map.of()));
        assertTrue(CityProgression.ready(deep,Map.of("forge",3),Map.of()));
        assertTrue(CityProgression.revealed(deep,Map.of("forge",3),Map.of()));
        assertTrue(CityProgression.revealed(new CityProgression.Node("foundry","Литейная","building","Производство","",1,5,Map.of("forge",3),Map.of()),Map.of(),Map.of()));
    }
    @Test void researchNeedsItsBuildingsAndPreviousStudy(){
        var next=new CityProgression.Node("walls","Стены","research","Исследования","",0,3,Map.of("great_library",3),Map.of("metallurgy",1));
        assertFalse(CityProgression.revealed(next,Map.of(),Map.of()));
        assertFalse(CityProgression.revealed(next,Map.of("great_library",1),Map.of()));
        assertFalse(CityProgression.ready(next,Map.of("great_library",3),Map.of()));
        assertTrue(CityProgression.ready(next,Map.of("great_library",3),Map.of("metallurgy",1)));
        assertTrue(CityProgression.revealed(next,Map.of("great_library",3),Map.of("metallurgy",1)));
    }
    @Test void onlyImmediateLockedBuildingBranchesArePreviewed(){
        var hall=new CityProgression.Node("town_hall","Ратуша","building","Город","",0,5,Map.of(),Map.of());
        var workshop=new CityProgression.Node("workshop","Мастерская","building","Город","",0,5,Map.of("town_hall",1),Map.of());
        var foundry=new CityProgression.Node("foundry","Литейная","building","Город","",0,5,Map.of("workshop",2),Map.of());
        var nodes=List.of(hall,workshop,foundry);
        assertTrue(CityProgression.frontier(workshop,nodes,Map.of(),Map.of()));
        assertFalse(CityProgression.frontier(foundry,nodes,Map.of(),Map.of()));
        var view=new CivicView();new CityProgression.Snapshot(nodes,List.of(),"").populate(view,Map.of());
        assertEquals(2,view.tree.size());
        assertEquals("building:workshop",view.tree.get(1).getAsJsonObject().get("id").getAsString());
        assertTrue(CityProgression.frontier(foundry,nodes,Map.of("workshop",1),Map.of()));
    }
    @Test void unavailableResearchDoesNotHideBuildingBranches(){
        var hall=new CityProgression.Node("town_hall","Ратуша","building","Город","",0,5,Map.of(),Map.of());
        var view=new CivicView();new CityProgression.Snapshot(List.of(hall),List.of(),"","Исследования временно недоступны").populate(view,Map.of());
        assertEquals(1,view.tree.size());
        assertTrue(view.lines.stream().anyMatch(s->s.contains("Исследования временно недоступны")));
    }
    @Test void treeActionKeysPointOnlyAtRealServerOwnedNodes(){
        var node=new CityProgression.Node("forge","Кузница","building","Базовые","",0,5,Map.of(),Map.of());
        var view=new CivicView();new CityProgression.Snapshot(List.of(node),List.of(),"").populate(view,Map.of());
        var actions=new HashMap<String,CivicView.Action>();var json=view.json("token",actions,0,12);
        assertEquals(1,json.getAsJsonArray("tree").size());assertEquals("build",actions.get("t0").kind());assertEquals("forge",actions.get("t0").id());
    }
}
