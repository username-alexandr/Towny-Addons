package ru.neverland.townybuilds.storage;
import java.util.*;
/** A late close/mirror from an old menu cannot release or overwrite a newer edit session. */
public final class StorageSessions {
    private record Key(UUID town,String project){}
    private final Map<Key,UUID> sessions=new HashMap<>();
    public boolean any(){try(var guard=ru.neverland.core.SuiteState.guard()){return !sessions.isEmpty();}}
    public boolean acquire(UUID town,String project,UUID session){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return sessions.putIfAbsent(new Key(town,project),session)==null;} }
    public boolean busy(UUID town,String project){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return sessions.containsKey(new Key(town,project));} }
    public boolean owns(UUID town,String project,UUID session){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return session.equals(sessions.get(new Key(town,project)));} }
    public boolean release(UUID town,String project,UUID session){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return sessions.remove(new Key(town,project),session);} }
}
