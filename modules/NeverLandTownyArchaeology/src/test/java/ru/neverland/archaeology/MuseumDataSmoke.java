package ru.neverland.archaeology;

import ru.neverland.archaeology.model.TownMuseumData;

import java.util.Map;
import java.util.UUID;

public final class MuseumDataSmoke {
    public static void main(String[] args) { TownMuseumData museum = new TownMuseumData(UUID.randomUUID(), "NeverLand"); museum.add("sun_tablet", 3, 25, UUID.randomUUID()); museum.add("pharaoh_mask", 1, 60, null); if (museum.points() != 135 || museum.donated("sun_tablet") != 3) throw new AssertionError("donation failed"); if (!museum.consume(Map.of("sun_tablet", 2, "pharaoh_mask", 1))) throw new AssertionError("valid consume failed"); if (museum.available("sun_tablet") != 1 || museum.donated("sun_tablet") != 3) throw new AssertionError("catalog must survive spending"); if (museum.consume(Map.of("sun_tablet", 2))) throw new AssertionError("overspend accepted"); }
}
