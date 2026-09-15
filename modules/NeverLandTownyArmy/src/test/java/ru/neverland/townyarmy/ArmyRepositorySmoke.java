package ru.neverland.townyarmy;
import java.util.*;
import java.nio.file.*;
import static ru.neverland.townyarmy.ArmyModel.*;
public final class ArmyRepositorySmoke {
    interface Work { void run() throws Exception; }
    static void check(boolean b) { if (!b) throw new AssertionError(); }
    static void denied(Work work) throws Exception { try { work.run(); } catch (Exception expected) { return; } throw new AssertionError("Expected refusal"); }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("army-store"); Path file = dir.resolve("army.yml"); var r = new ArmyRepository(file,100); r.load();
        UUID town=UUID.randomUUID(),id=UUID.randomUUID(); var soldier=Soldier.applicant(id,town,Unit.INFANTRY,1).swear().equipment(100); var d=r.draft();d.imported=true;d.handoff=true;d.soldier(soldier);d.cities.put(town,d.city(town).base(new Base(UUID.randomUUID(),1.5,64,2.5)).stock(Map.of("food",1000L)));
        var tx=new Transfer(UUID.randomUUID(),town,id,Map.of("metal",1000L),Phase.PLANNED,1);d.transfers.put(tx.id(),tx);r.commit(d,town,id,id,"TEST","Repository fixture",1);r.apply(tx.id());
        var copy=new ArmyRepository(file,100);copy.load();check(copy.state().equals(r.state()));check(copy.transfer(tx.id()).phase()==Phase.APPLIED);check(copy.state().cities().get(town).stock().get("metal")==1000);denied(()->copy.apply(tx.id()));
        byte[] good=Files.readAllBytes(file);Files.writeString(file,new String(good,java.nio.charset.StandardCharsets.UTF_8).replace("training: 0","training: 0.5"));denied(copy::load);check(!copy.writable()&&copy.state().equals(r.state()));denied(()->copy.phase(tx.phase(Phase.CLOSED)));Files.write(file,good);copy.load();check(copy.writable());
        Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("block"),"fixture");var before=r.state();denied(()->r.phase(tx.phase(Phase.CLOSED)));check(!r.writable()&&r.state().equals(before));
        denied(()->new State(false,true,Map.of(),Map.of(),Map.of(),Map.of(),List.of()));
        denied(()->new State(true,true,Map.of(),Map.of(id,soldier),Map.of(),Map.of(),List.of()));
        var first=soldier.rank(Rank.GENERAL,2);var second=Soldier.applicant(UUID.randomUUID(),town,Unit.INFANTRY,2).swear().rank(Rank.GENERAL,2);
        denied(()->new State(true,true,Map.of(),Map.of(first.resident(),first,second.resident(),second),Map.of(town,City.empty(town)),Map.of(),List.of()));
        denied(()->ArmySettings.amount("1.0001"));denied(()->ArmySettings.amount("NaN"));denied(()->ArmySettings.amount("-1"));denied(()->add(Map.of("metal",STOCK_LIMIT),Map.of("metal",1L)));denied(()->subtract(Map.of("metal",1L),Map.of("metal",2L)));
        System.out.println("Army repository: restart, atomic credit, strict corruption, failed writes and invariants PASS");
    }
}
