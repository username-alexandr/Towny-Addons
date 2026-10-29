package ru.neverland.townyarmy;
import java.util.*;
import java.nio.file.*;
import static ru.neverland.townyarmy.ArmyModel.*;
import static ru.neverland.townyarmy.ArmyRepositorySmoke.*;
public final class ArmyRaidSupplySmoke {
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("army-raid-supplies"),file=dir.resolve("army.yml");
        var repo=new ArmyRepository(file,100);repo.load();UUID town=UUID.randomUUID(),receipt=UUID.randomUUID();
        var cost=Map.of("food",1000L,"metal",250L);var d=repo.draft();d.imported=true;d.handoff=true;
        d.cities.put(town,City.empty(town).stock(Map.of("food",3000L,"metal",1000L)));done(repo.commit(d));
        check(done(repo.spendRaid(town,receipt,cost,1)));check(done(repo.spendRaid(town,receipt,cost,2)));
        check(repo.state().cities().get(town).stock().equals(Map.of("food",2000L,"metal",750L)));
        var copy=new ArmyRepository(file,100);copy.load();check(done(copy.spendRaid(town,receipt,cost,3))&&copy.state().equals(repo.state()));
        denied(()->done(copy.spendRaid(town,receipt,Map.of("food",2000L),4)));
        check(!done(copy.spendRaid(town,UUID.randomUUID(),Map.of("food",9999L),5)));
        var before=copy.state();Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("block"),"fixture");
        denied(()->done(copy.spendRaid(town,UUID.randomUUID(),Map.of("food",1000L),6)));check(copy.state().equals(before));
        done(repo.close());done(copy.close());
        System.out.println("ArmyRaidSupplySmoke PASS: atomic debit, insufficient stock, replay, restart, mismatch and disk failure");
    }
}
