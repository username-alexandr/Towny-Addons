package cc.crafto.ui;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OrderPresentationTest {
    @Test void itemProgressionMatchesTheContractsBoard(){
        assertEquals(List.of("minecraft:wooden_sword","minecraft:stone_sword","minecraft:iron_sword","minecraft:diamond_sword","minecraft:netherite_sword"),Arrays.stream(OrderRarityStyle.values()).map(r->r.icon("HUNTING")).toList());
        assertEquals(List.of("minecraft:wooden_pickaxe","minecraft:stone_pickaxe","minecraft:iron_pickaxe","minecraft:diamond_pickaxe","minecraft:netherite_pickaxe"),Arrays.stream(OrderRarityStyle.values()).map(r->r.icon("MINING")).toList());
        assertEquals(List.of("minecraft:white_shulker_box","minecraft:lime_shulker_box","minecraft:light_blue_shulker_box","minecraft:purple_shulker_box","minecraft:orange_shulker_box"),Arrays.stream(OrderRarityStyle.values()).map(r->r.icon("DELIVERY")).toList());
        assertEquals(List.of("minecraft:cod","minecraft:salmon","minecraft:pufferfish","minecraft:tropical_fish","minecraft:heart_of_the_sea"),Arrays.stream(OrderRarityStyle.values()).map(r->r.icon("FISHING")).toList());
        assertFalse(OrderRarityStyle.RARE.glint());assertTrue(OrderRarityStyle.EPIC.glint());assertTrue(OrderRarityStyle.LEGENDARY.glint());assertEquals("",OrderRarityStyle.RARE.icon("UNKNOWN"));
    }
    @Test void rarityUsesExplicitServerLoreAndDoesNotGuessFromTitle(){
        assertEquals(OrderRarityStyle.EPIC,OrderRarityStyle.lore("§7Редкость: §dЭпический"));
        assertEquals(OrderRarityStyle.RARE,OrderRarityStyle.lore("Редкость: Редкий"));
        assertEquals(OrderRarityStyle.LEGENDARY,OrderRarityStyle.lore("Rarity: LEGENDARY"));
        assertNull(OrderRarityStyle.lore("Легендарный улов"));assertNull(OrderRarityStyle.parse("NEW_SERVER_RARITY"));
    }
    @Test void rareFirstOrderIsIndependentOfPriceAndNeverCreatesMissingOffers(){
        var offers=List.of(new CivicMath.Offer("common","А","MINING","COMMON",10000,1,0),new CivicMath.Offer("rare","Б","HUNTING","RARE",100,1,0),new CivicMath.Offer("epic","В","FISHING","EPIC",50,1,0));
        assertEquals(List.of("epic","rare","common"),CivicMath.filter(offers,"ALL","ALL",0,"rarity",false).stream().map(CivicMath.Offer::id).toList());
        assertEquals(List.of("common","rare","epic"),CivicMath.filter(offers,"ALL","ALL",0,"reward",false).stream().map(CivicMath.Offer::id).toList());
        assertTrue(CivicMath.filter(offers,"ALL","LEGENDARY",0,"rarity",false).isEmpty());
    }
    @Test void cardsStayInsideAvailableWidthAndHeight(){
        for(int width:List.of(240,304,320,469,470,720))for(int height:List.of(44,55,69,70,150,230,480)){
            var g=OrderGridLayout.fit(width,height);assertTrue(g.columns()*g.cellWidth()+(g.columns()-1)*OrderGridLayout.GAP<=width);
            int rows=(g.capacity()+g.columns()-1)/g.columns();assertTrue(rows*g.stride()-OrderGridLayout.GAP<=height);
            assertTrue(g.capacity()>=1&&g.capacity()<=12);
        }
    }
}
