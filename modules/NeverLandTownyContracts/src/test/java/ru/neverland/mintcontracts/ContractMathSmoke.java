package ru.neverland.mintcontracts;

import ru.neverland.mintcontracts.model.ActiveContract;
import java.util.Map;
import java.util.UUID;

public final class ContractMathSmoke {
    public static void main(String[] args) {
        UUID player = UUID.randomUUID();
        ActiveContract contract = new ActiveContract(UUID.randomUUID(), UUID.randomUUID(), "iron_reserve",
                1000, 5000, 0, 100, 5000, Map.of());
        if (contract.add(player, 35) != 35 || contract.progress() != 35) throw new AssertionError("progress");
        if (Math.abs(contract.ratio() - 0.35) > 0.00001) throw new AssertionError("ratio");
        if (contract.add(player, 100) != 65 || !contract.completed()) throw new AssertionError("goal clamp");
        if (contract.contributions().get(player) != 100) throw new AssertionError("contribution");
        System.out.println("ContractMathSmoke OK");
    }
}
