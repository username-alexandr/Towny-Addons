package ru.neverland.townychronicles.api;

import ru.neverland.townychronicles.model.ChronicleCategory;
import ru.neverland.townychronicles.model.ChronicleEntry;
import java.util.List;
import java.util.UUID;

public interface TownyChroniclesApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("entries", "latest", "record"); }
    ChronicleEntry record(UUID townId,ChronicleCategory category,String title,List<String> details,String actor,String source);
    ChronicleEntry record(UUID townId,ChronicleCategory category,long timestamp,String title,List<String> details,String actor,String source);
    List<ChronicleEntry> entries(UUID townId);ChronicleEntry latest(UUID townId);
}
