package ru.neverland.mintespionage.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TownSpyData {
    private final UUID townId; private int networkLevel, defenseLevel; private final Map<String,Long> cooldowns=new HashMap<>();
    public TownSpyData(UUID townId){this.townId=townId;} public UUID townId(){ synchronized (ru.neverland.core.SuiteState.lock()) {return townId;} }
    public int networkLevel(){ synchronized (ru.neverland.core.SuiteState.lock()) {return networkLevel;} } public void networkLevel(int value){ synchronized (ru.neverland.core.SuiteState.lock()) {networkLevel=Math.max(0,value);} }
    public int defenseLevel(){ synchronized (ru.neverland.core.SuiteState.lock()) {return defenseLevel;} } public void defenseLevel(int value){ synchronized (ru.neverland.core.SuiteState.lock()) {defenseLevel=Math.max(0,value);} }
    public Map<String,Long> cooldowns(){ synchronized (ru.neverland.core.SuiteState.lock()) {return cooldowns;} } public long cooldown(String key){ synchronized (ru.neverland.core.SuiteState.lock()) {return cooldowns.getOrDefault(key,0L);} }
    public void cooldown(String key,long until){ synchronized (ru.neverland.core.SuiteState.lock()) {cooldowns.put(key,until);} }
}
