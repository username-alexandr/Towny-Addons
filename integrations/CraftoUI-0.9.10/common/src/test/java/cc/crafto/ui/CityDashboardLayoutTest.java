package cc.crafto.ui;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CityDashboardLayoutTest {
    @Test void plannerStaysInDevelopmentAndManagementStartsOnSeparateRow(){
        for(int width:new int[]{240,320,440,500,596,720}){
            var l=CityDashboardLayout.fit(width,240,8,4,3,4);
            assertTrue(l.shortcut(3).bottom()<l.managementY());assertEquals(l.managementY(),l.shortcut(4).y());
            assertEquals(width>=500?4:2,l.shortcutColumns());
            for(int i=0;i<8;i++)for(int j=i+1;j<8;j++){var a=l.shortcut(i);var b=l.shortcut(j);assertFalse(a.x()<b.right()&&b.x()<a.right()&&a.y()<b.bottom()&&b.y()<a.bottom());}
        }
    }
    @Test void financialCardsNeverOverlapNavigationAtAnyGuiSize(){
        for(int w:new int[]{240,296,360,440,500,620,960})for(int h:new int[]{98,158,240,458})for(int n:new int[]{1,3,12}){
            var l=CityDashboardLayout.fit(w,h,7,n,3);var boxes=new ArrayList<CityDashboardLayout.Box>();
            for(int i=0;i<4;i++)boxes.add(l.metric(i));for(int i=0;i<3;i++)boxes.add(l.finance(i));
            for(int i=0;i<7;i++)boxes.add(l.shortcut(i));for(int i=0;i<3;i++)boxes.add(l.tab(i));for(int i=0;i<n;i++)boxes.add(l.card(i));
            for(var a:boxes){assertTrue(a.x()>=0&&a.right()<=w&&a.bottom()<=l.contentHeight());for(var b:boxes)if(a!=b)assertFalse(a.x()<b.right()&&b.x()<a.right()&&a.y()<b.bottom()&&b.y()<a.bottom());}
            for(int i=0;i<3;i++){var b=l.finance(i);int scroll=l.scrollToReveal(0,b);assertTrue(b.y()>=scroll&&b.bottom()<=scroll+h);}
            assertEquals(w>=440?3:1,l.financeColumns());assertEquals(3,l.financeCount());
        }
    }
    @Test void allContentFitsWidthAndHasNoOverlapsAcrossGuiSizes(){
        for(int w:new int[]{240,296,360,424,500,610,620,712,960})for(int h:new int[]{98,158,240,323,458,720})for(int n:new int[]{0,1,3,12,13}){
            var l=CityDashboardLayout.fit(w,h,8,n);var boxes=new ArrayList<CityDashboardLayout.Box>();
            for(int i=0;i<4;i++)boxes.add(l.metric(i));for(int i=0;i<8;i++)boxes.add(l.shortcut(i));for(int i=0;i<3;i++)boxes.add(l.tab(i));for(int i=0;i<Math.max(1,n);i++)boxes.add(l.card(i));
            for(var a:boxes){assertTrue(a.x()>=0&&a.right()<=w&&a.width()>0);assertTrue(a.y()>=0&&a.bottom()<=l.contentHeight());
                for(var b:boxes)if(a!=b)assertFalse(a.x()<b.right()&&b.x()<a.right()&&a.y()<b.bottom()&&b.y()<a.bottom(),a+" overlaps "+b);}
            assertTrue(l.capacity()>0&&l.capacity()<=12);assertEquals(Math.max(0,l.contentHeight()-h),l.maxScroll());
        }
    }
    @Test void everyCardCanBeRevealedAtLargeGuiScale(){var l=CityDashboardLayout.fit(296,98,8,12);for(int i=0;i<12;i++){var b=l.card(i);int scroll=l.scrollToReveal(0,b);assertTrue(b.y()>=scroll&&b.bottom()<=scroll+l.viewport());}}
    @Test void focusScrollsBackToSummaryAndNeverPastEnd(){var l=CityDashboardLayout.fit(296,158,8,12);assertEquals(l.metricsY(),l.scrollToReveal(l.maxScroll(),l.metric(0)));assertTrue(l.scrollToReveal(0,l.card(11))<=l.maxScroll());}
    @Test void layoutReflowsInsteadOfShrinkingText(){assertEquals(1,CityDashboardLayout.fit(296,158,8,3).cardColumns());assertEquals(2,CityDashboardLayout.fit(500,323,8,3).cardColumns());assertEquals(3,CityDashboardLayout.fit(712,458,8,3).cardColumns());}
}
