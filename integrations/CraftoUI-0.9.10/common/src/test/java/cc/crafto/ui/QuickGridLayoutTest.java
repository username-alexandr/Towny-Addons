package cc.crafto.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuickGridLayoutTest {
    @Test void reflowsBetweenOneAndFourColumns() {
        assertEquals(4,QuickGridLayout.fit(720,500).columns());
        assertEquals(3,QuickGridLayout.fit(624,308).columns());
        assertEquals(2,QuickGridLayout.fit(411,158).columns());
        assertEquals(1,QuickGridLayout.fit(304,98).columns());
    }
    @Test void buttonsAndFavoritesStayInsideViewportAtEveryBreakpoint() {
        for(int width=288;width<=720;width++)for(int height:new int[]{20,47,98,158,308,500}) {
            var g=QuickGridLayout.fit(width,height);
            assertTrue(g.buttonWidth()>=120);
            assertTrue(g.columns()*g.cellWidth()+(g.columns()-1)*QuickGridLayout.GAP<=width);
            assertTrue((g.rows()-1)*QuickGridLayout.ROW_STRIDE+20<=height);
            assertEquals(g.rows()*g.columns(),g.capacity());
        }
    }
    @Test void screenshotSizeFitsAllFortyNineEntriesWithoutSecondPage() {
        assertTrue(QuickGridLayout.fit(720,500).capacity()>=49);
    }
}
