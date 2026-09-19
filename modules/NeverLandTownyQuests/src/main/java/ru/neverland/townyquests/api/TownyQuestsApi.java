package ru.neverland.townyquests.api;

import java.util.*;
import ru.neverland.core.ApiContract;

/** Main-thread API. Immutable JDK-only city snapshots; no personal quest state. */
public interface TownyQuestsApi extends ApiContract {
    @Override default Set<String> capabilities() { return Set.of("unlocked", "quests"); }
    boolean unlocked(UUID town, String unlock);
    List<Map<String, Object>> quests(UUID town);
}
