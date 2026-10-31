package ru.neverland.mintcontracts.fishing;

import static ru.neverland.mintcontracts.fishing.WaterSurface.Cell.*;
import static ru.neverland.mintcontracts.fishing.WaterSurface.Result.*;

public final class WaterSurfaceSmoke {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static WaterSurface.Result square(int side) {
        int min = -(side / 2), max = min + side;
        return WaterSurface.inspect(0, 0, 8, 25, 5, (x, z) ->
                x >= min && x < max && z >= min && z < max ? WATER : OTHER);
    }
    public static void main(String[] args) {
        check(square(1) == SMALL, "single water block denied");
        check(square(2) == SMALL, "deep or shallow 2x2 pond denied");
        check(square(4) == SMALL, "4x4 pond below threshold denied");
        check(square(5) == LARGE, "5x5 one-deep pond allowed without native open-water flag");
        check(square(7) == LARGE, "large artificial pond allowed regardless of provenance");
        check(WaterSurface.inspect(0, 0, 8, 25, 5, (x, z) -> Math.abs(x) <= 1 ? WATER : OTHER) == SMALL,
                "long narrow canal cannot satisfy minimum width");
        check(WaterSurface.inspect(-1000, -1000, 8, 25, 5, (x, z) ->
                x >= -1002 && x <= -998 && z >= -1002 && z <= -998 ? WATER : OTHER) == LARGE,
                "negative coordinates retain distinct water cells");
        check(WaterSurface.inspect(0, 0, 8, 25, 5, (x, z) -> x == 1 ? WaterSurface.Cell.UNKNOWN : Math.abs(x) <= 2 && Math.abs(z) <= 2 ? WATER : OTHER) == WaterSurface.Result.UNKNOWN,
                "unowned region never proves a small pond");
        check(WaterSurface.inspect(0, 0, 8, 25, 5, (x, z) -> x >= 3 ? WaterSurface.Cell.UNKNOWN : Math.abs(x) <= 3 && Math.abs(z) <= 3 ? WATER : OTHER) == LARGE,
                "owned portion can prove large water despite an unknown neighbour");
        System.out.println("WATER_SURFACE_SMOKE_PASS: " + checks + " checks");
    }
}
