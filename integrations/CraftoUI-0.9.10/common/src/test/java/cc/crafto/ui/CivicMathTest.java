package cc.crafto.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class CivicMathTest {
    @Test void stockIsSubtractedOnceFromCombinedDemand(){var n=CivicMath.need("STONE",64+96,64,32);assertEquals(64,n.gather());assertEquals(0,CivicMath.need("STONE",10,64,0).gather());}
    @Test void oversizedStockDoesNotOverflow(){assertEquals(0,CivicMath.need("STONE",Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE).gather());assertThrows(IllegalArgumentException.class,()->CivicMath.need("STONE",-1,0,0));}
    @Test void inventoryReadinessAppliesOnlyToDelivery(){var offers=List.of(new CivicMath.Offer("mine","Добыча","MINING","RARE",10000,16,64),new CivicMath.Offer("ship","Поставка","DELIVERY","COMMON",100,16,16));assertEquals(List.of("ship"),CivicMath.filter(offers,"ALL","ALL",0,"reward",true).stream().map(CivicMath.Offer::id).toList());}
    @Test void combinesFiltersAndStableOrdering(){var offers=List.of(new CivicMath.Offer("b","Б","DELIVERY","RARE",1000,1,0),new CivicMath.Offer("a","А","DELIVERY","RARE",1000,1,0),new CivicMath.Offer("c","В","MINING","COMMON",5000,1,0));assertEquals(List.of("a","b"),CivicMath.filter(offers,"DELIVERY","RARE",500,"reward",false).stream().map(CivicMath.Offer::id).toList());assertTrue(CivicMath.filter(offers,"DELIVERY","RARE",1001,"reward",false).isEmpty());}
    @Test void returnedConstructionMaterialIsReusable(){assertEquals(0,CivicMath.placement("STONE",160,0,1,true).gather());assertEquals(1,CivicMath.placement("STONE",160,0,0,true).gather());assertEquals(159,CivicMath.placement("STONE",160,0,1,false).gather());}
    @Test void configurablePlotSizeHandlesNegativeCoordinates(){assertEquals(-1,CivicMath.cell(-1,32));assertEquals(-2,CivicMath.cell(-33,32));assertEquals(1,CivicMath.cell(32,32));assertThrows(IllegalArgumentException.class,()->CivicMath.cell(0,0));}
}
