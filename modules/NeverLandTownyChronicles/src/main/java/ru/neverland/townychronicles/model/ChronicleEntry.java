package ru.neverland.townychronicles.model;

import java.util.List;
import java.util.UUID;

public record ChronicleEntry(UUID id,UUID townId,String townName,ChronicleCategory category,long timestamp,String title,List<String> details,String actor,String source){
    public ChronicleEntry{details=List.copyOf(details);actor=actor==null?"":actor;source=source==null?"":source;}
    public String shortId(){ synchronized (ru.neverland.core.SuiteState.lock()) {return id.toString().substring(0,8);} }
}
