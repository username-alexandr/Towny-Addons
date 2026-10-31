package cc.crafto.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CivicPageLayoutTest {
    @Test void desktopDialogLeavesWorldVisibleAndRemainsCentered(){
        var l=CivicPageLayout.fit(829,563);
        assertTrue(l.width()<=640&&l.height()<=400);
        assertTrue(l.x()>=80&&l.y()>=60);
        assertTrue(Math.abs(829-l.width()-2*l.x())<=1);
        assertTrue(Math.abs(563-l.height()-2*l.y())<=1);
    }
    @Test void compactHeaderUsesActualDialogHeight(){
        var l=CivicPageLayout.fit(520,340);
        assertTrue(l.height()<300&&l.compact());
        assertTrue(l.viewport()>=120);
    }
    @Test void navigationAndFooterNeverOverlapBodyAtSupportedGuiSizes(){
        for(int[] size:new int[][]{{320,180},{320,240},{436,223},{520,340},{871,447},{960,500},{1920,1080}}){
            var l=CivicPageLayout.fit(size[0],size[1]);
            assertTrue(l.x()>=0&&l.y()>=0);
            assertTrue(l.x()+l.width()<=size[0]&&l.y()+l.height()<=size[1]);
            assertTrue(l.bodyTop()>l.navY()+l.navHeight());
            assertTrue(l.bottom()>l.bodyTop());
            assertTrue(l.footerY()>l.bottom()+16&&l.footerY()+22<=size[1]);
            assertTrue(l.footerY()>=l.bottom()+26&&l.footerY()+22<=l.y()+l.height()-4);
            for(int i=0;i<5;i++){var t=l.tab(i,5);assertTrue(t.x()>=l.left()&&t.right()<=l.left()+l.span());if(i>0)assertTrue(l.tab(i-1,5).right()<t.x());}
        }
    }
    @Test void lastCardRemainsReachableAfterResize(){
        for(int[] size:new int[][]{{320,180},{520,340},{960,500}}){
            var l=CivicPageLayout.fit(size[0],size[1]);var last=l.card(11,12,72);int content=last.bottom();
            int scroll=l.scrollToReveal(0,content,last.y(),last.bottom());
            assertTrue(last.bottom()-scroll<=l.viewport());
            assertEquals(0,l.scrollToReveal(scroll,content,0,32));
        }
    }
    @Test void cardsDoNotCrossColumnsAndServerLimitIsRespected(){
        for(int width=320;width<=1600;width+=40){
            var l=CivicPageLayout.fit(width,460);assertTrue(l.capacity()>=2&&l.capacity()<=12);
            for(int i=0;i<12;i++){
                var b=l.card(i,12,0);assertTrue(b.x()>=l.left()&&b.right()<=l.left()+l.span()-8);assertTrue(b.height()>=60&&b.height()<=76);
                if(l.columns()==2&&i%2==0)assertTrue(b.right()<l.card(i+1,12,0).x());
            }
        }
    }
}
