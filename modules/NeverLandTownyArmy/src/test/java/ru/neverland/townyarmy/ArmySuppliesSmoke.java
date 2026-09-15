package ru.neverland.townyarmy;
import java.util.*;
import java.io.IOException;
import static ru.neverland.townyarmy.ArmyModel.*;
public final class ArmySuppliesSmoke {
    private static final class Faults {
        final int crash; int step; boolean fired;
        Faults(int crash) { this.crash = crash; }
        void point() throws IOException { if (++step == crash && !fired) { fired = true; throw new IOException("Simulated lost reply / process failure"); } }
    }
    private static final class Store implements ArmySupplies.Store {
        final Faults faults; Transfer transfer; long credited; int credits;
        Store(Faults f) { faults = f; transfer = new Transfer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Map.of("metal", 5000L), Phase.PLANNED, 1); }
        public Transfer transfer(UUID id) { return transfer; }
        public void phase(Transfer t) throws Exception { faults.point(); transfer = t; faults.point(); }
        public void apply(UUID id) throws Exception { faults.point(); if (transfer.phase() != Phase.PLANNED) throw new AssertionError("Duplicate credit"); credited += 5000; credits++; transfer = transfer.phase(Phase.APPLIED); faults.point(); }
    }
    private static final class Resources implements ArmySupplies.Resources {
        final Faults faults; String status = "NONE"; long available = 20000; int debits;
        Resources(Faults f) { faults = f; }
        public String status(UUID id) throws Exception { faults.point(); String value = status; faults.point(); return value; }
        public boolean reserve(UUID id, UUID town, Map<String,Long> amounts) throws Exception { faults.point(); if (available < 5000) return false; if (status.equals("NONE")) { available -= 5000; debits++; status = "HELD"; } faults.point(); return true; }
        public void consume(UUID id) throws Exception { faults.point(); if (!status.equals("HELD") && !status.equals("CONSUMED")) throw new AssertionError("Consume without reservation"); status = "CONSUMED"; faults.point(); }
        public void forget(UUID id) throws Exception { faults.point(); if (status.equals("HELD")) throw new AssertionError("Receipt forgotten before consume"); status = "NONE"; faults.point(); }
    }
    private static void complete(Store s, Resources r) throws Exception { for (int i = 0; i < 20 && s.transfer.phase() != Phase.CLOSED && s.transfer.phase() != Phase.CANCELLED; i++) try { ArmySupplies.resume(s.transfer.id(), s, r); } catch (IOException simulatedCrash) { } }
    public static void main(String[] args) throws Exception {
        for (int crash = 1; crash <= 16; crash++) {
            var faults = new Faults(crash); var s = new Store(faults); var r = new Resources(faults); complete(s, r);
            if (!faults.fired || s.transfer.phase() != Phase.CLOSED || s.credits != 1 || s.credited != 5000 || r.available != 15000 || r.debits != 1 || !r.status.equals("NONE")) throw new AssertionError("Failure boundary " + crash);
            ArmySupplies.resume(s.transfer.id(), s, r); if (s.credits != 1 || r.debits != 1) throw new AssertionError("Replay");
        }
        var f = new Faults(0); var s = new Store(f); var r = new Resources(f); r.available = 100; complete(s,r);
        if (s.transfer.phase() != Phase.CANCELLED || s.credits != 0 || r.debits != 0 || r.available != 100) throw new AssertionError("Declined supply mutated resources");
        var bad = new Store(f); r.status = "UNKNOWN"; try { ArmySupplies.resume(bad.transfer.id(),bad,r); throw new AssertionError("Unknown status accepted"); } catch (IllegalStateException expected) { }
        if (bad.transfer.phase() != Phase.PLANNED || bad.credits != 0) throw new AssertionError("Unknown result changed credit");
        System.out.println("Army supply: 16 crash boundaries, declined stock and unknown result PASS");
    }
}
