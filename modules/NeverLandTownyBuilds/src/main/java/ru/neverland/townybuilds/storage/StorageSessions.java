package ru.neverland.townybuilds.storage;
import java.util.*;
/** A late close/mirror from an old menu cannot release or overwrite a newer edit session. */
public final class StorageSessions {
    private record Key(UUID town,String project){}
    private final Map<Key,UUID> sessions=new HashMap<>();
    public boolean acquire(UUID town,String project,UUID session){return sessions.putIfAbsent(new Key(town,project),session)==null;}
    public boolean busy(UUID town,String project){return sessions.containsKey(new Key(town,project));}
    public boolean owns(UUID town,String project,UUID session){return session.equals(sessions.get(new Key(town,project)));}
    public boolean release(UUID town,String project,UUID session){return sessions.remove(new Key(town,project),session);}
}
