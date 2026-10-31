package cc.crafto.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AddonLayoutTest {
    @Test void panelsStayInsideViewportsAndDoNotOverlap(){
        int[][] sizes={{320,240},{427,300},{640,360},{640,450},{400,700},{1920,1080}};
        for(int[] v:sizes)for(float scale:new float[]{.75f,1f,1.25f})for(boolean market:new boolean[]{false,true})for(boolean detail:new boolean[]{false,true}){
            var l=AddonLayout.fit(v[0],v[1],620,420,scale,3,market,detail);
            assertTrue(l.width()*l.scale()<=v[0]-15);assertTrue(l.height()*l.scale()<=v[1]-15);
            assertTrue(l.gridW()/l.columns()>=100);assertTrue(l.pageSize()>0);
            assertTrue(l.gridY()+(l.rows()-1)*AddonLayout.STRIDE+AddonLayout.CARD_HEIGHT<=l.height()-44);
            if(l.detailW()>0){assertTrue(l.gridX()+l.gridW()<l.detailX());assertTrue(l.detailX()+l.detailW()<=l.width()-16);assertTrue(l.detailY()+l.detailH()<=l.height()-44);}
            else assertEquals(l.width()-32,l.gridW());
            assertTrue(l.width()*l.scale()<=640&&l.height()*l.scale()<=400);
        }
    }
    @Test void narrowerViewportReflowsCards(){var wide=AddonLayout.fit(800,600,620,420,1,3,false);var small=AddonLayout.fit(320,240,620,420,1,3,false);assertTrue(small.columns()<wide.columns());assertTrue(small.pageSize()>0);}
    @Test void adminFooterKeepsNativeCardsAboveItsDivider(){
        for(int[] size:new int[][]{{320,240},{427,300},{640,450},{1920,1080}}){
            var base=AddonLayout.fit(size[0],size[1],620,420,1,3,false);
            for(int footerRows=1;footerRows<=2;footerRows++){
                var layout=base.reserveAdminFooter(footerRows);
                int firstY=layout.height()-49-footerRows*56;
                int lastCard=layout.gridY()+(layout.rows()-1)*AddonLayout.STRIDE+AddonLayout.CARD_HEIGHT;
                assertTrue(lastCard<=firstY-8);
                assertTrue(firstY+(footerRows-1)*56+50<=layout.height()-49);
                assertTrue(layout.width()*layout.scale()<=size[0]-15);
                assertTrue(layout.height()*layout.scale()<=size[1]-15);
            }
        }
    }
}
