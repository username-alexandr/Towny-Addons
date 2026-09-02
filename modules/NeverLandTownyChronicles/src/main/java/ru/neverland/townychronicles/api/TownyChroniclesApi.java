package ru.neverland.townychronicles.api;

import ru.neverland.townychronicles.model.ChronicleCategory;
import ru.neverland.townychronicles.model.ChronicleEntry;
import java.util.List;
import java.util.UUID;

public interface TownyChroniclesApi {
    ChronicleEntry record(UUID townId,ChronicleCategory category,String title,List<String> details,String actor,String source);
    ChronicleEntry record(UUID townId,ChronicleCategory category,long timestamp,String title,List<String> details,String actor,String source);
    List<ChronicleEntry> entries(UUID townId);ChronicleEntry latest(UUID townId);
}
