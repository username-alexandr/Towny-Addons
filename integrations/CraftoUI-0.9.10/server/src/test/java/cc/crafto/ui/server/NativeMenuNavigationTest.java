package cc.crafto.ui.server;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeMenuNavigationTest {
    NativeMenuNavigation.Entry item(int slot,String action){return new NativeMenuNavigation.Entry(slot,action,false,false,"");}
    @Test void categoryReturnIsTheSameAuditedNativeBack(){assertTrue(NativeBack.backAction("categories-back"));assertTrue(NativeBack.backAction("back"));assertFalse(NativeBack.backAction("projects-previous"));assertFalse(NativeBack.backAction("buy"));}
    @Test void onlyAuditedActionTagsMergeWhileOriginalSlotSurvives(){var g=NativeMenuNavigation.group("NeverLandTownyBuilds","x$ProjectListHolder",List.of(item(4,"research"),item(8,"research"),item(12,"power"),item(16,"categories-back")));assertEquals(List.of(4,12),g.auxiliary());assertEquals(List.of(8),g.hidden());}
    @Test void identicalLookingBuildingsWithDifferentProjectsAreNeverRemoved(){var g=NativeMenuNavigation.group("NeverLandTownyBuilds","x$ProjectListHolder",List.of(new NativeMenuNavigation.Entry(0,"research",true,false,""),new NativeMenuNavigation.Entry(1,"research",true,false,""),item(2,"select"),item(3,"select")));assertTrue(g.auxiliary().isEmpty());assertTrue(g.hidden().isEmpty());}
    @Test void storageAndUnknownOwnersAreNotInterpretedAsNavigation(){for(String holder:List.of("x$StorageHolder","x$DetailsHolder")){var g=NativeMenuNavigation.group("NeverLandTownyBuilds",holder,List.of(item(0,"research"),item(1,"research")));assertTrue(g.hidden().isEmpty());assertTrue(g.auxiliary().isEmpty());}assertTrue(NativeMenuNavigation.group("OtherPlugin","x$ProjectListHolder",List.of(item(0,"research"),item(1,"research"))).hidden().isEmpty());}
    @Test void availabilityDifferencesAndTransactionActionsAreRetained(){var g=NativeMenuNavigation.group("NeverLandTownyBuilds","x$ProjectListHolder",List.of(item(0,"research"),new NativeMenuNavigation.Entry(1,"research",false,false,"Нужна ратуша"),item(2,"contribute"),item(3,"contribute")));assertTrue(g.hidden().isEmpty());assertEquals(List.of(0,1),g.auxiliary());}
    @Test void warehouseIsOnlyMovedFromItsAuditedSlot(){var g=NativeMenuNavigation.group("NeverLandTownyBuilds","x$ProjectListHolder",List.of(new NativeMenuNavigation.Entry(49,null,false,true,""),new NativeMenuNavigation.Entry(10,null,false,true,"")));assertEquals(List.of(49),g.auxiliary());}
    @Test void orderToolbarIsRestrictedToTheActualPublicBoard(){assertTrue(NativeMenuNavigation.orderBoard("NeverLandTownyContracts","ru.neverland.mintcontracts.orders.PublicOrders$Board"));assertFalse(NativeMenuNavigation.orderBoard("Other","ru.neverland.mintcontracts.orders.PublicOrders$Board"));assertFalse(NativeMenuNavigation.orderBoard("NeverLandTownyContracts","ru.neverland.mintcontracts.gui.ContractMenuHolder"));}
    @Test void buildingActionsMoveOnlyWhenTheAddonTagsTheExpectedSlots(){
        String holder="ru.neverland.townybuilds.gui.MenuManager$DetailsHolder";
        var items=List.of(item(17,"build-orders"),item(24,"refresh"),item(15,"upgrade"));
        assertEquals(Map.of("order",17,"refresh",24),NativeMenuNavigation.detailsToolbar("NeverLandTownyBuilds",holder,items));
        assertEquals(Map.of("refresh",24),NativeMenuNavigation.detailsToolbar("NeverLandTownyBuilds",holder,List.of(item(17,"upgrade"),item(24,"refresh"))));
        assertTrue(NativeMenuNavigation.detailsToolbar("Other",holder,items).isEmpty());
        assertTrue(NativeMenuNavigation.detailsToolbar("NeverLandTownyBuilds","OtherView",items).isEmpty());
    }
    @Test void legacyPreviewFallbackNeedsTheRealProjectSummary(){var summary=List.of(new NativeMenuNavigation.Entry(11,null,true,false,""));assertEquals(11,NativeMenuNavigation.previewSlot("NeverLandTownyBuilds","ru.neverland.townybuilds.gui.MenuManager$DetailsHolder",-1,summary));assertEquals(-1,NativeMenuNavigation.previewSlot("Other","ru.neverland.townybuilds.gui.MenuManager$DetailsHolder",-1,summary));assertEquals(-1,NativeMenuNavigation.previewSlot("NeverLandTownyBuilds","ru.neverland.townybuilds.gui.MenuManager$DetailsHolder",-2,summary));assertEquals(-1,NativeMenuNavigation.previewSlot("NeverLandTownyBuilds","ru.neverland.townybuilds.gui.MenuManager$DetailsHolder",-1,List.of(item(11,"upgrade"))));}
}
