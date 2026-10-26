package ru.neverland.mintespionage.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TownSpyData {
    private final UUID townId; private int networkLevel, defenseLevel; private final Map<String,Long> cooldowns=new HashMap<>();
    public TownSpyData(UUID townId){this.townId=townId;} public UUID townId(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return townId;} }
    public int networkLevel(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return networkLevel;} } public void networkLevel(int value){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {networkLevel=Math.max(0,value);} }
    public int defenseLevel(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return defenseLevel;} } public void defenseLevel(int value){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {defenseLevel=Math.max(0,value);} }
    public Map<String,Long> cooldowns(){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {return cooldowns;} } public long cooldown(String key){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {return cooldowns.getOrDefault(key,0L);} }
    public void cooldown(String key,long until){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {cooldowns.put(key,until);} }
}
