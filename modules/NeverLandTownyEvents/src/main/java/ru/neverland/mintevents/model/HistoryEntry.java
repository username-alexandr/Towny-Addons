package ru.neverland.mintevents.model;

public record HistoryEntry(String eventId, long startedAt, long endedAt, int progress, int goal, boolean success,java.util.UUID instanceId) {
    public HistoryEntry(String eventId,long startedAt,long endedAt,int progress,int goal,boolean success){this(eventId,startedAt,endedAt,progress,goal,success,null);}
}
