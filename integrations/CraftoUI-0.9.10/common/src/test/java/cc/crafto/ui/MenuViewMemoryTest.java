package cc.crafto.ui;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MenuViewMemoryTest {
    @Test void restoresPageAndScrollWithoutKeepingMoreThanOneSession(){
        var memory=new MenuViewMemory();memory.put("builds/list",3,12,24,Map.of("list",48d));
        assertEquals(3,memory.get("builds/list").page());assertEquals(24,memory.get("builds/list").scroll());
        assertEquals(0,memory.get("orders/list").page());memory.clear();assertEquals(0,memory.get("builds/list").page());
    }
    @Test void memoryIsBoundedAndRevisitingRetainsRecentPosition(){
        var memory=new MenuViewMemory();for(int i=0;i<64;i++)memory.put("menu"+i,i,-1,0,Map.of());
        memory.put("menu0",8,-1,0,Map.of());memory.put("menu64",1,-1,0,Map.of());
        assertEquals(8,memory.get("menu0").page());assertEquals(0,memory.get("menu1").page());
    }
    @Test void actionBarDoesNotOverlapCardsOrDetailsAtSmallAndLargeSizes(){
        for(int width:new int[]{360,480,620,900})for(int height:new int[]{310,420,650})for(boolean market:new boolean[]{false,true}){
            var layout=AddonLayout.fit(width+16,height+16,width,height,1,3,market).reserveActionBar();
            assertTrue(layout.detailY()+layout.detailH()<=layout.height()-75);
            assertTrue(layout.gridY()+(layout.rows()-1)*AddonLayout.STRIDE+AddonLayout.CARD_HEIGHT<=layout.height()-75);
        }
    }
}
