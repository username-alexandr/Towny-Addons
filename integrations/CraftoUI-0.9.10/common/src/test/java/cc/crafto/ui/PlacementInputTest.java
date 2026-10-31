package cc.crafto.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlacementInputTest {
    private static final String KEY="01234567-0123-0123-0123-0123456789ab";
    @Test void onlyHeldSlotAndFreshStateAreActive(){var input=new PlacementInput();input.update(KEY,2,0,0);assertTrue(input.active(2,2_999_999_999L));assertFalse(input.active(1,0));assertFalse(input.active(2,3_000_000_000L));}
    @Test void queueIsBoundedAndMovementIsPaced(){var input=new PlacementInput();input.update(KEY,0,0,0);for(int i=0;i<100;i++)input.enqueue("up");assertEquals(1,input.next(0).sequence());assertNull(input.next(249_999_999));assertEquals(2,input.next(250_000_000).sequence());assertNotNull(input.next(500_000_000));assertNotNull(input.next(750_000_000));assertNull(input.next(1_000_000_000));}
    @Test void wheelSignControlsHeightWithoutWraparound(){var input=new PlacementInput();input.update(KEY,8,0,0);input.wheel(1);input.wheel(-1);assertEquals("up",input.next(0).action());assertEquals("down",input.next(250_000_000).action());}
    @Test void wheelRejectsNonFiniteInputAndZero(){var input=new PlacementInput();input.update(KEY,0,0,0);input.wheel(Double.NaN);input.wheel(Double.POSITIVE_INFINITY);input.wheel(0);assertNull(input.next(0));}
    @Test void cannotQueuePaymentsOrArbitraryCoordinates(){var input=new PlacementInput();input.update(KEY,0,0,0);for(String s:new String[]{"confirm","rotate","1000,0,0","/op x"})input.enqueue(s);assertNull(input.next(0));}
    @Test void changingSelectionDropsPendingInput(){var input=new PlacementInput();input.update(KEY,0,0,0);input.enqueue("up");input.update("abcdefab-0123-0123-0123-0123456789ab",0,0,1);assertNull(input.next(1));input.enqueue("down");assertEquals(1,input.next(1).sequence());}
    @Test void changingHeldSlotDropsPendingInput(){var input=new PlacementInput();input.update(KEY,0,0,0);input.enqueue("up");input.update(KEY,1,0,1);assertNull(input.next(1));}
    @Test void openingScreenDiscardsQueueButKeepsSequence(){var input=new PlacementInput();input.update(KEY,0,0,0);input.enqueue("up");assertEquals(1,input.next(0).sequence());input.enqueue("up");input.discard();assertNull(input.next(250_000_000));input.enqueue("left");assertEquals(2,input.next(250_000_000).sequence());}
    @Test void expiryDropsQueuedMotion(){var input=new PlacementInput();input.update(KEY,0,0,0);input.enqueue("up");assertNull(input.next(3_000_000_000L));assertFalse(input.active(0,3_000_000_001L));}
    @Test void returningToSameBlueprintResumesAcknowledgedSequence(){var input=new PlacementInput();input.update(KEY,0,9,0);input.clear();input.update(KEY,0,9,1);input.enqueue("up");assertEquals(10,input.next(1).sequence());input.update(KEY,0,8,2);input.enqueue("down");assertEquals(11,input.next(250_000_001).sequence());}
    @Test void invalidStateDisablesInput(){var input=new PlacementInput();input.update(KEY,0,0,0);input.enqueue("up");input.update(KEY,9,0,1);assertNull(input.next(1));input.update("forged",0,0,2);assertFalse(input.active(0,2));}
}
