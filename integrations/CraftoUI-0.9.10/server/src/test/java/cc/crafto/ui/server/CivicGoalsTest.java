package cc.crafto.ui.server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CivicGoalsTest {
    @TempDir Path root;
    @SuppressWarnings("unchecked") private static <T> T stub(Class<T> type,Map<String,Object> methods){return (T)java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(proxy,method,args)->methods.get(method.getName()));}
    @Test void contributionTracksSurvivingBlocksAndDoesNotCarryAcrossPins()throws Exception{
        var store=new CivicGoals(root.resolve("blocks.json"));store.load();UUID town=UUID.randomUUID(),player=UUID.randomUUID(),world=UUID.randomUUID();
        var material=org.bukkit.Material.STONE;String pos=world+":1:64:2";
        var w=stub(org.bukkit.World.class,Map.of("getUID",world));var block=stub(org.bukkit.block.Block.class,Map.of("getWorld",w,"getX",1,"getY",64,"getZ",2,"getType",material));
        var actor=stub(org.bukkit.entity.Player.class,Map.of("getUniqueId",player));
        var site=new CityBuildReader.SiteInfo("warehouse","Склад",1,1,true,Map.of(),Map.of(pos,material),Set.of(pos));
        var result=new CityBuildReader.Result(List.of(),List.of(),Map.of(),Map.of(),true,List.of(site));
        store.pin(town,new CivicGoals.Goal("build","warehouse",1));store.plan(town,result);assertEquals(0,store.contribution(town,player,site));
        var placed=new org.bukkit.event.block.BlockPlaceEvent(block,null,null,null,actor,true,org.bukkit.inventory.EquipmentSlot.HAND);
        store.placed(placed);store.placed(placed);assertEquals(1,store.contribution(town,player,site));
        store.broken(new org.bukkit.event.block.BlockBreakEvent(block,actor));assertEquals(0,store.contribution(town,player,site));
        store.placed(placed);store.pin(town,new CivicGoals.Goal("build","warehouse",2));store.plan(town,result);assertEquals(0,store.contribution(town,player,site));
    }
    @Test void goalsAndRoleBaselineSurviveRestart()throws Exception{
        UUID town=UUID.randomUUID(),player=UUID.randomUUID();Path file=root.resolve("civic.json");var store=new CivicGoals(file);store.load();var goal=new CivicGoals.Goal("contract",UUID.randomUUID().toString(),123L);store.pin(town,goal);
        assertTrue(store.roleUpdates(player,"Житель").isEmpty());store.flush();var restored=new CivicGoals(file);restored.load();assertEquals(goal,restored.get(town));assertEquals(1,restored.roleUpdates(player,"Казначей").size());assertTrue(restored.roleUpdates(player,"Казначей").isEmpty());restored.pin(town,null);var cleared=new CivicGoals(file);cleared.load();assertNull(cleared.get(town));
    }
    @Test void stalePinCannotOverwriteAnotherMayorAction()throws Exception{var store=new CivicGoals(root.resolve("race.json"));store.load();var town=UUID.randomUUID();var first=new CivicGoals.Goal("build","warehouse",1);store.pinIfUnchanged(town,"null",first);assertThrows(IllegalArgumentException.class,()->store.pinIfUnchanged(town,"null",new CivicGoals.Goal("build","school",2)));assertEquals(first,store.get(town));}
    @Test void corruptStoreCannotBeOverwritten()throws Exception{Path file=root.resolve("civic.json");Files.writeString(file,"{broken");var store=new CivicGoals(file);assertThrows(Exception.class,store::load);assertFalse(store.ready());assertThrows(Exception.class,()->store.pin(UUID.randomUUID(),null));assertEquals("{broken",Files.readString(file));}
}
