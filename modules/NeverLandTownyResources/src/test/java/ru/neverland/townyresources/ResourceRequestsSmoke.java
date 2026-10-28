package ru.neverland.townyresources;

import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyresources.data.ResourcesRepository;
import ru.neverland.townyresources.model.*;

/** Real atomic storage and independent conservation checks, including old upkeep receipts. */
public final class ResourceRequestsSmoke {
    static int checks;static void check(boolean value,String text){checks++;if(!value)throw new AssertionError(text);}
    interface Work{void run()throws Exception;}
    static void reject(Work work,String text)throws Exception{try{work.run();}catch(Exception expected){checks++;return;}throw new AssertionError(text);}
    static Map<Resource,Long> stock(long wood,long food){return Amounts.copy(Map.of(Resource.WOOD,wood,Resource.FOOD,food));}
    public static void main(String[] args)throws Exception{
        UUID town=UUID.randomUUID(),creator=UUID.randomUUID(),funder=UUID.randomUUID();Path root=Files.createTempDirectory("target-resources-");Path file=root.resolve("resources.yml");
        var repo=new ResourcesRepository(file);repo.load();repo.replace(Map.of(town,TownState.initial(stock(100000,100000))));
        UUID first=ResourceRequests.id(town,"aqueduct"),second=ResourceRequests.id(town,"baths");
        check(first.equals(ResourceRequests.id(town,"aqueduct"))&&!first.equals(ResourceRequests.id(UUID.randomUUID(),"aqueduct")),"stable plan identity is scoped to city UUID");
        reject(()->ResourceRequests.id(town,"../broken"),"unsafe labels rejected");reject(()->ResourceRequests.parse("wood=2,wood=3"),"duplicate costs rejected");reject(()->ResourceRequests.parse("wood=0"),"empty request rejected");check(ResourceRequests.parse("wood=1.125,food=2").get(Resource.WOOD)==1125,"fixed precision input retained");
        repo.request(first,town,stock(70000,0),"aqueduct",creator,100);repo.request(second,town,stock(70000,0),"baths",creator,200);
        check(repo.states().get(town).balances().get(Resource.WOOD)==100000&&repo.held(town).get(Resource.WOOD)==0,"unfunded requests neither debit nor create protected escrow");
        var minimum=repo.states().get(town).settings(stock(10000,0),Set.of(),Map.of());
        check(ResourceRequests.deficits(town,repo.reservations().values(),minimum,Map.of()).get(Resource.WOOD)==50000,"total need subtracts free stock once and protects minimum");
        check(!repo.fund(first,stock(40000,0),funder)&&repo.reservations().get(first).status()==Reservation.Status.REQUESTED,"shortage keeps request and does not partially reserve");
        check(repo.states().get(town).balances().get(Resource.WOOD)==100000,"failed funding conserves stock exactly");
        check(repo.fund(first,stock(10000,0),funder),"fully funded plan succeeds");
        check(repo.states().get(town).balances().get(Resource.WOOD)+repo.held(town).get(Resource.WOOD)==100000,"balance plus funded escrow equals original stock");
        var restarted=new ResourcesRepository(file);restarted.load();check(restarted.reservations().get(first).changedBy().equals(funder.toString()),"actual funding administrator survives restart");
        check(restarted.fund(first,Map.of(),creator)&&restarted.states().get(town).balances().get(Resource.WOOD)==30000&&restarted.reservations().get(first).changedBy().equals(funder.toString()),"retry after restart cannot debit twice or replace operator evidence");
        reject(()->restarted.request(first,town,stock(71000,0),"aqueduct",creator,300),"same identifier cannot change its cost");
        reject(()->restarted.reserve(first,town,stock(70000,0),Map.of()),"legacy upkeep API cannot adopt purpose plan ID");
        reject(()->restarted.settle(second,true,creator),"unfunded resources cannot be consumed");
        restarted.settle(second,false,creator);restarted.settle(second,false,funder);check(restarted.states().get(town).balances().get(Resource.WOOD)==30000,"canceling unfunded plan cannot mint a refund");
        check(restarted.reservations().get(second).changedBy().equals(creator.toString()),"terminal retry retains first operator");
        restarted.settle(first,false,creator);restarted.settle(first,false,funder);check(restarted.states().get(town).balances().get(Resource.WOOD)==100000&&restarted.held(town).get(Resource.WOOD)==0,"funded cancellation refunds the exact amount once");
        reject(()->restarted.fund(first,Map.of(),creator),"cancelled label cannot be silently reused");
        UUID used=ResourceRequests.id(town,"school");restarted.request(used,town,stock(30000,0),"school",creator,500);restarted.fund(used,Map.of(),funder);restarted.settle(used,true,creator);restarted.settle(used,true,funder);
        check(restarted.states().get(town).balances().get(Resource.WOOD)==70000&&restarted.held(town).get(Resource.WOOD)==0,"completed expense consumes already held stock without second debit");
        check(restarted.productionWeeks(town).values().stream().mapToLong(w->w.get("consumed_wood")).sum()==30000,"only actual consumption enters production history");
        UUID legacy=UUID.randomUUID();check(restarted.reserve(legacy,town,stock(5000,0),Map.of()),"old upkeep reserve remains usable alongside purpose plans");restarted.settle(legacy,false);
        var yaml=new YamlConfiguration();yaml.load(file.toFile());yaml.set("schema",3);for(String id:new ArrayList<>(yaml.getConfigurationSection("reservations").getKeys(false)))if(!id.equals(legacy.toString()))yaml.set("reservations."+id,null);yaml.save(file.toFile());
        var v3=new ResourcesRepository(file);v3.load();check(v3.states().get(town).balances().get(Resource.WOOD)==70000&&v3.reservations().get(legacy).purpose().isEmpty(),"v3 migration preserves stock and old terminal receipt");
        Path bad=root.resolve("blocked.yml");var blocked=new ResourcesRepository(bad);blocked.load();blocked.replace(Map.of(town,TownState.initial(stock(100000,0))));UUID pending=ResourceRequests.id(town,"blocked");blocked.request(pending,town,stock(20000,0),"blocked",creator,600);Files.delete(bad);Files.createDirectory(bad);Files.writeString(bad.resolve("sentinel"),"keep");
        reject(()->blocked.fund(pending,Map.of(),funder),"actual atomic replacement failure is rejected");check(blocked.states().get(town).balances().get(Resource.WOOD)==100000&&blocked.reservations().get(pending).status()==Reservation.Status.REQUESTED,"failed write publishes neither debit nor funded receipt");
        System.out.println("ResourceRequestsSmoke PASS "+checks+" checks");
    }
}
