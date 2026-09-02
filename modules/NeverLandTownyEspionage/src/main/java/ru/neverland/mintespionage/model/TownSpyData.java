package ru.neverland.mintespionage.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TownSpyData {
    private final UUID townId; private int networkLevel, defenseLevel; private final Map<String,Long> cooldowns=new HashMap<>();
    public TownSpyData(UUID townId){this.townId=townId;} public UUID townId(){return townId;}
    public int networkLevel(){return networkLevel;} public void networkLevel(int value){networkLevel=Math.max(0,value);}
    public int defenseLevel(){return defenseLevel;} public void defenseLevel(int value){defenseLevel=Math.max(0,value);}
    public Map<String,Long> cooldowns(){return cooldowns;} public long cooldown(String key){return cooldowns.getOrDefault(key,0L);}
    public void cooldown(String key,long until){cooldowns.put(key,until);}
}
