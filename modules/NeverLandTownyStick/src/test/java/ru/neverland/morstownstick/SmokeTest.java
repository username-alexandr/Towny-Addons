package ru.neverland.morstownstick;

import ru.neverland.morstownstick.model.CellKey;
import ru.neverland.morstownstick.integration.TownCommandInterceptor;
import ru.neverland.morstownstick.service.BfsPlanner;
import ru.neverland.morstownstick.service.BorderGeometry;
import ru.neverland.morstownstick.util.ColorUtil;

import java.util.List;
import java.util.Set;

public final class SmokeTest {
    public static void main(String[] args) {
        CellKey town = new CellKey("world", 0, 0);
        CellKey first = new CellKey("world", 1, 0);
        CellKey second = new CellKey("world", 2, 0);
        CellKey isolated = new CellKey("world", 10, 10);
        BfsPlanner.Plan plan = BfsPlanner.plan(List.of(second, isolated, first), Set.of(town));
        if (!plan.ordered().equals(List.of(first, second))) throw new AssertionError("BFS order: " + plan.ordered());
        if (!plan.unreachable().equals(List.of(isolated))) throw new AssertionError("BFS unreachable");
        if (BorderGeometry.outsideEdges(List.of(town, first), 16).size() != 6)
            throw new AssertionError("Shared town edge was not removed");
        if (BorderGeometry.individualEdges(List.of(town, first), 16).size() != 8)
            throw new AssertionError("Individual selection edges were removed");
        if (!ColorUtil.legacy("&#55FF55Тест &cцвета").equals("§x§5§5§F§F§5§5Тест §cцвета"))
            throw new AssertionError("HEX/legacy colour conversion failed");
        if (!TownCommandInterceptor.isPlainClaim(new String[]{"claim"})
                || !TownCommandInterceptor.isPlainClaim(new String[]{"CLAIM"})
                || TownCommandInterceptor.isPlainClaim(new String[]{"claim", "outpost"}))
            throw new AssertionError("Town command interception matcher failed");
        if (!TownCommandInterceptor.isStickCommand(new String[]{"stick", "list"})
                || TownCommandInterceptor.isStickCommand(new String[]{"claim"})
                || !List.of(TownCommandInterceptor.stickArguments(new String[]{"stick", "list"})).equals(List.of("list"))
                || TownCommandInterceptor.stickArguments(new String[]{"stick"}).length != 0)
            throw new AssertionError("Town stick command routing failed");
        System.out.println("MORSTownStick smoke tests passed");
    }
}
