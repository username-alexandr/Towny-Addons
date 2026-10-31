package cc.crafto.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommandCatalogLayoutTest {
    @Test void searchCardsAndPagerHaveSeparateBandsAtDifferentGuiScales(){
        for(int[] size:new int[][]{{320,180},{320,240},{436,223},{520,340},{871,447},{960,500},{1920,1080}}){
            var page=CivicPageLayout.fit(size[0],size[1]);
            assertTrue(CommandCatalogLayout.CARD_Y>=CommandCatalogLayout.SEARCH_Y+CommandCatalogLayout.SEARCH_HEIGHT+8);
            assertTrue(page.bottom()+2+CommandCatalogLayout.PAGER_HEIGHT+5<=page.bottom()+26);
            assertTrue(page.footerY()>=page.bottom()+2+CommandCatalogLayout.PAGER_HEIGHT+6);
            int rows=CommandCatalogLayout.rows(page.viewport());
            if(page.viewport()>=CommandCatalogLayout.CARD_Y+CommandCatalogLayout.CARD_HEIGHT){
                assertTrue(CommandCatalogLayout.CARD_Y+(rows-1)*CommandCatalogLayout.ROW_STRIDE+CommandCatalogLayout.CARD_HEIGHT<=page.viewport());
            }
            assertTrue(CommandCatalogLayout.capacity(page.viewport(),3)<=24);
        }
    }
}
