package cc.crafto.ui.server;
import cc.crafto.ui.ContractHudState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PublicOrderReaderTest {
    @TempDir Path path;
    final UUID player=UUID.randomUUID(),id=UUID.randomUUID();
    @Test void compatibilityUsesSnapshotSchemaInsteadOfReleaseNumber()throws Exception{
        order(player,"HUNTING","ACTIVE",3,16);var file=path.resolve("public-orders.yml");
        Files.writeString(file,Files.readString(file)+"addon-version: 0.3.22\n");
        assertEquals(3,PublicOrderReader.read(path,Set.of(player)).get(player).getFirst().progress());
    }

    void order(UUID owner,String type,String state,int progress,int goal)throws Exception{
        Files.writeString(path.resolve("public-orders.yml"),"schema: 2\norders:\n  "+id+":\n    player: "+owner+"\n    title: Безопасность дорог\n    type: "+type+"\n    target: STONE\n    state: "+state+"\n    progress: "+progress+"\n    goal: "+goal+"\n");
    }
    void payment(String state,UUID owner)throws Exception{Files.createDirectories(path.resolve("order-payments"));Files.writeString(path.resolve("order-payments/"+id+".properties"),"schema=1\nid="+id+"\nplayer="+owner+"\nstate="+state+"\n");}
    @Test void onlyOwnActiveOrdersAreIncluded()throws Exception{order(UUID.randomUUID(),"HUNTING","ACTIVE",7,16);assertTrue(PublicOrderReader.read(path,Set.of(player)).isEmpty());order(player,"HUNTING","CANCELLED",7,16);assertTrue(PublicOrderReader.read(path,Set.of(player)).isEmpty());order(player,"HUNTING","ACTIVE",7,16);var row=PublicOrderReader.read(path,Set.of(player)).get(player).getFirst();assertEquals(7,row.progress());assertEquals(16,row.goal());}
    @Test void paidAndVoidedOrdersDisappear()throws Exception{order(player,"MINING","ACTIVE",16,16);for(String state:List.of("PAID","VOID")){payment(state,player);assertTrue(PublicOrderReader.read(path,Set.of(player)).isEmpty());}}
    @Test void reservedDeliveryDoesNotReadEmptyInventoryAsZero()throws Exception{order(player,"DELIVERY","ACTIVE",0,64);payment("HELD",player);var row=PublicOrderReader.read(path,Set.of(player)).get(player).getFirst();assertEquals(64,row.progress());assertEquals(ContractHudState.Status.HELD,row.status());}
    @Test void rewardInFlightShowsPending()throws Exception{order(player,"HUNTING","ACTIVE",16,16);payment("SENDING",player);assertEquals(ContractHudState.Status.PENDING,PublicOrderReader.read(path,Set.of(player)).get(player).getFirst().status());}
    @Test void refusesMismatchedPaymentIdentity()throws Exception{order(player,"HUNTING","ACTIVE",16,16);payment("PAID",UUID.randomUUID());assertThrows(Exception.class,()->PublicOrderReader.read(path,Set.of(player)));}
    @Test void invalidSnapshotDoesNotProduceInventedProgress()throws Exception{order(player,"FISHING","ACTIVE",-1,16);assertThrows(Exception.class,()->PublicOrderReader.read(path,Set.of(player)));Files.writeString(path.resolve("public-orders.yml"),"schema: 99\n");assertThrows(Exception.class,()->PublicOrderReader.read(path,Set.of(player)));}
    @Test void retainsConcreteTargetForEverySupportedActivity()throws Exception{
        var targets=Map.of("DELIVERY","HONEY_BOTTLE","MINING","DEEPSLATE_LAPIS_ORE","HUNTING","DROWNED","FISHING","SALMON");
        for(var target:targets.entrySet()){
            order(player,target.getKey(),"ACTIVE",0,16);var file=path.resolve("public-orders.yml");Files.writeString(file,Files.readString(file).replace("target: STONE","target: "+target.getValue()));
            var row=PublicOrderReader.read(path,Set.of(player)).get(player).getFirst();assertEquals(target.getKey(),row.type());assertEquals(target.getValue(),row.target());
            assertEquals(target.getKey().equals("DELIVERY")?target.getValue():"",row.deliveryMaterial());
        }
    }
}
