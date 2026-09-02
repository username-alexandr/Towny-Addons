package ru.neverland.townychronicles;

import ru.neverland.townychronicles.model.ChronicleCategory;
import ru.neverland.townychronicles.util.TimeUtil;

public final class ChronicleSmoke {
    public static void main(String[]args){if(ChronicleCategory.parse("войны")!=ChronicleCategory.WAR)throw new AssertionError("category");if(ChronicleCategory.parse("wonder")!=ChronicleCategory.WONDER)throw new AssertionError("wonder");if(TimeUtil.normalizedEpoch(1_700_000_000L)!=1_700_000_000_000L)throw new AssertionError("epoch");System.out.println("ChronicleSmoke OK");}
}
